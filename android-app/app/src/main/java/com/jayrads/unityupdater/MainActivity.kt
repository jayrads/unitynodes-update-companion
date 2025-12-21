package com.jayrads.unityupdater

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
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

    var installed by remember {
        mutableStateOf(TargetAppVersion.getInstalled(ctx, "io.unitynodes.unityapp"))
    }

    var latest by remember { mutableStateOf<LatestInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(Notifications.areAllowed(ctx)) }

    var isVerifying by remember { mutableStateOf(Prefs.isVerifying(ctx)) }
    var pendingInstall by remember { mutableStateOf(Prefs.getPendingInstall(ctx)) }
    var downloadedPath by remember { mutableStateOf(Prefs.getApkPath(ctx)) }

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

    val installPath = pendingInstall ?: downloadedPath
    val isVerified = !pendingInstall.isNullOrBlank()

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
                    normalizeSemverString(installed.versionName) // drops "(14)" because we don’t show versionCode anymore
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
                    OutlinedButton(onClick = { refresh() }, Modifier.weight(1f)) {
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

                    ElevatedButton(
                        onClick = {
                            ApkDownloader.enqueueLatest(ctx, latest!!)
                            isVerifying = Prefs.isVerifying(ctx)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Download update")
                    }

                    if (isVerifying) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Verifying…")
                        }
                    }

                    if (!installPath.isNullOrBlank()) {
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

