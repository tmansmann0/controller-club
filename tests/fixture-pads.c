#define main router_entry
#include "../native/router.c"
#undef main
static int half(bool left){
 int fd=open("/dev/uinput",O_WRONLY|O_NONBLOCK|O_CLOEXEC);
 if(fd<0)return -1;
 if(ioctl(fd,UI_SET_EVBIT,EV_KEY)<0 || ioctl(fd,UI_SET_EVBIT,EV_ABS)<0)return -1;
 const int button[]={BTN_SOUTH,BTN_EAST,BTN_NORTH,BTN_WEST,BTN_DPAD_UP,BTN_DPAD_DOWN,BTN_DPAD_LEFT,BTN_DPAD_RIGHT,BTN_START,BTN_TL,BTN_TR,BTN_TR2,BTN_TL2};
 for(unsigned i=0;i<sizeof(button)/sizeof(*button);i++)if(ioctl(fd,UI_SET_KEYBIT,button[i])<0)return -1;
 struct uinput_setup u={.id={.bustype=BUS_BLUETOOTH,.vendor=0x057e,.product=left?0x2006:0x2007,.version=1}};
 snprintf(u.name,sizeof(u.name),"Joy-Con (%c) [test]",left?'L':'R');
 set_axis(fd,left?ABS_X:ABS_RX,-32767,32767);
 set_axis(fd,left?ABS_Y:ABS_RY,-32767,32767);
 if(done||ioctl(fd,UI_DEV_SETUP,&u)<0||ioctl(fd,UI_DEV_CREATE)<0)return -1;
 return fd;
}
int main(void){
 int l=half(true),r=half(false);
 if(l<0||r<0)return 2;
 for(int i=0;i<36;i++){
   emit(l,EV_KEY,BTN_DPAD_UP,i%2);emit(l,EV_SYN,SYN_REPORT,0);
   emit(r,EV_KEY,BTN_SOUTH,i%2);emit(r,EV_SYN,SYN_REPORT,0);
   usleep(450000);
 }
 close(l);close(r);return 0;
}
