package com.sconcept.mirrordash.iptv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * This portal allows exactly one active session per MAC - confirmed live against edge.bz: a
 * second `handshake` for the same MAC silently kills the first (its token stops working, every
 * subsequent request against it comes back empty). [IptvViewModel] and [IptvRecordingEngine] used
 * to each open their own [StalkerPortalClient], which meant starting a recording would silently
 * kill whatever the live view was doing, or vice versa.
 *
 * When [MirrorDashSettings.iptvRecordingMacAddress] is set, recording avoids this entirely - a
 * different MAC is a genuinely separate session slot on the portal, so it never needs this
 * coordinator at all. This exists for when it's *not* set, so both sides share one token instead
 * of fighting over it: whoever asks first creates the session, whoever asks next (same portal,
 * same MAC) reuses it - no new handshake, nothing gets killed. A fresh handshake only happens when
 * reuse genuinely isn't possible (nothing alive yet, or the config changed), which may still take
 * over the single slot - that's an inherent limit of a one-connection account, not a bug.
 *
 * Ref-counted rather than single-owner: [IptvViewModel] can be SLEEPING (holding its share so
 * resuming is instant) at the same time a scheduled recording is using the same session - the
 * client is only actually dropped once nobody holds a share anymore.
 */
class IptvSessionCoordinator private constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var client: StalkerPortalClient? = null
    private var configKey: String? = null
    private var refCount = 0

    suspend fun acquire(portalUrl: String, macAddress: String): Result<StalkerPortalClient> = mutex.withLock {
        val key = "$portalUrl|$macAddress"
        val existing = client
        if (existing != null && configKey == key) {
            refCount++
            return@withLock Result.success(existing)
        }
        val newClient = StalkerPortalClient(portalUrl, macAddress)
        val result = newClient.connect()
        if (result.isSuccess) {
            client = newClient
            configKey = key
            refCount++
        }
        result.map { newClient }
    }

    /** Fire-and-forget by design - callers include [androidx.lifecycle.ViewModel]'s non-suspend
     * `onCleared()`, so this can't itself be suspend without every call site needing its own
     * throwaway coroutine. */
    fun release() {
        scope.launch {
            mutex.withLock {
                if (refCount > 0) refCount--
                if (refCount == 0) {
                    client = null
                    configKey = null
                }
            }
        }
    }

    /** Hard reset, not a share release - for when the portal URL/MAC in Settings actually
     * changed, so whatever token was cached is for the wrong config regardless of who still
     * thinks they're holding a share of it. */
    fun invalidate() {
        scope.launch {
            mutex.withLock {
                client = null
                configKey = null
                refCount = 0
            }
        }
    }

    companion object {
        @Volatile
        private var instance: IptvSessionCoordinator? = null

        fun get(): IptvSessionCoordinator =
            instance ?: synchronized(this) {
                instance ?: IptvSessionCoordinator().also { instance = it }
            }
    }
}
