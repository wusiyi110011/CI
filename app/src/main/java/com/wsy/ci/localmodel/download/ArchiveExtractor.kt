package com.wsy.ci.localmodel.download

import java.io.File
import java.io.IOException
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * 从 tar.bz2 归档里只解出 [whitelist] 命中的文件名、按文件名压平写入 [destDir] 根目录
 * （忽略归档内部的目录结构），并校验规范化后的目标路径没有跳出 [destDir]，防止 zip-slip。
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
                            target.outputStream().use { out -> tar.copyTo(out) }
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }
}
