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

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupArchiveInfo(
    val createdAtMillis: Long,
    val databaseVersion: Int,
    val sizeBytes: Long,
)

/** ZIP 输入限制；测试可收紧阈值，正式环境使用足够容纳纯文本与 SQLite 数据的默认值。 */
data class BackupArchiveLimits(
    val maxArchiveBytes: Long = 512L * 1024L * 1024L,
    val maxEntryBytes: Map<String, Long> = mapOf(
        BackupArchive.DATABASE_FILE to 1024L * 1024L * 1024L,
        BackupArchive.APP_SETTINGS_FILE to 4L * 1024L * 1024L,
        BackupArchive.LLM_ROUTES_FILE to 4L * 1024L * 1024L,
        BackupArchive.METADATA_FILE to 1024L * 1024L,
    ),
)

/**
 * 可跨设备传输的数据备份包格式。
 *
 * 归档只接受根目录下四个固定文件；未知条目、目录、重复条目和带路径的条目全部拒绝，
 * 解包时始终按白名单决定目标文件名，不使用归档提供的路径。
 */
object BackupArchive {
    const val DATABASE_FILE = "ci.db"
    const val APP_SETTINGS_FILE = "app_settings.xml"
    const val LLM_ROUTES_FILE = "llm_routes.xml"
    const val METADATA_FILE = "backup.properties"
    const val FORMAT_VERSION = 1

    val requiredEntries = listOf(
        DATABASE_FILE,
        APP_SETTINGS_FILE,
        LLM_ROUTES_FILE,
        METADATA_FILE,
    )

    fun pack(sourceDirectory: File, destination: File) {
        requiredEntries.forEach { name ->
            check(File(sourceDirectory, name).isFile) { "备份缺少文件：$name" }
        }
        val parent = destination.parentFile ?: error("导出文件缺少父目录")
        check(parent.mkdirs() || parent.isDirectory) { "无法创建导出目录" }
        val temporary = File(parent, ".${destination.name}.tmp")
        temporary.delete()
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                requiredEntries.forEach { name ->
                    val source = File(sourceDirectory, name)
                    zip.putNextEntry(ZipEntry(name).apply { time = source.lastModified() })
                    source.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            if (destination.exists()) check(destination.delete()) { "无法替换旧导出文件" }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                check(temporary.delete()) { "无法清理导出暂存文件" }
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    fun extract(
        source: InputStream,
        destinationDirectory: File,
        limits: BackupArchiveLimits = BackupArchiveLimits(),
    ) {
        check(destinationDirectory.mkdirs() || destinationDirectory.isDirectory) { "无法创建导入暂存目录" }
        check(destinationDirectory.listFiles().isNullOrEmpty()) { "导入暂存目录不是空目录" }
        val seen = mutableSetOf<String>()
        val created = mutableListOf<File>()
        try {
            ZipInputStream(LimitedInputStream(source.buffered(), limits.maxArchiveBytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    check(!entry.isDirectory) { "备份包不允许目录条目：$name" }
                    check(name in requiredEntries && '/' !in name && '\\' !in name) {
                        "备份包包含未知或非法文件：$name"
                    }
                    check(seen.add(name)) { "备份包包含重复文件：$name" }
                    val maximum = limits.maxEntryBytes[name] ?: error("没有配置文件大小限制：$name")
                    if (entry.size >= 0) check(entry.size <= maximum) { "备份文件过大：$name" }
                    val target = File(destinationDirectory, name)
                    created += target
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var written = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            written += count
                            check(written <= maximum) { "备份文件过大：$name" }
                            output.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
            val missing = requiredEntries.toSet() - seen
            check(missing.isEmpty()) { "备份包缺少文件：${missing.joinToString()}" }
        } catch (error: Throwable) {
            created.forEach(File::delete)
            throw when (error) {
                is IllegalStateException, is IOException -> error
                else -> IOException("备份包读取失败", error)
            }
        }
    }

    /** 校验元数据、文件大小和摘要；SQLite 结构与完整性由 Android 备份引擎继续校验。 */
    fun validate(
        directory: File,
        expectedDatabaseVersion: Int? = null,
    ): BackupArchiveInfo {
        val metadataFile = File(directory, METADATA_FILE)
        check(metadataFile.isFile) { "备份元数据缺失" }
        val metadata = Properties().apply { metadataFile.inputStream().use(::load) }
        val formatVersion = metadata.requiredLong("formatVersion").toInt()
        check(formatVersion == FORMAT_VERSION) { "备份格式版本不受支持：$formatVersion" }
        val databaseVersion = metadata.requiredLong("databaseVersion").toInt()
        if (expectedDatabaseVersion != null) {
            check(databaseVersion == expectedDatabaseVersion) {
                "备份数据库版本不兼容：文件为 $databaseVersion，当前为 $expectedDatabaseVersion，请先升级两台设备"
            }
        }
        verifyFile(directory, metadata, DATABASE_FILE, "database")
        verifyFile(directory, metadata, APP_SETTINGS_FILE, "appSettings")
        verifyFile(directory, metadata, LLM_ROUTES_FILE, "llmRoutes")
        val createdAt = metadata.requiredLong("createdAtMillis")
        check(createdAt > 0) { "备份创建时间无效" }
        return BackupArchiveInfo(
            createdAtMillis = createdAt,
            databaseVersion = databaseVersion,
            sizeBytes = requiredEntries.sumOf { File(directory, it).length() },
        )
    }

    private fun verifyFile(directory: File, metadata: Properties, name: String, keyPrefix: String) {
        val file = File(directory, name)
        check(file.isFile) { "备份缺少文件：$name" }
        check(file.length() == metadata.requiredLong("${keyPrefix}Size")) { "备份文件大小不匹配：$name" }
        val expected = metadata.getProperty("${keyPrefix}Sha256")
            ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
            ?: error("备份摘要无效：$name")
        check(sha256(file) == expected) { "备份文件校验失败：$name" }
    }

    private fun Properties.requiredLong(name: String): Long =
        getProperty(name)?.toLongOrNull() ?: error("备份元数据字段无效：$name")

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class LimitedInputStream(input: InputStream, private val maximum: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) add(1) }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) add(it.toLong()) }

        private fun add(delta: Long) {
            count += delta
            check(count <= maximum) { "备份包超过大小限制" }
        }
    }
}

/** 恢复失败时执行回滚，并保留原始失败作为主异常。 */
object BackupRestoreGuard {
    fun run(apply: () -> Unit, rollback: () -> Unit) {
        try {
            apply()
        } catch (original: Throwable) {
            runCatching(rollback).exceptionOrNull()?.let(original::addSuppressed)
            throw original
        }
    }
}
