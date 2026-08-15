package com.sconcept.mirrordash.brightness

import android.util.Log
import java.util.concurrent.TimeUnit

private const val TAG = "RootShell"
private const val DEFAULT_TIMEOUT_MS = 1500L

/**
 * A minimal `su` wrapper, ported from BerthierOptions' `RootShell` - but where that one only ever
 * tried `su -c "<command>"`, this tries that form AND `su 0 <command...>` per candidate binary,
 * since the actual su on this hardware family's test unit rejects `-c` outright
 * ("su: invalid uid/gid '-c'") and only understands `su <uid> <command...>`. Root is a best-effort
 * layer everywhere it's used (see [BacklightController]) - a failure here just means that layer
 * is silently skipped, never a crash.
 */
object RootShell {
    private val rootBinaryCandidates = listOf(
        "/system/xbin/su",
        "/system/bin/su",
        "/sbin/su",
        "/su/bin/su",
        "su",
    )

    fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
        for (su in rootBinaryCandidates) {
            if (tryRun(listOf(su, "-c", command), timeoutMs)) return true
            if (tryRun(listOf(su, "0", "sh", "-c", command), timeoutMs)) return true
        }
        return false
    }

    private fun tryRun(commandLine: List<String>, timeoutMs: Long): Boolean {
        var process: Process? = null
        return try {
            process = ProcessBuilder(commandLine).redirectErrorStream(true).start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "root command failed via $commandLine", e)
            false
        } finally {
            // Process.start() opens real pipe file descriptors for stdin/stdout(/stderr) that are
            // only closed by GC finalization otherwise - on an 8+ candidate x 2-form fallback list
            // (see [run]), a settings change that needs root can spawn a dozen+ of these per call,
            // and leaked FDs accumulate over a long-running session instead of being reclaimed
            // promptly.
            process?.let {
                runCatching { it.outputStream.close() }
                runCatching { it.inputStream.close() }
                runCatching { it.errorStream.close() }
            }
        }
    }
}
