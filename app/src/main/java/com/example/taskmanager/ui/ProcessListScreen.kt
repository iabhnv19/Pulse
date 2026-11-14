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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.taskmanager.AccessibilityCommandHolder
import com.example.taskmanager.AppProcess
import com.example.taskmanager.TaskManagerAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

enum class ProcessSortMode(val label: String, val icon: @Composable () -> Unit) {
    MEMORY("RAM",    { Icon(Icons.Outlined.Memory,  null, modifier = Modifier.size(16.dp)) }),
    CPU("CPU",       { Icon(Icons.Outlined.Speed,   null, modifier = Modifier.size(16.dp)) }),
    NAME("Name",     { Icon(Icons.Default.SortByAlpha, null, modifier = Modifier.size(16.dp)) }),
    DISK("I/O",      { Icon(Icons.Outlined.Storage, null, modifier = Modifier.size(16.dp)) }),
}

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
    onRefresh: () -> Unit,
    onEndTask: (String, Int) -> Unit,
    onEndAll: () -> Unit
) {
    var sortMode by remember { mutableStateOf(ProcessSortMode.MEMORY) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var hasUsagePerm by remember { mutableStateOf(hasUsageStatsPermission(context)) }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
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
                            placeholder = { Text("Search processes or apps…") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    isSearchActive = false
                                }) { Icon(Icons.Default.Close, null) }
                            },
                            shape = RoundedCornerShape(24.dp),
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
                                        "${displayedProcesses.size} processes active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                                IconButton(onClick = {
                                    onEndAll()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Ended all background tasks")
                                    }
                                }) {
                                    Icon(Icons.Default.StopCircle, contentDescription = "End All Tasks", tint = MaterialTheme.colorScheme.error)
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

                // Sort Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProcessSortMode.entries.forEach { mode ->
                        val selected = sortMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { sortMode = mode },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = { mode.icon() },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Usage Stats Permission Banner
            AnimatedVisibility(visible = !hasUsagePerm) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Grant Usage Access",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "To detect background apps and services, grant Usage Access in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                                hasUsagePerm = hasUsageStatsPermission(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Grant", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }



            // Column Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Process / App", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f))
                Text("RAM", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(64.dp))
                Text("CPU", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(44.dp))
                Spacer(Modifier.width(80.dp)) // space for End Task button
            }

            if (displayedProcesses.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Scanning active processes…", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = displayedProcesses,
                        key = { p -> p.pid }
                    ) { process ->
                        ProcessItemCard(
                            process = process,
                            onEndTask = {
                                onEndTask(process.packageName, process.pid)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Ended task: ${process.processName.split(".").last()}")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessItemCard(
    process: AppProcess,
    onEndTask: () -> Unit
) {
    val context = LocalContext.current
    var appIcon by remember(process.packageName) { mutableStateOf<Drawable?>(null) }
    val isRecentOnly = process.pid < 0

    LaunchedEffect(process.packageName) {
        appIcon = withContext(Dispatchers.IO) {
            try { context.packageManager.getApplicationIcon(process.packageName) }
            catch (e: PackageManager.NameNotFoundException) { null }
        }
    }

    val hasRamData = process.memoryUsage > 0L
    val cpuPct = (process.cpuUsage * 100).toInt().coerceIn(0, 100)
    val appShortName = process.processName.split(".").last()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val icon = appIcon
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        painter = BitmapPainter(icon.toBitmap(40, 40).asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name + Package
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appShortName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = process.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // RAM
            Column(
                modifier = Modifier.width(64.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (isRecentOnly) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Recent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                } else if (hasRamData) {
                    Text(
                        text = formatBytesCompact(process.memoryUsage),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text("—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
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
                    color = if (cpuPct > 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.width(8.dp))

            // End Task Button (Windows Task Manager style)
            OutlinedButton(
                onClick = onEndTask,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Text("End Task", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatBytesCompact(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.US, "%.0f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
