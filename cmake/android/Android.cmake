# Android.cmake - Android-specific CMake configuration for Amiberry
# This file is included when building for Android (ANDROID is defined by NDK toolchain)

if(NOT ANDROID)
    return()
endif()

message(STATUS "Configuring for Android")
message(STATUS "  Android ABI: ${ANDROID_ABI}")
message(STATUS "  Android API Level: ${ANDROID_NATIVE_API_LEVEL}")
message(STATUS "  Android NDK: ${ANDROID_NDK}")

# Require arm64-v8a only (JIT requires AArch64)
if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Amiberry Android requires arm64-v8a ABI for JIT support")
endif()

# Require API level 30+ for W^X compliant JIT
if(ANDROID_NATIVE_API_LEVEL LESS 30)
    message(WARNING "API level ${ANDROID_NATIVE_API_LEVEL} < 30. Recommend API 30+ for best W^X JIT support")
endif()

# Android-specific definitions
add_compile_definitions(
    AMIBERRY
    ANDROID
    __ANDROID__
    CPU_AARCH64
    CPU_64_BIT
    ARMV8
    # Disable features not available on Android
    ANDROID_NO_FLOPPYBRIDGE
    ANDROID_NO_SERIALPORT
    ANDROID_NO_PORTMIDI
    ANDROID_NO_DBUS
    ANDROID_NO_PCAP
    ANDROID_NO_GPIO
)

# Android requires Position Independent Code (PIC/PIE) - this is mandatory for shared libraries
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Compiler flags for Android arm64
set(ANDROID_ARM64_FLAGS
    -fPIC
    -march=armv8-a
    -mtune=cortex-a53
    -fomit-frame-pointer
    -fno-strict-aliasing
    -ffast-math
)

# Add Android-specific compiler flags
add_compile_options(${ANDROID_ARM64_FLAGS})

# Add Android JNI include directory globally (needed for android_sysconfig.h)
include_directories(${CMAKE_SOURCE_DIR}/android/app/src/main/jni)

# Link flags - Android NDK provides most libraries
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -landroid -llog")

# Android JIT source files
set(ANDROID_JIT_SOURCES
    ${CMAKE_SOURCE_DIR}/android/app/src/main/jni/android_jit.cpp
)

# Function to configure Android JIT support
function(configure_android_jit target)
    target_sources(${target} PRIVATE ${ANDROID_JIT_SOURCES})
    target_include_directories(${target} PRIVATE
        ${CMAKE_SOURCE_DIR}/android/app/src/main/jni
    )
    target_link_libraries(${target} PRIVATE log)
endfunction()

# SDL2 for Android - build from source
if(USE_ANDROID_SDL)
    message(STATUS "Building SDL2 from source for Android")
    
    # SDL2 core - configure for shared library
    set(SDL_SHARED ON CACHE BOOL "Build SDL2 shared library")
    set(SDL_STATIC OFF CACHE BOOL "Don't build SDL2 static library")
    set(SDL2_DISABLE_INSTALL ON CACHE BOOL "Disable SDL2 install")
    
    # Add SDL2 subdirectory
    add_subdirectory(${CMAKE_SOURCE_DIR}/external/SDL2 ${CMAKE_BINARY_DIR}/SDL2 EXCLUDE_FROM_ALL)
    
    # SDL2_image configuration
    set(SDL2IMAGE_INSTALL OFF CACHE BOOL "Disable SDL2_image install")
    set(SDL2IMAGE_SAMPLES OFF CACHE BOOL "Disable SDL2_image samples")
    set(SDL2IMAGE_VENDORED OFF CACHE BOOL "Don't use vendored libraries")
    set(SDL2IMAGE_PNG ON CACHE BOOL "Enable PNG support")
    set(SDL2IMAGE_JPG ON CACHE BOOL "Enable JPG support")
    set(SDL2IMAGE_WEBP OFF CACHE BOOL "Disable WEBP support")
    
    # SDL2_ttf configuration  
    set(SDL2TTF_INSTALL OFF CACHE BOOL "Disable SDL2_ttf install")
    set(SDL2TTF_SAMPLES OFF CACHE BOOL "Disable SDL2_ttf samples")
    set(SDL2TTF_VENDORED ON CACHE BOOL "Use vendored freetype")
    set(SDL2TTF_HARFBUZZ OFF CACHE BOOL "Disable harfbuzz")
    
    # Set SDL2_DIR so SDL2_image/ttf can find it
    set(SDL2_DIR ${CMAKE_BINARY_DIR}/SDL2)
    set(SDL2_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/external/SDL2/include)
    set(SDL2_LIBRARY SDL2)
    
    # Add SDL2_image and SDL2_ttf
    add_subdirectory(${CMAKE_SOURCE_DIR}/external/SDL2_image ${CMAKE_BINARY_DIR}/SDL2_image EXCLUDE_FROM_ALL)
    add_subdirectory(${CMAKE_SOURCE_DIR}/external/SDL2_ttf ${CMAKE_BINARY_DIR}/SDL2_ttf EXCLUDE_FROM_ALL)
    
    set(SDL2_FOUND TRUE)
    set(SDL2_IMAGE_FOUND TRUE)
    set(SDL2_TTF_FOUND TRUE)
endif()

# Function to link SDL2 to target for Android
function(configure_android_sdl target)
    if(USE_ANDROID_SDL)
        target_include_directories(${target} PRIVATE
            ${CMAKE_SOURCE_DIR}/external/SDL2/include
            ${CMAKE_SOURCE_DIR}/external/SDL2_image/include
            ${CMAKE_SOURCE_DIR}/external/SDL2_ttf
        )
        target_link_libraries(${target} PRIVATE
            SDL2
            SDL2_image
            SDL2_ttf
        )
    endif()
endfunction()

message(STATUS "Android configuration complete")
