# Build hôte du harnais de mesure. whisper.cpp est récupéré en v1.7.4, la
# version qu'embarquait la dictée hors ligne de l'application : mesurer contre
# une autre version ne serait plus comparable aux résultats d'août et de
# septembre 2026. L'application ne l'embarque plus depuis le 2 octobre 2026 (le
# sous-module a été retiré) ; le banc, lui, continue de pouvoir mesurer un
# modèle hors ligne.
cmake_minimum_required(VERSION 3.16)
project(lux_bench CXX C)

set(CMAKE_CXX_STANDARD 17)
if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG        v1.7.4
    GIT_SHALLOW    TRUE)
FetchContent_MakeAvailable(whisper)

add_executable(lux_bench lux_bench.cpp)
target_link_libraries(lux_bench PRIVATE whisper)
