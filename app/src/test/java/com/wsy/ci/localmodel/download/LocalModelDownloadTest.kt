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

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelDownloadTest {
    @Test
    fun `固定清单revision与文件校验信息稳定`() {
        assertEquals("MNN/Qwen3.5-2B-MNN", Qwen35ModelManifest.MODEL_ID)
        assertEquals("b9ae8c8f3da3fceb4278b558a747286b8a087dbe", Qwen35ModelManifest.REVISION)
        assertEquals(12, Qwen35ModelManifest.manifest.files.size)
        assertTrue(Qwen35ModelManifest.manifest.files.all { it.size >= 0 && it.sha256.matches(Regex("[0-9a-f]{64}")) })
        assertTrue(Qwen35ModelManifest.url(Qwen35ModelManifest.manifest.files.first()).contains(Qwen35ModelManifest.REVISION))
    }

    @Test
    fun `sha256和大小校验拒绝损坏文件`() {
        val file = Files.createTempFile("model", ".bin").toFile()
        file.writeText("abc")
        assertTrue(LocalModelVerifier.verify(file, 3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
        file.appendText("x")
        assertFalse(LocalModelVerifier.verify(file, 3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb41015ad"))
        file.delete()
    }

    @Test
    fun `状态持有器更新并持久化且进度不超过文件大小`() {
        var saved: LocalModelDownloadState? = null
        val manifest = LocalModelManifest("x", "r", listOf(LocalModelFile("a", 10, "0".repeat(64))))
        val holder = LocalModelDownloadStateHolder(LocalModelDownloadState.initial(manifest)) { saved = it }
        holder.update { it.copy(status = LocalModelDownloadStatus.DOWNLOADING) }
        holder.update { it.copy(files = it.files.map { f -> f.copy(downloaded = 99) }) }
        assertEquals(LocalModelDownloadStatus.DOWNLOADING, holder.get().status)
        assertEquals(99L, holder.get().files.single().downloaded)
        assertEquals(holder.get(), saved)
        assertEquals(1f, holder.get().fraction, 0f)
    }
}
