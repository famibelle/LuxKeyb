# Build hôte du harnais de mesure. Se lie au sous-module whisper.cpp déjà
# épinglé par l'application (v1.7.4) : mesurer contre une autre version de
# whisper ne dirait rien de l'APK.
cmake_minimum_required(VERSION 3.16)
project(lux_bench CXX C)

set(CMAKE_CXX_STANDARD 17)
if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../android_keyboard/app/src/main/cpp/whisper.cpp
    ${CMAKE_BINARY_DIR}/whisper.cpp)

add_executable(lux_bench lux_bench.cpp)
target_link_libraries(lux_bench PRIVATE whisper)
