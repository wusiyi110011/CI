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

package com.wsy.ci.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Properties
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {

    @Test
    fun `备份目录打包后可完整解包并通过元数据校验`() {
        val source = fixtureDirectory()
        val archive = Files.createTempFile("ci-backup", ".zip").toFile()
        val extracted = Files.createTempDirectory("ci-extracted").toFile().apply { delete() }

        BackupArchive.pack(source, archive)
        archive.inputStream().use { BackupArchive.extract(it, extracted) }
        val info = BackupArchive.validate(extracted, expectedDatabaseVersion = 5)

        assertEquals(5, info.databaseVersion)
        assertEquals(1_700_000_000_000L, info.createdAtMillis)
        BackupArchive.requiredEntries.forEach { name ->
            assertEquals(File(source, name).readBytes().toList(), File(extracted, name).readBytes().toList())
        }
    }

    @Test
    fun `文件内容被篡改后摘要校验失败`() {
        val directory = fixtureDirectory()
        File(directory, BackupArchive.DATABASE_FILE).appendText("篡改")

        val error = assertThrows(IllegalStateException::class.java) {
            BackupArchive.validate(directory, expectedDatabaseVersion = 5)
        }

        assertTrue(error.message.orEmpty().contains("大小不匹配"))
    }

    @Test
    fun `损坏或缺少条目的ZIP被拒绝`() {
        val destination = Files.createTempDirectory("ci-damaged").toFile().apply { deleteRecursively() }

        val error = assertThrows(IllegalStateException::class.java) {
            BackupArchive.extract(ByteArrayInputStream("不是ZIP".toByteArray()), destination)
        }

        assertTrue(error.message.orEmpty().contains("缺少文件"))
    }

    @Test
    fun `未知条目和路径穿越条目均被拒绝`() {
        listOf("extra.txt", "../ci.db", "folder/ci.db").forEach { illegalName ->
            val destination = Files.createTempDirectory("ci-illegal").toFile().apply { deleteRecursively() }
            val error = assertThrows(IllegalStateException::class.java) {
                BackupArchive.extract(
                    ByteArrayInputStream(zipBytes(listOf(illegalName to "内容".toByteArray()))),
                    destination,
                )
            }
            assertTrue(error.message.orEmpty().contains("未知或非法"))
            assertFalse(File(destination.parentFile, "ci.db").exists())
        }
    }

    @Test
    fun `重复条目被拒绝`() {
        val destination = Files.createTempDirectory("ci-duplicate").toFile().apply { deleteRecursively() }
        val duplicate = listOf(
            BackupArchive.DATABASE_FILE to "一".toByteArray(),
            BackupArchive.DATABASE_FILE to "二".toByteArray(),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            BackupArchive.extract(ByteArrayInputStream(zipBytes(duplicate)), destination)
        }

        assertTrue(error.message.orEmpty().contains("重复文件"))
    }

    @Test
    fun `单个文件或整个压缩包超限时被拒绝`() {
        val entries = completeEntries(database = "123456".toByteArray())
        val strictEntries = BackupArchiveLimits().maxEntryBytes.toMutableMap().apply {
            this[BackupArchive.DATABASE_FILE] = 5
        }
        val entryDestination = Files.createTempDirectory("ci-entry-limit").toFile().apply { deleteRecursively() }
        val entryError = assertThrows(IllegalStateException::class.java) {
            BackupArchive.extract(
                ByteArrayInputStream(zipBytes(entries)),
                entryDestination,
                BackupArchiveLimits(maxEntryBytes = strictEntries),
            )
        }
        assertTrue(entryError.message.orEmpty().contains("文件过大"))

        val archiveDestination = Files.createTempDirectory("ci-archive-limit").toFile().apply { deleteRecursively() }
        val archiveError = assertThrows(IllegalStateException::class.java) {
            BackupArchive.extract(
                ByteArrayInputStream(zipBytes(entries)),
                archiveDestination,
                BackupArchiveLimits(maxArchiveBytes = 10),
            )
        }
        assertTrue(archiveError.message.orEmpty().contains("超过大小限制"))
    }

    @Test
    fun `数据库版本不一致时给出升级提示`() {
        val directory = fixtureDirectory(databaseVersion = 4)

        val error = assertThrows(IllegalStateException::class.java) {
            BackupArchive.validate(directory, expectedDatabaseVersion = 5)
        }

        assertTrue(error.message.orEmpty().contains("文件为 4，当前为 5"))
        assertTrue(error.message.orEmpty().contains("升级两台设备"))
    }

    @Test
    fun `恢复失败会执行回滚并保留原始异常`() {
        var rolledBack = false
        val original = IllegalStateException("写入设置失败")

        val thrown = assertThrows(IllegalStateException::class.java) {
            BackupRestoreGuard.run(
                apply = { throw original },
                rollback = { rolledBack = true },
            )
        }

        assertTrue(rolledBack)
        assertTrue(thrown === original)
    }

    private fun fixtureDirectory(databaseVersion: Int = 5): File {
        val directory = Files.createTempDirectory("ci-backup-source").toFile()
        val database = File(directory, BackupArchive.DATABASE_FILE).apply { writeText("SQLite测试内容") }
        val appSettings = File(directory, BackupArchive.APP_SETTINGS_FILE).apply { writeText("theme=DARK") }
        val routes = File(directory, BackupArchive.LLM_ROUTES_FILE).apply { writeText("review=OFF") }
        Properties().apply {
            setProperty("formatVersion", BackupArchive.FORMAT_VERSION.toString())
            setProperty("databaseVersion", databaseVersion.toString())
            setProperty("createdAtMillis", "1700000000000")
            setFileMetadata("database", database)
            setFileMetadata("appSettings", appSettings)
            setFileMetadata("llmRoutes", routes)
        }.also { properties ->
            File(directory, BackupArchive.METADATA_FILE).outputStream().use { properties.store(it, null) }
        }
        return directory
    }

    private fun Properties.setFileMetadata(prefix: String, file: File) {
        setProperty("${prefix}Size", file.length().toString())
        setProperty("${prefix}Sha256", BackupArchive.sha256(file))
    }

    private fun completeEntries(database: ByteArray = "db".toByteArray()): List<Pair<String, ByteArray>> = listOf(
        BackupArchive.DATABASE_FILE to database,
        BackupArchive.APP_SETTINGS_FILE to "settings".toByteArray(),
        BackupArchive.LLM_ROUTES_FILE to "routes".toByteArray(),
        BackupArchive.METADATA_FILE to "metadata".toByteArray(),
    )

    private fun zipBytes(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipArchiveOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putArchiveEntry(ZipArchiveEntry(name).apply { size = bytes.size.toLong() })
                zip.write(bytes)
                zip.closeArchiveEntry()
            }
        }
        return output.toByteArray()
    }
}
