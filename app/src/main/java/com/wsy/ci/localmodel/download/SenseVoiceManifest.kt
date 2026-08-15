package com.wsy.ci.localmodel.download

/** sherpa-onnx 的 SenseVoice-Small 离线语音识别模型，来自 GitHub Release（非 ModelScope）。 */
object SenseVoiceManifest {
    const val MODEL_ID = "k2-fsa/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"
    const val REVISION = "asr-models"
    const val DOWNLOAD_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2"

    val manifest = LocalModelManifest(
        modelId = MODEL_ID,
        revision = REVISION,
        files = listOf(
            LocalModelFile(
                path = "sensevoice.tar.bz2",
                size = 165_783_878L,
                sha256 = "7305f7905bfcf77fa0b39388a313f3da35c68d971661a65475b56fb2162c8e63",
            ),
        ),
    )
}
