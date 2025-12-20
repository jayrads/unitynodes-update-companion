package com.jayrads.unityupdater

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File

class InstallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_APK_PATH = "apk_path"
    }

    private var apkPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        if (apkPath.isNullOrBlank()) {
            finish()
            return
        }
        startInstallOrRequestPermission()
    }

    override fun onResume() {
        super.onResume()
        if (!apkPath.isNullOrBlank()) startInstallOrRequestPermission()
    }

    private fun startInstallOrRequestPermission() {
        val path = apkPath ?: return
        val apkFile = File(path)
        if (!apkFile.exists()) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(installIntent)
        finish()
    }
}
