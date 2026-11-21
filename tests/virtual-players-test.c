#define main router_entry
#include "router-mocks.h"
#undef main

static void identity(int slot){
    struct TestDevice *device=&test_devices[vfd[slot]-TEST_FD];
    char name[80];snprintf(name,sizeof(name),"Controller Club P%d",slot+1);
    assert(device->created&&!device->destroyed);
    assert(!strcmp(device->setup.name,name));
    assert(device->setup.id.vendor==0x1209&&device->setup.id.product==0x5440+slot);
    for(int i=0;i<AXES;i++){
        assert(device->has_axis[club_axis[i]]);
        assert(device->ranges[club_axis[i]].minimum==(i<4?-32767:i<6?0:-1));
        assert(device->ranges[club_axis[i]].maximum==(i<6?32767:1));
    }
}
int main(void){
    for(int i=0;i<MAX;i++)pads[i].fd=-1;
    // Partial setup retries fill the missing slots without recreating existing players.
    test_fail_open=true;assert(!ensure_players());
    assert(ensure_players()&&test_created==4&&!routing());
    int original[4];memcpy(original,vfd,sizeof(original));
    for(int slot=0;slot<4;slot++)identity(slot);
    assert(ensure_players()&&test_created==4);

    // Avoid reserving the production UDP port on the test machine.
    dsu_fd=socket(AF_INET,SOCK_DGRAM,0);assert(dsu_fd>=0);
    int replies[2];assert(socketpair(AF_UNIX,SOCK_STREAM,0,replies)==0);
    steam_present=true;
    char first[]="1000000000:0:0";
    apply(replies[0],first,OUTPUT_EDEN);
    steam_command("STEAM INPUT 1 12345 0 0 0 0 0 0 0");
    assert(routing()&&test_devices[original[0]-TEST_FD].keys[BTN_SOUTH]==1);

    char second[]="1000000000:1:0";
    apply(replies[0],second,OUTPUT_XBOX);
    assert(test_devices[original[0]-TEST_FD].keys[BTN_SOUTH]==0);
    assert(test_devices[original[1]-TEST_FD].keys[BTN_SOUTH]==1);
    assert(test_devices[original[1]-TEST_FD].axes[ABS_X]==12345);
    assert(test_created==4&&!memcmp(original,vfd,sizeof(original)));
    steam_command("STEAM REMOVE");
    assert(test_devices[original[1]-TEST_FD].keys[BTN_SOUTH]==0);

    char empty[]="";
    apply(replies[0],empty,OUTPUT_PROFILES);
    assert(routing()&&test_created==4);
    eden_idle();assert(!routing());
    for(int slot=0;slot<4;slot++)identity(slot);
    assert(!memcmp(original,vfd,sizeof(original)));

    // Recreate after process death: names, vendor/product IDs and layout stay fixed.
    for(int slot=0;slot<4;slot++)destroy_virtual(&vfd[slot]);
    assert(ensure_players()&&test_created==8);
    for(int slot=0;slot<4;slot++)identity(slot);
    close(replies[0]);close(replies[1]);off();
    for(int slot=0;slot<4;slot++)destroy_virtual(&vfd[slot]);
    puts("Four fixed player identities, Eden virtual input, swaps, mode changes, disconnect, stop and recreation: pass");
}
