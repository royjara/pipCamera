cmake_minimum_required(VERSION 3.16)

# Project configuration
project(libmedia_pipeline
    VERSION 1.0.0
    DESCRIPTION "Cross-platform media processing library with OSC communication"
    LANGUAGES CXX C
)

# Modern C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Build options
option(BUILD_SHARED_LIBS "Build shared libraries" ON)
option(BUILD_EXAMPLES "Build example applications" ON)
option(BUILD_TESTS "Build test suite" OFF)
option(ENABLE_ALSA "Enable ALSA audio backend (Linux)" ON)
option(ENABLE_COREAUDIO "Enable CoreAudio backend (macOS)" ON)
option(ENABLE_WASAPI "Enable WASAPI backend (Windows)" ON)

# Platform detection
if(WIN32)
    set(PLATFORM_NAME "windows")
elseif(APPLE)
    set(PLATFORM_NAME "macos")
elseif(UNIX)
    set(PLATFORM_NAME "linux")
else()
    set(PLATFORM_NAME "unknown")
endif()

message(STATUS "Building for platform: ${PLATFORM_NAME}")

# Find required packages
find_package(Threads REQUIRED)

# Platform-specific dependencies
if(PLATFORM_NAME STREQUAL "linux" AND ENABLE_ALSA)
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(ALSA REQUIRED alsa)
endif()

if(PLATFORM_NAME STREQUAL "macos" AND ENABLE_COREAUDIO)
    find_library(COREAUDIO_FRAMEWORK CoreAudio)
    find_library(AUDIOUNIT_FRAMEWORK AudioUnit)
    find_library(COREFOUNDATION_FRAMEWORK CoreFoundation)
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

# Core library sources
set(CORE_SOURCES
    src/core/audio_processor.cpp
    src/core/sine_generator.cpp
    src/core/buffer_manager.cpp
    src/osc/osc_client.cpp
    src/osc/message_formatter.cpp
)

# Platform-specific sources
if(PLATFORM_NAME STREQUAL "linux" AND ENABLE_ALSA)
    list(APPEND CORE_SOURCES src/platform/linux_audio.cpp)
    add_compile_definitions(HAVE_ALSA)
endif()

if(PLATFORM_NAME STREQUAL "macos" AND ENABLE_COREAUDIO)
    list(APPEND CORE_SOURCES src/platform/macos_audio.cpp)
    add_compile_definitions(HAVE_COREAUDIO)
endif()

if(PLATFORM_NAME STREQUAL "windows" AND ENABLE_WASAPI)
    list(APPEND CORE_SOURCES src/platform/windows_audio.cpp)
    add_compile_definitions(HAVE_WASAPI)
endif()

# Create the main library
add_library(media_pipeline ${CORE_SOURCES})

# Set library properties
set_target_properties(media_pipeline PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    PUBLIC_HEADER "include/media_pipeline/core.h;include/media_pipeline/audio_processor.h;include/media_pipeline/osc_client.h;include/media_pipeline/buffer_manager.h"
)

# Include directories for the library
target_include_directories(media_pipeline
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:include>
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src
)

# Link libraries
target_link_libraries(media_pipeline
    PUBLIC
        Threads::Threads
)

# Platform-specific linking
if(PLATFORM_NAME STREQUAL "linux" AND ENABLE_ALSA)
    target_link_libraries(media_pipeline PRIVATE ${ALSA_LIBRARIES})
    target_include_directories(media_pipeline PRIVATE ${ALSA_INCLUDE_DIRS})
endif()

if(PLATFORM_NAME STREQUAL "macos" AND ENABLE_COREAUDIO)
    target_link_libraries(media_pipeline PRIVATE
        ${COREAUDIO_FRAMEWORK}
        ${AUDIOUNIT_FRAMEWORK}
        ${COREFOUNDATION_FRAMEWORK}
    )
endif()

if(PLATFORM_NAME STREQUAL "windows" AND ENABLE_WASAPI)
    target_link_libraries(media_pipeline PRIVATE
        winmm
        ole32
        oleaut32
    )
endif()

# Compiler flags
target_compile_options(media_pipeline
    PRIVATE
        $<$<CXX_COMPILER_ID:GNU,Clang>:-Wall -Wextra -O2 -ffast-math>
        $<$<CXX_COMPILER_ID:MSVC>:/W4 /O2>
)

# Build examples if requested
if(BUILD_EXAMPLES)
    add_subdirectory(examples)
endif()

# Build tests if requested
if(BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()

# Install configuration
install(TARGETS media_pipeline
    EXPORT media_pipelineTargets
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
    PUBLIC_HEADER DESTINATION include/media_pipeline
)

install(EXPORT media_pipelineTargets
    FILE media_pipelineTargets.cmake
    NAMESPACE media_pipeline::
    DESTINATION lib/cmake/media_pipeline
)

# Create and install package config
include(CMakePackageConfigHelpers)

configure_package_config_file(
    ${CMAKE_CURRENT_SOURCE_DIR}/cmake/media_pipelineConfig.cmake.in
    ${CMAKE_CURRENT_BINARY_DIR}/media_pipelineConfig.cmake
    INSTALL_DESTINATION lib/cmake/media_pipeline
)

write_basic_package_version_file(
    ${CMAKE_CURRENT_BINARY_DIR}/media_pipelineConfigVersion.cmake
    VERSION ${PROJECT_VERSION}
    COMPATIBILITY SameMajorVersion
)

install(FILES
    ${CMAKE_CURRENT_BINARY_DIR}/media_pipelineConfig.cmake
    ${CMAKE_CURRENT_BINARY_DIR}/media_pipelineConfigVersion.cmake
    DESTINATION lib/cmake/media_pipeline
)

# Print configuration summary
message(STATUS "")
message(STATUS "=== Configuration Summary ===")
message(STATUS "Platform: ${PLATFORM_NAME}")
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "Shared libraries: ${BUILD_SHARED_LIBS}")
message(STATUS "Build examples: ${BUILD_EXAMPLES}")
message(STATUS "Build tests: ${BUILD_TESTS}")
if(PLATFORM_NAME STREQUAL "linux")
    message(STATUS "ALSA support: ${ENABLE_ALSA}")
endif()
if(PLATFORM_NAME STREQUAL "macos")
    message(STATUS "CoreAudio support: ${ENABLE_COREAUDIO}")
endif()
if(PLATFORM_NAME STREQUAL "windows")
    message(STATUS "WASAPI support: ${ENABLE_WASAPI}")
endif()
message(STATUS "=============================")
message(STATUS "")