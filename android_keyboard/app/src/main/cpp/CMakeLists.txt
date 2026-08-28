cmake_minimum_required(VERSION 3.22.1)
project(luxstt C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# whisper.cpp est un sous-module épinglé (v1.7.4). On ne construit que la
# bibliothèque : ses exemples tirent SDL2 et ses tests tirent des modèles de
# plusieurs centaines de Mo, dont rien n'a sa place dans un APK.
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
# Le NDK fournit bien libomp, mais la lier obligerait à embarquer un .so de plus
# par ABI pour un gain nul : ggml parallélise déjà par son propre pool de threads.
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp EXCLUDE_FROM_ALL)

add_library(luxstt SHARED whisper_jni.cpp)

target_link_libraries(luxstt
    whisper
    android      # AAssetManager : le modèle est lu directement depuis l'APK
    log)
