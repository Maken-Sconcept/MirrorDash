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
        return try {
            val process = ProcessBuilder(commandLine).redirectErrorStream(true).start()
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
        }
    }
}
