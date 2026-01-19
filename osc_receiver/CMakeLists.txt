cmake_minimum_required(VERSION 3.20)
project(OSCAudioReceiver)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required packages
find_package(PkgConfig REQUIRED)

# Find PortAudio for audio output
pkg_check_modules(PORTAUDIO REQUIRED portaudio-2.0)

# AOO library path (using the git submodule)
set(AOO_ROOT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../cpp/aoo)

# Include directories
include_directories(${PORTAUDIO_INCLUDE_DIRS})
include_directories(${AOO_ROOT_DIR}/include)

# Source files
set(SOURCES
    main.cpp
    osc_receiver.cpp
    audio_output.cpp
)

# Create executable
add_executable(osc_audio_receiver ${SOURCES})

# Link libraries
target_link_libraries(osc_audio_receiver
    ${PORTAUDIO_LIBRARIES}
)

# Compiler and linker flags
target_compile_options(osc_audio_receiver PRIVATE ${PORTAUDIO_CFLAGS_OTHER})
target_link_directories(osc_audio_receiver PRIVATE ${PORTAUDIO_LIBRARY_DIRS})

# macOS specific settings
if(APPLE)
    target_link_libraries(osc_audio_receiver
        "-framework CoreAudio"
        "-framework AudioToolbox"
        "-framework CoreFoundation"
    )
endif()

# Install target
install(TARGETS osc_audio_receiver DESTINATION bin)