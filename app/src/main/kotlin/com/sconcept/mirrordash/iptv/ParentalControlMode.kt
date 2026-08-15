package com.sconcept.mirrordash.iptv

/** How often [IptvViewModel] re-locks channels/genres the portal marks `censored` (see
 * [StalkerChannel.censored]) - entirely enforced client-side, since the portal itself doesn't
 * check any PIN. */
enum class ParentalControlMode(val storageKey: String) {
    DISABLED("disabled"),

    /** Unlocked for as long as the tab's portal session stays alive - re-locks on a full
     * [IptvViewModel] teardown (OFF), but *not* on just sleeping and resuming. */
    ONCE_PER_SESSION("once_per_session"),

    /** Re-locks every time the tab is left, even briefly - any SLEEPING transition, not just a
     * full teardown. The stricter of the two enabled modes. */
    EVERY_REJOIN("every_rejoin"),
    ;

    companion object {
        fun fromStorageKey(key: String): ParentalControlMode = entries.firstOrNull { it.storageKey == key } ?: DISABLED
    }
}

const val DEFAULT_PARENTAL_CONTROL_PIN = "0000"
const val MAX_PARENTAL_CONTROL_PIN_LENGTH = 6
