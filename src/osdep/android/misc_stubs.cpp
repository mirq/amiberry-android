/*
 * misc_stubs.cpp - Miscellaneous stub definitions for Android
 *
 * These are variables and functions that are normally defined in files
 * excluded from the Android build (specialmonitors.cpp, amiberry_serial.cpp).
 */

#include "sysconfig.h"
#include "sysdeps.h"

#ifdef __ANDROID__

// From specialmonitors.cpp - needed by PanelChipset.cpp
// Empty array terminated by nullptr - no special monitors on Android
const TCHAR *specialmonitorfriendlynames[] = {
    nullptr
};

// From amiberry_serial.cpp - needed by memory.cpp
int seriallog = 0;
int log_sercon = 0;

// From amiberry_serial.cpp - needed by PanelIOPorts.cpp
// Shared memory serial is not available on Android
int shmem_serial_state(void)
{
    return 0;  // Not available
}

bool shmem_serial_create(void)
{
    return false;  // Cannot create on Android
}

#endif /* __ANDROID__ */
