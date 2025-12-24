# Amiberry Android Porting Plan

## Target Specifications
- **Minimum API:** 30 (Android 11)
- **Target API:** 35 (Android 15) - Play Store requirement
- **Architecture:** arm64-v8a only
- **Distribution:** Google Play Store
- **Critical Feature:** JIT compilation with W^X compliance

## Design Philosophy

**Key Principle:** Keep Android-specific changes **isolated** from core Amiberry code to enable easy merging of upstream updates.

### Strategy:
1. **New files** in a separate `android/` directory - never modified by upstream
2. **Minimal, surgical patches** to existing files - clearly marked with `#ifdef __ANDROID__`
3. **Wrapper CMake** that includes the original build system
4. **Override mechanism** for platform-specific behavior

---

## File Structure

```
amiberry/                          # Original Amiberry (upstream)
├── CMakeLists.txt                 # Modified: add Android include
├── cmake/
│   ├── android/
│   │   └── Android.cmake          # NEW: Android CMake config
│   └── SourceFiles.cmake          # Modified: conditional -fno-pie
├── src/
│   ├── vm.cpp                     # Modified: add Android JIT dual-mapping
│   ├── main.cpp                   # Modified: guard linux/kd.h
│   ├── osdep/
│   │   └── sysconfig.h            # Modified: Android feature flags
│   └── jit/arm/
│       └── compemu_support_arm.cpp # Modified: guard PIE check
│
android/                           # NEW: Android-specific (our code)
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/org/amiberry/app/
│   │   │   └── AmiberryActivity.kt
│   │   ├── jni/
│   │   │   ├── CMakeLists.txt     # Wrapper CMake
│   │   │   ├── android_jit.cpp    # Android JIT helpers
│   │   │   ├── android_jit.h
│   │   │   └── android_sysconfig.h
│   │   ├── assets/                # Data files
│   │   └── res/                   # Android resources
│   └── proguard-rules.pro
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Phase 1: Build System & Project Structure

### Changes to Existing Files

#### 1. `CMakeLists.txt` (root) - Add Android include
```cmake
# Android platform support (add before include(cmake/SourceFiles.cmake))
if(CMAKE_SYSTEM_NAME STREQUAL "Android")
    include(cmake/android/Android.cmake)
endif()
```

#### 2. `cmake/SourceFiles.cmake` - Conditional PIE flags
```cmake
# PIE is required on Android, disable elsewhere
if(NOT CMAKE_SYSTEM_NAME STREQUAL "Android")
    target_compile_options(${PROJECT_NAME} PRIVATE -fno-pie)
    target_link_options(${PROJECT_NAME} PRIVATE -no-pie)
endif()
```

#### 3. `cmake/Dependencies.cmake` - Skip librt on Android
```cmake
if (CMAKE_SYSTEM_NAME STREQUAL "Linux" AND NOT CMAKE_SYSTEM_NAME STREQUAL "Android")
    target_link_libraries(${PROJECT_NAME} PRIVATE rt)
endif ()
```

### New Files

#### `cmake/android/Android.cmake`
- Force disable unsupported features (GPIOD, DBUS, LIBSERIALPORT, etc.)
- Add Android definitions
- Add Android-specific source files
- Link Android libraries (log, android)

---

## Phase 2: JIT W^X Compliance (Critical)

Android 10+ enforces W^X - memory cannot be simultaneously writable and executable.

### Solution: Dual-Mapping with `memfd_create`

1. Create anonymous shared memory with `memfd_create()` (API 30+)
2. Map it twice at different addresses:
   - **Write mapping:** `PROT_READ | PROT_WRITE` - JIT compiler writes here
   - **Execute mapping:** `PROT_READ | PROT_EXEC` - CPU executes from here
3. Same physical memory, two virtual addresses with different permissions

### Changes to Existing Files

#### `src/vm.cpp` - Add Android JIT intercept
```cpp
#ifdef __ANDROID__
#include "android/android_jit.h"
#endif

