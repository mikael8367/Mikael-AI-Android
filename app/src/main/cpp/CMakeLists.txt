cmake_minimum_required(VERSION 3.22.1)
project(mikael_android_engines LANGUAGES C CXX)

include(FetchContent)

# Reproducible upstream revision; Android baseline stays portable ARM64.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG 281e7dad7cfe4539b13d0218f4b0fd5a03f3abc9
    GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(mikael_llama SHARED llama_engine_jni.cpp)
target_compile_features(mikael_llama PRIVATE cxx_std_17)
target_compile_options(mikael_llama PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(mikael_llama PRIVATE llama android log)

# O motor autoral MKAI é independente do runtime GGUF e também precisa
# estar empacotado para LocalInferenceEngine.loadLibrary("mikael_engine").
add_library(mikael_engine SHARED mikael_engine_jni.cpp)
target_compile_features(mikael_engine PRIVATE cxx_std_17)
target_compile_options(mikael_engine PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(mikael_engine PRIVATE android log)
