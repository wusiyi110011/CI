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
import java.io.IOException
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * 从 tar.bz2 归档里只解出 [whitelist] 命中的文件名、按文件名压平写入 [destDir] 根目录
 * （忽略归档内部的目录结构），并校验规范化后的目标路径没有跳出 [destDir]，防止 zip-slip。
 *
 * 解压耗时较长（单线程 bzip2 解压大文件可能要几分钟），执行中途很可能被系统打断
 * （后台任务执行配额、Doze 等）。目标文件名只在整份内容写完后才 rename 落地，
 * 中途被杀不会留下"看起来完整、实际写了一半"的半成品；已存在的目标文件直接跳过，
 * 这样重试时不用重复写盘，只需要重新走一遍必经的顺序解压扫描。
 */
object ArchiveExtractor {
    fun extract(archive: File, destDir: File, whitelist: Set<String>) {
        val destCanonical = destDir.canonicalFile
        BZip2CompressorInputStream(archive.inputStream()).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val basename = File(entry.name).name
                        if (basename in whitelist) {
                            val target = File(destDir, basename)
                            val targetCanonical = target.canonicalFile
                            if (!targetCanonical.path.startsWith(destCanonical.path + File.separator)) {
                                throw IOException("压缩包条目路径非法：${entry.name}")
                            }
                            if (!target.exists()) {
                                val tmp = File(destDir, "$basename.extracting")
                                tmp.outputStream().use { out -> tar.copyTo(out) }
                                if (!tmp.renameTo(target)) {
                                    tmp.copyTo(target, overwrite = true)
                                    tmp.delete()
                                }
                            }
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }
}
