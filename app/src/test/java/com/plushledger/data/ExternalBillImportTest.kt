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
        assertEquals("奶茶咖啡", parsed?.categoryLabel)
        assertEquals("支付宝", parsed?.accountLabel)
    }
}
