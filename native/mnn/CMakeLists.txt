cmake_minimum_required(VERSION 3.22.1)
project(cimnn LANGUAGES CXX)

if(NOT DEFINED MNN_ROOT OR NOT DEFINED MNN_LIBRARY)
    message(FATAL_ERROR "请通过 -DMNN_ROOT 与 -DMNN_LIBRARY 指定 MNN 3.6.1 源码和 arm64-v8a 库")
endif()

add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${MNN_LIBRARY}")

add_library(cimnn SHARED cimnn_jni.cpp)
target_compile_features(cimnn PRIVATE cxx_std_17)
target_compile_definitions(cimnn PRIVATE LLM_SUPPORT_VISION)
target_include_directories(cimnn PRIVATE
    "${MNN_ROOT}/include"
    "${MNN_ROOT}/transformers/llm/engine/include"
)
target_link_libraries(cimnn PRIVATE MNN log android)
target_link_options(cimnn PRIVATE "-Wl,-z,max-page-size=16384")
