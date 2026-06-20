package com.plushledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.plushledger.data.CategoryEntity
import com.plushledger.data.LedgerState
import com.plushledger.data.LedgerSummary
import com.plushledger.data.TransactionEntity
import java.time.LocalDate
import java.time.ZoneId

private val previewDate = LocalDate.of(2026, 6, 12)
private val previewCategories = listOf(
    CategoryEntity("food", "preview", "book", "餐饮", "expense", "#FF9F2D", "food", 1, 0, 0),
    CategoryEntity("traffic", "preview", "book", "交通", "expense", "#69C69E", "bus", 2, 0, 0),
    CategoryEntity("salary", "preview", "book", "工资", "income", "#82AEE8", "work", 3, 0, 0)
)
private val previewTransactions = listOf(
    TransactionEntity("t1", "preview", "book", "expense", 3250, categoryId = "food", accountId = "cash", note = "午餐", occurredAt = previewDate.atTime(12, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), createdAt = 0, updatedAt = 0),
    TransactionEntity("t2", "preview", "book", "expense", 1850, categoryId = "traffic", accountId = "cash", note = "地铁", occurredAt = previewDate.atTime(8, 15).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), createdAt = 0, updatedAt = 0),
    TransactionEntity("t3", "preview", "book", "income", 368000, categoryId = "salary", accountId = "cash", note = "六月工资", occurredAt = previewDate.minusDays(2).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), createdAt = 0, updatedAt = 0)
)
private val previewLedger = LedgerState(
    categories = previewCategories,
    transactions = previewTransactions,
    summary = LedgerSummary(incomeMinor = 368000, expenseMinor = 5100, balanceMinor = 362900)
)

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    PlushLedgerTheme(false) { content() }
}

@Preview(name = "首页", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomePreview() = PreviewFrame {
    HomeScreen(previewLedger, previewDate, {}, {}, {}, {}, {}, null, false, {}, {}, {})
}

@Preview(name = "账单", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun BillsPreview() = PreviewFrame {
    BillsScreen(previewLedger, previewDate, {}, {}, {})
}

@Preview(name = "统计", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun StatsPreview() = PreviewFrame {
    StatsScreen(previewLedger, previewDate, {}, {})
}
