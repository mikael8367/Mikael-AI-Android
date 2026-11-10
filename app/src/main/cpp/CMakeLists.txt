cmake_minimum_required(VERSION 3.22.1)
project(mikael_llama LANGUAGES C CXX)

# The CI's installable debug variant is also used for on-device performance
# diagnosis. Keep debug symbols, but avoid compiling llama.cpp/ggml kernels at
# -O0 on low-power ARM devices. Release builds retain their normal toolchain flags.
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_C_FLAGS_DEBUG "-O2 -g" CACHE STRING "Optimized debug C flags" FORCE)
    set(CMAKE_CXX_FLAGS_DEBUG "-O2 -g" CACHE STRING "Optimized debug C++ flags" FORCE)
endif()

include(FetchContent)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG b12143
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(mikael_llama SHARED mikael_llama_jni.cpp)
target_compile_features(mikael_llama PRIVATE cxx_std_17)
target_compile_options(mikael_llama PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(mikael_llama PRIVATE llama android log)
