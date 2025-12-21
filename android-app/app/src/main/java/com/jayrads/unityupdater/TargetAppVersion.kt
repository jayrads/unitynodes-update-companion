package com.jayrads.unityupdater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object TargetAppVersion {

    private const val TAG = "TargetAppVersion"

    data class Installed(
        val isInstalled: Boolean,
        val versionName: String,
        val versionCode: Long
    ) {
        fun display(): String =
            if (!isInstalled) {
                "Not installed"
            } else if (versionCode > 0) {
                "$versionName ($versionCode)"
            } else {
                versionName
            }
    }

    fun getInstalled(ctx: Context, packageName: String): Installed {
        val pm = ctx.packageManager

        return try {
            Log.d(TAG, "Checking installed package: $packageName")

            val pi: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }

            val versionName = pi.versionName ?: "(no versionName)"
            val versionCode =
                if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode
                else {
                    @Suppress("DEPRECATION")
                    pi.versionCode.toLong()
                }

            Log.d(
                TAG,
                "Package FOUND: $packageName versionName=$versionName versionCode=$versionCode"
            )

            Installed(
                isInstalled = true,
                versionName = versionName,
                versionCode = versionCode
            )

        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package NOT installed: $packageName")
            Installed(
                isInstalled = false,
                versionName = "Not installed",
                versionCode = -1L
            )

        } catch (t: Throwable) {
            // Never swallow unexpected errors silently
            Log.e(TAG, "Error checking installed package: $packageName", t)
            Installed(
                isInstalled = false,
                versionName = "Error",
                versionCode = -1L
            )
        }
    }
}
