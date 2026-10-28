cmake_minimum_required(VERSION 3.22.1)
project(aetheris)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(aetheris SHARED
    jni.cpp
    engine.cpp
    search.cpp
    evaluate.cpp
    nnue.cpp
)

target_include_directories(aetheris PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(aetheris log)

# UCI executable for subprocess? Android runs binary; build as executable too
add_executable(aetheris_engine
    uci.cpp
    engine.cpp
    search.cpp
    evaluate.cpp
    nnue.cpp
)
set_target_properties(aetheris_engine PROPERTIES SUFFIX ".so" OUTPUT_NAME "libaetheris_engine")
target_include_directories(aetheris_engine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(aetheris_engine log)
# Separate UCI binary that starts in Pure Classical mode by default.
add_executable(aetheris_classical
    uci.cpp
    engine.cpp
    search.cpp
    evaluate.cpp
    nnue.cpp
)
target_compile_definitions(aetheris_classical PRIVATE AETHERIS_DEFAULT_CLASSICAL=1)
set_target_properties(aetheris_classical PROPERTIES SUFFIX ".so" OUTPUT_NAME "libaetheris_classical")
target_include_directories(aetheris_classical PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(aetheris_classical log)
