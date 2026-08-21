package com.sconcept.mirrordash.gym

import org.junit.Assert.assertEquals
import org.junit.Test

class GymAdaptiveLayoutTest {
    @Test fun `portrait remains portrait even on a tablet`() {
        assertEquals(GymLayoutTier.PORTRAIT, gymLayoutTier(widthDp = 800, heightDp = 1280))
    }

    @Test fun `short landscape windows use the compact tier`() {
        assertEquals(GymLayoutTier.COMPACT_LANDSCAPE, gymLayoutTier(widthDp = 760, heightDp = 420))
    }

    @Test fun `tablet landscape uses the medium tier`() {
        assertEquals(GymLayoutTier.MEDIUM_LANDSCAPE, gymLayoutTier(widthDp = 1024, heightDp = 700))
    }

    @Test fun `large mirror uses the expanded tier`() {
        assertEquals(GymLayoutTier.EXPANDED_LANDSCAPE, gymLayoutTier(widthDp = 1920, heightDp = 1080))
    }
}
