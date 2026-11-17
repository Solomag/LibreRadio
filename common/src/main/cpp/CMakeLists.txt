cmake_minimum_required(VERSION 3.22.1)
project(byedpi C)

# ByeDPI 0.17.3 (byedpi/, MIT, https://github.com/hufrea/byedpi), unchanged; its main() is renamed so the
# JNI bridge can call it.
add_library(byedpi SHARED
        byedpi_jni.c
        byedpi/conev.c
        byedpi/desync.c
        byedpi/extend.c
        byedpi/main.c
        byedpi/mpool.c
        byedpi/packets.c
        byedpi/proxy.c)

target_include_directories(byedpi PRIVATE byedpi)
target_compile_definitions(byedpi PRIVATE _DEFAULT_SOURCE main=byedpi_main)
target_compile_options(byedpi PRIVATE -std=c99 -O2 -Wno-unused -Wno-unused-parameter)
target_link_options(byedpi PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(byedpi log)
