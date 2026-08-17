package com.sconcept.mirrordash.wedding

import org.junit.Assert.assertEquals
import org.junit.Test

class WeddingProfileTest {

    @Test
    fun coupleDisplayName_joinsTwoConfiguredNames() {
        val profile = WeddingProfile(partnerOne = "Amélie", partnerTwo = "Jonathan")

        assertEquals("Amélie & Jonathan", profile.coupleDisplayName)
    }

    @Test
    fun coupleDisplayName_handlesOneOrNoConfiguredNames() {
        assertEquals("Amélie", WeddingProfile(partnerOne = " Amélie ").coupleDisplayName)
        assertEquals("", WeddingProfile().coupleDisplayName)
    }
}
