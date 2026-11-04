cmake_minimum_required(VERSION 3.22)
project(bitstream_audio C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

get_filename_component(BITSTREAM_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)
set(BITSTREAM_THIRD_PARTY "${BITSTREAM_ROOT}/third_party" CACHE PATH "Vendored third-party sources")

# The ring buffer and packet sizing are platform independent, so the host test
# compiles only those files with the system compiler.
if(BITSTREAM_HOST_TEST)
    enable_testing()
    add_executable(ring_buffer_test tests/ring_buffer_test.cpp)
    target_include_directories(ring_buffer_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    add_test(NAME ring_buffer_test COMMAND ring_buffer_test)

    add_executable(pcm_packer_test tests/pcm_packer_test.cpp)
    target_include_directories(pcm_packer_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    add_test(NAME pcm_packer_test COMMAND pcm_packer_test)

    add_executable(feedback_test tests/feedback_test.cpp feedback.cpp)
    target_include_directories(feedback_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    add_test(NAME feedback_test COMMAND feedback_test)
    return()
endif()

set(LIBUSB_DIR "${BITSTREAM_THIRD_PARTY}/libusb-1.0.27")
if(NOT EXISTS "${LIBUSB_DIR}/libusb/core.c")
    message(FATAL_ERROR "libusb 1.0.27 sources are missing from ${LIBUSB_DIR}")
endif()

# libusb 1.0.27 ships an Android makefile, not a CMakeLists, so compile its
# documented Android source set here with the pinned android/config.h.
add_library(usb-1.0 SHARED
    "${LIBUSB_DIR}/libusb/core.c"
    "${LIBUSB_DIR}/libusb/descriptor.c"
    "${LIBUSB_DIR}/libusb/hotplug.c"
    "${LIBUSB_DIR}/libusb/io.c"
    "${LIBUSB_DIR}/libusb/sync.c"
    "${LIBUSB_DIR}/libusb/strerror.c"
    "${LIBUSB_DIR}/libusb/os/linux_usbfs.c"
    "${LIBUSB_DIR}/libusb/os/events_posix.c"
    "${LIBUSB_DIR}/libusb/os/threads_posix.c"
    "${LIBUSB_DIR}/libusb/os/linux_netlink.c"
)
target_include_directories(usb-1.0
    PUBLIC "${LIBUSB_DIR}/libusb"
    PRIVATE "${LIBUSB_DIR}/android" "${LIBUSB_DIR}/libusb/os"
)
target_compile_definitions(usb-1.0 PRIVATE HAVE_CONFIG_H=1)
target_compile_options(usb-1.0 PRIVATE -fvisibility=hidden -pthread)
target_link_libraries(usb-1.0 PRIVATE log)

set(FLAC_DIR "${BITSTREAM_THIRD_PARTY}/flac-1.4.3")
if(NOT EXISTS "${FLAC_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "libFLAC 1.4.3 sources are missing from ${FLAC_DIR}")
endif()

set(BUILD_CXXLIBS OFF CACHE BOOL "" FORCE)
set(BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(WITH_OGG OFF CACHE BOOL "" FORCE)
set(INSTALL_MANPAGES OFF CACHE BOOL "" FORCE)
set(INSTALL_PKGCONFIG_MODULES OFF CACHE BOOL "" FORCE)
set(INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory("${FLAC_DIR}" "${CMAKE_BINARY_DIR}/flac" EXCLUDE_FROM_ALL)

add_library(bitstream_audio SHARED
    usb_audio.cpp
    feedback.cpp
    flac_decoder.cpp
    jni_bridge.cpp
)
target_include_directories(bitstream_audio PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
    "${FLAC_DIR}/include"
    "${FLAC_DIR}/src/libFLAC/include"
)
target_link_libraries(bitstream_audio PRIVATE usb-1.0 FLAC log)