// In uae_vm_alloc_with_flags():
#ifdef __ANDROID__
if (protect == UAE_VM_READ_WRITE_EXECUTE) {
    return android_jit_alloc(size);
}
#endif
```

#### `src/jit/arm/compemu_support_arm.cpp` - Guard PIE error
```cpp
#if (defined(__pie__) || defined(__PIE__)) && !defined(__ANDROID__)
#error Position-independent code (PIE) cannot be used with JIT (except Android)
#endif
```

### New Files

#### `android/app/src/main/jni/android_jit.cpp`
- `android_jit_alloc()` - Creates dual-mapped memory using memfd_create
- `android_jit_free()` - Cleans up mappings
- `android_jit_get_write_ptr()` - Gets write pointer from exec pointer
- `android_jit_flush_cache()` - Flushes CPU cache

---

## Phase 3: Platform Adaptations

### Changes to Existing Files

#### `src/main.cpp` - Guard linux/kd.h
```cpp
#if defined(__linux__) && !defined(__ANDROID__)
#include <linux/kd.h>
#endif
```

#### `src/osdep/sysconfig.h` - Include Android config
```cpp
#ifdef __ANDROID__
#include "android/android_sysconfig.h"
#endif
```

### New Files

#### `android/app/src/main/jni/android_sysconfig.h`
- Undefine unavailable headers (HAVE_SYS_TIMEB_H, HAVE_CURSES_H, etc.)
- Disable unavailable features (FLOPPYBRIDGE)
- Enable JIT on ARM64

---

## Phase 4: Android App Structure

### New Files

#### `android/app/src/main/kotlin/org/amiberry/app/AmiberryActivity.kt`
- Extends SDLActivity
- Loads native libraries (SDL2, SDL2_image, SDL2_ttf, amiberry)

#### `android/app/src/main/AndroidManifest.xml`
- Permissions: WAKE_LOCK, INTERNET
- Features: OpenGL ES 2.0, gamepad support
- Landscape orientation, leanback launcher support

#### `android/app/build.gradle.kts`
- CMake integration for native build
- Target SDK 35, min SDK 30
- arm64-v8a only

---

## Summary: Changes to Core Files

| File | Lines Changed | Change Type |
|------|---------------|-------------|
| `CMakeLists.txt` | +3 | Add Android include |
| `cmake/SourceFiles.cmake` | +4 | Conditional PIE flags |
| `cmake/Dependencies.cmake` | +2 | Skip librt on Android |
| `src/main.cpp` | +1 | Guard linux/kd.h |
| `src/osdep/sysconfig.h` | +3 | Include Android config |
| `src/vm.cpp` | +10 | Android JIT intercept |
| `src/jit/arm/compemu_support_arm.cpp` | +1 | Guard PIE error |

**Total: ~24 lines changed in 7 files**

---

## Upgrade Process

When a new Amiberry version is released:

1. **Pull upstream changes:**
   ```bash
   git fetch upstream
   git merge upstream/main
   ```

2. **Resolve conflicts (if any)** - limited to the 7 modified files

3. **Verify Android changes still present:**
   ```bash
   grep -r "__ANDROID__" src/
   grep -r "Android" cmake/
   ```

4. **Rebuild and test**

The `android/` directory is completely ours and won't have conflicts.

---

## Features Automatically Working via SDL2

- Game controllers ✅
- Touch input ✅
- Audio ✅
- Graphics ✅
- Keyboard ✅

## Features Disabled on Android

- FloppyBridge (serial port)
- libserialport
- PortMidi
- GPIO
- DBus
- libpcap

---

## Build Instructions

```bash
# From android/ directory
./gradlew assembleDebug

# Or open in Android Studio
# File -> Open -> select android/ directory
```

## Testing

```bash
# Install on connected device
./gradlew installDebug

# View logs
adb logcat -s AmiberryJIT:* SDL:* libc:*
```
