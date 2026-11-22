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
  // Captured BattlerGC Pro (Switch mode): A=131, B=130, X=133, Y=134.
  // Each labeled face button must reach the same Club control, not its opposite.
  Pad battle={.vendor=0x057e,.product=0x2009};
  const int physical[]={BTN_EAST,BTN_SOUTH,BTN_NORTH,BTN_WEST};
  const unsigned expected[]={1u,2u,8u,4u};
  for(int i=0;i<4;i++){
    memset(battle.keys,0,sizeof(battle.keys));press(&battle,physical[i]);
    assert((map(&battle).k&15u)==expected[i]);
  }
  memset(battle.keys,0,sizeof(battle.keys));
  stick(&battle,ABS_RX,12345);stick(&battle,ABS_RY,-23456);
  press(&battle,BTN_TL2);press(&battle,BTN_TR2);
  State b=map(&battle);
  assert(b.a[2]==12345&&b.a[3]==-23456&&b.a[4]==32767&&b.a[5]==32767);
  memset(battle.keys,0,sizeof(battle.keys));battle.raw[ABS_RX]=battle.raw[ABS_RY]=0;
  b=map(&battle);assert(b.k==0&&b.a[2]==0&&b.a[3]==0&&b.a[4]==0&&b.a[5]==0);
  Pad proxy={.vendor=0x2020,.product=0x0111};
  strcpy(proxy.name,"Nintendo Switch Pro Controller");
  assert(rewritten_switch(&proxy));
  press(&proxy,BTN_SOUTH);assert((map(&proxy).k&15u)==1u);
  strcpy(proxy.name,"Odin Controller");assert(!rewritten_switch(&proxy));
  // Xbox and the Thor retain the existing mapping.
  battle.vendor=0x045e;battle.product=0x0b4f;press(&battle,BTN_EAST);
  assert((map(&battle).k&15u)==2u);
  puts("Nintendo/BattlerGC labels and releases; Joy-Con combined and sideways mappings; Thor analog and buttons: pass");
}
