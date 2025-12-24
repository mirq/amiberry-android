/*
 * android_sysconfig.h - Android-specific configuration overrides for Amiberry
 *
 * This file contains Android-specific defines and overrides.
 * It is included from sysconfig.h when building for Android.
 */

#ifndef ANDROID_SYSCONFIG_H
#define ANDROID_SYSCONFIG_H

#ifdef __ANDROID__

// Android API level requirements
#if __ANDROID_API__ < 30
#warning "Android API level < 30. Some features may not work correctly."
#endif

// Enable JIT on Android ARM64
#if defined(__aarch64__)
#define JIT
#define USE_JIT_FPU
#define CPU_AARCH64
#define CPU_64_BIT
#define ARMV8
#endif

// Disable features not available or not needed on Android
#undef USE_LIBSERIALPORT
#undef USE_PORTMIDI
#undef USE_DBUS
#undef USE_GPIOD
#undef WITH_UAENET_PCAP

// Disable optional audio codecs for initial Android build
// TODO: Add FLAC and mpg123 support later
#define ANDROID_NO_FLAC
#define ANDROID_NO_MPG123

// Android uses Bionic libc which has some differences
#define ANDROID_BIONIC

// Android doesn't have linux/kd.h keyboard definitions
#define ANDROID_NO_KD_H

// Android storage paths are different
#define ANDROID_STORAGE

// Android logging
#include <android/log.h>
#define ANDROID_LOG_TAG "Amiberry"

// Override write_log to use Android logging when appropriate
// (The actual implementation is in the source files)

#endif /* __ANDROID__ */

#endif /* ANDROID_SYSCONFIG_H */
