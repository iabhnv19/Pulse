package com.example.taskmanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanager.AppProcess
import com.example.taskmanager.DeviceSpecs
import com.example.taskmanager.SystemStats
import com.example.taskmanager.benchmark.StressTestResult
import com.example.taskmanager.ui.components.ExpressiveCard
import com.example.taskmanager.ui.components.RealTimeGraph
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    stats: SystemStats?,
    specs: DeviceSpecs?,
    cpuHistory: List<Float>,
    ramHistory: List<Float>,
    gpu3DHistory: List<Float>,
    gpuCopyHistory: List<Float>,
    gpuComputeHistory: List<Float>,
    gpuMemHistory: List<Float>,
    diskHistory: List<Float>,
    netSendHistory: List<Float>,
    netReceiveHistory: List<Float>,
    tempHistory: List<Float> = emptyList(),
    isTestingCpu: Boolean,
    cpuTestResult: String,
    cpuBenchProgress: Float,
    cpuBenchPhase: String,
    onRunCpuBenchmark: () -> Unit,
    isTestingGpu: Boolean,
    gpuTestResult: String,
    gpuBenchProgress: Float,
    gpuBenchPhase: String,
    onRunGpuBenchmark: () -> Unit,
    isTestingDisk: Boolean,
    diskTestResult: String,
    diskBenchProgress: Float,
    diskBenchPhase: String,
    onRunDiskBenchmark: () -> Unit,
    topProcesses: List<AppProcess>,
    isStressTesting: Boolean = false,
    stressTestProgress: Float = 0f,
    stressTestPhase: String = "",
    stressTestTimeRemaining: String = "02:00",
    stressTestResult: StressTestResult? = null,
    stressTestHistory: List<Float> = emptyList(),
    onRunFullDeviceStressTest: () -> Unit = {},
) {
    var expandedCard by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Performance", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 150.dp)
        ) {
            item {
                StressTestCard(
                    isTesting = isStressTesting,
                    progress = stressTestProgress,
                    phase = stressTestPhase,
                    timeRemaining = stressTestTimeRemaining,
                    result = stressTestResult,
                    history = stressTestHistory,
                    onRunStressTest = onRunFullDeviceStressTest
                )
            }
            item {
                ProcessorCard(
                    processorName = specs?.processorName ?: "Processor",
                    cpuUsage = stats?.cpuUsage ?: 0f,
                    cpuSpeed = stats?.cpuSpeed ?: 0f,
                    cpuHistory = cpuHistory,
                    isTestingCpu = isTestingCpu,
                    cpuTestResult = cpuTestResult,
                    cpuBenchProgress = cpuBenchProgress,
                    cpuBenchPhase = cpuBenchPhase,
                    onRunCpuBenchmark = onRunCpuBenchmark,
                    isExpanded = expandedCard == "Processor",
                    onClick = {
                        expandedCard = if (expandedCard == "Processor") null else "Processor"
                    },
                    stats = stats,
                    specs = specs
                )
            }
            item {
                StatCard(
                    title = "Memory",
                    value = formatBytes(stats?.ramUsed ?: 0L),
                    capacity = formatBytes(stats?.ramTotal ?: 0L),
                    usageLabel = "Memory usage",
                    history = ramHistory,
                    color = Color(0xFF8E24AA),
                    isExpanded = expandedCard == "Memory",
                    onClick = {
                        expandedCard = if (expandedCard == "Memory") null else "Memory"
                    },
                    expandedContent = {
                        MemoryDetails(stats, specs)
                        Spacer(Modifier.height(16.dp))
                        Text("Top Processes", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8E24AA))
                        Spacer(Modifier.height(8.dp))
                        ProcessListSmall(topProcesses)
                    }
                )
            }
            
            item {
                GPUCard(
                    gpuName = specs?.gpuName ?: "Integrated Graphics",
                    gpuUsage3D = stats?.gpuUsage3D ?: 0f,
                    gpuUsageCopy = stats?.gpuUsageCopy ?: 0f,
                    gpuUsageCompute = stats?.gpuUsageCompute ?: 0f,
                    gpu3DHistory = gpu3DHistory,
                    gpuCopyHistory = gpuCopyHistory,
                    gpuComputeHistory = gpuComputeHistory,
                    gpuMemHistory = gpuMemHistory,
                    isTestingGpu = isTestingGpu,
                    gpuTestResult = gpuTestResult,
                    gpuBenchProgress = gpuBenchProgress,
                    gpuBenchPhase = gpuBenchPhase,
                    onRunGpuBenchmark = onRunGpuBenchmark,
                    isExpanded = expandedCard == "GPU",
                    onClick = {
                        expandedCard = if (expandedCard == "GPU") null else "GPU"
                    },
                    stats = stats,
                    specs = specs
                )
            }

            item {
                DiskCard(
                    stats = stats,
                    diskHistory = diskHistory,
                    isTestingDisk = isTestingDisk,
                    diskTestResult = diskTestResult,
                    diskBenchProgress = diskBenchProgress,
                    diskBenchPhase = diskBenchPhase,
                    onRunDiskBenchmark = onRunDiskBenchmark
                )
            }

            item {
                NetworkCard(
                    adapterName = specs?.networkAdapterName ?: "Android Wi-Fi",
                    stats = stats,
                    netSendHistory = netSendHistory,
                    netReceiveHistory = netReceiveHistory
                )
            }

            item {
                BatteryThermalCard(
                    stats = stats,
                    tempHistory = tempHistory
                )
            }
        }
    }
}

