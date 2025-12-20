package com.jayrads.unityupdater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object ApkSignatureVerifier {
    fun verifyPinnedCert(context: Context, apkPath: String, pinnedCertSha256Hex: String): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val pkgInfo: PackageInfo = pm.getPackageArchiveInfo(apkPath, flags) ?: return false

        val sigBytesList: List<ByteArray> = if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = pkgInfo.signingInfo ?: return false
            val sigs = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
            sigs.map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            (pkgInfo.signatures ?: return false).map { it.toByteArray() }
        }

        val cf = CertificateFactory.getInstance("X.509")
        val pinned = pinnedCertSha256Hex.lowercase().replace(":", "").trim()

        return sigBytesList.any { sigBytes ->
            val cert = cf.generateCertificate(ByteArrayInputStream(sigBytes)) as X509Certificate
            sha256Hex(cert.encoded) == pinned
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
