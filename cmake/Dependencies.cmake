include(FindHelper)

if (USE_GPIOD)
    target_compile_definitions(${PROJECT_NAME} PRIVATE USE_GPIOD)
    find_library(LIBGPIOD_LIBRARIES gpiod REQUIRED)
    target_link_libraries(${PROJECT_NAME} PRIVATE ${LIBGPIOD_LIBRARIES})
endif ()

if (USE_DBUS)
    target_compile_definitions(${PROJECT_NAME} PRIVATE USE_DBUS)
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(DBUS REQUIRED dbus-1)
    target_include_directories(${PROJECT_NAME} PRIVATE ${DBUS_INCLUDE_DIRS})
    target_link_libraries(${PROJECT_NAME} PRIVATE ${DBUS_LIBRARIES})
endif ()

if (USE_OPENGL)
    target_compile_definitions(${PROJECT_NAME} PRIVATE USE_OPENGL)
    find_package(OpenGL REQUIRED)
    find_package(GLEW REQUIRED)
    target_link_libraries(${PROJECT_NAME} PRIVATE ${TARGET_LINK_LIBRARIES} GLEW OpenGL::GL)
endif ()

# Android: SDL2 is built from source in Android.cmake
# Non-Android: use find_package
if(NOT ANDROID)
    find_package(SDL2 CONFIG REQUIRED)
    find_package(SDL2_image MODULE REQUIRED)
    find_package(SDL2_ttf MODULE REQUIRED)
    find_package(FLAC REQUIRED)
    find_package(mpg123 REQUIRED)
    find_package(PNG REQUIRED)
    find_package(ZLIB REQUIRED)
endif()

if (USE_ZSTD)
    target_compile_definitions(${PROJECT_NAME} PRIVATE USE_ZSTD)
    if(NOT ANDROID)
        find_helper(ZSTD libzstd zstd.h zstd)
        if(NOT ZSTD_FOUND)
            message(WARNING "ZSTD library not found - CHD compressed disk images will not be supported")
        else()
            target_include_directories(${PROJECT_NAME} PRIVATE ${ZSTD_INCLUDE_DIRS})
            target_link_libraries(${PROJECT_NAME} PRIVATE ${ZSTD_LIBRARIES})
        endif()
    endif()
endif ()

if (USE_LIBSERIALPORT)
    if(NOT ANDROID)
        target_compile_definitions(${PROJECT_NAME} PRIVATE USE_LIBSERIALPORT)
        find_helper(LIBSERIALPORT libserialport libserialport.h serialport)
        target_link_libraries(${PROJECT_NAME} PRIVATE ${LIBSERIALPORT_LIBRARIES})
    endif()
endif ()

if (USE_PORTMIDI)
    if(NOT ANDROID)
        target_compile_definitions(${PROJECT_NAME} PRIVATE USE_PORTMIDI)
        find_helper(PORTMIDI portmidi portmidi.h portmidi)
        target_link_libraries(${PROJECT_NAME} PRIVATE ${PORTMIDI_LIBRARIES})
    endif()
endif ()

if (USE_LIBMPEG2)
    if(NOT ANDROID)
        target_compile_definitions(${PROJECT_NAME} PRIVATE USE_LIBMPEG2)
        find_helper(LIBMPEG2_CONVERT libmpeg2convert mpeg2convert.h mpeg2convert)
        find_helper(LIBMPEG2 libmpeg2 mpeg2.h mpeg2)
        target_link_libraries(${PROJECT_NAME} PRIVATE ${LIBMPEG2_LIBRARIES} ${LIBMPEG2_CONVERT_LIBRARIES})
    endif()
endif ()

if (USE_LIBENET)
    if(NOT ANDROID)
        target_compile_definitions(${PROJECT_NAME} PRIVATE USE_LIBENET)
        find_helper(LIBENET libenet enet/enet.h enet)
        if(NOT LIBENET_FOUND)
            message(WARNING "LibENET library not found - network emulation will not be supported")
        else()
            target_include_directories(${PROJECT_NAME} PRIVATE ${LIBENET_INCLUDE_DIRS})
            target_link_libraries(${PROJECT_NAME} PRIVATE ${LIBENET_LIBRARIES})
        endif()
    endif()
