package com.sconcept.mirrordash.mirrordrop

import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** How long a share session stays valid without the user touching it - a safety net against a
 * mirror left "sharing" indefinitely after everyone's gone home (brief §6/§29), not a hard
 * security boundary (the random [ShareSession.token] is that). */
private const val SESSION_TTL_MS = 2 * 60 * 60 * 1000L
private const val TOKEN_RANDOM_BYTES = 18
private const val PIN_LENGTH = 6

data class ShareSession(
    val token: String,
    val pinHash: String?,
    val createdAtMs: Long,
    val expiresAtMs: Long,
)

enum class ShareAuthResult { OK, NO_ACTIVE_SESSION, INVALID_TOKEN, EXPIRED, PIN_REQUIRED, INVALID_PIN }

/**
 * Owns the one active [ShareSession] at a time (brief §6/§43): a cryptographically random opaque
 * token (never derived from anything guessable), an optional PIN (stored hashed, brief §7 - a
 * casual second factor for "someone screenshotted the QR from across the room", not the primary
 * security boundary), and immediate revocation. [authorize] is the single choke point every
 * incoming `/signal` WebSocket connection passes through (see [MirrorDropServer]) - nothing else
 * in MirrorDrop needs to know about sessions at all.
 */
class MirrorDropShareSessionManager {

    private val secureRandom = SecureRandom()
    private val _session = MutableStateFlow<ShareSession?>(null)
    val session: StateFlow<ShareSession?> = _session

    fun start(requirePin: Boolean): MirrorDropShareStartResult {
        val now = System.currentTimeMillis()
        val pin = if (requirePin) generatePin() else null
        val newSession = ShareSession(
            token = generateToken(),
            pinHash = pin?.let { hash(it) },
            createdAtMs = now,
            expiresAtMs = now + SESSION_TTL_MS,
        )
        _session.value = newSession
        return MirrorDropShareStartResult(newSession, pin)
    }

    /** Only callable right after [start] returns the same session - the plaintext PIN is never
     * stored, so it can only be surfaced once, for the QR/share screen to display alongside it. */
    fun revoke() {
        _session.value = null
    }

    fun authorize(token: String?, pin: String?): ShareAuthResult {
        val current = _session.value ?: return ShareAuthResult.NO_ACTIVE_SESSION
        if (token.isNullOrBlank() || token != current.token) return ShareAuthResult.INVALID_TOKEN
        if (System.currentTimeMillis() > current.expiresAtMs) return ShareAuthResult.EXPIRED
        val requiredHash = current.pinHash
        if (requiredHash != null) {
            if (pin.isNullOrBlank()) return ShareAuthResult.PIN_REQUIRED
            if (hash(pin) != requiredHash) return ShareAuthResult.INVALID_PIN
        }
        return ShareAuthResult.OK
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_RANDOM_BYTES)
        secureRandom.nextBytes(bytes)
        // android.util.Base64, not java.util.Base64 - the latter doesn't exist below API 26 and
        // this app's minSdk (and the actual Echelon/Rockchip reference hardware) is API 25; using
        // it here crashed with NoClassDefFoundError the moment a real device tried to share.
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
    }

    private fun generatePin(): String {
        val value = secureRandom.nextInt(1_000_000)
        return value.toString().padStart(PIN_LENGTH, '0')
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Convenience pairing of a freshly [MirrorDropShareSessionManager.start]-ed session with the
 * plaintext PIN (if any) - only ever held transiently by the caller that just created it. */
data class MirrorDropShareStartResult(val session: ShareSession, val plaintextPin: String?)
