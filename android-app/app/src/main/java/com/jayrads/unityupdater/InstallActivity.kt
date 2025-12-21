package com.jayrads.unityupdater

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File

class InstallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_APK_PATH = "apk_path"
        private const val TAG = "InstallActivity"
    }

    private var apkPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apkPath = intent.getStringExtra(EXTRA_APK_PATH)

        Log.d(TAG, "onCreate()")
        Log.d(TAG, "  intent.action=${intent.action}")
        Log.d(TAG, "  apkPath=$apkPath")

        if (apkPath.isNullOrBlank()) {
            Log.e(TAG, "Missing apkPath extra; finishing")
            finish()
            return
        }

        startInstallOrRequestPermission()
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "onResume()")
        Log.d(TAG, "  apkPath=$apkPath")

        if (!apkPath.isNullOrBlank()) startInstallOrRequestPermission()
    }

    private fun startInstallOrRequestPermission() {
        val path = apkPath ?: return
        val apkFile = File(path)

        Log.d(TAG, "startInstallOrRequestPermission()")
        Log.d(TAG, "  path=$path")
        Log.d(TAG, "  exists=${apkFile.exists()} size=${if (apkFile.exists()) apkFile.length() else 0}")

        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist; finishing")
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = packageManager.canRequestPackageInstalls()
            Log.d(TAG, "  canRequestPackageInstalls=$canInstall")

            if (!canInstall) {
                Log.w(TAG, "Requesting unknown sources permission via Settings")
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                return
            }
        }

        val authority = "$packageName.fileprovider"
        val apkUri = runCatching { FileProvider.getUriForFile(this, authority, apkFile) }.getOrNull()

        Log.d(TAG, "  FileProvider authority=$authority")
        Log.d(TAG, "  apkUri=$apkUri")

        if (apkUri == null) {
            Log.e(TAG, "FileProvider.getUriForFile returned null; finishing")
            finish()
            return
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        Log.d(TAG, "Launching installer intent")
        Log.d(TAG, "  intent.data=$apkUri")
        Log.d(TAG, "  intent.type=${installIntent.type}")

        runCatching { startActivity(installIntent) }
            .onFailure { e -> Log.e(TAG, "Failed to start installer activity", e) }

        finish()
    }
}
