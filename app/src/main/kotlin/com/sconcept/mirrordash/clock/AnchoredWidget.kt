package com.sconcept.mirrordash.clock

/** Common shape shared by every draggable Clock-page widget type (weather, text, calendar,
 * tasks, stocks, news) - just enough for [ClockLayoutHelper]/[DraggableAnchor]'s generic
 * placement math and [WidgetOverlayLayer]'s generic rendering loop to work off one interface
 * instead of each widget list needing its own hand-written render block. */
interface AnchoredWidget {
    val id: String
    val anchorX: Float
    val anchorY: Float
    val rotationDegrees: Float
}
