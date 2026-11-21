/* Only for running the router's syscall-mocked tests on macOS. */
#pragma once
#include <sys/socket.h>
#include <time.h>
#define CLOCK_BOOTTIME CLOCK_MONOTONIC
static inline int accept4(int fd,struct sockaddr *address,socklen_t *length,int flags){
    (void)flags;return accept(fd,address,length);
}
#include <sys/ioctl.h>
/* Flags are not used by the mocked input devices or the test's UDP client. */
#define SOCK_CLOEXEC 0
#define SOCK_NONBLOCK 0
#define _IOC_READ IOC_OUT
