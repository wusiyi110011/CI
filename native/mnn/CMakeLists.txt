# Copyright 2026 吴思毅
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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
