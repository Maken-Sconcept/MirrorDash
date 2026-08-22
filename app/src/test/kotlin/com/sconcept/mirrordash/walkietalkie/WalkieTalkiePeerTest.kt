package com.sconcept.mirrordash.walkietalkie

import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkiePeer
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkieTalkiePeerTest {

    @Test
    fun `initials use the first two words for room names`() {
        assertEquals("BG", WalkieTalkiePeer(name = "Basement Gym", ip = "192.168.1.24").initials())
    }

    @Test
    fun `initials are concise for one-word device names`() {
        assertEquals("GA", WalkieTalkiePeer(name = "Garage", ip = "192.168.1.18").initials())
    }
}
