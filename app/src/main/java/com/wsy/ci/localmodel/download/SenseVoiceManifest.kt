/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wsy.ci.localmodel.download

/**
 * sherpa-onnx 的 SenseVoice-Small 离线语音识别模型。GitHub Release 的 tar.bz2 在部分国内
 * 网络下 TLS 握手长期不稳定（ping 得通、连接却经常超时），改用同一份模型在 HuggingFace 上
 * 逐文件直发的版本（不再需要下载后解压），固定 revision 到具体 commit，经 hf-mirror.com
 * 镜像下载。
 */
object SenseVoiceManifest {
    const val MODEL_ID = "csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"
    const val REVISION = "355f4d4884d8afd08aef04b9007a8556d7b463b2"
    const val DEFAULT_BASE_URL = "https://hf-mirror.com/$MODEL_ID/resolve"

    val manifest = LocalModelManifest(
        modelId = MODEL_ID,
        revision = REVISION,
        files = listOf(
            LocalModelFile(
                path = "model.int8.onnx",
                size = 237_115_547L,
                sha256 = "12ca1a2ae7ecf3e0019ef2822307ee0b5cadc9196569e379b4c4026f8205276d",
            ),
            LocalModelFile(
                path = "tokens.txt",
                size = 315_894L,
                sha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
            ),
        ),
    )

    fun url(file: LocalModelFile, baseUrl: String = DEFAULT_BASE_URL): String =
        "${baseUrl.trimEnd('/')}/$REVISION/${file.path}"
}
