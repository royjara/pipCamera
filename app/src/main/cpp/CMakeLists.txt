cmake_minimum_required(VERSION 3.22.1)

# Project configuration
project(AudioProcessingPipeline)

# Enable C++17 features for modern development
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Include FetchContent for future package management
include(FetchContent)

# Set up AOO submodule path (relative to project root)
set(AOO_ROOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../cpp/aoo")

# Find required packages
find_package(PkgConfig REQUIRED)
find_library(log-lib log)
find_library(android-lib android)

# Audio processing pipeline library
add_library(
    audio_pipeline
    SHARED
    audio_pipeline.cpp
    sine_generator.cpp
    osc_sender.cpp
    buffer_manager.cpp
)

# Include directories
target_include_directories(
    audio_pipeline
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${AOO_ROOT_DIR}/include
)

# Link libraries
target_link_libraries(
    audio_pipeline
    ${log-lib}
    ${android-lib}
)

# Compiler flags for optimization and debugging
target_compile_options(
    audio_pipeline
    PRIVATE
    -Wall
    -Wextra
    -O2
    -ffast-math
    -DANDROID
)

# Define platform-specific macros
target_compile_definitions(
    audio_pipeline
    PRIVATE
    ANDROID_NDK
    __ANDROID__
)

# Future: Add AOO library integration when needed
# Note: AOO might require additional CMake configuration
# target_link_libraries(audio_pipeline ${AOO_LIBRARIES})