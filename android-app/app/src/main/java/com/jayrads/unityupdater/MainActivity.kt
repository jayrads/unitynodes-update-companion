package com.jayrads.unityupdater

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    ) { /* UI refreshes on resume */ }

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
        // Only touch Firebase APIs if Firebase is initialized (google-services.json present).
        val firebaseReady = FirebaseApp.getApps(ctx).isNotEmpty()
        if (firebaseReady) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                runCatching { ApiClient.registerToken(ctx, token) }
            }
        }
        refreshAll()
    }

    val latestText = when {
        isLoadingLatest -> "Checking…"
        latest == null -> "Unavailable"
        else -> latest!!.versionName
    }

    val updateAvailable =
        latest != null &&
            latest!!.versionName.isNotBlank() &&
            latest!!.versionName != installedVersion &&
            latest!!.apkUrl.isNotBlank()

    // Simple heuristic: if we got latest, backend was reachable.
    val backendConnected = latest != null

    // We always pin a cert; verification happens after download.
    val signaturePinned = true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                androidx.compose.foundation.layout.WindowInsets.systemBars
            )
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeroHeader(
            title = "UnityNodes",
            subtitle = "Update Companion",
            status = when {
                updateAvailable -> "Update available"
                isLoadingLatest -> "Checking…"
                latest == null -> "Offline"
                else -> "Up to date"
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                icon = if (backendConnected) Icons.Filled.CloudQueue else Icons.Filled.CloudOff,
                label = if (backendConnected) "Server" else "Offline",
                state = if (backendConnected) PillState.Good else PillState.Bad
            )
            StatusPill(
                icon = if (notificationsAllowed) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                label = if (notificationsAllowed) "Alerts" else "Muted",
                state = if (notificationsAllowed) PillState.Good else PillState.Warn
            )
            StatusPill(
                icon = Icons.Filled.Security,
                label = "Pinned",
                state = if (signaturePinned) PillState.Good else PillState.Bad
            )
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KeyValue("Installed", installedVersion, big = true)
                KeyValue("Latest", latestText, big = true)

                latest?.let {
                    Text(
                        "Published: ${it.publishedAt}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { refreshAll() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Refresh")
                    }

                    OutlinedButton(
                        onClick = {
                            // For paranoid users: jump right to settings if they want to verify perms, etc.
                            Notifications.openAppDetailsSettings(ctx)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("App settings")
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!notificationsAllowed) {
                    Text(
                        "Notifications are off. Turn them on to get update alerts.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
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
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Enable notifications")
                    }
                } else {
                    Text(
                        if (updateAvailable) "A newer version is ready." else "No updates right now.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    AnimatedVisibility(
                        visible = updateAvailable,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ElevatedButton(
                                onClick = { ApkDownloader.enqueueLatest(ctx, latest!!) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.elevatedButtonColors()
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(Modifier.size(10.dp))
                                Text("Download update", fontWeight = FontWeight.SemiBold)
                            }

                            val downloadedPath = Prefs.getApkPath(ctx)
                            if (!downloadedPath.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        ctx.startActivity(
                                            Intent(ctx, InstallActivity::class.java).apply {
                                                putExtra(InstallActivity.EXTRA_APK_PATH, downloadedPath)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.InstallMobile, contentDescription = null)
                                    Spacer(Modifier.size(10.dp))
                                    Text("Install downloaded APK")
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !updateAvailable,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OutlinedButton(
                            onClick = { refreshAll() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                            Spacer(Modifier.size(10.dp))
                            Text("Check again")
                        }
                    }
                }
            }
        }

        latest?.let { l ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    KeyValue("APK URL", l.apkUrl, big = false)
                    KeyValue("Size", "${l.sizeBytes} bytes", big = false)
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(title: String, subtitle: String, status: String) {
    val scheme = MaterialTheme.colorScheme

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Background graphic
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                // Soft gradient band
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.28f),
                            scheme.tertiary.copy(alpha = 0.18f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )

                // Decorative rings (gives “techy” look)
                val ringColor = scheme.onSurface.copy(alpha = 0.08f)
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.78f, size.height * 0.45f),
                    style = Stroke(width = 8f)
                )
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension * 0.35f,
                    center = Offset(size.width * 0.72f, size.height * 0.55f),
                    style = Stroke(width = 6f)
                )

                // Small “node” dots
                val dotColor = scheme.primary.copy(alpha = 0.25f)
                fun dot(x: Float, y: Float, r: Float) {
                    drawCircle(color = dotColor, radius = r, center = Offset(x, y))
                }
                dot(size.width * 0.10f, size.height * 0.25f, 10f)
                dot(size.width * 0.18f, size.height * 0.62f, 7f)
                dot(size.width * 0.30f, size.height * 0.40f, 6f)
                dot(size.width * 0.92f, size.height * 0.25f, 8f)

                // A subtle “bar” element
                drawRoundRect(
                    color = scheme.onSurface.copy(alpha = 0.05f),
                    topLeft = Offset(size.width * 0.12f, size.height * 0.82f),
                    size = Size(size.width * 0.56f, 10f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f)
                )
            }

            // Foreground text
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(6.dp))
                StatusChip(status)
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun KeyValue(label: String, value: String, big: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodySmall
        )
    }
}

private enum class PillState { Good, Warn, Bad }

@Composable
private fun StatusPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, state: PillState) {
    val scheme = MaterialTheme.colorScheme
    val bg = when (state) {
        PillState.Good -> scheme.primaryContainer
        PillState.Warn -> scheme.tertiaryContainer
        PillState.Bad -> scheme.errorContainer
    }
    val fg = when (state) {
        PillState.Good -> scheme.onPrimaryContainer
        PillState.Warn -> scheme.onTertiaryContainer
        PillState.Bad -> scheme.onErrorContainer
    }

    Surface(
        color = bg,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Text(label, color = fg, style = MaterialTheme.typography.labelSmall)
        }
    }
}
