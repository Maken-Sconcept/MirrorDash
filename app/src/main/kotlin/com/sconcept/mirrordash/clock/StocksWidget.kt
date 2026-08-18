package com.sconcept.mirrordash.clock

import kotlinx.serialization.Serializable

/** A draggable stock ticker on the Clock page - one row per symbol, price + change fetched by
 * [com.sconcept.mirrordash.stocks.StocksRepository]. */
@Serializable
data class StocksWidget(
    override val id: String,
    val symbols: List<String> = emptyList(),
    val fontSizeSp: Int = 18,
    val fontId: String = CLOCK_FONT_SYSTEM_DEFAULT,
    val colorArgb: Int = 0xFFF5F3EF.toInt(),
    override val anchorX: Float = 0.94f,
    override val anchorY: Float = 0.6f,
    override val rotationDegrees: Float = 0f,
) : AnchoredWidget

fun defaultStocksWidget(
    id: String = java.util.UUID.randomUUID().toString(),
    anchorX: Float = 0.94f,
    anchorY: Float = 0.6f,
): StocksWidget = StocksWidget(id = id, anchorX = anchorX, anchorY = anchorY)
