/* Host-test stub. Production builds use Linux's real parent-death signal. */
#pragma once
#define PR_SET_PDEATHSIG 1
static inline int prctl(int option, ...) {(void)option;return 0;}
