package com.jayrads.unityupdater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
//import com.google.ar.core.InstallActivity

object NotificationHelper {
    private const val CHANNEL_ID = "unitynodes_updates"
    private const val CHANNEL_NAME = "UnityNodes Updates"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun showUpdateAvailable(ctx: Context, versionName: String) {
        ensureChannel(ctx)
        val openIntent = Intent(ctx, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            ctx, 100, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("UnityNodes update available")
            .setContentText("New APK: $versionName")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(ctx).notify(1001, n)
    }

    fun showDownloadCompleteInstall(ctx: Context, apkPath: String) {
        ensureChannel(ctx)
        val installIntent = Intent(ctx, InstallActivity::class.java).apply {
            putExtra(InstallActivity.EXTRA_APK_PATH, apkPath)
        }
        val pi = PendingIntent.getActivity(
            ctx, 200, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("UnityNodes APK downloaded")
            .setContentText("Tap to install")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(ctx).notify(1002, n)
    }

    fun showIntegrityFailed(ctx: Context, reason: String) {
        ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("APK integrity check failed")
            .setContentText(reason)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(ctx).notify(1003, n)
    }

    fun showIdentityFailed(ctx: Context) {
        ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("APK identity check failed")
            .setContentText("APK is not signed by expected certificate")
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(ctx).notify(1004, n)
    }
}
