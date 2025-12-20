package com.jayrads.unityupdater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val expectedId = Prefs.getDownloadId(context)
        if (completedId <= 0 || completedId != expectedId) return

        val apkPath = Prefs.getApkPath(context) ?: return
        val apkFile = File(apkPath)
        if (!apkFile.exists()) return

        CoroutineScope(Dispatchers.Default).launch {
            val latest = runCatching { ApiClient.fetchLatest() }.getOrNull()
            val expectedSha = latest?.sha256?.trim()?.lowercase()
            if (expectedSha.isNullOrBlank()) {
                NotificationHelper.showIntegrityFailed(context, "Missing expected SHA-256")
                return@launch
            }

            val actualSha = runCatching { FileHash.sha256Hex(apkFile).lowercase() }.getOrNull()
            if (actualSha == null || actualSha != expectedSha) {
                NotificationHelper.showIntegrityFailed(context, "SHA-256 mismatch")
                return@launch
            }

            val pinnedOk = ApkSignatureVerifier.verifyPinnedCert(context, apkPath, Config.PINNED_CERT_SHA256)
            if (!pinnedOk) {
                NotificationHelper.showIdentityFailed(context)
                return@launch
            }

            NotificationHelper.showDownloadCompleteInstall(context, apkPath)
        }
    }
}
