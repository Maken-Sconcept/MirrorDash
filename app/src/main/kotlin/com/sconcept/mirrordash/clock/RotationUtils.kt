package com.sconcept.mirrordash.clock

/** Wraps any rotation value into [0, 360) so persisted/displayed degrees never drift into
 * negative numbers or multi-turn values (e.g. 720°) even though the drag gesture that produces
 * them tracks unbounded angle deltas. */
internal fun normalizeRotationDegrees(degrees: Float): Float {
    val wrapped = degrees % 360f
    return if (wrapped < 0f) wrapped + 360f else wrapped
}
