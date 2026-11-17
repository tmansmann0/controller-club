#define _GNU_SOURCE
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <glob.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <math.h>
#include <netinet/in.h>
#include <poll.h>
#include <signal.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define PORT 37819
#define MAX 24
#define AXES 8
#define KEYS 15
#define BITS(n) (((n)+8)/8)
static int bit(const unsigned char *s,int n){ return (s[n/8]>>(n%8))&1; }
static const int club_axis[AXES]={ABS_X,ABS_Y,ABS_Z,ABS_RZ,ABS_BRAKE,ABS_GAS,ABS_HAT0X,ABS_HAT0Y};
static const int xbox_axis[AXES]={ABS_X,ABS_Y,ABS_RX,ABS_RY,ABS_Z,ABS_RZ,ABS_HAT0X,ABS_HAT0Y};
/* These Xbox models have the same Android layout and give each seat a stable descriptor. */
static const unsigned short xbox_product[4]={0x028e,0x028f,0x02a1,0x02d1};
static const int system_key[]={KEY_HOME,KEY_VOLUMEDOWN,KEY_VOLUMEUP,KEY_BACK,KEY_APPSELECT};
static const int key[KEYS]={BTN_SOUTH,BTN_EAST,BTN_NORTH,BTN_WEST,BTN_TL,BTN_TR,BTN_TL2,BTN_TR2,BTN_SELECT,BTN_START,BTN_THUMBL,BTN_THUMBR,BTN_DPAD_UP,BTN_DPAD_DOWN,BTN_MODE};
typedef struct {int a[AXES]; unsigned k;} State;
typedef struct {
 uint64_t timestamp_us;
 float acceleration[3], gyroscope[3];
} Motion;
typedef struct {
 Motion motion;
 int fd,serial,slot,side,style,vendor,product; bool grabbed,builtin,drop; dev_t dev; ino_t inode;
 char path[96], name[96], unique[96]; int raw[ABS_MAX+1]; struct input_absinfo abs[ABS_MAX+1];
 unsigned char caps[BITS(ABS_MAX)], keys[BITS(KEY_MAX)];
} Pad;
static Pad pads[MAX];
static int vfd[4]={-1,-1,-1,-1}, nextid=1, rev=0;
static State previous[4], steam_state;
static Motion steam_motion;
enum Output { OUTPUT_PROFILES, OUTPUT_XBOX, OUTPUT_EDEN };
static enum Output output;
static bool direct_active;
static int system_fd=-1;
static unsigned previous_system_keys;
#define STEAM_PAD_ID 1000000000
static bool steam_present;
static int steam_slot=-1;
static char steam_identity[17];
static volatile sig_atomic_t done;
static long long now_ms(void){struct timespec t; clock_gettime(CLOCK_BOOTTIME,&t); return (long long)t.tv_sec*1000+t.tv_nsec/1000000;}
#include "eden_dsu.h"
static int clamp(int x,int lo,int hi){return x<lo?lo:(x>hi?hi:x);}
static void stop_sig(int x){(void)x;done=1;}
static void emit(int fd,int type,int code,int value){
 struct input_event e={.type=type,.code=code,.value=value};
 if(write(fd,&e,sizeof(e))!=(ssize_t)sizeof(e)) done=1;
}
static void set_axis(int fd,int code,int lo,int hi){
 struct uinput_abs_setup a={.code=code,.absinfo={.minimum=lo,.maximum=hi}};
 if(ioctl(fd,UI_SET_ABSBIT,code)<0 || ioctl(fd,UI_ABS_SETUP,&a)<0) done=1;
}
static int make_pad(int slot,bool xbox){
 int fd=open("/dev/uinput",O_WRONLY|O_NONBLOCK|O_CLOEXEC);
 if(fd<0)return -1;
 if(ioctl(fd,UI_SET_EVBIT,EV_KEY)<0 || ioctl(fd,UI_SET_EVBIT,EV_ABS)<0) goto bad;
 for(int i=0;i<KEYS;i++) if(i<12 || i==14) if(ioctl(fd,UI_SET_KEYBIT,key[i])<0)goto bad;
 struct uinput_setup u={.id={.bustype=BUS_USB,.vendor=0x1209,.product=0x5440+slot,.version=1}};
 if(xbox){u.id.vendor=0x045e;u.id.product=xbox_product[slot];}
 snprintf(u.name,sizeof(u.name),xbox?"Xbox Controller P%d":"Controller Club P%d",slot+1);
 const int *axis=xbox?xbox_axis:club_axis;
 for(int i=0;i<AXES;i++)set_axis(fd,axis[i],i<4?-32767:(i<6?0:-1),i<6?32767:1);
 if(done || ioctl(fd,UI_DEV_SETUP,&u)<0 || ioctl(fd,UI_DEV_CREATE)<0)goto bad;
 return fd;
 bad: close(fd);return -1;
}
static int make_system_keys(void){
 int fd=open("/dev/uinput",O_WRONLY|O_NONBLOCK|O_CLOEXEC);
 if(fd<0)return -1;
 struct uinput_setup u={.id={.bustype=BUS_USB,.vendor=0x1209,.product=0x5444,.version=1},
                        .name="Controller Club system keys"};
 if(ioctl(fd,UI_SET_EVBIT,EV_KEY)<0)goto bad;
 for(size_t i=0;i<sizeof(system_key)/sizeof(*system_key);i++)
   if(ioctl(fd,UI_SET_KEYBIT,system_key[i]==KEY_HOME?KEY_HOMEPAGE:system_key[i])<0)goto bad;
 if(ioctl(fd,UI_DEV_SETUP,&u)<0||ioctl(fd,UI_DEV_CREATE)<0)goto bad;
 return fd;
 bad: close(fd);return -1;
}
static void destroy_virtual(int *fd){
 if(*fd<0)return;
 ioctl(*fd,UI_DEV_DESTROY);close(*fd);*fd=-1;
}
static bool routing(void){
 if(direct_active)return true;
 for(int n=0;n<4;n++)if(vfd[n]>=0)return true;
 return false;
}
static void snapshot(Pad *p){
 memset(p->keys,0,sizeof(p->keys));
 ioctl(p->fd,EVIOCGKEY(sizeof(p->keys)),p->keys);
 for(int i=0;i<=ABS_MAX;i++)if(bit(p->caps,i)&&ioctl(p->fd,EVIOCGABS(i),&p->abs[i])==0)p->raw[i]=p->abs[i].value;
}
static int scale(Pad *p,int i,bool trigger){
 if(!bit(p->caps,i))return 0;
 long lo=p->abs[i].minimum,hi=p->abs[i].maximum;
 if(lo>=hi)return 0;
 long r=clamp(p->raw[i],lo,hi);
 return trigger?(int)((r-lo)*32767/(hi-lo)):(int)((r-lo)*65534/(hi-lo)-32767);
}
static int getk(Pad *p,int k){return bit(p->keys,k);}
static void put(State *s,int i,int val){ if(val)s->k|=1u<<i; }
static int prefers(Pad *p,int a,int b){return bit(p->caps,a)?a:b;}
static State map(Pad *p){
 State s={0};
 int x=scale(p,ABS_X,false), y=scale(p,ABS_Y,false);
 int hx=clamp(p->raw[ABS_HAT0X],-1,1), hy=clamp(p->raw[ABS_HAT0Y],-1,1);
 if(p->side && !bit(p->caps,ABS_X) && bit(p->caps,ABS_RX)){
    x=scale(p,ABS_RX,false); y=scale(p,ABS_RY,false);
 }
 int up=getk(p,BTN_DPAD_UP)||getk(p,BTN_TRIGGER_HAPPY3)||hy<0;
 int down=getk(p,BTN_DPAD_DOWN)||getk(p,BTN_TRIGGER_HAPPY4)||hy>0;
 int left=getk(p,BTN_DPAD_LEFT)||getk(p,BTN_TRIGGER_HAPPY1)||hx<0;
 int right=getk(p,BTN_DPAD_RIGHT)||getk(p,BTN_TRIGGER_HAPPY2)||hx>0;
 if(!p->side){
   for(int i=0;i<KEYS;i++) put(&s,i,getk(p,key[i]));
   s.a[0]=x;s.a[1]=y;
   s.a[2]=scale(p,prefers(p,ABS_RX,ABS_Z),false);
   s.a[3]=scale(p,prefers(p,ABS_RY,ABS_RZ),false);
   s.a[4]=scale(p,prefers(p,ABS_BRAKE,ABS_Z),true);
   s.a[5]=scale(p,prefers(p,ABS_GAS,ABS_RZ),true);
   if(!bit(p->caps,ABS_RX)&&!bit(p->caps,ABS_BRAKE))s.a[4]=0;
   if(!bit(p->caps,ABS_RY)&&!bit(p->caps,ABS_GAS))s.a[5]=0;
   s.a[6]=right-left;s.a[7]=down-up;
 }else if(p->style==1){ // one upright half of a combined controller
   if(p->side==1){
     s.a[0]=x;s.a[1]=y;s.a[6]=right-left;s.a[7]=down-up;
     put(&s,4,getk(p,BTN_TL));put(&s,6,getk(p,BTN_TL2));
     put(&s,8,getk(p,BTN_SELECT));put(&s,10,getk(p,BTN_THUMBL));
   }else{
     s.a[2]=x;s.a[3]=y;
     for(int i=0;i<4;i++)put(&s,i,getk(p,key[i]));
     put(&s,5,getk(p,BTN_TR));put(&s,7,getk(p,BTN_TR2));
     put(&s,9,getk(p,BTN_START));put(&s,11,getk(p,BTN_THUMBR));
   }
 }else{ // each half acts as a sideways controller
   bool l=p->side==1;
   s.a[0]=l?y:-y;s.a[1]=l?-x:x;
   if(l){put(&s,0,right);put(&s,1,down);put(&s,2,left);put(&s,3,up);}
   else{put(&s,0,getk(p,BTN_WEST));put(&s,1,getk(p,BTN_NORTH));put(&s,2,getk(p,BTN_EAST));put(&s,3,getk(p,BTN_SOUTH));}
   put(&s,4,getk(p,l?BTN_TR:BTN_TL)||getk(p,BTN_TRIGGER_HAPPY5));
   put(&s,5,getk(p,l?BTN_TR2:BTN_TL2)||getk(p,BTN_TRIGGER_HAPPY6));
   put(&s,9,getk(p,BTN_START)||getk(p,BTN_SELECT));
   put(&s,10,getk(p,BTN_THUMBL)||getk(p,BTN_THUMBR));
 }
 if(!s.a[4] && (s.k&(1u<<6)))s.a[4]=32767;
 if(!s.a[5] && (s.k&(1u<<7)))s.a[5]=32767;
 return s;
}
static void push_slot(int n){
 if(n<0||(output!=OUTPUT_EDEN&&vfd[n]<0))return;
 State s={0}, old=previous[n]; Motion motion={0}; bool changed=false, connected=false;
 for(int i=0;i<MAX;i++)if(pads[i].fd>=0&&pads[i].slot==n){
   connected=true;
   if(pads[i].motion.timestamp_us>motion.timestamp_us)motion=pads[i].motion;
   State q=map(&pads[i]);s.k|=q.k;
   for(int a=0;a<AXES;a++)if(abs(q.a[a])>abs(s.a[a]))s.a[a]=q.a[a];
 }
 if(steam_slot==n&&steam_present){s=steam_state;motion=steam_motion;connected=true;}
 dsu_update(n,s,motion,connected);
 if(output==OUTPUT_EDEN)return;
 const int *axis=output==OUTPUT_XBOX?xbox_axis:club_axis;
 for(int a=0;a<AXES;a++)if(s.a[a]!=old.a[a]){emit(vfd[n],EV_ABS,axis[a],s.a[a]);changed=true;}
 for(int k=0;k<KEYS;k++)if((k<12||k==14)&&((s.k^old.k)&(1u<<k))){
   emit(vfd[n],EV_KEY,key[k],!!(s.k&(1u<<k)));changed=true;
 }
 if(changed)emit(vfd[n],EV_SYN,SYN_REPORT,0);
 previous[n]=s;
}
static void stop_system_keys(void){
 destroy_virtual(&system_fd);
 previous_system_keys=0;
}
static void push_system_keys(Pad *p){
 if(!p->builtin||!p->grabbed||system_fd<0)return;
 unsigned current=0;
 for(size_t i=0;i<sizeof(system_key)/sizeof(*system_key);i++){
   int k=system_key[i],down=getk(p,k);
   if(down)current|=1u<<i;
   if((current^previous_system_keys)&(1u<<i))
     emit(system_fd,EV_KEY,k==KEY_HOME?KEY_HOMEPAGE:k,down);
 }
 if(current!=previous_system_keys)emit(system_fd,EV_SYN,SYN_REPORT,0);
 previous_system_keys=current;
}
static void off(void){
 direct_active=false;
 dsu_stop();
 steam_slot=-1;
 memset(&steam_state,0,sizeof(steam_state));
 memset(&steam_motion,0,sizeof(steam_motion));
 for(int i=0;i<MAX;i++){
   if(pads[i].grabbed)ioctl(pads[i].fd,EVIOCGRAB,0);
   pads[i].grabbed=false; pads[i].slot=-1;
 }
 for(int n=0;n<4;n++)destroy_virtual(&vfd[n]);
 stop_system_keys();
 memset(previous,0,sizeof(previous));
}
static void eden_idle(void){
 if(output!=OUTPUT_EDEN){off();return;}
 direct_active=false;
 steam_slot=-1;
 memset(&steam_state,0,sizeof(steam_state));
 memset(&steam_motion,0,sizeof(steam_motion));
 for(int i=0;i<MAX;i++){
   Pad *p=&pads[i];
   if(p->grabbed)ioctl(p->fd,EVIOCGRAB,0);
   p->grabbed=false;
   p->slot=p->fd>=0&&p->builtin?0:-1;
   p->style=0;
   if(p->slot==0)snapshot(p);
 }
 stop_system_keys();
 for(int slot=0;slot<4;slot++)push_slot(slot);
}
static void forget(Pad *p){
 if(p->builtin&&p->grabbed)stop_system_keys();
 int slot=p->slot; close(p->fd); memset(p,0,sizeof(*p));p->fd=-1;p->slot=-1;rev++;push_slot(slot);
}
static uint64_t identity_hash(const char *value){
 uint64_t hash=UINT64_C(14695981039346656037);
 for(;*value;value++){hash^=(unsigned char)*value;hash*=UINT64_C(1099511628211);}
 return hash;
}
static bool find_xbox_nano(char *unique,size_t size){
 glob_t nodes={0};
 bool found=false,ambiguous=false;
 if(glob("/dev/input/event*",0,NULL,&nodes)==0){
   for(size_t i=0;i<nodes.gl_pathc;i++){
     int fd=open(nodes.gl_pathv[i],O_RDONLY|O_NONBLOCK|O_CLOEXEC);
     if(fd<0)continue;
     struct input_id id={0};
     if(ioctl(fd,EVIOCGID,&id)==0&&id.bustype==BUS_BLUETOOTH&&id.vendor==0x045e&&id.product==0x0b4f){
       char candidate[96]={0};
       found=true;
       if(ioctl(fd,EVIOCGUNIQ(sizeof(candidate)),candidate)>0&&candidate[0]){
         if(unique[0]&&strcmp(unique,candidate))ambiguous=true;
         else snprintf(unique,size,"%s",candidate);
       }
     }
     close(fd);
   }
 }
 globfree(&nodes);
 if(ambiguous)unique[0]=0;
 return found;
}
static void scan(void){
 for(int i=0;i<MAX;i++)if(pads[i].fd>=0){
   struct stat st;
   if(stat(pads[i].path,&st)<0 || st.st_ino!=pads[i].inode||st.st_rdev!=pads[i].dev)forget(&pads[i]);
 }
 glob_t g={0}; if(glob("/dev/input/event*",0,NULL,&g)!=0){globfree(&g);return;}
 for(size_t i=0;i<g.gl_pathc;i++){
   const char *path=g.gl_pathv[i];int idx=-1;bool exists=false;
   for(int j=0;j<MAX;j++){
     if(pads[j].fd>=0 && !strcmp(path,pads[j].path))exists=true;
     if(idx<0&&pads[j].fd<0)idx=j;
   }
   if(exists||idx<0)continue;
   int fd=open(path,O_RDONLY|O_NONBLOCK|O_CLOEXEC); if(fd<0)continue;
   Pad p={.fd=fd,.slot=-1}; struct stat st; unsigned char kb[BITS(KEY_MAX)]={0};
   if(fstat(fd,&st)<0 || ioctl(fd,EVIOCGNAME(sizeof(p.name)),p.name)<0 || strncmp(p.name,"Controller Club P",17)==0 ||
      strncmp(p.name,"Xbox Controller P",17)==0 ||
      ioctl(fd,EVIOCGBIT(EV_KEY,sizeof(kb)),kb)<0 ||
      !(bit(kb,BTN_GAMEPAD)||bit(kb,BTN_EAST)||bit(kb,BTN_WEST)||bit(kb,BTN_NORTH)||bit(kb,BTN_DPAD_UP)||bit(kb,BTN_JOYSTICK))){close(fd);continue;}
   struct input_id ident={0};
   ioctl(fd,EVIOCGID,&ident);p.vendor=ident.vendor;p.product=ident.product;
   ioctl(fd,EVIOCGUNIQ(sizeof(p.unique)),p.unique);
   p.unique[sizeof(p.unique)-1]=0;
   if(strstr(p.name,"Joy-Con")||strstr(p.name,"Joy Con")){
     if(strstr(p.name,"(L)")||strstr(p.name," Left"))p.side=1;
     if(strstr(p.name,"(R)")||strstr(p.name," Right"))p.side=2;
   }
   if(p.vendor==0x057e && p.product==0x2006)p.side=1;
   if(p.vendor==0x057e && p.product==0x2007)p.side=2;
   for(char *c=p.name;*c;c++)if((unsigned char)*c<32)*c=' ';
   p.builtin=!strcmp(p.name,"Odin Controller");
   p.inode=st.st_ino;p.dev=st.st_rdev;p.serial=nextid++;
   snprintf(p.path,sizeof(p.path),"%s",path);
   ioctl(fd,EVIOCGBIT(EV_ABS,sizeof(p.caps)),p.caps);
   snapshot(&p);pads[idx]=p;rev++;
 }
 globfree(&g);
}
static void events(Pad *p){
 struct input_event e[32]; ssize_t n;
 for(int r=0;r<4;r++){
   n=read(p->fd,e,sizeof(e));
   if(n<0&&errno==EINTR)continue;
   if(n==0||(n<0&&errno!=EAGAIN&&errno!=EWOULDBLOCK)){forget(p);return;}
   if(n<=0)return;
   for(size_t i=0;i<(size_t)n/sizeof(*e);i++){
     if(e[i].type==EV_SYN){
       if(e[i].code==SYN_DROPPED)p->drop=true;
       if(e[i].code==SYN_REPORT){
         if(p->drop){snapshot(p);p->drop=false;}
         push_slot(p->slot);
         push_system_keys(p);
       }
     } else if(!p->drop && e[i].type==EV_KEY&&e[i].code<=KEY_MAX){
       int k=e[i].code;if(e[i].value)p->keys[k/8]|=1u<<(k%8);else p->keys[k/8]&=~(1u<<(k%8));
     } else if(!p->drop && e[i].type==EV_ABS&&e[i].code<=ABS_MAX)p->raw[e[i].code]=e[i].value;
   }
 }
}
static void status(int fd){
 int rewritten_xbox_count=0;
 for(int i=0;i<MAX;i++)
   if(pads[i].fd>=0&&pads[i].vendor==0x2020&&!strcmp(pads[i].name,"Xbox Wireless Controller"))rewritten_xbox_count++;
 // AYN gives its rewritten Xbox gamepad a generic ID. Only infer a match when there is one such gamepad.
 char nano_unique[96]={0};
 bool nano=rewritten_xbox_count==1&&find_xbox_nano(nano_unique,sizeof(nano_unique));
 dprintf(fd,"STATE %d %d\n",rev,routing());
 for(int i=0;i<MAX;i++)if(pads[i].fd>=0){
   Pad *p=&pads[i];
   bool rewritten_nano=nano&&p->vendor==0x2020&&!strcmp(p->name,"Xbox Wireless Controller");
   const char *unique=rewritten_nano&&nano_unique[0]?nano_unique:p->unique;
   dprintf(fd,"PAD2 %d %d %d %d %d %d %d %016llx %s\n",p->serial,p->side,p->builtin,p->slot,p->grabbed,
           rewritten_nano?0x045e:p->vendor,rewritten_nano?0x0b4f:p->product,
           *unique?(unsigned long long)identity_hash(unique):0ULL,p->name);
 }
 if(steam_present)
   dprintf(fd,"PAD2 %d 0 0 %d 0 10462 4358 %s Steam Controller (2015)\n",STEAM_PAD_ID,steam_slot,steam_identity);
 dprintf(fd,"END\n");
}
static void apply(int fd,char *cmd,enum Output next_output){
 bool eden=next_output==OUTPUT_EDEN, xbox=next_output==OUTPUT_XBOX;
 int slots[MAX],styles[MAX], count[4]={0}, halves[4]={0}, id,slot,style,end;
 int next_steam_slot=-1;
 for(int i=0;i<MAX;i++)slots[i]=-1;
 while(*cmd){
   if(sscanf(cmd," %d:%d:%d%n",&id,&slot,&style,&end)!=3||slot<0||slot>3||style<0||style>1)goto bad;
   if(id==STEAM_PAD_ID){
     if(!steam_present||style||next_steam_slot>=0)goto bad;
     next_steam_slot=slot;count[slot]++;
     cmd+=end;if(*cmd==' ')cmd++;else if(*cmd)goto bad;
     continue;
   }
   int i=0;while(i<MAX&&(pads[i].fd<0||pads[i].serial!=id))i++;
   if(i==MAX||slots[i]>=0)goto bad;
   if(style && (!pads[i].side||(halves[slot]&(1<<pads[i].side))))goto bad;
   if(style)halves[slot]|=1<<pads[i].side;
   slots[i]=slot;styles[i]=style;count[slot]++;
   cmd+=end;if(*cmd==' ')cmd++;else if(*cmd)goto bad;
 }
 bool any=false;for(int s=0;s<4;s++){if(count[s])any=true;if(count[s]>1&&(count[s]!=2||halves[s]!=6))goto bad;}
 if(!any&&!eden)goto bad;
 if(eden){
   bool thor=false;
   for(int i=0;i<MAX;i++)if(pads[i].fd>=0&&pads[i].builtin)thor=true;
   if(!thor){dprintf(fd,"ERR Connect USB and restart the helper to enable all Eden player slots\n");return;}
 }
 if(!dsu_start()&&eden){dprintf(fd,"ERR Eden controller port is unavailable\n");return;}

 for(int s=0;s<4;s++){
   if(output!=next_output||(xbox&&!count[s])){
     destroy_virtual(&vfd[s]);memset(&previous[s],0,sizeof(previous[s]));
   }
   if(!eden&&(!xbox||count[s])&&vfd[s]<0 && (vfd[s]=make_pad(s,xbox))<0){
     off();dprintf(fd,"ERR Virtual gamepads unavailable\n");return;
   }
 }
 output=next_output;
 direct_active=eden;
 for(int i=0;i<MAX;i++)if(pads[i].fd>=0){
   Pad *p=&pads[i];
   bool grab=slots[i]>=0&&(!p->builtin||xbox||eden);
   if(p->builtin&&grab&&system_fd<0&&(system_fd=make_system_keys())<0){
     off();dprintf(fd,"ERR Could not preserve Thor system buttons\n");return;
   }
   if(p->grabbed&&!grab){ioctl(p->fd,EVIOCGRAB,0);p->grabbed=false;}
   if(p->builtin&&!grab)stop_system_keys();
   if(grab&&!p->grabbed){
     if(ioctl(p->fd,EVIOCGRAB,1)<0){off();dprintf(fd,"ERR Could not take exclusive control of a controller\n");return;}
     p->grabbed=true;
   }
   p->slot=slots[i];p->style=slots[i]<0?0:styles[i];
   if(p->slot>=0){snapshot(p);push_system_keys(p);}
 }
 steam_slot=next_steam_slot;
 for(int s=0;s<4;s++)push_slot(s);
 status(fd);return;
 bad: dprintf(fd,"ERR Invalid or stale player assignment\n");
}
static void motion_command(const char *command){
 int id,consumed=0;
 unsigned long long timestamp;
 Motion motion={0};
 if(sscanf(command,"%d %llu %f %f %f %f %f %f %n",&id,&timestamp,
      &motion.acceleration[0],&motion.acceleration[1],&motion.acceleration[2],
      &motion.gyroscope[0],&motion.gyroscope[1],&motion.gyroscope[2],&consumed)!=8
      ||command[consumed]||timestamp==0)return;
 for(int i=0;i<3;i++)
   if(!isfinite(motion.acceleration[i])||fabsf(motion.acceleration[i])>32
      ||!isfinite(motion.gyroscope[i])||fabsf(motion.gyroscope[i])>8000)return;
 motion.timestamp_us=(uint64_t)timestamp;
 if(id==STEAM_PAD_ID&&steam_present){
   if(motion.timestamp_us<=steam_motion.timestamp_us)return;
   steam_motion=motion;
   push_slot(steam_slot);
   return;
 }
 for(int i=0;i<MAX;i++)if(pads[i].fd>=0&&pads[i].serial==id){
   if(motion.timestamp_us<=pads[i].motion.timestamp_us)return;
   pads[i].motion=motion;
   push_slot(pads[i].slot);
   return;
 }
}
/* Messages below come only from the authenticated app connection. */
static void steam_command(const char *command){
 if(!strncmp(command,"STEAM ADD ",10)){
   const char *id=command+10;
   if(strlen(id)!=16||strspn(id,"0123456789abcdef")!=16)return;
   memcpy(steam_identity,id,17);steam_present=true;rev++;
 }else if(!strcmp(command,"STEAM REMOVE")){
   int previous_slot=steam_slot;
   steam_present=false;steam_slot=-1;rev++;
   memset(&steam_state,0,sizeof(steam_state));
 memset(&steam_motion,0,sizeof(steam_motion));push_slot(previous_slot);
 }else if(!strncmp(command,"STEAM INPUT ",12)&&steam_present&&steam_slot>=0){
   State input={0};
   if(sscanf(command+12,"%u %d %d %d %d %d %d %d %d",&input.k,
      &input.a[0],&input.a[1],&input.a[2],&input.a[3],&input.a[4],&input.a[5],&input.a[6],&input.a[7])!=9)return;
   for(int i=0;i<AXES;i++)input.a[i]=clamp(input.a[i],i<4?-32767:i<6?0:-1,i<6?32767:1);
   steam_state=input;push_slot(steam_slot);
 }
}
int main(int argc,char **argv){
 if(argc!=2&&argc!=3)return 2;
 int port=PORT;
 if(argc==3){
   char *end=NULL;
   long parsed=strtol(argv[2],&end,10);
   if(!end||*end||parsed<1024||parsed>65535)return 2;
   port=(int)parsed;
 }
 FILE *tf=fopen(argv[1],"r");char token[65]={0};
 if(!tf||fscanf(tf,"%64[0-9a-f]",token)!=1||strlen(token)!=64)return 2;
 fclose(tf);
 signal(SIGPIPE,SIG_IGN);signal(SIGINT,stop_sig);signal(SIGTERM,stop_sig);signal(SIGHUP,SIG_IGN);
 for(int i=0;i<MAX;i++){pads[i].fd=-1;pads[i].slot=-1;}
 umask(077);
 int server=socket(AF_INET,SOCK_STREAM|SOCK_CLOEXEC,0),on=1;
 if(server<0)return 2;
 setsockopt(server,SOL_SOCKET,SO_REUSEADDR,&on,sizeof(on));
 struct sockaddr_in addr={.sin_family=AF_INET,.sin_port=htons(port),.sin_addr={.s_addr=htonl(INADDR_LOOPBACK)}};
 if(bind(server,(void*)&addr,sizeof(addr))<0||listen(server,3)<0){perror("listen");return 2;}
 int fd=-1; bool auth=false; char in[2048];size_t used=0;long long last=0,scanned=0;
 while(!done){
   long long now=now_ms();
   if(now-scanned>=500){scan();scanned=now;}
   if(fd>=0 && now-last>6500){close(fd);fd=-1;eden_idle();steam_present=false;}
   struct pollfd f[MAX+3];f[MAX+2]=(struct pollfd){dsu_fd,POLLIN,0};f[0]=(struct pollfd){server,POLLIN,0};f[1]=(struct pollfd){fd,POLLIN,0};
   for(int i=0;i<MAX;i++)f[i+2]=(struct pollfd){pads[i].fd,POLLIN,0};
   if(poll(f,MAX+3,160)<0)continue;
   if(dsu_fd>=0&&f[MAX+2].revents&POLLIN)dsu_receive();
   for(int i=0;i<MAX;i++)if(pads[i].fd>=0&&f[i+2].revents)events(&pads[i]);
   if(fd>=0&&f[1].revents){
     char b[512];ssize_t n=read(fd,b,sizeof(b));bool closeit=n<=0;
     for(ssize_t i=0;i<n&&!closeit;i++){
       if(b[i]=='\n'){
         in[used]=0;
         if(!auth){if(used!=69||memcmp(in,"AUTH ",5)||memcmp(in+5,token,64))closeit=true;else{auth=true;status(fd);}}
         else if(!strcmp(in,"STATUS"))status(fd);
         else if(!strcmp(in,"OFF")){eden_idle();status(fd);}
         else if(!strcmp(in,"EDENIDLE")){
           if(!routing()&&dsu_start()){output=OUTPUT_EDEN;eden_idle();}
           status(fd);
         }
         else if(!strcmp(in,"DIRECT")){off();direct_active=true;status(fd);}
         else if(!strncmp(in,"ROUTE ",6))apply(fd,in+6,OUTPUT_PROFILES);
         else if(!strncmp(in,"XBOX ",5))apply(fd,in+5,OUTPUT_XBOX);
         else if(!strcmp(in,"EDEN"))apply(fd,in+4,OUTPUT_EDEN);
         else if(!strncmp(in,"EDEN ",5))apply(fd,in+5,OUTPUT_EDEN);
         else if(!strncmp(in,"MOTION ",7))motion_command(in+7);
         else if(!strncmp(in,"STEAM ",6))steam_command(in);
         else dprintf(fd,"ERR Unknown request\n");
         if(auth)last=now_ms();
         used=0;
       } else if(used>=sizeof(in)-1||b[i]<32||b[i]>126)closeit=true;else in[used++]=b[i];
     }
     if(closeit){close(fd);fd=-1;eden_idle();steam_present=false;}
   }
   if(f[0].revents&POLLIN){
     int c=accept4(server,NULL,NULL,SOCK_CLOEXEC);
     if(c>=0){if(fd>=0)close(c);else{
       struct timeval t={.tv_sec=1};setsockopt(c,SOL_SOCKET,SO_SNDTIMEO,&t,sizeof(t));
       fd=c;auth=false;used=0;last=now_ms();
     }}
   }
 }
 off();if(fd>=0)close(fd);close(server);return 0;
}
