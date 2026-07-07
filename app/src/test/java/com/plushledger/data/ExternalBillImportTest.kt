package com.plushledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExternalBillImportTest {
    @Test
    fun parsesSuccessfulWechatBillAndSkipsRefund() {
        val csv = """
            微信支付账单明细
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,备注
            2026-06-20 12:30:00,商户消费,咖啡店,冰美式,支出,18.50,零钱,支付成功,WX2026062001,下午提神
            2026-06-19 09:00:00,退款,咖啡店,冰美式,收入,18.50,零钱,已退款,WX2026061901,
        """.trimIndent()

        val preview = ExternalBillCsvParser.parse("微信", csv)

        assertEquals("微信", preview.provider)
        assertEquals(1, preview.entries.size)
        assertEquals(1, preview.skippedRows)
        assertEquals("expense", preview.entries.single().type)
        assertEquals(1_850L, preview.entries.single().amountMinor)
        assertEquals("WX2026062001", preview.entries.single().sourceId)
    }

    @Test
    fun parsesRongrongMiniProgramCsvBackup() {
        val csv = """
            日期,时间,类型,金额,分类路径,备注,账户,id
            2026-07-07,09:18,支出,12.50,餐饮/早餐,小程序早餐,现金,mini-001
            2026-07-07,10:00,收入,4.00,收入/退税退费,顺风车退费,支付宝,mini-002
        """.trimIndent()

        val preview = ExternalBillCsvParser.parse("绒绒备份", csv, "rongrong-miniprogram.csv")

        assertEquals("绒绒备份", preview.provider)
        assertEquals(2, preview.entries.size)
        assertEquals("早餐", preview.entries.first().categoryName)
        assertEquals("餐饮", preview.entries.first().categoryParentName)
        assertEquals(1_250L, preview.entries.first().amountMinor)
        assertEquals("income", preview.entries.last().type)
        assertEquals("退税退费", preview.entries.last().categoryName)
    }

    @Test
    fun localAiUsesSecondLevelCategoryAndAccount() {
        val categories = CategoryCatalog.defaultCategories("user", "book", 0)
        val accounts = listOf(
            AccountEntity("cash", "user", "book", "现金", "cash", "#F9A35E", createdAt = 0, updatedAt = 0),
            AccountEntity("alipay", "user", "book", "支付宝", "alipay", "#5C8ED6", createdAt = 0, updatedAt = 0)
        )

        val parsed = LocalAiLedgerParser.parse("昨天奶茶 18.5 元，用支付宝", categories, accounts)

        assertNotNull(parsed)
        assertEquals("expense", parsed?.type)
        assertEquals(1_850L, parsed?.amountMinor)
        assertEquals("咖啡", parsed?.categoryLabel)
        assertEquals("支付宝", parsed?.accountLabel)
    }
}
