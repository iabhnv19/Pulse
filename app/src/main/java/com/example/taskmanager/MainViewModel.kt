package com.example.taskmanager

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.benchmark.CpuBenchmarkEngine
import com.example.taskmanager.benchmark.GpuBenchmarkEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SystemMonitorRepository(application)
    private val cpuEngine = CpuBenchmarkEngine()
    private val gpuEngine = GpuBenchmarkEngine()

    var stats = mutableStateOf<SystemStats?>(null)
        private set

    val cpuHistory       = mutableStateListOf<Float>()
    val ramHistory       = mutableStateListOf<Float>()
    val gpu3DHistory     = mutableStateListOf<Float>()
    val gpuCopyHistory   = mutableStateListOf<Float>()
    val gpuComputeHistory= mutableStateListOf<Float>()
    val gpuMemHistory    = mutableStateListOf<Float>()
    val diskHistory      = mutableStateListOf<Float>()
    val netSendHistory   = mutableStateListOf<Float>()
    val netReceiveHistory= mutableStateListOf<Float>()

    var deviceSpecs = mutableStateOf<DeviceSpecs?>(null)
        private set

    var runningProcesses = mutableStateListOf<AppProcess>()
        private set

    private val dismissedPackages = mutableSetOf<String>()

    val topProcesses = derivedStateOf {
        runningProcesses.take(5)
    }

    // ── CPU benchmark state ───────────────────────────────────────────────────
    var isTestingCpu = mutableStateOf(false)
        private set
    var cpuTestResult = mutableStateOf("")
        private set
    var cpuBenchProgress = mutableStateOf(0f)
        private set
    var cpuBenchPhase = mutableStateOf("")
        private set

    // ── GPU benchmark state ───────────────────────────────────────────────────
    var isTestingGpu = mutableStateOf(false)
        private set
    var gpuTestResult = mutableStateOf("")
        private set
    var gpuBenchProgress = mutableStateOf(0f)
        private set
    var gpuBenchPhase = mutableStateOf("")
        private set

    // ── Disk benchmark state ──────────────────────────────────────────────────
    var isTestingDisk = mutableStateOf(false)
        private set
    var diskTestResult   = mutableStateOf("")
        private set
    /** 0.0 – 1.0 progress for the benchmark progress indicator */
    var diskBenchProgress= mutableStateOf(0f)
        private set
    /** Phase label shown during the benchmark */
    var diskBenchPhase   = mutableStateOf("")
        private set

    init {
        deviceSpecs.value = repository.getDeviceSpecs()

        viewModelScope.launch {
            repository.getRealTimeStats().collectLatest { newStats ->
                stats.value = newStats

                cpuHistory.add(newStats.cpuUsage)
                if (cpuHistory.size > 60) cpuHistory.removeAt(0)

                ramHistory.add(newStats.ramUsed.toFloat())
                if (ramHistory.size > 60) ramHistory.removeAt(0)

                gpu3DHistory.add(newStats.gpuUsage3D)
                if (gpu3DHistory.size > 60) gpu3DHistory.removeAt(0)

                gpuCopyHistory.add(newStats.gpuUsageCopy)
                if (gpuCopyHistory.size > 60) gpuCopyHistory.removeAt(0)

                gpuComputeHistory.add(newStats.gpuUsageCompute)
                if (gpuComputeHistory.size > 60) gpuComputeHistory.removeAt(0)

                gpuMemHistory.add(newStats.gpuMemoryUsed.toFloat())
                if (gpuMemHistory.size > 60) gpuMemHistory.removeAt(0)

                val diskActivity = (newStats.storageReadSpeed + newStats.storageWriteSpeed).toFloat() / (200L * 1024 * 1024)
                diskHistory.add(diskActivity.coerceIn(0f, 1f))
                if (diskHistory.size > 60) diskHistory.removeAt(0)

                netSendHistory.add(newStats.networkSendSpeed.toFloat())
                if (netSendHistory.size > 60) netSendHistory.removeAt(0)

                netReceiveHistory.add(newStats.networkReceiveSpeed.toFloat())
                if (netReceiveHistory.size > 60) netReceiveHistory.removeAt(0)
            }
        }

        viewModelScope.launch {
            while (true) {
                refreshProcesses()
                delay(2.seconds)
            }
        }
    }

    // ── Disk Benchmark ────────────────────────────────────────────────────────
    /**
     * Proper disk benchmark:
     *  • Uses FileDescriptor.sync() (fdatasync) so writes actually hit flash
     *  • 3-pass write average to reduce variance
     *  • 3-pass sequential read average
     *  • Live progress + phase label
     *  • 100 MB test file for meaningful numbers (falls back to 50 MB on low storage)
     *  • Drop OS page-cache between write and read (via re-open) so reads are real
     */
    fun runDiskBenchmark() {
        viewModelScope.launch {
            isTestingDisk.value   = true
            diskTestResult.value  = ""
            diskBenchProgress.value = 0f

            withContext(Dispatchers.IO) {
                val cacheDir = getApplication<Application>().cacheDir
                val tmpFile  = File(cacheDir, "disk_bench.tmp")

                try {
                    // ── Choose test size based on available space ─────────────
                    val freeBytes = cacheDir.freeSpace
                    val testSize  = when {
                        freeBytes > 200L * 1024 * 1024 -> 100L * 1024 * 1024   // 100 MB
                        freeBytes > 80L  * 1024 * 1024 ->  50L * 1024 * 1024   //  50 MB
                        freeBytes > 30L  * 1024 * 1024 ->  20L * 1024 * 1024   //  20 MB
                        else -> throw IllegalStateException("Not enough storage space for benchmark")
                    }
                    val testSizeMB = testSize / 1024.0 / 1024.0
                    val chunkSize  = 4 * 1024 * 1024   // 4 MB chunks (realistic block I/O)
                    val passes     = 3

                    // Pre-fill buffer with random-ish data (not zero – bypasses some optimisations)
                    val buf = ByteArray(chunkSize)
                    for (i in buf.indices step 8) {
                        buf[i] = (i % 251).toByte()   // simple non-zero pattern, fast to fill
                    }

                    // ══════════════════════════════════════════════════════════
                    //  WRITE TEST  (with fdatasync per pass → real flash speed)
                    // ══════════════════════════════════════════════════════════
                    val writeSpeeds = DoubleArray(passes)
                    for (pass in 0 until passes) {
                        setPhase("Write pass ${pass + 1}/$passes", 0f + pass.toFloat() / passes * 0.5f)
                        var written = 0L
                        val t0 = System.nanoTime()

                        FileOutputStream(tmpFile, false).use { fos ->
                            while (written < testSize) {
                                val toWrite = minOf(chunkSize.toLong(), testSize - written).toInt()
                                fos.write(buf, 0, toWrite)
                                written += toWrite
                                // Update progress within the write half (0–50%)
                                val passProgress = written.toFloat() / testSize
                                setPhase(
                                    "Write pass ${pass + 1}/$passes — ${(testSizeMB * passProgress).toInt()} / ${testSizeMB.toInt()} MB",
                                    (pass.toFloat() / passes + passProgress / passes) * 0.5f
                                )
                            }
                            // fdatasync: flush kernel buffers → controller buffer → NAND
                            fos.flush()
                            fos.fd.sync()     // <-- This is the key: forces real write to flash
                        }

                        val elapsedSec = (System.nanoTime() - t0) / 1_000_000_000.0
                        writeSpeeds[pass] = testSizeMB / elapsedSec
                    }

                    val avgWriteMBps = writeSpeeds.average()

                    // ══════════════════════════════════════════════════════════
                    //  READ TEST  (re-open file each pass → defeats readahead cache)
                    // ══════════════════════════════════════════════════════════
                    val readSpeeds = DoubleArray(passes)
                    for (pass in 0 until passes) {
                        setPhase("Read pass ${pass + 1}/$passes", 0.5f + pass.toFloat() / passes * 0.5f)
                        var totalRead = 0L
                        val t0 = System.nanoTime()

                        // Use RandomAccessFile to allow controlled reads without full readBytes()
                        RandomAccessFile(tmpFile, "r").use { raf ->
                            raf.seek(0)
                            val readBuf = ByteArray(chunkSize)
                            var n = raf.read(readBuf)
                            while (n > 0) {
                                totalRead += n
                                val passProgress = totalRead.toFloat() / testSize
                                setPhase(
                                    "Read pass ${pass + 1}/$passes — ${(testSizeMB * passProgress).toInt()} / ${testSizeMB.toInt()} MB",
                                    0.5f + (pass.toFloat() / passes + passProgress / passes) * 0.5f
                                )
                                n = raf.read(readBuf)
                            }
                        }

                        val elapsedSec = (System.nanoTime() - t0) / 1_000_000_000.0
                        val actualMB   = totalRead / 1024.0 / 1024.0
                        readSpeeds[pass] = actualMB / elapsedSec
                    }

                    val avgReadMBps = readSpeeds.average()

                    tmpFile.delete()

                    // ── Format individual pass results ────────────────────────
                    val writeDetails = writeSpeeds.mapIndexed { i, v ->
                        "W${i+1}: ${String.format(Locale.US, "%.0f", v)} MB/s"
                    }.joinToString("  ")
                    val readDetails  = readSpeeds.mapIndexed { i, v ->
                        "R${i+1}: ${String.format(Locale.US, "%.0f", v)} MB/s"
                    }.joinToString("  ")

                    val result = buildString {
                        appendLine(String.format(Locale.US,
                            "Write: %.1f MB/s  |  Read: %.1f MB/s",
                            avgWriteMBps, avgReadMBps))
                        appendLine("(${testSizeMB.toInt()} MB × $passes passes, sync'd)")
                        append("$writeDetails\n$readDetails")
                    }

                    withContext(Dispatchers.Main) {
                        diskTestResult.value    = result.trim()
                        diskBenchProgress.value = 1f
                        diskBenchPhase.value    = "Complete"
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        diskTestResult.value = "Error: ${e.message}"
                        diskBenchPhase.value = ""
                    }
                    try { tmpFile.delete() } catch (_: Exception) {}
                } finally {
                    withContext(Dispatchers.Main) {
                        isTestingDisk.value = false
                    }
                }
            }
        }
    }

    private suspend fun setPhase(label: String, progress: Float) {
        withContext(Dispatchers.Main) {
            diskBenchPhase.value    = label
            diskBenchProgress.value = progress.coerceIn(0f, 1f)
        }
    }

    fun runCpuBenchmark() {
        if (isTestingCpu.value) return
        viewModelScope.launch {
            isTestingCpu.value = true
            cpuTestResult.value = ""
            cpuBenchProgress.value = 0f
            cpuBenchPhase.value = "Starting CPU Benchmark…"
            try {
                val result = cpuEngine.runBenchmark { phase, progress ->
                    withContext(Dispatchers.Main) {
                        cpuBenchPhase.value = phase
                        cpuBenchProgress.value = progress.coerceIn(0f, 1f)
                    }
                }
                withContext(Dispatchers.Main) {
                    cpuTestResult.value = result.summary
                    cpuBenchProgress.value = 1f
                    cpuBenchPhase.value = "Complete"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    cpuTestResult.value = "Error: ${e.message}"
                    cpuBenchPhase.value = ""
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isTestingCpu.value = false
                }
            }
        }
    }

    fun runGpuBenchmark() {
        if (isTestingGpu.value) return
        viewModelScope.launch {
            isTestingGpu.value = true
            gpuTestResult.value = ""
            gpuBenchProgress.value = 0f
            gpuBenchPhase.value = "Starting 4K 120Hz Extreme GPU Benchmark…"
            try {
                val result = gpuEngine.runBenchmark { phase, progress ->
                    withContext(Dispatchers.Main) {
                        gpuBenchPhase.value = phase
                        gpuBenchProgress.value = progress.coerceIn(0f, 1f)
                    }
                }
                withContext(Dispatchers.Main) {
                    gpuTestResult.value = result.summary
                    gpuBenchProgress.value = 1f
                    gpuBenchPhase.value = "Complete"
                    // If hardware renderer was found, update device specs so GPU card displays it!
                    if (result.renderer.isNotBlank() && result.renderer != "Adreno GPU" && result.renderer != "System Graphics") {
                        val currentSpecs = deviceSpecs.value
                        if (currentSpecs != null) {
                            deviceSpecs.value = currentSpecs.copy(gpuName = result.renderer)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    gpuTestResult.value = "Error: ${e.message}"
                    gpuBenchPhase.value = ""
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isTestingGpu.value = false
                }
            }
        }
    }

    fun refreshProcesses() {
        viewModelScope.launch {
            val processes = withContext(Dispatchers.IO) {
                repository.getRunningProcesses()
            }
            runningProcesses.clear()
            runningProcesses.addAll(processes.filter { it.packageName !in dismissedPackages })
        }
    }

    fun endTask(packageName: String, pid: Int = -1, onComplete: (String) -> Unit = {}) {
        if (packageName.isNotBlank()) {
            dismissedPackages.add(packageName)
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.endTask(packageName, pid)
            withContext(Dispatchers.Main) {
                runningProcesses.removeAll { it.packageName == packageName || (pid > 0 && it.pid == pid) }
                onComplete(packageName)
            }
        }
    }

    fun endAllTasks(onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val myPkg = getApplication<Application>().packageName
            val toKill = runningProcesses.filter { it.packageName.isNotBlank() && it.packageName != myPkg }
            toKill.forEach { process ->
                if (process.packageName.isNotBlank()) {
                    dismissedPackages.add(process.packageName)
                }
                repository.endTask(process.packageName, process.pid)
            }
            withContext(Dispatchers.Main) {
                runningProcesses.removeAll { it.packageName != myPkg && it.packageName.isNotBlank() }
                onComplete()
            }
        }
    }
}
