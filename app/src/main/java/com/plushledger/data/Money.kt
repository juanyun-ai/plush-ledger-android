package com.plushledger.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object Money {
    fun parseToMinor(raw: String): Long? {
        val normalized = raw.trim().replace(",", "")
        if (normalized.isBlank()) return null
        return runCatching {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()?.takeIf { it > 0 }
    }

    fun formatCny(minor: Long, signed: Boolean = false): String {
        val amount = BigDecimal(minor).movePointLeft(2)
        val formatted = NumberFormat.getCurrencyInstance(Locale.CHINA).format(amount)
        return if (signed && minor > 0) "+$formatted" else formatted
    }
}

data class LedgerSummary(
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val balanceMinor: Long = 0,
    val budgetLimitMinor: Long = 0,
    val budgetUsedMinor: Long = 0
)

data class CategorySpend(
    val category: CategoryEntity,
    val amountMinor: Long
)
