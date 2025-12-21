package com.jayrads.unityupdater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

class VerifyDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)

        // Mark verifying at the start of every run.
        Prefs.setVerifying(applicationContext, true)

        Log.d(TAG, "doWork() start id=$downloadId attempt=$runAttemptCount")

        try {
            if (downloadId <= 0L) {
                Log.e(TAG, "Missing/invalid downloadId: $downloadId")
                NotificationHelper.showIntegrityFailed(applicationContext, "Invalid download id")
                return Result.failure()
            }

            val dm = applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))

            cursor.use { c ->
                if (c == null || !c.moveToFirst()) {
                    Log.w(TAG, "DownloadManager has no record for id=$downloadId; retrying")
                    // Keep verifying=true during retry so UI can show progress.
                    return Result.retry()
                }

                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                Log.d(TAG, "DownloadManager status=$status for id=$downloadId")

                when (status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_RUNNING -> {
                        Log.d(TAG, "Download still in progress; retrying")
                        // Keep verifying=true during retry.
                        return Result.retry()
                    }

                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        Log.e(TAG, "Download FAILED id=$downloadId reason=$reason")
                        NotificationHelper.showIntegrityFailed(applicationContext, "Download failed (reason=$reason)")
                        return Result.failure()
                    }

                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val localUriStr =
                            c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                        Log.d(TAG, "Download SUCCESS id=$downloadId localUri=$localUriStr")

                        if (localUriStr.isNullOrBlank()) {
                            Log.e(TAG, "Success but localUri is blank for id=$downloadId")
                            NotificationHelper.showIntegrityFailed(
                                applicationContext,
                                "Download completed but file path missing"
                            )
                            return Result.failure()
                        }

                        val apkFile = resolveDownloadedFile(localUriStr)
                        if (apkFile == null || !apkFile.exists()) {
                            Log.e(TAG, "Resolved file missing. localUri=$localUriStr")
                            NotificationHelper.showIntegrityFailed(applicationContext, "Downloaded file missing")
                            return Result.failure()
                        }

                        Log.d(
                            TAG,
                            "Resolved file=${apkFile.absolutePath} exists=${apkFile.exists()} size=${apkFile.length()}"
                        )

                        // Persist so UI can show an install button even if notifications are blocked.
                        Prefs.setApkPath(applicationContext, apkFile.absolutePath)

                        // Fetch expected SHA from backend
                        val latest = runCatching { ApiClient.fetchLatest() }.getOrNull()
                        val expectedSha = latest?.sha256?.trim()?.lowercase()
                        if (expectedSha.isNullOrBlank()) {
                            Log.e(TAG, "Expected SHA-256 missing from backend response")
                            NotificationHelper.showIntegrityFailed(applicationContext, "Missing expected SHA-256")
                            return Result.failure()
                        }

                        // Compute actual SHA
                        val actualSha =
                            runCatching { FileHash.sha256Hex(apkFile).lowercase() }.getOrNull()
                        if (actualSha == null) {
                            Log.e(TAG, "Failed computing SHA-256 for ${apkFile.absolutePath}")
                            NotificationHelper.showIntegrityFailed(applicationContext, "Failed computing SHA-256")
                            return Result.failure()
                        }

                        Log.d(TAG, "SHA expected=$expectedSha actual=$actualSha")

                        if (actualSha != expectedSha) {
                            Log.e(TAG, "SHA mismatch")
                            NotificationHelper.showIntegrityFailed(applicationContext, "SHA-256 mismatch")
                            return Result.failure()
                        }

                        // ---------- TOFU publisher identity (signer cert) ----------
                        val signerSha = ApkSignatureVerifier
                            .getSignerCertSha256(applicationContext, apkFile.absolutePath)
                            ?.trim()
                            ?.lowercase()

                        if (signerSha.isNullOrBlank()) {
                            Log.e(TAG, "Could not extract signer cert SHA-256 from APK")
                            NotificationHelper.showIdentityFailed(applicationContext)
                            return Result.failure()
                        }

                        val trusted =
                            Prefs.getTrustedSignerSha256(applicationContext)?.trim()?.lowercase()

                        val changed = !trusted.isNullOrBlank() && trusted != signerSha
                        if (trusted.isNullOrBlank()) {
                            // First time we ever saw a signer: trust-on-first-use (TOFU)
                            Prefs.setTrustedSignerSha256(applicationContext, signerSha)
                            Log.w(TAG, "TOFU: trusted signer set to $signerSha")
                        }

                        Prefs.setPendingInstallSignerInfo(
                            applicationContext,
                            signerSha256 = signerSha,
                            previousSignerSha256 = trusted,
                            changed = changed
                        )

                        Log.d(TAG, "TOFU signer check: trusted=$trusted current=$signerSha changed=$changed")

                        Log.d(TAG, "Verification OK (SHA + signer identity)")

                        // ✅ Slick: Always mark as "verified install ready" so UI can show the Verified chip,
                        // regardless of whether notifications are allowed.
                        Prefs.setPendingInstall(applicationContext, apkFile.absolutePath)

                        // If notifications are allowed, still show the notification.
                        val allowed = Notifications.areAllowed(applicationContext)
                        Log.d(TAG, "notificationsAllowed=$allowed (will notify if true)")
                        if (allowed) {
                            // If lint complains about permission, handle SecurityException inside NotificationHelper
                            NotificationHelper.showDownloadCompleteInstall(applicationContext, apkFile.absolutePath)
                        } else {
                            Log.w(TAG, "Notifications blocked; UI will show Install verified update")
                        }

                        return Result.success()
                    }

                    else -> {
                        Log.w(TAG, "Unknown DownloadManager status=$status; retrying")
                        return Result.retry()
                    }
                }
            }
        } finally {
            // See discussion: we set verifying=true at the start of each attempt.
            // Clearing here means the UI may flicker during backoff, but won't get "stuck".
            Prefs.setVerifying(applicationContext, false)
            Log.d(TAG, "doWork() end (verifying=false)")
        }
    }

    private fun resolveDownloadedFile(localUriStr: String): File? {
        val uri = Uri.parse(localUriStr)
        val path = uri.path ?: return null
        return File(path)
    }

    companion object {
        private const val TAG = "VerifyDownloadWorker"
        private const val KEY_DOWNLOAD_ID = "download_id"

        fun enqueue(ctx: Context, downloadId: Long) {
            val appCtx = ctx.applicationContext

            val data = Data.Builder()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .build()

            val req = OneTimeWorkRequestBuilder<VerifyDownloadWorker>()
                // Exponential backoff so we poll politely while download runs.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(data)
                .addTag("verify_download_$downloadId")
                .build()

            WorkManager.getInstance(appCtx).enqueue(req)
            Log.d(TAG, "Enqueued WorkManager verification for downloadId=$downloadId")
        }
    }
}
