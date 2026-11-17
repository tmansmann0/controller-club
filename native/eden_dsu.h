#ifndef CLUB_EDEN_DSU_H
#define CLUB_EDEN_DSU_H

/* Eden's Cemuhook client consumes normalized states on fixed loopback slots. */
#define DSU_PORT 37821
#define DSU_CLIENTS 4
#define DSU_VERSION 1001
#define DSU_GET_VERSION 0x100000
#define DSU_GET_SLOTS 0x100001
#define DSU_GET_DATA 0x100002

typedef struct {
 struct sockaddr_in peer;
 unsigned slots;
 long long expires;
} DsuClient;
static DsuClient dsu_clients[DSU_CLIENTS];
static int dsu_fd=-1;
static uint32_t dsu_sequence;
static State dsu_state[4];
static Motion dsu_motion[4];
static bool dsu_connected[4];

static void dsu_write(unsigned char *at,uint32_t value,int bytes){
 for(int i=0;i<bytes;i++)at[i]=(unsigned char)(value>>(8*i));
}
static uint32_t dsu_read(const unsigned char *at,int bytes){
 uint32_t value=0;
 for(int i=0;i<bytes;i++)value|=(uint32_t)at[i]<<(8*i);
 return value;
}
static uint32_t dsu_crc(const unsigned char *packet,size_t size){
 uint32_t crc=UINT32_MAX;
 for(size_t i=0;i<size;i++){
   crc^=packet[i];
   for(int bit=0;bit<8;bit++)crc=(crc>>1)^((crc&1)?UINT32_C(0xedb88320):0);
 }
 return ~crc;
}
static void dsu_reply(const struct sockaddr_in *peer,unsigned char *packet,size_t size,uint32_t type){
 memcpy(packet,"DSUS",4);
 dsu_write(packet+4,DSU_VERSION,2);
 dsu_write(packet+6,(uint32_t)size-16,2);
 dsu_write(packet+12,0x434c5542,4);
 dsu_write(packet+16,type,4);
 dsu_write(packet+8,dsu_crc(packet,size),4);
 sendto(dsu_fd,packet,size,MSG_DONTWAIT,(const struct sockaddr *)peer,sizeof(*peer));
}
static void dsu_port_info(unsigned char *packet,int slot,bool connected){
 packet[20]=(unsigned char)slot;
 if(!connected)return;
 packet[21]=2; /* connected */
 packet[22]=dsu_motion[slot].timestamp_us?2:1;
 packet[23]=1;
 packet[24]=0x02; /* fixed locally administered ID for this player slot */
 packet[29]=(unsigned char)slot;
 packet[30]=5;
}
static unsigned char dsu_stick(int value){
 return (unsigned char)(value<=0?127+value*127/32767:127+value*128/32767);
}
static void dsu_send_slot(const struct sockaddr_in *peer,int slot){
 unsigned char packet[100]={0};
 bool connected=dsu_connected[slot];
 State state=connected?dsu_state[slot]:(State){0};
 dsu_port_info(packet,slot,connected);
 packet[31]=(unsigned char)connected;
 dsu_write(packet+32,++dsu_sequence,4);
 unsigned buttons=0;
 /* Xbox face buttons are mapped by position to the corresponding Switch buttons. */
 static const unsigned mask[]={0x4000,0x2000,0x8000,0x1000,0x0400,0x0800,0x0100,0x0200,
                              0x0001,0x0008,0x0002,0x0004};
 for(size_t i=0;i<sizeof(mask)/sizeof(*mask);i++)if(state.k&(1u<<i))buttons|=mask[i];
 if(state.a[4]>16383)buttons|=0x0100;
 if(state.a[5]>16383)buttons|=0x0200;
 if(state.a[6]<0)buttons|=0x0080;
 if(state.a[6]>0)buttons|=0x0020;
 if(state.a[7]<0)buttons|=0x0010;
 if(state.a[7]>0)buttons|=0x0040;
 dsu_write(packet+36,buttons,2);
 packet[38]=(state.k>>14)&1;
 packet[40]=dsu_stick(state.a[0]);
 packet[41]=dsu_stick(-state.a[1]);
 packet[42]=dsu_stick(state.a[2]);
 packet[43]=dsu_stick(-state.a[3]);
 packet[54]=(unsigned char)(state.a[5]*255/32767);
 packet[55]=(unsigned char)(state.a[4]*255/32767);
 Motion motion=connected?dsu_motion[slot]:(Motion){0};
 for(int i=0;i<8;i++)packet[68+i]=(unsigned char)(motion.timestamp_us>>(8*i));
 for(int i=0;i<3;i++){
   uint32_t bits;
   memcpy(&bits,&motion.acceleration[i],sizeof(bits));
   dsu_write(packet+76+4*i,bits,4);
   memcpy(&bits,&motion.gyroscope[i],sizeof(bits));
   dsu_write(packet+88+4*i,bits,4);
 }
 dsu_reply(peer,packet,sizeof(packet),DSU_GET_DATA);
}
static void dsu_publish(int slot){
 for(int i=0;i<DSU_CLIENTS;i++)
   if(dsu_clients[i].expires>now_ms()&&(dsu_clients[i].slots&(1u<<slot)))
     dsu_send_slot(&dsu_clients[i].peer,slot);
}
static void dsu_update(int slot,State state,Motion motion,bool connected){
 if(dsu_fd<0||slot<0||slot>=4)return;
 bool changed=dsu_connected[slot]!=connected||memcmp(&dsu_state[slot],&state,sizeof(state))
     ||motion.timestamp_us!=dsu_motion[slot].timestamp_us;
 dsu_connected[slot]=connected;
 dsu_state[slot]=state;
 dsu_motion[slot]=motion;
 if(changed)dsu_publish(slot);
}
static bool dsu_start(void){
 if(dsu_fd>=0)return true;
 int fd=socket(AF_INET,SOCK_DGRAM|SOCK_NONBLOCK|SOCK_CLOEXEC,0);
 if(fd<0)return false;
 struct sockaddr_in addr={.sin_family=AF_INET,.sin_port=htons(DSU_PORT),.sin_addr={.s_addr=htonl(INADDR_LOOPBACK)}};
 if(bind(fd,(void *)&addr,sizeof(addr))<0){close(fd);return false;}
 dsu_fd=fd;
 dsu_sequence=(uint32_t)now_ms();
 return true;
}
static void dsu_stop(void){
 if(dsu_fd<0)return;
 for(int slot=0;slot<4;slot++)
   if(dsu_connected[slot])dsu_update(slot,(State){0},(Motion){0},false);
 close(dsu_fd);
 dsu_fd=-1;
 memset(dsu_clients,0,sizeof(dsu_clients));
}
static void dsu_register(const struct sockaddr_in *peer,unsigned slots){
 long long now=now_ms();
 int entry=-1;
 for(int i=0;i<DSU_CLIENTS;i++){
   if(dsu_clients[i].peer.sin_port==peer->sin_port){entry=i;break;}
   if(dsu_clients[i].expires<=now&&entry<0)entry=i;
 }
 if(entry<0)return;
 dsu_clients[entry]=(DsuClient){*peer,slots,now+9000};
 for(int slot=0;slot<4;slot++)
   if(dsu_connected[slot]&&(slots&(1u<<slot)))dsu_send_slot(peer,slot);
}
static void dsu_receive(void){
 for(int i=0;i<8;i++){
   unsigned char input[128];
   struct sockaddr_in peer={0};
   socklen_t len=sizeof(peer);
   ssize_t bytes=recvfrom(dsu_fd,input,sizeof(input),MSG_DONTWAIT|MSG_TRUNC,(void *)&peer,&len);
   if(bytes<0)return;
   if(bytes<20||bytes>(ssize_t)sizeof(input)||peer.sin_addr.s_addr!=htonl(INADDR_LOOPBACK)
      ||memcmp(input,"DSUC",4)||dsu_read(input+4,2)>DSU_VERSION
      ||dsu_read(input+6,2)!=(uint32_t)(bytes-16))continue;
   uint32_t crc=dsu_read(input+8,4);
   memset(input+8,0,4);
   if(dsu_crc(input,(size_t)bytes)!=crc)continue;
   uint32_t type=dsu_read(input+16,4);
   if(type==DSU_GET_VERSION&&bytes==20){
     unsigned char packet[22]={0};dsu_write(packet+20,DSU_VERSION,2);
     dsu_reply(&peer,packet,sizeof(packet),type);
   }else if(type==DSU_GET_SLOTS&&bytes>=24){
     uint32_t count=dsu_read(input+20,4);
     if(count>4||bytes<(ssize_t)(24+count))continue;
     for(uint32_t j=0;j<count;j++){
       unsigned slot=input[24+j];
       if(slot>=4)continue;
       unsigned char packet[32]={0};
       dsu_port_info(packet,(int)slot,dsu_connected[slot]);
       dsu_reply(&peer,packet,sizeof(packet),type);
     }
   }else if(type==DSU_GET_DATA&&bytes==28){
     unsigned flags=input[20],slot=input[21];
     if(flags==0)dsu_register(&peer,0x0f);
     else if(flags==1&&slot<4)dsu_register(&peer,1u<<slot);
     else if(flags==2&&input[22]==0x02&&input[27]<4)dsu_register(&peer,1u<<input[27]);
   }
 }
}

#endif
