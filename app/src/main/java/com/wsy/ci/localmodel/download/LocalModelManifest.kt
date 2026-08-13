package com.wsy.ci.localmodel.download

/** 固定下载的本地模型文件。版本号固定，避免远端 master 漂移导致模型不可复现。 */
data class LocalModelFile(
    val path: String,
    val size: Long,
    val sha256: String,
)

data class LocalModelManifest(
    val modelId: String,
    val revision: String,
    val files: List<LocalModelFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.size }

    fun file(path: String): LocalModelFile? = files.firstOrNull { it.path == path }
}

/** MNN/Qwen3.5-2B-MNN 在 ModelScope 上的固定 revision。 */
object Qwen35ModelManifest {
    const val MODEL_ID = "MNN/Qwen3.5-2B-MNN"
    const val REVISION = "b9ae8c8f3da3fceb4278b558a747286b8a087dbe"
    const val DEFAULT_BASE_URL = "https://modelscope.cn/models/MNN/Qwen3.5-2B-MNN/resolve"

    val manifest = LocalModelManifest(
        modelId = MODEL_ID,
        revision = REVISION,
        files = listOf(
            LocalModelFile(".gitattributes", 2_324L, "f3f89c356c2ac2e67fcf56245d0f5d1b1dc581ba79e4a01cd2c928bada25fc2b"),
            LocalModelFile("config.json", 652L, "92853033efe602f95efca3e1c05cd8b108f973c8beed417843a9671f8147ed8d"),
            LocalModelFile("configuration.json", 46L, "56c362f108bb9f17fc0e7b490b7e8927c03dc4478631b4f771c37728019beaa2"),
            LocalModelFile("export_args.json", 1040L, "a5b3a7d2c45e53c887e539259696d5fcec793637c9a3e9fbc7cd0dd008af6a87"),
            LocalModelFile("llm.mnn", 2_148_136L, "23df98f8b341b277365e0bbca025c1d192939e3d32d7f79776352c6f32e77960"),
            LocalModelFile("llm.mnn.json", 5_344_018L, "7131ff4f1a441add1039d371815ad94652ae6801f579591cb2f9ad90b5954025"),
            LocalModelFile("llm.mnn.weight", 1_176_647_702L, "c93f71a2dbecf9328782bd38861656d8faa82e95e7f99607350074768a482054"),
            LocalModelFile("llm_config.json", 8_692L, "a88234b36c2af0eff8e5c89667011badf71c15e30459eb0e21030a8f3f9ed240"),
            LocalModelFile("README.md", 1_252L, "2f74f9fb06d39380a68adc6f6a21799131b6301b24c8abfc9c7586bf2fea0483"),
            LocalModelFile("tokenizer.txt", 6_465_727L, "7e75de1f279a10b65bd9dc1a5207205cb8993823861c4c42bbbd74e48e1c23a4"),
            LocalModelFile("visual.mnn", 488_096L, "88fc40a7b676e90eb2cb86d854db15cb90b9eb1f34087ab0f48c5e43572c8dac"),
            LocalModelFile("visual.mnn.weight", 195_587_264L, "8f90e106f5b9ae9a939faed240305cfdd5c6740ae91d3fc418a990bee0cce36b"),
        ),
    )

    fun url(file: LocalModelFile, baseUrl: String = DEFAULT_BASE_URL): String =
        "${baseUrl.trimEnd('/')}/$REVISION/${file.path}"
}