@Composable
fun ProcessorCard(
    processorName: String,
    cpuUsage: Float,
    cpuSpeed: Float,
    cpuHistory: List<Float>,
    isTestingCpu: Boolean,
    cpuTestResult: String,
    cpuBenchProgress: Float,
    cpuBenchPhase: String,
    onRunCpuBenchmark: () -> Unit,
    isExpanded: Boolean,
    onClick: () -> Unit,
    stats: SystemStats?,
    specs: DeviceSpecs?
) {
    val cpuColor = MaterialTheme.colorScheme.primary
    val animatedProgress by animateFloatAsState(
        targetValue = cpuBenchProgress,
        animationSpec = tween(300),
        label = "cpu_bench_progress"
    )

    ExpressiveCard(modifier = Modifier.clickable { onClick() }.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text("Processor", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    processorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onRunCpuBenchmark,
                enabled = !isTestingCpu,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.height(32.dp)
            ) {
                if (isTestingCpu) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Running…", style = MaterialTheme.typography.labelSmall)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Benchmark", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Processor usage", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("${(cpuUsage * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            RealTimeGraph(data = cpuHistory, color = cpuColor)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("60 seconds", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("0", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }

        // ── Benchmark progress UI ────────────────────────────────────────────
        AnimatedVisibility(visible = isTestingCpu) {
            Column {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = cpuColor.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                cpuBenchPhase.ifBlank { "Preparing…" },
                                style = MaterialTheme.typography.labelSmall,
                                color = cpuColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = cpuColor
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                            color = cpuColor,
                            trackColor = cpuColor.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }

        // ── Benchmark Results ────────────────────────────────────────────────
        AnimatedVisibility(visible = cpuTestResult.isNotEmpty() && !isTestingCpu) {
            Column {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = cpuColor.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = cpuColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "CPU Benchmark Result",
                                style = MaterialTheme.typography.labelMedium,
                                color = cpuColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            cpuTestResult,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = cpuColor.copy(alpha = 0.2f))
                Text("Details", style = MaterialTheme.typography.labelMedium, color = cpuColor)
                Spacer(modifier = Modifier.height(8.dp))
                ProcessorDetails(stats, specs)
            }
        }
    }
}

@Composable
fun GPUCard(
    gpuName: String,
    gpuUsage3D: Float,
    gpuUsageCopy: Float,
    gpuUsageCompute: Float,
    gpu3DHistory: List<Float>,
    gpuCopyHistory: List<Float>,
    gpuComputeHistory: List<Float>,
    gpuMemHistory: List<Float>,
    isTestingGpu: Boolean,
    gpuTestResult: String,
    gpuBenchProgress: Float,
    gpuBenchPhase: String,
    onRunGpuBenchmark: () -> Unit,
    isExpanded: Boolean,
    onClick: () -> Unit,
    stats: SystemStats?,
    specs: DeviceSpecs?
) {
    val gpuColor = Color(0xFF00838F)
    val animatedProgress by animateFloatAsState(
        targetValue = gpuBenchProgress,
        animationSpec = tween(300),
        label = "gpu_bench_progress"
    )

    ExpressiveCard(modifier = Modifier.clickable { onClick() }.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text("GPU", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    gpuName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onRunGpuBenchmark,
                enabled = !isTestingGpu,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.height(32.dp)
            ) {
                if (isTestingGpu) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Running…", style = MaterialTheme.typography.labelSmall)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Benchmark", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                EngineGraph("3D Engine", gpuUsage3D, gpu3DHistory, gpuColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                EngineGraph("Compute / Copy", gpuUsageCompute, gpuComputeHistory, gpuColor)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        MemoryGraph("Dedicated GPU memory usage", formatBytes(stats?.gpuDedicatedUsed ?: 0L), gpuMemHistory, gpuColor)
        Spacer(Modifier.height(8.dp))
        MemoryGraph("Shared GPU memory usage", formatBytes(stats?.gpuSharedUsed ?: 0L), gpuMemHistory, gpuColor)

        // ── Benchmark progress UI ────────────────────────────────────────────
        AnimatedVisibility(visible = isTestingGpu) {
            Column {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = gpuColor.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                gpuBenchPhase.ifBlank { "Preparing…" },
                                style = MaterialTheme.typography.labelSmall,
                                color = gpuColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = gpuColor
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                            color = gpuColor,
                            trackColor = gpuColor.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }

        // ── Benchmark Results ────────────────────────────────────────────────
        AnimatedVisibility(visible = gpuTestResult.isNotEmpty() && !isTestingGpu) {
            Column {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = gpuColor.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = gpuColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "4K 120Hz Extreme GPU Benchmark",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = gpuColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = gpuColor.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    "4K • 120Hz",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = gpuColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            gpuTestResult,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = gpuColor.copy(alpha = 0.2f))
                Text("Details", style = MaterialTheme.typography.labelMedium, color = gpuColor)
                Spacer(modifier = Modifier.height(8.dp))
                GPUDetails(stats, specs)
            }
        }
    }
}

@Composable
fun GPUDetails(stats: SystemStats?, specs: DeviceSpecs?) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            DetailColumn(Modifier.weight(1f), listOf(
                "Utilization" to "${((stats?.gpuUsage3D ?: 0f) * 100).toInt()}%",
                "GPU Memory" to "${formatBytes(stats?.gpuMemoryUsed ?: 0L)}/${formatBytes(stats?.gpuMemoryTotal ?: 0L)}"
            ))
            DetailColumn(Modifier.weight(1f), listOf(
                "Dedicated GPU memory" to "${formatBytes(stats?.gpuDedicatedUsed ?: 0L)}/${formatBytes(stats?.gpuDedicatedTotal ?: 0L)}",
                "Shared GPU memory" to "${formatBytes(stats?.gpuSharedUsed ?: 0L)}/${formatBytes(stats?.gpuSharedTotal ?: 0L)}"
            ))
            DetailColumn(Modifier.weight(1f), listOf(
                "Driver version:" to (specs?.gpuDriverVersion ?: "-"),
                "Driver date:" to (specs?.gpuDriverDate ?: "-"),
                "Vulkan version:" to (specs?.gpuVulkanVersion ?: "-"),
                "Physical location:" to (specs?.gpuPhysicalLocation ?: "-"),
                "Hardware reserved memory:" to (specs?.gpuHardwareReserved ?: "-")
            ))
        }
    }
}

@Composable
fun MemoryDetails(stats: SystemStats?, specs: DeviceSpecs?) {
    val ramColor = Color(0xFF8E24AA)
    val ramUsed = stats?.ramUsed ?: 0L
    val ramTotal = stats?.ramTotal ?: 0L
    val ramCompressed = stats?.ramCompressed ?: 0L
    val ramCommitted = stats?.ramCommitted ?: 0L
    val ramLimit = stats?.ramLimit ?: 0L
    val ramCached = stats?.ramCached ?: 0L
    val ramPagedPool = stats?.ramPagedPool ?: 0L
    val ramNonPagedPool = stats?.ramNonPagedPool ?: 0L

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Memory composition", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(ramColor.copy(alpha = 0.1f))
        ) {
            val usedRatio = if (ramTotal > 0) ramUsed.toFloat() / ramTotal else 0f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(usedRatio)
                    .background(ramColor.copy(alpha = 0.5f))
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Row(Modifier.fillMaxWidth()) {
            DetailColumn(Modifier.weight(1.2f), listOf(
                "In use (Compressed)" to "${formatBytes(ramUsed)} (${formatBytes(ramCompressed)})",
                "Committed" to "${formatBytes(ramCommitted)}/${formatBytes(ramLimit)}",
                "Paged pool" to formatBytes(ramPagedPool)
            ))
            DetailColumn(Modifier.weight(0.8f), listOf(
                "Available" to formatBytes(ramTotal - ramUsed),
                "Cached" to formatBytes(ramCached),
                "Non-paged pool" to formatBytes(ramNonPagedPool)
            ))
            DetailColumn(Modifier.weight(1f), listOf(
                "Speed:" to (specs?.ramSpeed ?: "-"),
                "Slots used:" to (specs?.ramSlots ?: "-"),
                "Form factor:" to (specs?.ramFormFactor ?: "-"),
                "Hardware reserved:" to (specs?.ramHardwareReserved ?: "-")
            ))
        }
    }
}

@Composable
fun NetworkCard(
    adapterName: String,
    stats: SystemStats?,
    netSendHistory: List<Float>,
    netReceiveHistory: List<Float>
) {
    val netColor = Color(0xFFE67E22)
    ExpressiveCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Wi-Fi", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(adapterName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        
        Text("Throughput", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val maxData = (netSendHistory + netReceiveHistory).maxOrNull()?.takeIf { it > 0 } ?: 1024f
            RealTimeGraph(data = netReceiveHistory, color = netColor, modifier = Modifier.fillMaxSize(), fixedMaxData = maxData)
            RealTimeGraph(data = netSendHistory, color = netColor.copy(alpha = 0.7f), modifier = Modifier.fillMaxSize(), isDashed = true, fixedMaxData = maxData)
        }
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("60 seconds", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("0", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        
        Spacer(Modifier.height(16.dp))
        
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(0.8f)) {
                NetworkDetailItem("Send", formatBits((stats?.networkSendSpeed ?: 0L) * 8))
                Spacer(Modifier.height(12.dp))
                NetworkDetailItem("Receive", formatBits((stats?.networkReceiveSpeed ?: 0L) * 8))
            }
            Column(Modifier.weight(1.2f)) {
                NetworkDetailItem("SSID:", stats?.ssid ?: "-")
                NetworkDetailItem("IPv4 address:", stats?.ipv4 ?: "-")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Signal strength: ", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = netColor
                    )
                }
            }
        }
    }
}

