package com.sconcept.mirrordash.nas.model

import com.sconcept.mirrordash.nas.SmbPaths

data class SmbShare(
    val host: String,
    val share: String,
    val username: String,
    val password: String,
    val domain: String,
) {
    val isConfigured get() = host.isNotBlank() && share.isNotBlank()

    /** smb://host/share/ */
    fun rootUrl() = SmbPaths.displayPath(this) + "/"

    fun displayPath(relativePath: String = "") = SmbPaths.displayPath(this, relativePath)

    companion object {
        val EMPTY = SmbShare(host = "", share = "", username = "", password = "", domain = "")
    }
}

data class SmbFileItem(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

/** A single supported image file discovered under the configured Photorama folder. */
data class LanPhoto(
    val name: String,
    val url: String,
    val sizeBytes: Long,
)

enum class SmbConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATION_FAILED,
    SERVER_UNREACHABLE,
    SHARE_UNAVAILABLE,
    OFFLINE,
    RECONNECTING,
}

/** Result of an SMB connect/list attempt: either the data, or a state + a human-readable
 * message plus the raw technical detail (for advanced/debug display only). */
sealed class SmbResult<out T> {
    data class Success<T>(val value: T) : SmbResult<T>()
    data class Failure(
        val state: SmbConnectionState,
        val message: String,
        val technicalDetail: String? = null,
    ) : SmbResult<Nothing>()
}
