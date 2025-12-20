package com.jayrads.unityupdater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

object ApkDownloader {
    fun enqueueLatest(ctx: Context, latest: LatestInfo): Long {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val fileName = if (latest.fileName.isNotBlank()) latest.fileName else "UnityNodes-latest.apk"
        val destDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val destFile = File(destDir, fileName)

        Prefs.setApkPath(ctx, destFile.absolutePath)

        val req = DownloadManager.Request(Uri.parse(latest.apkUrl))
            .setTitle("UnityNodes APK")
            .setDescription("Downloading ${latest.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(destFile))
            .setAllowedOverRoaming(false)

        val id = dm.enqueue(req)
        Prefs.setDownloadId(ctx, id)
        return id
    }
}