endif ()

if (USE_PCEM)
    target_compile_definitions(${PROJECT_NAME} PRIVATE USE_PCEM)
endif ()

# Add libpcap for uaenet (Linux/macOS) - not on Android
if (USE_UAENET_PCAP AND NOT ANDROID)
    find_path(PCAP_INCLUDE_DIR pcap.h)
    find_library(PCAP_LIBRARY pcap)
    if (PCAP_INCLUDE_DIR AND PCAP_LIBRARY)
        message(STATUS "Found libpcap: ${PCAP_LIBRARY}")
        target_include_directories(${PROJECT_NAME} PRIVATE ${PCAP_INCLUDE_DIR})
        target_link_libraries(${PROJECT_NAME} PRIVATE ${PCAP_LIBRARY})
        target_compile_definitions(${PROJECT_NAME} PRIVATE WITH_UAENET_PCAP)
    else()
        message(FATAL_ERROR "libpcap not found. Please install libpcap-dev (Linux) or brew install libpcap (macOS)")
    endif()
endif()

# SDL2 include directories - different for Android vs other platforms
if(ANDROID)
    # Android: SDL2 built from source
    target_include_directories(${PROJECT_NAME} PRIVATE 
        ${CMAKE_SOURCE_DIR}/external/SDL2/include
        ${CMAKE_SOURCE_DIR}/external/SDL2_image/include
        ${CMAKE_SOURCE_DIR}/external/SDL2_ttf
    )
else()
    get_target_property(SDL2_INCLUDE_DIRS SDL2::SDL2 INTERFACE_INCLUDE_DIRECTORIES)
    target_include_directories(${PROJECT_NAME} PRIVATE ${SDL2_INCLUDE_DIRS} ${SDL2_IMAGE_INCLUDE_DIR} ${SDL2_TTF_INCLUDE_DIR})
endif()

set(libmt32emu_SHARED FALSE)
add_subdirectory(external/mt32emu)
if(NOT ANDROID)
    # FloppyBridge not available on Android
    add_subdirectory(external/floppybridge)
endif()
add_subdirectory(external/capsimage)
add_subdirectory(external/libguisan)

# guisan SDL2 includes
if(ANDROID)
    target_include_directories(guisan PRIVATE 
        ${CMAKE_SOURCE_DIR}/external/SDL2/include
        ${CMAKE_SOURCE_DIR}/external/SDL2_image/include
        ${CMAKE_SOURCE_DIR}/external/SDL2_ttf
    )
else()
    target_include_directories(guisan PRIVATE ${SDL2_INCLUDE_DIRS} ${SDL2_IMAGE_INCLUDE_DIR} ${SDL2_TTF_INCLUDE_DIR})
endif()

# Link libraries - different set for Android
if(ANDROID)
    target_link_libraries(${PROJECT_NAME} PRIVATE
        guisan
        mt32emu
        SDL2
        SDL2_image
        SDL2_ttf
        z
        log
        android
    )
    # Add dependencies for Android
    add_dependencies(${PROJECT_NAME} mt32emu capsimage guisan SDL2 SDL2_image SDL2_ttf)
else()
    target_link_libraries(${PROJECT_NAME} PRIVATE
        guisan
        mt32emu
        FLAC
        png
        MPG123::libmpg123
        z
        pthread
        dl
    )
    # Linux needs librt
    if (CMAKE_SYSTEM_NAME STREQUAL "Linux")
        target_link_libraries(${PROJECT_NAME} PRIVATE rt)
    endif ()
    # Add dependencies to ensure external libraries are built
    add_dependencies(${PROJECT_NAME} mt32emu floppybridge capsimage guisan)
endif()