@Composable
fun NetworkDetailItem(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

fun formatBits(bits: Long): String {
    if (bits <= 0) return "0 bps"
    val units = arrayOf("bps", "Kbps", "Mbps", "Gbps")
    val digitGroups = (log10(bits.toDouble()) / log10(1000.0)).toInt()
    return String.format(Locale.US, "%.1f %s", bits / 1000.0.pow(digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun ProcessorDetails(stats: SystemStats?, specs: DeviceSpecs?) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DetailColumn(Modifier.weight(1f), listOf(
                "Utilization" to "${((stats?.cpuUsage ?: 0f) * 100).toInt()}%",
                "Processes" to (stats?.processCount?.toString() ?: "0"),
                "Up time" to (stats?.upTime ?: "0:00:00:00")
            ))
            DetailColumn(Modifier.weight(1f), listOf(
                "Speed" to String.format(Locale.US, "%.2f GHz", stats?.cpuSpeed ?: 0f),
                "Threads" to (stats?.threadCount?.toString() ?: "0")
            ))
            DetailColumn(Modifier.weight(1f), listOf(
                "Base speed:" to (specs?.baseSpeed ?: "-"),
                "Sockets:" to (specs?.sockets?.toString() ?: "-"),
                "Cores:" to (specs?.cores?.toString() ?: "-"),
                "Logical processors:" to (specs?.logicalProcessors?.toString() ?: "-"),
                "Virtualization:" to (specs?.virtualization ?: "-"),
                "L1 cache:" to (specs?.l1Cache ?: "-"),
                "L2 cache:" to (specs?.l2Cache ?: "-"),
                "L3 cache:" to (specs?.l3Cache ?: "-")
            ))
        }
    }
}

@Composable
fun DetailColumn(modifier: Modifier, items: List<Pair<String, String>>) {
    Column(modifier) {
        items.forEach { (label, value) ->
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun DiskCard(
    stats: SystemStats?,
    diskHistory: List<Float>,
    isTestingDisk: Boolean,
    diskTestResult: String,
    diskBenchProgress: Float,
    diskBenchPhase: String,
    onRunDiskBenchmark: () -> Unit
) {
    val diskColor = Color(0xFFFD7E14)
    val animatedProgress by animateFloatAsState(
        targetValue = diskBenchProgress,
        animationSpec = tween(400),
        label = "bench_progress"
    )

    val usedStorage = stats?.storageUsed ?: 0L
    val totalStorage = stats?.storageTotal ?: 0L
    val freeStorage = (totalStorage - usedStorage).coerceAtLeast(0L)
    val usedRatio = if (totalStorage > 0) usedStorage.toFloat() / totalStorage else 0f

    ExpressiveCard {
        // Header Row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text("Disk & Storage", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${formatBytes(usedStorage)} used of ${formatBytes(totalStorage)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onRunDiskBenchmark,
                enabled = !isTestingDisk,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.height(32.dp)
            ) {
                if (isTestingDisk) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Running…", style = MaterialTheme.typography.labelSmall)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Benchmark", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Storage Capacity Bar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Capacity Usage", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("${(usedRatio * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { usedRatio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = diskColor,
            trackColor = diskColor.copy(alpha = 0.2f)
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${formatBytes(usedStorage)} used", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("${formatBytes(freeStorage)} free", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(Modifier.height(16.dp))

        // Live I/O activity graph
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Active Flash I/O", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("${if (isTestingDisk) "100" else "0"}%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Spacer(modifier = Modifier.height(4.dp))
        RealTimeGraph(data = diskHistory, color = diskColor, modifier = Modifier.height(90.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("60 seconds", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("0", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }

        // ── Benchmark progress UI ────────────────────────────────────────────
        AnimatedVisibility(visible = isTestingDisk) {
            Column {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = diskColor.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                diskBenchPhase.ifBlank { "Preparing…" },
                                style = MaterialTheme.typography.labelSmall,
                                color = diskColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = diskColor
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                            color = diskColor,
                            trackColor = diskColor.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }

        // ── Results ─────────────────────────────────────────────────────────
        AnimatedVisibility(visible = diskTestResult.isNotEmpty() && !isTestingDisk) {
            Column {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = diskColor.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = diskColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Benchmark Result", style = MaterialTheme.typography.labelMedium,
                                color = diskColor, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            diskTestResult,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EngineGraph(label: String, value: Float, history: List<Float>, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        RealTimeGraph(data = history, color = color, modifier = Modifier.height(60.dp))
    }
}

@Composable
fun MemoryGraph(label: String, value: String, history: List<Float>, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        RealTimeGraph(data = history, color = color, modifier = Modifier.height(40.dp))
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    capacity: String,
    usageLabel: String,
    history: List<Float>,
    color: Color,
    isExpanded: Boolean,
    onClick: () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    ExpressiveCard(modifier = Modifier.clickable { onClick() }.animateContentSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(capacity, style = MaterialTheme.typography.bodyLarge, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(usageLabel, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(4.dp))
        
        Box(modifier = Modifier.fillMaxWidth()) {
            RealTimeGraph(data = history, color = color)
        }
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("60 seconds", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("0", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }

        AnimatedVisibility(visible = isExpanded) {
            Column {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = color.copy(alpha = 0.2f))
                Text("Details", style = MaterialTheme.typography.labelMedium, color = color)
                Spacer(modifier = Modifier.height(8.dp))
                expandedContent()
            }
        }
    }
}

@Composable
fun ProcessListSmall(processes: List<AppProcess>) {
    Column {
        processes.forEach { process ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = process.processName.split(".").last(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatBytes(process.memoryUsage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun BatteryThermalCard(
    stats: SystemStats?,
    tempHistory: List<Float>
) {
    val bStats = stats?.batteryStats
    val level = bStats?.level ?: 80
    val isCharging = bStats?.isCharging ?: false
    val chargeType = bStats?.chargeType ?: "Discharging"
    val tempC = bStats?.temperatureC ?: 32.0f
    val tempF = bStats?.temperatureF ?: 89.6f
    val voltage = bStats?.voltageMv ?: 4000
    val health = bStats?.health ?: "Good"
    val thermalStatus = bStats?.thermalStatus ?: "Optimal"

    val thermalColor = when {
        tempC >= 43.0f -> Color(0xFFEF5350) // Red
        tempC >= 36.0f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFF28A745)          // Green
    }

    ExpressiveCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Battery & Thermal", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (isCharging) "$chargeType • $level%" else "$chargeType • $level%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Level & Temp Dual Gauge Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Battery Level Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Battery Level", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Text("$level%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (level / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (isCharging) Color(0xFF28A745) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Temperature Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Temperature", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Text(
                        String.format(Locale.US, "%.1f°C", tempC),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = thermalColor
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { ((tempC - 20f) / 30f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = thermalColor,
                    trackColor = thermalColor.copy(alpha = 0.15f)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Live Thermal History Graph
        Text("Thermal trend (60s)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        RealTimeGraph(data = tempHistory, color = thermalColor, modifier = Modifier.height(50.dp))

        Spacer(Modifier.height(12.dp))

        // Technical details row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Voltage: ${voltage} mV",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                "Fahrenheit: ${String.format(Locale.US, "%.1f", tempF)}°F",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                "Health: $health",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun StressTestCard(
    isTesting: Boolean,
    progress: Float,
    phase: String,
    timeRemaining: String,
    result: StressTestResult?,
    history: List<Float> = emptyList(),
    onRunStressTest: () -> Unit
) {
    val fireColor = Color(0xFFFF6D00)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "stress_progress"
    )

    ExpressiveCard {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = fireColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Device Stress Test",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "Full Hardware Benchmark",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onRunStressTest,
                enabled = !isTesting,
                colors = ButtonDefaults.buttonColors(containerColor = fireColor),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(timeRemaining, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Start", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Live progress bar & real-time performance graph during test
        AnimatedVisibility(visible = isTesting) {
            Column {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        phase.ifBlank { "Testing…" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fireColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fireColor
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = fireColor,
                    trackColor = fireColor.copy(alpha = 0.15f)
                )

                Spacer(Modifier.height(10.dp))
                Text("Live Benchmark Output Curve", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                RealTimeGraph(data = history, color = fireColor, modifier = Modifier.height(65.dp))
            }
        }

        // Clean Benchmark Results + Performance Curve
        AnimatedVisibility(visible = result != null && !isTesting) {
            val res = result ?: return@AnimatedVisibility
            Column {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))

                // Score + Tier Badge
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "HARDWARE SCORE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = fireColor,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = String.format(Locale.US, "%,d PTS", res.totalScore),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        color = fireColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = res.tierGrade,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = fireColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Benchmark Performance Curve Graph
                val graphData = res.performanceHistory.ifEmpty { history }
                if (graphData.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("120s Benchmark Performance Curve", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    RealTimeGraph(data = graphData, color = fireColor, modifier = Modifier.height(65.dp))
                }

                Spacer(Modifier.height(12.dp))

                // Minimalist Sub-scores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MinimalSubScore("CPU", formatCompactScore(res.cpuScore))
                    MinimalSubScore("GPU", formatCompactScore(res.gpuScore))
                    MinimalSubScore("RAM", formatCompactScore(res.memoryScore))
                    MinimalSubScore("Storage", formatCompactScore(res.storageScore))
                }

                Spacer(Modifier.height(10.dp))

                // Subtle Temperature & Stability line
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Peak: ${String.format(Locale.US, "%.1f", res.peakTempCelsius)}°C",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        "Stability: ${String.format(Locale.US, "%.1f", res.stabilityRatingPct)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun MinimalSubScore(label: String, scoreText: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
        Text(
            text = scoreText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatCompactScore(score: Int): String {
    return if (score >= 1_000_000) {
        String.format(Locale.US, "%.2fM", score / 1_000_000.0)
    } else if (score >= 1_000) {
        String.format(Locale.US, "%.0fK", score / 1000.0)
    } else {
        score.toString()
    }
}
