package com.example.taskmanager

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Locale
import java.util.concurrent.TimeUnit

class SystemMonitorRepository(private val context: Context) {

    // ──────────────────────────────────────────────────────────────────────────
    //  CPU via /proc/stat  (with multi-source freq fallback)
    // ──────────────────────────────────────────────────────────────────────────
    private data class CpuTick(val idle: Long, val total: Long)

    private fun readCpuTick(): CpuTick? {
        return try {
            val br = BufferedReader(FileReader("/proc/stat"))
            val line = br.readLine()
            br.close()
            if (line == null || !line.startsWith("cpu")) return null
            val nums = line.substringAfter("cpu").trim()
                .split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
            if (nums.size < 5) return null
            val idle  = nums[3] + nums[4]
            val total = nums.sum()
            if (total == 0L) null else CpuTick(idle, total)
        } catch (e: Exception) { null }
    }

    /**
     * Read CPU frequency ratio across all cores.
     * Uses cpuinfo_max_freq (always exists) as the max reference,
     * falling back to scaling_max_freq.
     */
    private fun cpuFreqRatio(): Float {
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            var totalRatio = 0f; var counted = 0
            for (i in 0 until cores) {
                val curFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                if (!curFile.exists()) continue
                val cur = try { curFile.readText().trim().toLongOrNull() } catch (e: Exception) { null }
                    ?: continue

                // cpuinfo_max_freq is the hardware max (always present on Qualcomm)
                val maxKHz: Long = listOf(
                    "/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq",
                    "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq"
                ).firstNotNullOfOrNull { path ->
                    try {
                        val f = File(path)
                        if (f.exists()) f.readText().trim().toLongOrNull() else null
                    } catch (e: Exception) { null }
                } ?: continue

                if (maxKHz > 0) { totalRatio += cur.toFloat() / maxKHz; counted++ }
            }
            if (counted > 0) (totalRatio / counted).coerceIn(0f, 1f) else 0f
        } catch (e: Exception) { 0f }
    }

    private fun readCpuCurFreqGHz(): Float {
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            (0 until cores).mapNotNull { cpu ->
                val f = File("/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_cur_freq")
                if (f.exists()) f.readText().trim().toLongOrNull() else null
            }.maxOrNull()?.let { it / 1_000_000f } ?: 0f
        } catch (e: Exception) { 0f }
    }

    private fun readCpuMaxFreqGHz(): Float {
        return try {
            val cores = Runtime.getRuntime().availableProcessors()
            (0 until cores).mapNotNull { cpu ->
                val f = File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
                if (f.exists()) f.readText().trim().toLongOrNull() else null
            }.maxOrNull()?.let { it / 1_000_000f } ?: 0f
        } catch (e: Exception) { 0f }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  GPU  – Qualcomm Adreno KGSL + Mali fallback
    // ──────────────────────────────────────────────────────────────────────────
    private fun readGpuUsage(): Float {
        // Qualcomm Adreno: gpu_busy_percentage is the real utilization
        val busyPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/class/misc/mali0/device/utilization_stats"
        )
        for (path in busyPaths) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val raw = f.readText().trim()
                    // Might be "45" or "45 %" or "45/100"
                    val pct = raw.replace("%", "").split(" ", "/").firstOrNull()?.trim()?.toFloatOrNull()
                    if (pct != null) return (pct / 100f).coerceIn(0f, 1f)
                }
            } catch (e: Exception) { continue }
        }
        // Fallback: GPU clock freq ratio (Adreno devfreq)
        return try {
            val cur = File("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")
            val max = File("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq")
            if (cur.exists() && max.exists()) {
                val c = cur.readText().trim().toLongOrNull() ?: return 0f
                val m = max.readText().trim().toLongOrNull() ?: return 0f
                if (m > 0) (c.toFloat() / m).coerceIn(0f, 1f) else 0f
            } else 0f
        } catch (e: Exception) { 0f }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Disk I/O via /proc/diskstats
    // ──────────────────────────────────────────────────────────────────────────
    private data class DiskTick(val read: Long, val write: Long)

    private fun readDiskTick(): DiskTick? {
        return try {
            var totalRead = 0L; var totalWrite = 0L; var found = false
            BufferedReader(FileReader("/proc/diskstats")).use { br ->
                var line = br.readLine()
                while (line != null) {
                    val p = line.trim().split(Regex("\\s+"))
                    if (p.size >= 14) {
                        val dev = p[2]
                        if (dev.matches(Regex("(mmcblk\\d+|sd[a-z]+|nvme\\d+n\\d+|vd[a-z]+)"))) {
                            totalRead  += p[5].toLongOrNull() ?: 0L
                            totalWrite += p[9].toLongOrNull() ?: 0L
                            found = true
                        }
                    }
                    line = br.readLine()
                }
            }
            if (found) DiskTick(totalRead * 512L, totalWrite * 512L) else null
        } catch (e: Exception) { null }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Memory
    // ──────────────────────────────────────────────────────────────────────────
    private fun getMemMap(): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        try {
            BufferedReader(FileReader("/proc/meminfo")).use { br ->
                var line = br.readLine()
                while (line != null) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        map[parts[0].removeSuffix(":")] = (parts[1].toLongOrNull() ?: 0L) * 1024L
                    }
                    line = br.readLine()
                }
            }
        } catch (e: Exception) {}
        return map
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Real-time stats flow
    // ──────────────────────────────────────────────────────────────────────────
    fun getRealTimeStats(): Flow<SystemStats> = flow {
        var lastRx   = TrafficStats.getTotalRxBytes()
        var lastTx   = TrafficStats.getTotalTxBytes()
        var lastCpu  = readCpuTick()
        var lastDisk = readDiskTick()
        var lastStatFsFree: Long? = null

        while (true) {
            delay(1000)

            // ── Network ──────────────────────────────────────────────────────
            val curRx = TrafficStats.getTotalRxBytes()
            val curTx = TrafficStats.getTotalTxBytes()
            val rxSpeed = (if (lastRx > 0) curRx - lastRx else 0L).coerceAtLeast(0L)
            val txSpeed = (if (lastTx > 0) curTx - lastTx else 0L).coerceAtLeast(0L)
            lastRx = curRx; lastTx = curTx

            // ── CPU ──────────────────────────────────────────────────────────
            val curCpu = readCpuTick()
            val cpuUsage: Float = when {
                curCpu != null && lastCpu != null -> {
                    val idleDelta  = curCpu.idle  - lastCpu.idle
                    val totalDelta = curCpu.total - lastCpu.total
                    if (totalDelta > 0)
                        ((totalDelta - idleDelta).toFloat() / totalDelta).coerceIn(0f, 1f)
                    else 0f
                }
                // Fallback: CPU frequency ratio (works when /proc/stat is blocked)
                else -> cpuFreqRatio()
            }
            lastCpu = curCpu

            // ── GPU ──────────────────────────────────────────────────────────
            val gpuUsage = readGpuUsage()

            // ── Disk ─────────────────────────────────────────────────────────
            val curDisk = readDiskTick()
            var readSpeed = 0L; var writeSpeed = 0L
            if (curDisk != null && lastDisk != null) {
                readSpeed  = (curDisk.read  - lastDisk.read ).coerceAtLeast(0L)
                writeSpeed = (curDisk.write - lastDisk.write).coerceAtLeast(0L)
            } else {
                val sf = StatFs(Environment.getDataDirectory().path)
                val freeNow = sf.availableBlocksLong * sf.blockSizeLong
                lastStatFsFree?.let { prev ->
                    val delta = prev - freeNow
                    if (delta > 0) writeSpeed = delta else readSpeed = (-delta).coerceAtLeast(0L)
                }
                lastStatFsFree = freeNow
            }
            lastDisk = curDisk

            // ── Memory ───────────────────────────────────────────────────────
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val memMap   = getMemMap()
            val memTotal = memInfo.totalMem
            val usedRam  = (memTotal - memInfo.availMem).coerceAtLeast(0L)
            val cached   = (memMap["Cached"] ?: 0L) + (memMap["SReclaimable"] ?: 0L)

            // ── Storage ──────────────────────────────────────────────────────
            val stat = StatFs(Environment.getDataDirectory().path)
            val usedStorage  = (stat.blockCountLong - stat.availableBlocksLong) * stat.blockSizeLong
            val totalStorage = stat.blockCountLong * stat.blockSizeLong

            // ── Network info ─────────────────────────────────────────────────
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION") val connectionInfo = wifiManager.connectionInfo
            @Suppress("DEPRECATION") var ssid = connectionInfo.ssid.removeSurrounding("\"")
            if (ssid == "<unknown ssid>" || ssid == "0x" || ssid.isBlank()) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                ssid = if (caps != null) when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> "Wi-Fi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Connected"
                } else "Not Connected"
            }
            var ipv4 = "-"; var ipv6 = "-"
            try {
                val ifaces = NetworkInterface.getNetworkInterfaces()
                while (ifaces.hasMoreElements()) {
                    val intf = ifaces.nextElement()
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress) {
                            if (addr is Inet4Address) ipv4 = addr.hostAddress ?: "-"
                            else if (addr is Inet6Address) ipv6 = addr.hostAddress?.split("%")?.get(0) ?: "-"
                        }
                    }
                }
            } catch (e: Exception) {}

            // ── Uptime ───────────────────────────────────────────────────────
            val uptimeMs = SystemClock.elapsedRealtime()
            val uptime   = String.format(Locale.US, "%d:%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toDays(uptimeMs),
                TimeUnit.MILLISECONDS.toHours(uptimeMs) % 24,
                TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60,
                TimeUnit.MILLISECONDS.toSeconds(uptimeMs) % 60)

            val processCount = am.runningAppProcesses?.size ?: 0
            val cpuSpeedGHz  = readCpuCurFreqGHz()

            val gpuMemUsed  = memMap["GpuTotalPages"]?.let { it * 4096L } ?: (usedRam / 8L)
            val gpuMemTotal = memTotal / 4L

            @Suppress("DEPRECATION")
            val signalStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                wifiManager.calculateSignalLevel(connectionInfo.rssi)
            else WifiManager.calculateSignalLevel(connectionInfo.rssi, 5)

            emit(SystemStats(
                cpuUsage            = cpuUsage,
                ramUsed             = usedRam,
                ramTotal            = memTotal,
                storageUsed         = usedStorage,
                storageTotal        = totalStorage,
                storageReadSpeed    = readSpeed,
                storageWriteSpeed   = writeSpeed,
                gpuUsage3D          = gpuUsage,        // Real Adreno/Mali data
                gpuUsageCopy        = gpuUsage * 0.3f, // Proxy from 3D usage
                gpuUsageCompute     = gpuUsage * 0.1f,
                gpuMemoryUsed       = gpuMemUsed,
                gpuMemoryTotal      = gpuMemTotal,
                networkSendSpeed    = txSpeed,
                networkReceiveSpeed = rxSpeed,
                ssid                = ssid,
                ipv4                = ipv4,
                ipv6                = ipv6,
                signalStrength      = signalStrength,
                cpuSpeed            = cpuSpeedGHz,
                processCount        = processCount,
                threadCount         = processCount * 20,
                handleCount         = processCount * 300,
                upTime              = uptime,
                ramCompressed       = memMap["Active(anon)"] ?: 0L,
                ramCommitted        = memMap["Committed_AS"] ?: usedRam,
                ramLimit            = memMap["CommitLimit"]  ?: memTotal,
                ramCached           = cached,
                ramPagedPool        = memMap["SReclaimable"] ?: 0L,
                ramNonPagedPool     = memMap["SUnreclaim"]   ?: 0L,
                gpuDedicatedUsed    = gpuMemUsed,
                gpuDedicatedTotal   = gpuMemTotal,
                gpuSharedUsed       = usedRam / 8L,
                gpuSharedTotal      = gpuMemTotal
            ))
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Device Specs
    // ──────────────────────────────────────────────────────────────────────────
    fun getDeviceSpecs(): DeviceSpecs {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = stat.blockCountLong * stat.blockSizeLong
        val cores = Runtime.getRuntime().availableProcessors()

        val totalMem = memInfo.totalMem
        val possibleRamSizes = listOf(2,3,4,6,8,12,16,24,32).map { it.toLong() * 1024 * 1024 * 1024 }
        val physicalRam  = possibleRamSizes.firstOrNull { it >= totalMem } ?: totalMem
        val reservedMem  = physicalRam - totalMem

        val maxFreqGHz = readCpuMaxFreqGHz()
        val baseSpeed  = if (maxFreqGHz > 0) String.format(Locale.US, "%.2f GHz", maxFreqGHz) else "N/A"

        fun cacheSize(idx: Int) = try {
            val f = File("/sys/devices/system/cpu/cpu0/cache/index$idx/size")
            if (f.exists()) f.readText().trim() else "N/A"
        } catch (e: Exception) { "N/A" }

        val cpuInfoName = try {
            BufferedReader(FileReader("/proc/cpuinfo")).use { br ->
                br.lineSequence()
                    .firstOrNull { it.startsWith("Hardware") || it.startsWith("Processor") }
                    ?.substringAfter(":")?.trim()
            } ?: Build.HARDWARE.uppercase(Locale.US)
        } catch (e: Exception) { Build.HARDWARE.uppercase(Locale.US) }

        return DeviceSpecs(
            processorName   = cpuInfoName,
            gpuName         = when {
                Build.HARDWARE.contains("qcom",   true) -> "Qualcomm Adreno GPU"
                Build.HARDWARE.contains("mtk",    true) -> "ARM Mali GPU"
                Build.HARDWARE.contains("exynos", true) -> "Samsung Mali GPU"
                Build.HARDWARE.contains("kirin",  true) -> "HiSilicon Mali GPU"
                else -> "System Graphics"
            },
            totalRam        = totalMem,
            totalStorage    = totalStorage,
            androidVersion  = Build.VERSION.RELEASE,
            modelName       = "${Build.MANUFACTURER} ${Build.MODEL}",
            networkAdapterName = "Android Network Interface",
            baseSpeed       = baseSpeed,
            sockets         = 1,
            cores           = cores / 2,
            logicalProcessors = cores,
            virtualization  = if (Build.SUPPORTED_ABIS.contains("x86_64")) "VT-x" else "ARM Virtualization",
            l1Cache         = cacheSize(0),
            l2Cache         = cacheSize(1),
            l3Cache         = cacheSize(2),
            ramSpeed        = "LPDDR",
            ramSlots        = "Integrated",
            ramFormFactor   = "LPDDR",
            ramHardwareReserved = String.format(Locale.US, "%.1f MB", reservedMem / 1024.0 / 1024.0),
            gpuDriverVersion = "OpenGL ES ${am.deviceConfigurationInfo.glEsVersion}",
            gpuDriverDate   = "Build ${Build.ID}",
            gpuVulkanVersion = if (context.packageManager.hasSystemFeature("android.hardware.vulkan.version"))
                "Vulkan 1.1+" else "N/A",
            gpuPhysicalLocation = "Integrated SoC",
            gpuHardwareReserved = String.format(Locale.US, "%.1f MB", (reservedMem / 5) / 1024.0 / 1024.0)
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Running Processes  –  multi-source with /proc/[pid]/status memory fallback
    // ──────────────────────────────────────────────────────────────────────────
    fun getRunningProcesses(): List<AppProcess> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processMap = mutableMapOf<Int, AppProcess>()

        // ── Source 1: getRunningAppProcesses ─────────────────────────────────
        try {
            am.runningAppProcesses?.forEach { info ->
                val pss = readProcessMemory(am, info.pid)
                val pkg = info.pkgList?.firstOrNull() ?: info.processName
                processMap[info.pid] = AppProcess(
                    pid         = info.pid,
                    processName = info.processName,
                    packageName = pkg,
                    memoryUsage = pss,
                    cpuUsage    = readProcPidCpu(info.pid)
                )
            }
        } catch (e: Exception) {}

        // ── Source 2: getRunningServices ─────────────────────────────────────
        try {
            @Suppress("DEPRECATION")
            am.getRunningServices(Int.MAX_VALUE)?.forEach { svc ->
                val pid = svc.pid
                if (pid > 0 && !processMap.containsKey(pid)) {
                    val name = svc.process ?: svc.service.packageName
                    val pkg  = svc.service.packageName
                    val pss  = readProcessMemory(am, pid)
                    processMap[pid] = AppProcess(
                        pid         = pid,
                        processName = name,
                        packageName = pkg,
                        memoryUsage = pss,
                        cpuUsage    = readProcPidCpu(pid)
                    )
                }
            }
        } catch (e: Exception) {}

        // ── Source 3: UsageStats (last 30 min) ───────────────────────────────
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 30 * 60_000L, now)
                ?.filter { it.lastTimeUsed > now - 30 * 60_000L }
                ?.forEach { usage ->
                    val alreadyCaptured = processMap.values.any { it.packageName == usage.packageName }
                    if (!alreadyCaptured) {
                        try {
                            context.packageManager.getApplicationInfo(usage.packageName, 0)
                            val syntheticPid = -(usage.packageName.hashCode())
                            processMap[syntheticPid] = AppProcess(
                                pid         = syntheticPid,
                                processName = usage.packageName,
                                packageName = usage.packageName,
                                memoryUsage = 0L,
                                cpuUsage    = 0f,
                                lastUsed    = usage.lastTimeUsed
                            )
                        } catch (e: Exception) {}
                    }
                }
        } catch (e: SecurityException) {
        } catch (e: Exception) {}

        return processMap.values
            .filter { it.packageName.isNotBlank() }
            .sortedByDescending { it.memoryUsage }
    }

    /**
     * End a task: attempts root su force-stop, reflection forceStopPackage, and background process killing.
     */
    fun endTask(packageName: String, pid: Int = -1) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            // 1. Try root `am force-stop` (works if device is rooted)
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
                p.waitFor()
            } catch (e: Exception) {}

            // 2. Try forceStopPackage via reflection
            try {
                val method = am.javaClass.getMethod("forceStopPackage", String::class.java)
                method.invoke(am, packageName)
            } catch (e: Exception) {}

            // 3. Kill background processes
            am.killBackgroundProcesses(packageName)

            // 4. Kill by PID if valid
            if (pid > 0) {
                try {
                    android.os.Process.killProcess(pid)
                } catch (e: Exception) {}
            }

            // 5. Remove app from recent tasks / Recents screen
            am.appTasks?.forEach { task ->
                try {
                    val info = task.taskInfo
                    val basePkg = info?.baseIntent?.component?.packageName
                    val topPkg = info?.topActivity?.packageName
                    if (basePkg == packageName || topPkg == packageName) {
                        task.finishAndRemoveTask()
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
    }

    /**
     * Get process memory in bytes.
     * Tries getProcessMemoryInfo() first (works for same-UID processes),
     * then falls back to /proc/[pid]/status (VmRSS) which is readable on most devices.
     */
    private fun readProcessMemory(am: ActivityManager, pid: Int): Long {
        // Try official API first
        try {
            val memArr = am.getProcessMemoryInfo(intArrayOf(pid))
            val pss = memArr?.firstOrNull()?.totalPss ?: 0
            if (pss > 0) return pss.toLong() * 1024L
        } catch (e: Exception) {}

        // Fallback: /proc/[pid]/status → VmRSS (Resident Set Size)
        return readProcessRssFromProc(pid)
    }

    /** Read RSS (physical RAM) for a process from /proc/[pid]/status */
    private fun readProcessRssFromProc(pid: Int): Long {
        if (pid <= 0) return 0L
        return try {
            BufferedReader(FileReader("/proc/$pid/status")).use { br ->
                var line = br.readLine()
                while (line != null) {
                    if (line.startsWith("VmRSS:")) {
                        // "VmRSS:   12345 kB"
                        val kbStr = line.substringAfter("VmRSS:").trim().split(Regex("\\s+"))[0]
                        return (kbStr.toLongOrNull() ?: 0L) * 1024L
                    }
                    line = br.readLine()
                }
                0L
            }
        } catch (e: Exception) { 0L }
    }

    /** Read cumulative CPU ticks for a process → use as relative CPU ranking */
    private fun readProcPidCpu(pid: Int): Float {
        if (pid <= 0) return 0f
        return try {
            val parts = File("/proc/$pid/stat").readText().trim().split(" ")
            val utime = parts.getOrNull(13)?.toLongOrNull() ?: 0L
            val stime = parts.getOrNull(14)?.toLongOrNull() ?: 0L
            val ticks = utime + stime
            val uptimeTicks = SystemClock.elapsedRealtime() / 10L
            if (uptimeTicks > 0) (ticks.toFloat() / uptimeTicks).coerceIn(0f, 2f) else 0f
        } catch (e: Exception) { 0f }
    }
}
