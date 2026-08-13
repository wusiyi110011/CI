package com.wsy.ci.localmodel.download

import java.io.File
import java.security.MessageDigest

object LocalModelVerifier {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedSize: Long, expectedSha256: String): Boolean =
        file.isFile && file.length() == expectedSize && sha256(file).equals(expectedSha256, ignoreCase = true)
}
