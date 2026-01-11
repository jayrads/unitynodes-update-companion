package com.jayrads.unityupdater

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestNotifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
    val downloadManager = remember { ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }

    var installed by remember {
        mutableStateOf(TargetAppVersion.getInstalled(ctx, "io.unitynodes.unityapp"))
    }

    var latest by remember { mutableStateOf<LatestInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(Notifications.areAllowed(ctx)) }

    var isVerifying by remember { mutableStateOf(Prefs.isVerifying(ctx)) }
    var pendingInstall by remember { mutableStateOf(Prefs.getPendingInstall(ctx)) }
    var downloadedPath by remember { mutableStateOf(Prefs.getApkPath(ctx)) }

    // --- NEW STATES FOR DOWNLOADING ---
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var currentDownloadId by remember { mutableStateOf(-1L) }

    var detailsExpanded by remember { mutableStateOf(false) }

    fun refresh() {
        installed = TargetAppVersion.getInstalled(ctx, "io.unitynodes.unityapp")
        notificationsAllowed = Notifications.areAllowed(ctx)
        isVerifying = Prefs.isVerifying(ctx)
        pendingInstall = Prefs.getPendingInstall(ctx)
        downloadedPath = Prefs.getApkPath(ctx)

        isLoading = true
        latest = null

        activity.lifecycleScope.launch {
            latest = withContext(Dispatchers.IO) {
                runCatching { ApiClient.fetchLatest() }.getOrNull()
            }
            isLoading = false
        }
    }

    // --- NEW POLLING LOGIC ---
    // Watches the download ID and updates progress / triggers refresh on completion
    LaunchedEffect(currentDownloadId) {
        if (currentDownloadId != -1L) {
            isDownloading = true
            while (isActive) {
                val q = DownloadManager.Query().setFilterById(currentDownloadId)
                val cursor: Cursor? = downloadManager.query(q)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                    if (bytesIndex != -1 && totalIndex != -1) {
                        val downloaded = cursor.getLong(bytesIndex)
                        val total = cursor.getLong(totalIndex)
                        if (total > 0) {
                            downloadProgress = downloaded.toFloat() / total.toFloat()
                        }
                    }

                    if (statusIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            isDownloading = false
                            currentDownloadId = -1L
                            refresh() // Reloads to show the Install button
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            isDownloading = false
                            currentDownloadId = -1L
                        }
                    }
                } else {
                    // Cursor empty usually means download cancelled or lost
                    isDownloading = false
                    currentDownloadId = -1L
                }
                cursor?.close()
                delay(500) // Poll every 500ms
            }
        }
    }

    LaunchedEffect(Unit) {
        if (FirebaseApp.getApps(ctx).isNotEmpty()) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener {
                runCatching { ApiClient.registerToken(ctx, it) }
            }
        }
        refresh()
    }

    val updateAvailable =
        latest != null &&
            latest!!.apkUrl.isNotBlank() &&
            isLatestNewer(installed, latest!!)

    // Logic: Show install path, but ONLY if we aren't currently downloading a new one
    val installPath = pendingInstall ?: downloadedPath
    val isVerified = !pendingInstall.isNullOrBlank()
    val showInstallButton = !installPath.isNullOrBlank() && !isDownloading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        HeroHeader(
            title = "UnityNodes",
            subtitle = "Update Companion",
            status = when {
                isDownloading -> "Downloading..."
                updateAvailable -> "Update available"
                isLoading -> "Checking…"
                latest == null -> "Offline"
                else -> "Up to date"
            }
        )

        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val installedPretty = if (!installed.isInstalled) {
                    "Not installed"
                } else {
                    normalizeSemverString(installed.versionName)
                }

                val latestPretty = when {
                    isLoading -> "Checking…"
                    latest == null -> "Unavailable"
                    else -> {
                        val v = normalizeSemverString(latest!!.versionName)
                        val date = extractYyyyMmDd(latest!!.versionName) ?: extractYyyyMmDd(latest!!.publishedAt)
                        if (!date.isNullOrBlank()) "$v ($date)" else v
                    }
                }

                KeyValue("Installed (UnityNodes)", installedPretty, big = true)
                KeyValue("Latest", latestPretty, big = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Disable refresh while downloading to prevent state conflicts
                    OutlinedButton(
                        onClick = { refresh() },
                        modifier = Modifier.weight(1f),
                        enabled = !isDownloading
                    ) {
                        Icon(Icons.Filled.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
                    OutlinedButton(
                        onClick = { Notifications.openAppDetailsSettings(ctx) },
                        Modifier.weight(1f)
                    ) {
                        Text("App settings")
                    }
                }
            }
        }

        if (updateAvailable) {
            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    // --- DOWNLOAD BUTTON ---
                    ElevatedButton(
                        onClick = {
                            ApkDownloader.enqueueLatest(ctx, latest!!)
                            isVerifying = Prefs.isVerifying(ctx)

                            // Attempt to grab the latest download ID to start tracking
                            // (Since ApkDownloader handles the enqueue, we query for the most recent one)
                            val q = DownloadManager.Query().setFilterByStatus(
                                DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING
                            )
                            // We wait a tiny bit to ensure DownloadManager has registered it
                            activity.lifecycleScope.launch {
                                delay(200)
                                val cursor = downloadManager.query(q)
                                if (cursor.moveToFirst()) {
                                    // Assuming the last one created is ours (safe bet for this app)
                                    val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                                    if(idIndex != -1) {
                                        currentDownloadId = cursor.getLong(idIndex)
                                    }
                                }
                                cursor.close()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDownloading // Disable while downloading
                    ) {
                        Icon(Icons.Filled.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if(isDownloading) "Downloading..." else "Download update")
                    }

                    // --- PROGRESS BAR ---
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    if (isVerifying) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Verifying…")
                        }
                    }

                    // --- INSTALL BUTTON ---
                    // Hidden if downloading or if path is invalid
                    if (showInstallButton) {
                        OutlinedButton(
                            onClick = {
                                if (isVerified) Prefs.clearPendingInstall(ctx)
                                ctx.startActivity(
                                    Intent(ctx, InstallActivity::class.java)
                                        .putExtra(InstallActivity.EXTRA_APK_PATH, installPath)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.InstallMobile, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Install verified update")
                        }
                    }
                }
            }
        }

        // ---------- Details ----------
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { detailsExpanded = !detailsExpanded },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Details", fontWeight = FontWeight.SemiBold)
            Icon(
                if (detailsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null
            )
        }

        AnimatedVisibility(detailsExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyValue("Installed versionName", installed.versionName, false)
                KeyValue("Installed versionCode", installed.versionCode.toString(), false)
                KeyValue("Latest versionName", latest?.versionName ?: "(unknown)", false)
            }
        }
    }
}

