package com.plushledger.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNormalizationTest {
    @Test
    fun mainlandMobileDefaultsToChinaCountryCode() {
        assertEquals("+8613812345678", normalizePhoneNumber("138 1234 5678"))
    }

    @Test
    fun explicitE164NumberIsPreserved() {
        assertEquals("+85261234567", normalizePhoneNumber("+852 6123 4567"))
    }

    @Test
    fun internationalDialPrefixBecomesE164() {
        assertEquals("+447911123456", normalizePhoneNumber("0044 7911 123456"))
    }
}
