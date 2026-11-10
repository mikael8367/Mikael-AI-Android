cmake_minimum_required(VERSION 3.22.1)
project(mikael_engine LANGUAGES C CXX)

add_library(mikael_engine SHARED mikael_engine_jni.cpp)
target_compile_features(mikael_engine PRIVATE cxx_std_17)
target_compile_options(mikael_engine PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(mikael_engine PRIVATE android log)
