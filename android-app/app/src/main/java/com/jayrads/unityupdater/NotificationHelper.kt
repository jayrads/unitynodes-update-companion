package com.jayrads.unityupdater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    private const val CHANNEL_ID = "unitynodes_updates"
    private const val CHANNEL_NAME = "UnityNodes Updates"
    private const val TAG = "NotificationHelper"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun canPostNotifications(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun safeNotify(ctx: Context, id: Int, n: android.app.Notification) {
        if (!canPostNotifications(ctx)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping notify(id=$id)")
            return
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException notifying id=$id (permission revoked?)", se)
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected error notifying id=$id", t)
        }
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

        safeNotify(ctx, 1001, n)
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

        safeNotify(ctx, 1002, n)
    }

    fun showIntegrityFailed(ctx: Context, reason: String) {
        ensureChannel(ctx)

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("APK integrity check failed")
            .setContentText(reason)
            .setAutoCancel(true)
            .build()

        safeNotify(ctx, 1003, n)
    }

    fun showIdentityFailed(ctx: Context) {
        ensureChannel(ctx)

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("APK identity check failed")
            .setContentText("APK signer identity could not be verified")
            .setAutoCancel(true)
            .build()

        safeNotify(ctx, 1004, n)
    }
}
