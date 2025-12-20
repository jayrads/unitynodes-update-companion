package com.jayrads.unityupdater

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object FileHash {
    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
