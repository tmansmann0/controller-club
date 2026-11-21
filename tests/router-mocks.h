/* Exercise the real routing code without touching a host's input devices. */
#include <linux/input.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <stdarg.h>
#include <assert.h>
#include <string.h>
#include <unistd.h>
static int test_open(const char *,int,...);
static int test_ioctl(int,unsigned long,...);
static ssize_t test_write(int,const void *,size_t);
static int test_close(int);
static struct uinput_ff_upload test_upload;
static struct uinput_ff_erase test_erase;
#define open test_open
#define ioctl test_ioctl
#define write test_write
#define close test_close
#include "../native/router.c"
#undef open
#undef ioctl
#undef write
#undef close

#define TEST_FD 10000
static struct TestDevice {
    struct uinput_setup setup;
    int axes[ABS_MAX+1],keys[KEY_MAX+1];
    struct input_absinfo ranges[ABS_MAX+1];
    bool has_axis[ABS_MAX+1];
    bool created,destroyed;
} test_devices[32];
static int test_opened,test_created;
static bool test_fail_open;
static int test_open(const char *path,int flags,...){
    if(strcmp(path,"/dev/uinput"))return open(path,flags);
    if(test_fail_open){test_fail_open=false;return -1;}
    assert(test_opened<32);
    return TEST_FD+test_opened++;
}
static int test_ioctl(int fd,unsigned long request,...){
    assert(fd>=TEST_FD&&fd<TEST_FD+test_opened);
    struct TestDevice *device=&test_devices[fd-TEST_FD];
    va_list args;va_start(args,request);
    if(request==UI_BEGIN_FF_UPLOAD){
        struct uinput_ff_upload *upload=va_arg(args,struct uinput_ff_upload *);*upload=test_upload;
    }
    else if(request==UI_END_FF_UPLOAD)test_upload=*va_arg(args,struct uinput_ff_upload *);
    else if(request==UI_BEGIN_FF_ERASE){
        struct uinput_ff_erase *erase=va_arg(args,struct uinput_ff_erase *);*erase=test_erase;
    }
    else if(request==UI_END_FF_ERASE)test_erase=*va_arg(args,struct uinput_ff_erase *);
    else if(request==UI_DEV_SETUP)device->setup=*va_arg(args,struct uinput_setup *);
    else if(request==UI_ABS_SETUP){
        struct uinput_abs_setup *axis=va_arg(args,struct uinput_abs_setup *);
        device->ranges[axis->code]=axis->absinfo;device->has_axis[axis->code]=true;
    }
    else if(request==UI_DEV_CREATE){device->created=true;test_created++;}
    else if(request==UI_DEV_DESTROY)device->destroyed=true;
    va_end(args);return 0;
}
static ssize_t test_write(int fd,const void *data,size_t bytes){
    if(fd<TEST_FD)return write(fd,data,bytes);
    assert(fd<TEST_FD+test_opened&&bytes==sizeof(struct input_event));
    struct TestDevice *device=&test_devices[fd-TEST_FD];
    assert(device->created&&!device->destroyed);
    const struct input_event *event=data;
    if(event->type==EV_KEY)device->keys[event->code]=event->value;
    if(event->type==EV_ABS)device->axes[event->code]=event->value;
    return (ssize_t)bytes;
}
static int test_close(int fd){
    if(fd<TEST_FD)return close(fd);
    assert(fd<TEST_FD+test_opened);return 0;
}
