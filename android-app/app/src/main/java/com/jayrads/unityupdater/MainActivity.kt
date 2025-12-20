package com.jayrads.unityupdater

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* refresh on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StatusScreen(
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusScreen(onRequestNotificationPermission: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity

    var installedVersion by remember { mutableStateOf(AppVersion.getInstalledVersionName(ctx)) }
    var latest by remember { mutableStateOf<LatestInfo?>(null) }
    var notificationsAllowed by remember { mutableStateOf(Notifications.areAllowed(ctx)) }
    var isLoadingLatest by remember { mutableStateOf(false) }

    fun refreshAll() {
        installedVersion = AppVersion.getInstalledVersionName(ctx)
        notificationsAllowed = Notifications.areAllowed(ctx)

        isLoadingLatest = true
        latest = null

        activity.lifecycleScope.launch {
            val fetched = withContext(Dispatchers.IO) {
                runCatching { ApiClient.fetchLatest() }.getOrNull()
            }
            latest = fetched
            isLoadingLatest = false
        }
    }

    LaunchedEffect(Unit) {
        val firebaseReady = FirebaseApp.getApps(ctx).isNotEmpty()
        if (firebaseReady) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                runCatching { ApiClient.registerToken(ctx, token) }
            }
        }
        refreshAll()
    }

    val latestText = when {
        isLoadingLatest -> "Loading…"
        latest == null -> "Unavailable"
        else -> latest!!.versionName
    }

    val updateAvailable =
        latest != null &&
            latest!!.versionName.isNotBlank() &&
            latest!!.versionName != installedVersion &&
            latest!!.apkUrl.isNotBlank()

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("UnityNodes Update Companion", style = MaterialTheme.typography.headlineSmall)

        InfoRow("Installed version", installedVersion)
        InfoRow("Latest version", latestText)
        InfoRow("Notifications", if (notificationsAllowed) "Allowed" else "Blocked")

        if (!notificationsAllowed) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        val granted =
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            onRequestNotificationPermission()
                            return@Button
                        }
                    }
                    Notifications.openAppNotificationSettings(ctx)
                }) { Text("Enable notifications") }

                OutlinedButton(onClick = { Notifications.openAppDetailsSettings(ctx) }) {
                    Text("App settings")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { refreshAll() }) { Text("Refresh") }
        }

        if (updateAvailable) {
            Button(onClick = { ApkDownloader.enqueueLatest(ctx, latest!!) }) {
                Text("Download latest APK")
            }

            val downloadedPath = Prefs.getApkPath(ctx)
            if (!downloadedPath.isNullOrBlank()) {
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        android.content.Intent(ctx, InstallActivity::class.java).apply {
                            putExtra(InstallActivity.EXTRA_APK_PATH, downloadedPath)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }) { Text("Install downloaded APK") }
            }
        }

        latest?.let { l ->
            Text(
                "APK: ${l.apkUrl}\nPublished: ${l.publishedAt}\nSize: ${l.sizeBytes} bytes",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
