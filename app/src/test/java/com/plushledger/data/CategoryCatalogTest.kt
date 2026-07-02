package com.plushledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCatalogTest {
    @Test
    fun expenseRootsExposeEveryDesignedSubcategory() {
        val categories = CategoryCatalog.defaultCategories("user", "book", 0)
        val roots = CategoryCatalog.rootCategories(categories, "expense")
        val namesByRoot = roots.associate { root ->
            root.name to CategoryCatalog.childrenOf(categories, root.id).map { it.name }
        }

        assertEquals(10, roots.size)
        assertEquals(listOf("早餐", "午餐", "晚餐", "外卖", "咖啡", "零食", "聚餐"), namesByRoot["餐饮"])
        assertTrue(namesByRoot["交通"].orEmpty().contains("单车月卡"))
        assertTrue(namesByRoot["人情社交"].orEmpty().contains("社交活动"))
        assertEquals(listOf("临时支出", "杂项备用", "未分类"), namesByRoot["其他"])
    }

    @Test
    fun oldUnknownCategoryNameMapsToNewLabel() {
        assertEquals(setOf("无法归类"), CategoryCatalog.legacyNamesFor("expense.other.unknown"))
    }
}
