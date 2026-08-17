package com.sconcept.mirrordash.wedding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeddingPinPolicyTest {

    @Test
    fun sanitize_keepsOnlyDigitsAndCapsLength() {
        assertEquals("12345678", WeddingPinPolicy.sanitize("12a34-567890"))
    }

    @Test
    fun validPin_acceptsConfiguredLengthRange() {
        assertTrue(WeddingPinPolicy.isValid("1234"))
        assertTrue(WeddingPinPolicy.isValid("12345678"))
    }

    @Test
    fun validPin_rejectsShortLongAndNonNumericValues() {
        assertFalse(WeddingPinPolicy.isValid("123"))
        assertFalse(WeddingPinPolicy.isValid("123456789"))
        assertFalse(WeddingPinPolicy.isValid("12a4"))
    }
}
