package com.sconcept.mirrordash.gym

/**
 * Presentation tiers for the Gym surface. They deliberately consider usable dimensions as
 * well as orientation: a short, split-screen landscape phone must not receive the mirror UI.
 */
internal enum class GymLayoutTier {
    PORTRAIT,
    COMPACT_LANDSCAPE,
    MEDIUM_LANDSCAPE,
    EXPANDED_LANDSCAPE,
}

internal fun gymLayoutTier(
    widthDp: Int,
    heightDp: Int,
): GymLayoutTier = when {
    widthDp <= heightDp -> GymLayoutTier.PORTRAIT
    heightDp < 480 || widthDp < 700 -> GymLayoutTier.COMPACT_LANDSCAPE
    widthDp >= 1_200 && heightDp >= 650 -> GymLayoutTier.EXPANDED_LANDSCAPE
    else -> GymLayoutTier.MEDIUM_LANDSCAPE
}

internal val GymLayoutTier.isLandscape: Boolean
    get() = this != GymLayoutTier.PORTRAIT

internal val GymLayoutTier.sessionControlPaneWidthDp: Int
    get() = when (this) {
        GymLayoutTier.COMPACT_LANDSCAPE -> 248
        GymLayoutTier.MEDIUM_LANDSCAPE -> 304
        GymLayoutTier.EXPANDED_LANDSCAPE -> 356
        GymLayoutTier.PORTRAIT -> 0
    }
