# AGENTS.md - Amiberry Codebase Guide

## Build Commands
```bash
cmake -B build -G Ninja && cmake --build build -j$(nproc)  # Release build
cmake -B build -DCMAKE_BUILD_TYPE=Debug && cmake --build build  # Debug build
```

## Testing
No formal test framework. Manual testing via running the built `amiberry` binary.

## Code Style
- **Language**: C/C++ (C++17 standard)
- **Formatting**: No `.clang-format`; some files use `// clang-format off/on` pragmas
- **Includes**: Project headers use quotes (`#include "options.h"`), system/STL use angle brackets
- **Naming**: `snake_case` for functions/variables, `PascalCase` for structs, `UPPER_CASE` for macros/constants
- **Types**: Use UAE types (`uae_u8`, `uae_s16`, `uae_u32`, etc.) from `uae/types.h`
- **Macros**: Use `STATIC_INLINE` for inline functions, `_T()` for string literals
- **Error handling**: Use `write_log()` for logging; check return values explicitly
- **Conditionals**: Feature flags via `#ifdef` (e.g., `#ifdef AMIBERRY`, `#ifdef WITH_LUA`)
- **Comments**: C-style block comments for file headers, C++ style for inline

## Android Port (WORKING ✅)

### Overview
Porting Amiberry to Android API 30+ (Android 11+) with arm64-v8a architecture. All C++ changes isolated with `#ifdef __ANDROID__` for easy upstream merges.

### Android-Specific Components

#### Kotlin/Android Layer
- `android/app/src/main/kotlin/com/blitterstudio.amiberry/AmiberryActivity.kt` - Main SDL activity with portrait mode layout
- `android/app/src/main/kotlin/com.blitterstudio.amiberry/LauncherActivity.kt` - First-run setup with folder selection and storage permission handling
- `android/app/src/main/kotlin/com.blitterstudio.amiberry/VirtualJoystickOverlay.kt` - On-screen virtual joystick (D-pad + buttons)
- `android/app/src/main/kotlin/com.blitterstudio.amiberry/VirtualKeyboardOverlay.kt` - Full Amiga keyboard overlay
- `android/app/src/main/AndroidManifest.xml` - Permissions and activity configuration

#### Native/C++ Layer
- `src/osdep/android/android_vjoystick.cpp` - JNI functions for virtual controls
- `src/osdep/amiberry.cpp` - SDL initialization with Android-specific hints
- `src/osdep/amiberry_input.cpp` - Joystick device filtering (removes sensors)

### Working Features

#### Virtual Controls (✅)
- **Virtual Joystick**: Toggle via top-right hamburger icon (OFF → JOYSTICK → KEYBOARD → OFF)
- **D-pad**: 8-way directional input with visual feedback
- **Fire Buttons**: A & B buttons positioned bottom-right
- **Resize**: +/- buttons with SharedPreferences persistence (0.5x to 2.0x scale)
- **Multi-touch**: Simultaneous input support

#### Virtual Keyboard (✅)
- Full Amiga keyboard layout (bottom 40% of screen)
- Sticky modifier keys (Shift, Ctrl, Alt, Amiga)
- Appears when text fields are focused in GUI

#### Layout & Orientation (✅)
- Portrait mode support with SDL surface resized to 55% height
- `screenOrientation="fullUser"` in manifest for flexible orientation
- Automatic layout adjustments

#### Sensor Filtering (✅)
- Disables accelerometer/gyroscope as joystick input
- Filters out sensor-like device names during joystick initialization
- Prevents phone movement from interfering with controls

#### Storage Permissions (✅)
- **MANAGE_EXTERNAL_STORAGE** permission requested on Android 11+
- SAF (Storage Access Framework) folder picker for data directory
- Proper file access to external storage (ROMs, disk images)

### Build Process
```bash
# Native library
cd /home/mirek/amiberry-android/amiberry
cmake --build build-android --target amiberry -j$(nproc)

# Copy to Android project
cp build-android/libamiberry.so android/app/src/main/jniLibs/arm64-v8a/

# Build APK
cd android && ./gradlew assembleDebug

# Deploy
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.blitterstudio.amiberry/.LauncherActivity
```

### Key Android-Specific Code Patterns
```cpp
// SDL hints for Android (in amiberry.cpp)
#ifdef __ANDROID__
SDL_SetHint(SDL_HINT_ACCELEROMETER_AS_JOYSTICK, "0");
// ... other sensor disabling hints
#endif

// Joystick filtering (in amiberry_input.cpp)
#ifdef __ANDROID__
if (num_buttons == 0 || strstr(name, "Accelerometer") || strstr(name, "Gyroscope")) {
    // Skip sensor devices
    continue;
}
#endif
```

### Debugging
```bash
# View logs
adb logcat --pid=$(adb shell pidof com.blitterstudio.amiberry)

# Filter for specific components
adb logcat | grep -E "VJoystick|VKeyboard|AmiberryActivity|rom|ROM|kick"

# Check permissions
adb shell dumpsys package com.blitterstudio.amiberry | grep permission
```

## Project Structure
- `src/` - Main emulator source; `src/osdep/` - Platform-specific code
- `external/` - Third-party libs (libguisan, mt32emu, floppybridge, capsimage)
- `cmake/` - Build system modules and toolchains
- `android/` - Android-specific build files and Kotlin source
