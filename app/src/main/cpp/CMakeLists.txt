cmake_minimum_required(VERSION 3.18.1)
project("txcore")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -Wall -Wextra")

find_library(log-lib log)

add_library(txcore SHARED
    tx_core/engine.cpp
    tx_core/screen.cpp
    tx_core/pty.cpp
    tx_core/parser.cpp
    tx_core/renderer.cpp
    tx_core/jni_bridge.cpp
)

target_include_directories(txcore PRIVATE include)

target_link_libraries(txcore
    ${log-lib}
    pthread
)

