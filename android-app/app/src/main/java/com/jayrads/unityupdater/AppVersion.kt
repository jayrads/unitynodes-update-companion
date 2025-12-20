package com.jayrads.unityupdater

import android.content.Context
import android.content.pm.PackageManager

object AppVersion {
    fun getInstalledVersionName(context: Context): String {
        return try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            pi.versionName ?: "unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }
}
