package com.jayrads.unityupdater

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.security.MessageDigest

object ApkSignatureVerifier {

    private const val TAG = "ApkSignatureVerifier"

    /**
     * Extracts SHA-256 of the signing certificate used to sign an APK file.
     * This represents the publisher identity (TOFU).
     */
    fun getSignerCertSha256(ctx: Context, apkPath: String): String? {
        return try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                Log.w(TAG, "APK missing at path=$apkPath")
                return null
            }

            val pm = ctx.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val pi = pm.getPackageArchiveInfo(apkPath, flags)
            if (pi == null) {
                Log.e(TAG, "getPackageArchiveInfo returned null for $apkPath")
                return null
            }

            // ✅ Critical for archive APKs: tell PM where the APK actually lives.
            pi.applicationInfo?.sourceDir = apkPath
            pi.applicationInfo?.publicSourceDir = apkPath

            val sigBytes: ByteArray? = if (Build.VERSION.SDK_INT >= 28) {
                val info = pi.signingInfo
                if (info == null) {
                    Log.e(TAG, "signingInfo is null for archive APK $apkPath")
                    null
                } else {
                    val sigs = if (info.hasMultipleSigners()) {
                        info.apkContentsSigners
                    } else {
                        info.signingCertificateHistory
                    }
                    sigs.firstOrNull()?.toByteArray()
                }
            } else {
                @Suppress("DEPRECATION")
                pi.signatures?.firstOrNull()?.toByteArray()
            }

            if (sigBytes == null) {
                Log.e(TAG, "No signatures found for archive APK $apkPath")
                return null
            }

            val sha = sha256Hex(sigBytes)
            Log.d(TAG, "Signer cert SHA-256 extracted: $sha")
            sha
        } catch (t: Throwable) {
            Log.e(TAG, "Failed extracting signer cert SHA-256", t)
            null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
