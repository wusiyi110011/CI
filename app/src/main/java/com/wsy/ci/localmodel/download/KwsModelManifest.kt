/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.wsy.ci.localmodel.download

/** sherpa-onnx 中文开放词汇唤醒模型；固定 ModelScope Git revision，支持运行时更换关键词。 */
object KwsModelManifest {
    const val MODEL_ID = "pkufool/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
    const val REVISION = "3787015f084cb241dfa0e4ba237703a2d4322d50"
    const val DEFAULT_BASE_URL = "https://modelscope.cn/models/$MODEL_ID/resolve"

    const val ENCODER = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val DECODER = "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val JOINER = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val TOKENS = "tokens.txt"

    val manifest = LocalModelManifest(
        modelId = MODEL_ID,
        revision = REVISION,
        files = listOf(
            LocalModelFile(ENCODER, 4_807_159L, "017af32f2c0138f931d05fbc009ee864295e910aff304f77d2f563815fc834fb"),
            LocalModelFile(DECODER, 181_025L, "fe53b8d6a07bc5373d1770649025a5a985c8fb3dab70323386a1b73aacba0546"),
            LocalModelFile(JOINER, 65_208L, "431de10b554f134ef8af320feea2db337e641290449a3d3f6cb6e5f5fd2c9c3d"),
            LocalModelFile(TOKENS, 1_627L, "72316508d9119696145abc6f1f8cdc46287535c34e5ce7e595f845cb1499cf2e"),
        ),
    )

    fun url(file: LocalModelFile, baseUrl: String = DEFAULT_BASE_URL): String =
        "${baseUrl.trimEnd('/')}/$REVISION/${file.path}"
}
