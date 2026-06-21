package com.plushledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

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

    private fun parse(text: String): AiLedgerAnalysis =
        requireNotNull(LocalAiLedgerParser.parse(text, categories, emptyList()))
}
