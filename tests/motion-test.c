#define main router_entry
#include "router-mocks.h"
#undef main
#include <assert.h>

static float float_at(const unsigned char *packet,int offset){
 uint32_t bits=dsu_read(packet+offset,4);
 float value;
 memcpy(&value,&bits,sizeof(value));
 return value;
}

int main(void){
 int client=socket(AF_INET,SOCK_DGRAM,0);
 dsu_fd=socket(AF_INET,SOCK_DGRAM,0);
 assert(client>=0&&dsu_fd>=0);
 struct sockaddr_in address={.sin_family=AF_INET,.sin_addr={.s_addr=htonl(INADDR_LOOPBACK)}};
 assert(bind(client,(void *)&address,sizeof(address))==0);
 socklen_t length=sizeof(address);
 assert(getsockname(client,(void *)&address,&length)==0);
 struct timeval wait={.tv_sec=1};
 assert(setsockopt(client,SOL_SOCKET,SO_RCVTIMEO,&wait,sizeof(wait))==0);
 unsigned char packet[100];
 output=OUTPUT_EDEN;
 for(int i=0;i<MAX;i++)pads[i].fd=-1;
 steam_present=true;
 // A Bluetooth-only lineup must work immediately, even before Thor access is ready.
 int replies[2];assert(socketpair(AF_UNIX,SOCK_STREAM,0,replies)==0);
 for(int i=0;i<4;i++)vfd[i]=-1;
 char external_p1[]="1000000000:0:0";
 apply(replies[0],external_p1,OUTPUT_EDEN);
 assert(output==OUTPUT_EDEN&&routing()&&steam_slot==0);
 dsu_send_slot(&address,0);
 assert(recv(client,packet,sizeof(packet),0)==100);
 assert(packet[20]==0&&packet[31]==1);
 eden_idle();
 assert(!routing()&&steam_slot==-1);
 close(replies[0]);close(replies[1]);
 steam_slot=2;
 motion_command("1000000000 123456789 0 1 -0.5 90 -45 180");
 dsu_send_slot(&address,2);
 assert(recv(client,packet,sizeof(packet),0)==100);
 assert(packet[20]==2&&packet[22]==2&&packet[31]==1);
 assert(dsu_read(packet+68,4)==123456789);
 assert(float_at(packet,80)==1.0f&&float_at(packet,84)==-0.5f);
 assert(float_at(packet,88)==90.0f&&float_at(packet,96)==180.0f);
 motion_command("1000000000 123456788 0 0 0 0 0 0");
 motion_command("1000000000 123456790 nan 0 0 0 0 0");
 assert(steam_motion.timestamp_us==123456789&&steam_motion.gyroscope[0]==90.0f);
 steam_slot=1;push_slot(2);push_slot(1);
 dsu_send_slot(&address,1);
 assert(recv(client,packet,sizeof(packet),0)==100);
 assert(packet[20]==1&&packet[22]==2&&float_at(packet,88)==90.0f);
 dsu_send_slot(&address,2);
 assert(recv(client,packet,sizeof(packet),0)==100);
 assert(packet[31]==0&&float_at(packet,88)==0.0f);
 eden_idle();
 dsu_send_slot(&address,1);
 assert(recv(client,packet,sizeof(packet),0)==100);
 assert(packet[31]==0&&dsu_read(packet+68,4)==0&&float_at(packet,88)==0.0f);
 dsu_stop();close(client);
 puts("Motion values, timestamps, invalid samples, seat changes and stop cleanup: pass");
 return 0;
}
