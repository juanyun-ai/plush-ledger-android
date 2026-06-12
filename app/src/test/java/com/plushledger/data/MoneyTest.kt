package com.plushledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun parseToMinorKeepsTwoDecimals() {
        assertEquals(1288L, Money.parseToMinor("12.88"))
        assertEquals(1200L, Money.parseToMinor("12"))
        assertEquals(123456L, Money.parseToMinor("1,234.56"))
    }

    @Test
    fun parseToMinorRejectsInvalidOrZero() {
        assertNull(Money.parseToMinor(""))
        assertNull(Money.parseToMinor("abc"))
        assertNull(Money.parseToMinor("0"))
    }
}