/* ---------------- Version comparison ---------------- */

private fun isLatestNewer(
    installed: TargetAppVersion.Installed,
    latest: LatestInfo
): Boolean {
    if (!installed.isInstalled) return true

    val installedSem = normalizeSemver(installed.versionName)
    val latestSem = normalizeSemver(latest.versionName)

    if (installedSem.isNotEmpty() && latestSem.isNotEmpty()) {
        for (i in 0..2) {
            if (latestSem[i] != installedSem[i]) {
                return latestSem[i] > installedSem[i]
            }
        }
        return false // same X.Y.Z → NOT newer
    }

    return latest.versionName != installed.versionName
}

private fun normalizeSemver(v: String): List<Int> {
    val m = Regex("""(\d+)\.(\d+)\.(\d+)""").find(v) ?: return emptyList()
    return listOf(
        m.groupValues[1].toInt(),
        m.groupValues[2].toInt(),
        m.groupValues[3].toInt()
    )
}

/* ---------------- UI helpers ---------------- */

@Composable
private fun HeroHeader(title: String, subtitle: String, status: String) {
    val scheme = MaterialTheme.colorScheme

    ElevatedCard {
        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.28f),
                            scheme.tertiary.copy(alpha = 0.18f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )

                drawCircle(
                    color = scheme.onSurface.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.45f,
                    center = Offset(size.width * 0.8f, size.height * 0.5f),
                    style = Stroke(width = 8f)
                )
            }


            Column(Modifier.padding(16.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle)
                Spacer(Modifier.height(6.dp))
                Surface(shape = CircleShape) {
                    Text(status, Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String, big: Boolean) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodySmall
        )
    }
}
private fun extractYyyyMmDd(s: String?): String? {
    if (s.isNullOrBlank()) return null
    return Regex("""\d{4}-\d{2}-\d{2}""").find(s)?.value
}

private fun normalizeSemverString(v: String?): String {
    if (v.isNullOrBlank()) return "Unknown"
    val m = Regex("""(\d+)\.(\d+)\.(\d+)""").find(v) ?: return v
    return "${m.groupValues[1]}.${m.groupValues[2]}.${m.groupValues[3]}"
}
