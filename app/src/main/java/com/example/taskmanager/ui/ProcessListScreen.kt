package com.example.taskmanager.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.taskmanager.AppProcess
import com.example.taskmanager.ui.components.ExpressiveCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

// ────────────────────────────────────────────────────────────
//  Sort modes
// ────────────────────────────────────────────────────────────
enum class ProcessSortMode(val label: String, val icon: @Composable () -> Unit) {
    MEMORY("RAM",    { Icon(Icons.Outlined.Memory,  null, modifier = Modifier.size(16.dp)) }),
    CPU("CPU",       { Icon(Icons.Outlined.Speed,   null, modifier = Modifier.size(16.dp)) }),
    NAME("Name",     { Icon(Icons.Default.SortByAlpha, null, modifier = Modifier.size(16.dp)) }),
    DISK("I/O",      { Icon(Icons.Outlined.Storage, null, modifier = Modifier.size(16.dp)) }),
}

// ────────────────────────────────────────────────────────────
//  Screen
// ────────────────────────────────────────────────────────────

/** Check if the app has been granted PACKAGE_USAGE_STATS permission. */
@Suppress("DEPRECATION")
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(), context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessListScreen(
    processes: List<AppProcess>,
    onRefresh: () -> Unit
) {
    var sortMode by remember { mutableStateOf(ProcessSortMode.MEMORY) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Check usage stats permission (needed to show background apps like WhatsApp)
    var hasUsagePerm by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    // Apply sort + filter
    val displayedProcesses by remember(processes, sortMode, searchQuery) {
        derivedStateOf {
            val filtered = if (searchQuery.isBlank()) processes
            else processes.filter {
                it.processName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
            when (sortMode) {
                ProcessSortMode.MEMORY -> filtered.sortedByDescending { it.memoryUsage }
                ProcessSortMode.CPU    -> filtered.sortedByDescending { it.cpuUsage }
                ProcessSortMode.NAME   -> filtered.sortedBy { it.processName.split(".").last() }
                ProcessSortMode.DISK   -> filtered.sortedByDescending { it.memoryUsage }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── Top bar ──────────────────────────────────────────
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally { it / 4 }).togetherWith(
                    fadeOut(tween(180)) + slideOutHorizontally { -it / 4 }
                )
            },
            label = "top_bar"
        ) { searching ->
            if (searching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search processes…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearchActive = false
                        }) { Icon(Icons.Default.Close, null) }
                    },
                    shape = RoundedCornerShape(32.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Running Tasks", fontWeight = FontWeight.Bold)
                            Text(
                                "${displayedProcesses.size} processes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }

        // ── Sort filter chips ─────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProcessSortMode.entries.forEach { mode ->
                val selected = sortMode == mode
                val chipScale by animateFloatAsState(
                    if (selected) 1f else 0.95f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "chip_scale_${mode.name}"
                )
                FilterChip(
                    selected = selected,
                    onClick = { sortMode = mode },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { mode.icon() },
                    modifier = Modifier.animateContentSize(
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // ── Usage Stats permission banner ─────────────────────
        AnimatedVisibility(visible = !hasUsagePerm) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info, null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Grant Usage Access",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "To see background apps like WhatsApp, grant Usage Access in Settings.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        hasUsagePerm = hasUsageStatsPermission(context)
                    }) {
                        Text("Grant", color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Column header ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Process", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f))
            Text("RAM", style = MaterialTheme.typography.labelSmall,
                color = if (sortMode == ProcessSortMode.MEMORY) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.width(64.dp))
            Text("CPU%", style = MaterialTheme.typography.labelSmall,
                color = if (sortMode == ProcessSortMode.CPU) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.width(48.dp))
        }

        // ── List ──────────────────────────────────────────────
        if (displayedProcesses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Loading processes…", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = displayedProcesses,
                    key = { _, p -> p.pid }
                ) { index, process ->
                    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = visible,
                        enter = fadeIn(tween(200, delayMillis = (index * 20).coerceAtMost(300))) +
                                slideInVertically(
                                    tween(250, delayMillis = (index * 20).coerceAtMost(300),
                                        easing = FastOutSlowInEasing)
                                ) { it / 2 },
                        modifier = Modifier.animateItem(
                            placementSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )
                    ) {
                        ProcessItem(
                            process = process,
                            rank = index + 1,
                            sortMode = sortMode,
                            onEndTask = { openAppSettings(context, process.packageName) }
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Single process row card
// ────────────────────────────────────────────────────────────
@Composable
fun ProcessItem(
    process: AppProcess,
    rank: Int,
    sortMode: ProcessSortMode,
    onEndTask: () -> Unit
) {
    val context = LocalContext.current
    var appIcon by remember(process.packageName) { mutableStateOf<Drawable?>(null) }
    val isRecentOnly = process.pid < 0  // Came from UsageStats, no live PSS

    // Load app icon asynchronously
    LaunchedEffect(process.packageName) {
        appIcon = withContext(Dispatchers.IO) {
            try { context.packageManager.getApplicationIcon(process.packageName) }
            catch (e: PackageManager.NameNotFoundException) { null }
        }
    }

    val hasRamData = process.memoryUsage > 0L
    val memColor = when {
        process.memoryUsage > 200L * 1024 * 1024 -> Color(0xFFEF5350)
        process.memoryUsage > 80L * 1024 * 1024  -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }
    val cpuPct = (process.cpuUsage * 100).toInt().coerceIn(0, 100)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (rank <= 3)
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        else Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.width(10.dp))

            // App icon or fallback
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val icon = appIcon
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        painter = BitmapPainter(icon.toBitmap(36, 36).asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Process name + package
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = process.processName.split(".").last(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = process.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // RAM column
            Column(
                modifier = Modifier.width(68.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (isRecentOnly) {
                    // No live memory data – show "Recent" chip
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "Recent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                } else if (hasRamData) {
                    Text(
                        text = formatBytesCompact(process.memoryUsage),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = memColor,
                        maxLines = 1
                    )
                    LinearProgressIndicator(
                        progress = { (process.memoryUsage.toFloat() / (512L * 1024 * 1024)).coerceIn(0f, 1f) },
                        modifier = Modifier.width(52.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = memColor,
                        trackColor = memColor.copy(alpha = 0.15f)
                    )
                } else {
                    // getProcessMemoryInfo returned 0 (OS restriction) + /proc/status also failed
                    Text(
                        "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // CPU%
            Column(
                modifier = Modifier.width(40.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$cpuPct%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (cpuPct > 20) Color(0xFFEF5350) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(4.dp))

            // Action button
            IconButton(
                onClick = onEndTask,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "App Info", modifier = Modifier.size(14.dp))
            }
        }
    }
}

fun openAppSettings(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun formatBytesCompact(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.US, "%.0f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
