package com.sconcept.mirrordash.clock

import kotlinx.serialization.Serializable

/** A draggable rotating-headline ticker on the Clock page, fed by an arbitrary user-supplied
 * RSS/Atom [feedUrl] via [com.sconcept.mirrordash.news.NewsFeedRepository]. */
@Serializable
data class NewsWidget(
    override val id: String,
    val feedUrl: String = "",
    val itemCount: Int = 5,
    val fontSizeSp: Int = 18,
    val fontId: String = CLOCK_FONT_SYSTEM_DEFAULT,
    val colorArgb: Int = 0xFFF5F3EF.toInt(),
    override val anchorX: Float = 0.5f,
    override val anchorY: Float = 0.92f,
    override val rotationDegrees: Float = 0f,
) : AnchoredWidget

fun defaultNewsWidget(
    id: String = java.util.UUID.randomUUID().toString(),
    anchorX: Float = 0.5f,
    anchorY: Float = 0.92f,
): NewsWidget = NewsWidget(id = id, anchorX = anchorX, anchorY = anchorY)
