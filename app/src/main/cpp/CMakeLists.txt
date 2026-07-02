cmake_minimum_required(VERSION 3.22.1)
project("fpsbinder" LANGUAGES CXX)

# Require C++17 for std::string_view, structured bindings, etc.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(fpsbinder SHARED fps_binder.cpp)

# Link Android log library for __android_log_print
target_link_libraries(fpsbinder PRIVATE log)
