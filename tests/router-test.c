#define main router_entry
#include "../native/router.c"
#undef main
#include <assert.h>
static void press(Pad *p,int k){p->keys[k/8]|=1u<<(k%8);}
static void stick(Pad *p,int a,int value){
  p->caps[a/8]|=1u<<(a%8);p->abs[a].minimum=-32767;p->abs[a].maximum=32767;p->raw[a]=value;
}
int main(void){
  Pad left={.side=1,.style=1},right={.side=2,.style=1},thor={.builtin=true};
  stick(&left,ABS_X,12000);press(&left,BTN_DPAD_UP);press(&left,BTN_TL2);
  stick(&right,ABS_RX,-12000);press(&right,BTN_SOUTH);press(&right,BTN_TR2);
  State l=map(&left),r=map(&right);
  assert(l.a[0]==12000&&l.a[2]==0&&l.a[7]==-1&&l.a[4]==32767);
  assert(r.a[0]==0&&r.a[2]==-12000&&r.a[5]==32767&&(r.k&1));
  left.style=right.style=0;
  press(&left,BTN_TR);press(&left,BTN_TR2);
  press(&right,BTN_TL);press(&right,BTN_TL2);
  l=map(&left);r=map(&right);
  assert(l.a[1]==-12000 && r.a[1]==-12000);
  assert((l.k&0x30)==0x30 && (r.k&0x30)==0x30);
  stick(&thor,ABS_Z,15000);stick(&thor,ABS_RZ,-25000);press(&thor,BTN_EAST);
  State t=map(&thor);
  assert(t.a[2]==15000&&t.a[3]==-25000&&t.a[4]==0&&t.a[5]==0&&(t.k&2));
  puts("Joy-Con combined and sideways mappings; Thor analog and buttons: pass");
}
