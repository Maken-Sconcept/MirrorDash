package com.sconcept.mirrordash.nas

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.sconcept.mirrordash.nas.model.SmbConnectionState
import com.sconcept.mirrordash.nas.model.SmbFileItem
import com.sconcept.mirrordash.nas.model.SmbResult
import com.sconcept.mirrordash.nas.model.SmbShare
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import java.io.InputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Repository facade over [SmbClient]: adds network-availability short-circuiting and
 * translates low-level jcifs/network exceptions into a [SmbConnectionState] plus a message
 * fit for display. Ported from BerthierOptions with only the `ensureBackgroundThread` (Fossify)
 * dependency removed - callers now dispatch to Dispatchers.IO themselves via coroutines. All
 * functions here still perform blocking I/O and must be called off the main thread.
 */
class SmbRepository(context: Context) {

    private val appContext = context.applicationContext

    private fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun testConnection(share: SmbShare): SmbResult<Unit> = runCatching(share) {
        SmbClient.testConnection(share)
    }

    fun list(share: SmbShare, path: String): SmbResult<List<SmbFileItem>> = runCatching(share) {
        SmbClient.list(share, path)
    }

    /** Same as [list] but with files filtered out - used by the folder picker. */
    fun listDirectories(share: SmbShare, path: String): SmbResult<List<SmbFileItem>> {
        return when (val result = list(share, path)) {
            is SmbResult.Success -> SmbResult.Success(result.value.filter { it.isDirectory })
            is SmbResult.Failure -> result
        }
    }

    @Throws(Exception::class)
    fun openStream(share: SmbShare, url: String): InputStream = SmbClient.openStream(share, url)

    @Throws(Exception::class)
    fun openOutputStream(share: SmbShare, path: String): java.io.OutputStream = SmbClient.openOutputStream(share, path)

    @Throws(Exception::class)
    fun freeSpaceBytes(share: SmbShare): Long = SmbClient.freeSpaceBytes(share)

    private fun <T> runCatching(share: SmbShare, block: () -> T): SmbResult<T> {
        if (!isNetworkAvailable()) {
            return SmbResult.Failure(
                SmbConnectionState.OFFLINE,
                "The device is not currently connected to the network.",
            )
        }
        return try {
            SmbResult.Success(block())
        } catch (e: Exception) {
            val (state, message) = translate(e)
            Log.e("SmbRepository", "SMB operation failed: $message")
            SmbResult.Failure(state, message, e.javaClass.simpleName + ": " + e.message)
        }
    }

    private fun translate(e: Exception): Pair<SmbConnectionState, String> {
        return when {
            e is SmbAuthException ->
                SmbConnectionState.AUTHENTICATION_FAILED to "Incorrect username or password."

            e is SocketTimeoutException ->
                SmbConnectionState.SERVER_UNREACHABLE to "The connection timed out."

            e is UnknownHostException || e is ConnectException || e is NoRouteToHostException ->
                SmbConnectionState.SERVER_UNREACHABLE to "Unable to reach this server."

            e is SmbException && containsAny(e.message, "BAD_NETWORK_NAME", "OBJECT_PATH_NOT_FOUND", "OBJECT_NAME_NOT_FOUND") ->
                SmbConnectionState.SHARE_UNAVAILABLE to "The SMB share could not be found."

            e is SmbException && containsAny(e.message, "ACCESS_DENIED", "LOGON_FAILURE") ->
                SmbConnectionState.AUTHENTICATION_FAILED to "Incorrect username or password."

            else ->
                SmbConnectionState.SERVER_UNREACHABLE to "Unable to reach this server."
        }
    }

    private fun containsAny(message: String?, vararg needles: String): Boolean {
        if (message == null) return false
        return needles.any { message.contains(it, ignoreCase = true) }
    }
}
