package com.jayrads.unityupdater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File

object ApkDownloader {

    private const val TAG = "ApkDownloader"

    fun enqueueLatest(ctx: Context, latest: LatestInfo): Long {
        val appCtx = ctx.applicationContext
        val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Optional but nice: clear any prior “install ready” UI so users don’t install an old file
        Prefs.clearPendingInstall(appCtx)
        Prefs.clearPendingInstallSignerInfo(appCtx)
        Prefs.setVerifying(appCtx, false)

        val fileName = if (latest.fileName.isNotBlank()) latest.fileName else "UnityNodes-latest.apk"
        val destDir = appCtx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appCtx.filesDir
        val destFile = File(destDir, fileName)

        // --- Logging: inputs + destination ---
        Log.d(TAG, "enqueueLatest() called")
        Log.d(TAG, "  latest.versionName=${latest.versionName}")
        Log.d(TAG, "  latest.fileName=${latest.fileName}")
        Log.d(TAG, "  latest.apkUrl=${latest.apkUrl}")
        Log.d(TAG, "  destDir=${destDir.absolutePath}")
        Log.d(TAG, "  destFile=${destFile.absolutePath}")
        Log.d(TAG, "  destFile.exists(before)=${destFile.exists()} size=${if (destFile.exists()) destFile.length() else 0}")

        // Prevent collisions
        if (destFile.exists()) {
            val deleted = runCatching { destFile.delete() }.getOrDefault(false)
            Log.w(TAG, "  destFile existed; delete attempted deleted=$deleted")
        }

        val req = DownloadManager.Request(Uri.parse(latest.apkUrl))
            .setTitle("UnityNodes APK")
            .setDescription("Downloading ${latest.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(destFile))
            .setAllowedOverRoaming(false)

        val id = dm.enqueue(req)

        Log.d(TAG, "DownloadManager.enqueue() returned downloadId=$id")

        // Save state for receiver/worker/UI
        Prefs.setDownloadId(appCtx, id)
        Prefs.setApkPath(appCtx, destFile.absolutePath)

        Log.d(TAG, "Prefs updated")
        Log.d(TAG, "  Prefs.downloadId=${Prefs.getDownloadId(appCtx)}")
        Log.d(TAG, "  Prefs.apkPath=${Prefs.getApkPath(appCtx)}")

        // Kick off verification polling (single source of truth)
        Log.d(TAG, "Enqueuing VerifyDownloadWorker for downloadId=$id")
        VerifyDownloadWorker.enqueue(appCtx, id)

        return id
    }
}
