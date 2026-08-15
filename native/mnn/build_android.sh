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

#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "用法：$0 <MNN-3.6.1 源码目录> <MNN arm64-v8a 构建目录>" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "$0")" && pwd)"
project_dir="$(cd "$script_dir/../.." && pwd)"
mnn_root="$(cd "$1" && pwd)"
mnn_build="$(cd "$2" && pwd)"
android_sdk="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"
ndk_root="$android_sdk/ndk/27.2.12479018"
cmake_bin="$android_sdk/cmake/3.22.1/bin/cmake"
build_dir="$script_dir/build/android-arm64"

"$cmake_bin" -S "$script_dir" -B "$build_dir" \
  -DCMAKE_TOOLCHAIN_FILE="$ndk_root/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-31 \
  -DANDROID_STL=c++_shared \
  -DMNN_ROOT="$mnn_root" \
  -DMNN_LIBRARY="$mnn_build/libMNN.so" \
  -DCMAKE_BUILD_TYPE=Release
"$cmake_bin" --build "$build_dir" --parallel

mkdir -p "$project_dir/app/src/main/jniLibs/arm64-v8a"
cp "$mnn_build/libMNN.so" "$project_dir/app/src/main/jniLibs/arm64-v8a/libMNN.so"
cp "$build_dir/libcimnn.so" "$project_dir/app/src/main/jniLibs/arm64-v8a/libcimnn.so"
cp "$ndk_root/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" \
  "$project_dir/app/src/main/jniLibs/arm64-v8a/libc++_shared.so"

echo "已写入 app/src/main/jniLibs/arm64-v8a"
