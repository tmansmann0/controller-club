#define main router_entry
#include "router-mocks.h"
#undef main
int main(void){
    for(int i=0;i<MAX;i++)pads[i].fd=-1;
    assert(ensure_players());
    for(int slot=0;slot<4;slot++)assert(test_devices[vfd[slot]-TEST_FD].setup.ff_effects_max==CLUB_EFFECTS);
    struct input_event upload={.type=EV_UINPUT,.code=UI_FF_UPLOAD,.value=42};
    test_upload.effect=(struct ff_effect){.type=FF_RUMBLE,.id=3,.replay={.length=100,.delay=20},.u.rumble={.strong_magnitude=1234,.weak_magnitude=4567}};
    rumble_event(1,&upload);
    assert(test_upload.retval==0&&effects[1][3].effect.u.rumble.weak_magnitude==4567);
    test_upload.effect.type=FF_PERIODIC;rumble_event(1,&upload);assert(test_upload.retval==-EINVAL);
    test_upload.effect.type=FF_RUMBLE;test_upload.effect.id=CLUB_EFFECTS;
    rumble_event(1,&upload);assert(test_upload.retval==-EINVAL);
    routing_active=true;
    long long now=now_ms();
    ClubEffect *e=&effects[1][3];
    e->effect=(struct ff_effect){.type=FF_RUMBLE,.id=3,.replay={.length=100,.delay=20},.u.rumble={.strong_magnitude=1234,.weak_magnitude=4567}};
    struct input_event start={.type=EV_FF,.code=3,.value=2};
    rumble_event(1,&start);e->started=now;
    assert(rumble_strength(1,now+10)==0);
    assert(rumble_strength(1,now+30)==4567);
    assert(rumble_strength(0,now+30)==0&&rumble_strength(2,now+30)==0);
    assert(rumble_strength(1,now+125)==0);
    assert(rumble_strength(1,now+150)==4567);
    assert(rumble_strength(1,now+240)==0);
    rumble_event(1,&start);e->started=now;
    start.value=0;rumble_event(1,&start);
    assert(rumble_strength(1,now+30)==0);
    start.value=1;rumble_event(1,&start);e->started=now;
    rumble_clear();assert(rumble_strength(1,now+30)==0);
    // A reassignment cancels playback, but uploaded effects remain reusable.
    assert(e->effect.u.rumble.weak_magnitude==4567);
    rumble_event(1,&start);e->started=now;
    routing_active=false;assert(rumble_strength(1,now+30)==0);
    e->effect.replay.length=0;e->effect.replay.delay=0;
    routing_active=true;rumble_event(1,&start);e->started=now;
    assert(rumble_strength(1,now+100000)==4567);
    rumble_clear();assert(rumble_strength(1,now+100001)==0);
    struct input_event erase={.type=EV_UINPUT,.code=UI_FF_ERASE,.value=43};
    test_erase.effect_id=3;rumble_event(1,&erase);
    assert(test_erase.retval==0&&effects[1][3].effect.type==0);
    puts("Rumble slot isolation, delay, repeats, stop, reassignment and inactive routing: pass");
}
