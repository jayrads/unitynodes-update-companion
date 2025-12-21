package com.jayrads.unityupdater

import android.content.Context
import android.os.Build

object AppVersion {

    data class Installed(
        val versionName: String,
        val versionCode: Long
    ) {
        fun display(): String = if (versionCode > 0) "$versionName ($versionCode)" else versionName
    }

    fun getInstalled(ctx: Context): Installed {
        return try {
            val pm = ctx.packageManager
            val pi = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ctx.packageName, 0)
            }

            val vName = pi.versionName ?: "Unknown"
            val vCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else {
                @Suppress("DEPRECATION")
                pi.versionCode.toLong()
            }

            Installed(vName, vCode)
        } catch (_: Throwable) {
            Installed("Unknown", -1L)
        }
    }

    fun getInstalledVersionName(ctx: Context): String = getInstalled(ctx).versionName
    fun getInstalledVersionCode(ctx: Context): Long = getInstalled(ctx).versionCode
}
