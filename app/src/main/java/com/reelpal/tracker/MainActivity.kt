package com.reelpal.tracker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reelpal.tracker.data.ReelRepository
import com.reelpal.tracker.service.ReelAccessibilityService
import com.reelpal.tracker.util.MonitoredApps
import com.reelpal.tracker.util.PermissionUtils
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private lateinit var repository: ReelRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ReelRepository.getInstance(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReelPalApp(repository)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReelPalApp(repository)
                }
            }
        }
    }
}

@Composable
fun ReelPalApp(repository: ReelRepository) {
    var refreshTick by remember { mutableStateOf(0) }
    val context = LocalContext.current

    val accessibilityGranted = remember(refreshTick) {
        PermissionUtils.isAccessibilityServiceEnabled(context, ReelAccessibilityService::class.java)
    }
    val overlayGranted = remember(refreshTick) { PermissionUtils.canDrawOverlays(context) }
    val usageGranted = remember(refreshTick) { PermissionUtils.hasUsageAccess(context) }
    val allGranted = accessibilityGranted && overlayGranted && usageGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("ReelPal", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "See how many reels you're really scrolling.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        if (!allGranted) {
            Text("Setup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            PermissionRow(
                title = "Accessibility access",
                subtitle = "Needed to detect scrolling inside Instagram, YouTube, Snapchat, TikTok",
                granted = accessibilityGranted,
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
            PermissionRow(
                title = "Display over other apps",
                subtitle = "Needed for the floating live counter",
                granted = overlayGranted,
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            )
            PermissionRow(
                title = "Usage access",
                subtitle = "Needed to know which app is in the foreground",
                granted = usageGranted,
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    (context as ComponentActivity).requestPermissions(
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001
                    )
                }) { Text("Allow notifications") }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = { refreshTick++ }) { Text("I've granted these - refresh") }

        } else {
            DashboardSection(repository)
        }
    }
}

@Composable
fun PermissionRow(title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (granted) {
                Text("Granted")
            } else {
                Button(onClick = onClick) { Text("Grant") }
            }
        }
    }
}

@Composable
fun DashboardSection(repository: ReelRepository) {
    val todayCounts = remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        repository.observeToday().collectLatest { rows ->
            todayCounts.value = rows.associate { it.packageName to it.count }
        }
    }

    Text("Today", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    LazyColumn(modifier = Modifier.weight(1f)) {
        items(MonitoredApps.ALL) { app ->
            val count = todayCounts.value[app.packageName] ?: 0
            var monitored by remember { mutableStateOf(repository.isAppMonitored(app.packageName)) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(app.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("$count reels scrolled today", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = monitored,
                        onCheckedChange = {
                            monitored = it
                            repository.setAppMonitored(app.packageName, it)
                        }
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    var overlayEnabled by remember { mutableStateOf(repository.isOverlayEnabled()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Show floating counter", modifier = Modifier.weight(1f))
        Switch(
            checked = overlayEnabled,
            onCheckedChange = {
                overlayEnabled = it
                repository.setOverlayEnabled(it)
            }
        )
    }
}
