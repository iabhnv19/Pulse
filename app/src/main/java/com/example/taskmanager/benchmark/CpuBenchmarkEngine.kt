package com.example.taskmanager.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.sin
import kotlin.math.sqrt

data class CpuBenchmarkResult(
    val singleScore: Int,
    val multiScore: Int,
    val scalingRatio: Float,
    val numCores: Int,
    val durationSec: Double,
    val rating: String,
    val summary: String
)

class CpuBenchmarkEngine {

    suspend fun runBenchmark(
        onProgress: suspend (phase: String, progress: Float) -> Unit
    ): CpuBenchmarkResult = withContext(Dispatchers.Default) {
        val totalStart = System.nanoTime()
        val numCores = Runtime.getRuntime().availableProcessors()

        // ══════════════════════════════════════════════════════════════
        //  PHASE 1: SINGLE-CORE BENCHMARK (~3.5 seconds)
        // ══════════════════════════════════════════════════════════════
        onProgress("Single-Core: Initializing stress workload…", 0.05f)
        val targetSingleDurationMs = 3500L
        val singleStart = System.currentTimeMillis()
        var singleOps = 0L

        while (System.currentTimeMillis() - singleStart < targetSingleDurationMs) {
            runWorkloadUnit()
            singleOps++
            val elapsed = System.currentTimeMillis() - singleStart
            val p = (elapsed.toFloat() / targetSingleDurationMs).coerceIn(0f, 1f)
            onProgress(
                "Single-Core: Math, Primes & Cryptography ($singleOps ops)",
                0.05f + p * 0.42f
            )
        }

        val actualSingleDuration = (System.currentTimeMillis() - singleStart).coerceAtLeast(1)
        val singleOpsPerSec = (singleOps * 1000.0) / actualSingleDuration
        // Normalized score baseline: ~10 ops/sec = 100 pts, typical modern phone = 800 - 1600 pts
        val singleScore = (singleOpsPerSec * 9.2).toInt().coerceAtLeast(100)

        onProgress("Single-Core score: $singleScore. Preparing Multi-Core…", 0.50f)
        delay(300) // Brief cool-down between passes

        // ══════════════════════════════════════════════════════════════
        //  PHASE 2: MULTI-CORE BENCHMARK (~3.5 seconds across all cores)
        // ══════════════════════════════════════════════════════════════
        val targetMultiDurationMs = 3500L
        val multiStart = System.currentTimeMillis()

        onProgress("Multi-Core: Launching parallel threads across $numCores cores…", 0.52f)

        val coreResults = coroutineScope {
            val deferreds = (0 until numCores).map { coreIndex ->
                async(Dispatchers.Default) {
                    var ops = 0L
                    while (System.currentTimeMillis() - multiStart < targetMultiDurationMs && isActive) {
                        runWorkloadUnit()
                        ops++
                    }
                    ops
                }
            }

            // Progress updater job
            val progressJob = async(Dispatchers.Default) {
                while (System.currentTimeMillis() - multiStart < targetMultiDurationMs && isActive) {
                    val elapsed = System.currentTimeMillis() - multiStart
                    val p = (elapsed.toFloat() / targetMultiDurationMs).coerceIn(0f, 1f)
                    onProgress(
                        "Multi-Core: $numCores cores parallel compute (${(elapsed / 1000.0).format1()}s / 3.5s)",
                        0.52f + p * 0.45f
                    )
                    delay(150)
                }
            }

            val totalPerCore = deferreds.awaitAll()
            progressJob.cancel()
            totalPerCore
        }

        val totalMultiOps = coreResults.sum()
        val actualMultiDuration = (System.currentTimeMillis() - multiStart).coerceAtLeast(1)
        val multiOpsPerSec = (totalMultiOps * 1000.0) / actualMultiDuration
        val multiScore = (multiOpsPerSec * 9.2).toInt().coerceAtLeast(singleScore)

        val scaling = multiScore.toFloat() / singleScore.coerceAtLeast(1)
        val totalDurationSec = (System.nanoTime() - totalStart) / 1_000_000_000.0

        val rating = when {
            multiScore >= 4500 -> "Flagship Grade"
            multiScore >= 3000 -> "High Performance"
            multiScore >= 1800 -> "Mid-Range"
            else -> "Entry Level"
        }

        val summary = buildString {
            appendLine("Single-Core: $singleScore pts  |  Multi-Core: $multiScore pts")
            appendLine(String.format(Locale.US, "Scaling: %.2fx across %d cores  •  %s", scaling, numCores, rating))
            append(String.format(Locale.US, "Duration: %.1fs  (Stress test: Primes, SHA-256, Matrix)", totalDurationSec))
        }

        onProgress("Benchmark Complete", 1.0f)

        CpuBenchmarkResult(
            singleScore = singleScore,
            multiScore = multiScore,
            scalingRatio = scaling,
            numCores = numCores,
            durationSec = totalDurationSec,
            rating = rating,
            summary = summary
        )
    }

    /**
     * Balanced CPU stress unit:
     * 1) Sieve of Eratosthenes prime search up to 80,000 (Integer + memory cache)
     * 2) SHA-256 block hashing (Cryptographic / bitwise ops)
     * 3) Matrix multiplication 64x64 floats (Floating point arithmetic)
     */
    private fun runWorkloadUnit() {
        // 1. Prime sieve up to 80,000
        val maxPrime = 80000
        val isPrime = BooleanArray(maxPrime + 1) { true }
        isPrime[0] = false
        isPrime[1] = false
        val limit = sqrt(maxPrime.toDouble()).toInt()
        for (p in 2..limit) {
            if (isPrime[p]) {
                var k = p * p
                while (k <= maxPrime) {
                    isPrime[k] = false
                    k += p
                }
            }
        }

        // 2. Cryptographic hashing: 16 iterations of SHA-256 over 2 KB buffer
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(2048) { (it xor 0x5A).toByte() }
        for (r in 0 until 16) {
            buffer[0] = (buffer[0] + r).toByte()
            val hash = md.digest(buffer)
            System.arraycopy(hash, 0, buffer, 0, minOf(hash.size, buffer.size))
        }

        // 3. Matrix multiplication 48x48 floats
        val size = 48
        val a = FloatArray(size * size) { (it % 17).toFloat() }
        val b = FloatArray(size * size) { (it % 23).toFloat() }
        val c = FloatArray(size * size)
        for (i in 0 until size) {
            val iOffset = i * size
            for (k in 0 until size) {
                val aik = a[iOffset + k]
                val kOffset = k * size
                for (j in 0 until size) {
                    c[iOffset + j] += aik * b[kOffset + j]
                }
            }
        }
    }

    private fun Double.format1(): String = String.format(Locale.US, "%.1f", this)
}
