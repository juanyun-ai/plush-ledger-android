package com.plushledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AiLedgerParserTest {
    private val categories = CategoryCatalog.defaultCategories("test-user", "test-book", 0L)

    @Test
    fun recognizesDesignedExpenseSubcategoriesBeforeGenericFallback() {
        assertEquals("奶茶咖啡", parse("下午买奶茶 18 元，用微信").categoryLabel)
        assertEquals("正餐", parse("午饭花了 28 元").categoryLabel)
        assertEquals("公交地铁", parse("坐地铁 4 元").categoryLabel)
        assertEquals("水电房租", parse("交房租 2600 元").categoryLabel)
        assertEquals("人情红包", parse("随礼红包 200 元").categoryLabel)
    }

    @Test
    fun fallsBackToCurrentOtherCategoryInsteadOfLeavingExpenseUncategorized() {
        val analysis = parse("买了一个说不清用途的东西 38 元")

        assertEquals("未分类", analysis.categoryLabel)
        assertNotNull(analysis.categoryId)
    }

    @Test
    fun recognizesIncomeCategories() {
        assertEquals("工资", parse("工资到账 8000 元").categoryLabel)
        assertEquals("兼职", parse("兼职稿费收到 300 元").categoryLabel)
        assertEquals("理财", parse("理财收益 15.6 元").categoryLabel)
    }

    @Test
    fun keepsExplicitCategoryAheadOfModelGuess() {
        val category = LocalAiLedgerParser.resolveCategory(
            type = "expense",
            categoryName = "早餐",
            parentName = "餐饮",
            categories = categories,
            sourceText = "请归类到奶茶咖啡，瑞幸 15 元"
        )

        assertEquals("奶茶咖啡", category?.name)
    }

    @Test
    fun parsesArbitraryDateWithoutTreatingDateAsAmount() {
        val analysis = parse("15元买瑞幸咖啡，记到2025年12月31日，微信支付")
        val date = Instant.ofEpochMilli(analysis.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

        assertEquals(1_500L, analysis.amountMinor)
        assertEquals(LocalDate.of(2025, 12, 31), date)
        assertEquals("奶茶咖啡", analysis.categoryLabel)
    }

    @Test
    fun parsesRelativeAndNumericDates() {
        val daysAgo = parse("3天前打车 26 元")
        val numeric = parse("2024/2/29 买书 45 元")

        assertEquals(LocalDate.now().minusDays(3), daysAgo.localDate())
        assertEquals(LocalDate.of(2024, 2, 29), numeric.localDate())
    }

    @Test
    fun keepsRelativeDateAndRecognizesRentalIncome() {
        val yesterday = parse("昨天吃了一坨屎，花了10块钱")
        val rental = parse("6月7日，房屋转租收入1200块，微信支付")

        assertEquals(LocalDate.now().minusDays(1), yesterday.localDate())
        assertEquals(LocalDate.of(LocalDate.now().year, 6, 7), rental.localDate())
        assertEquals("income", rental.type)
        assertEquals("房屋", rental.categoryLabel)
        assertEquals(120_000L, rental.amountMinor)
    }

    @Test
    fun splitsOneSentenceIntoMultipleLedgerEntries() {
        val entries = LocalAiLedgerParser.parseAll("昨天瑞幸咖啡15元微信支付，今天地铁4元现金", categories, emptyList())

        assertEquals(2, entries.size)
        assertEquals("奶茶咖啡", entries[0].categoryLabel)
        assertEquals(1_500L, entries[0].amountMinor)
        assertEquals(LocalDate.now().minusDays(1), entries[0].localDate())
        assertEquals("公交地铁", entries[1].categoryLabel)
        assertEquals(400L, entries[1].amountMinor)
        assertEquals(LocalDate.now(), entries[1].localDate())
    }

    private fun AiLedgerAnalysis.localDate(): LocalDate =
        Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun parse(text: String): AiLedgerAnalysis =
        requireNotNull(LocalAiLedgerParser.parse(text, categories, emptyList()))
}
