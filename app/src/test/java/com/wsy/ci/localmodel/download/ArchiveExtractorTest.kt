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
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveExtractorTest {

    private fun buildArchive(vararg entries: Pair<String, String?>): File {
        val archive = Files.createTempFile("fixture", ".tar.bz2").toFile()
        BZip2CompressorOutputStream(archive.outputStream()).use { bzip ->
            TarArchiveOutputStream(bzip).use { tar ->
                entries.forEach { (name, content) ->
                    if (content == null) {
                        tar.putArchiveEntry(TarArchiveEntry("$name/"))
                        tar.closeArchiveEntry()
                    } else {
                        val bytes = content.toByteArray()
                        val entry = TarArchiveEntry(name)
                        entry.size = bytes.size.toLong()
                        tar.putArchiveEntry(entry)
                        tar.write(bytes)
                        tar.closeArchiveEntry()
                    }
                }
            }
        }
        return archive
    }

    private val whitelist = setOf("model.int8.onnx", "tokens.txt")

    @Test
    fun `只解出白名单文件并按文件名压平到目标目录`() {
        val archive = buildArchive(
            "sherpa-onnx-sense-voice/model.int8.onnx" to "模型内容",
            "sherpa-onnx-sense-voice/tokens.txt" to "词表内容",
            "sherpa-onnx-sense-voice/README.md" to "说明",
            "sherpa-onnx-sense-voice/test_wavs/zh.wav" to "音频",
        )
        val destDir = Files.createTempDirectory("dest").toFile()

        ArchiveExtractor.extract(archive, destDir, whitelist)

        assertEquals("模型内容", File(destDir, "model.int8.onnx").readText())
        assertEquals("词表内容", File(destDir, "tokens.txt").readText())
        assertFalse(File(destDir, "README.md").exists())
        assertFalse(File(destDir, "sherpa-onnx-sense-voice").exists())
    }

    @Test
    fun `条目路径带目录穿越也只落在目标目录根不会跳出去`() {
        val archive = buildArchive("../../../tmp/evil/tokens.txt" to "词表内容")
        val destDir = Files.createTempDirectory("dest").toFile()

        ArchiveExtractor.extract(archive, destDir, whitelist)

        val extracted = File(destDir, "tokens.txt")
        assertTrue(extracted.exists())
        assertEquals("词表内容", extracted.readText())
        assertTrue(extracted.canonicalFile.path.startsWith(destDir.canonicalFile.path))
    }

    @Test
    fun `不在白名单里的条目全部跳过`() {
        val archive = buildArchive("random.bin" to "无关内容")
        val destDir = Files.createTempDirectory("dest").toFile()

        ArchiveExtractor.extract(archive, destDir, whitelist)

        assertEquals(0, destDir.listFiles()?.size ?: 0)
    }

    @Test
    fun `目录条目被跳过不会当成文件写入`() {
        val archive = buildArchive("model.int8.onnx" to null)
        val destDir = Files.createTempDirectory("dest").toFile()

        ArchiveExtractor.extract(archive, destDir, whitelist)

        assertFalse(File(destDir, "model.int8.onnx").exists())
    }
}
