/* Linux FF_RUMBLE -> short, renewable app feedback. Effects remain slot-owned. */
#define CLUB_EFFECTS 16
typedef struct { struct ff_effect effect; long long started; int repeats; } ClubEffect;
static ClubEffect effects[4][CLUB_EFFECTS];
static void rumble_clear(void){
 for(int slot=0;slot<4;slot++)for(int id=0;id<CLUB_EFFECTS;id++)effects[slot][id].repeats=0;
}
static void rumble_event(int slot,const struct input_event *event){
 int fd=vfd[slot];
 if(event->type==EV_UINPUT&&event->code==UI_FF_UPLOAD){
   struct uinput_ff_upload upload={.request_id=event->value};
   if(ioctl(fd,UI_BEGIN_FF_UPLOAD,&upload)<0)return;
   int id=upload.effect.id;
   upload.retval=0;
   if(id<0||id>=CLUB_EFFECTS||upload.effect.type!=FF_RUMBLE)upload.retval=-EINVAL;
   else effects[slot][id].effect=upload.effect;
   ioctl(fd,UI_END_FF_UPLOAD,&upload);
 }else if(event->type==EV_UINPUT&&event->code==UI_FF_ERASE){
   struct uinput_ff_erase erase={.request_id=event->value};
   if(ioctl(fd,UI_BEGIN_FF_ERASE,&erase)<0)return;
   erase.retval=0;
   if(erase.effect_id>=CLUB_EFFECTS)erase.retval=-EINVAL;
   else memset(&effects[slot][erase.effect_id],0,sizeof(ClubEffect));
   ioctl(fd,UI_END_FF_ERASE,&erase);
 }else if(event->type==EV_FF&&event->code<CLUB_EFFECTS){
   ClubEffect *effect=&effects[slot][event->code];
   effect->started=now_ms();effect->repeats=event->value>0?event->value:0;
 }
}
static void rumble_events(int slot){
 struct input_event events[16];ssize_t bytes;
 while((bytes=read(vfd[slot],events,sizeof(events)))>0)
   for(size_t i=0;i<(size_t)bytes/sizeof(*events);i++)rumble_event(slot,&events[i]);
}
static int rumble_strength(int slot,long long now){
 if(!routing())return 0;
 int strength=0;
 for(int i=0;i<CLUB_EFFECTS;i++){
   ClubEffect *e=&effects[slot][i];
   if(!e->repeats||e->effect.type!=FF_RUMBLE)continue;
   long long elapsed=now-e->started;
   unsigned length=e->effect.replay.length,delay=e->effect.replay.delay;
   unsigned period=length+delay;
   if(elapsed<0){e->repeats=0;continue;}
   if(length==0){if(elapsed<delay)continue;} // Continuous until an explicit stop.
   else {
     if(elapsed>=(long long)period*e->repeats){e->repeats=0;continue;}
     if(elapsed%period<delay)continue;
   }
   int level=e->effect.u.rumble.strong_magnitude;
   if(e->effect.u.rumble.weak_magnitude>level)level=e->effect.u.rumble.weak_magnitude;
   if(level>strength)strength=level;
 }
 return strength;
}
static void rumble_feedback(int fd){
 long long now=now_ms();
 dprintf(fd,"RUMBLE");
 for(int slot=0;slot<4;slot++)dprintf(fd," %d",rumble_strength(slot,now));
 dprintf(fd,"\n");
}
