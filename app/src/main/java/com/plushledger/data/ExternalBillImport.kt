package com.plushledger.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ExternalBillEntry(
    val sourceId: String,
    val type: String,
    val amountMinor: Long,
    val note: String,
    val occurredAt: Long,
    val accountHint: String
)

data class ExternalBillPreview(
    val provider: String,
    val entries: List<ExternalBillEntry>,
    val skippedRows: Int
)

data class ExternalBillImportResult(
    val imported: Int,
    val skipped: Int
)

/** Parses user-exported WeChat Pay or Alipay CSV locally. It never accesses either account. */
object ExternalBillCsvParser {
    fun parse(provider: String, raw: String): ExternalBillPreview {
        require(raw.length <= 8 * 1024 * 1024) { "账单文件过大，请先在支付平台按月份导出" }
        val rows = parseCsv(raw).filter { row -> row.any { it.isNotBlank() } }
        require(rows.size >= 2) { "没有识别到可导入的账单 CSV" }
        val headerIndex = rows.indexOfFirst { row ->
            val headers = row.map(::normalizedHeader)
            headers.any { it == "交易时间" || it == "交易创建时间" || it == "付款时间" } &&
                headers.any { it == "金额" || it == "金额元" || it == "交易金额" }
        }
        require(headerIndex >= 0) { "未找到交易时间和金额列，请选择微信或支付宝导出的原始 CSV" }
        val headers = rows[headerIndex].map(::normalizedHeader)
        val entries = mutableListOf<ExternalBillEntry>()
        var skipped = 0
        rows.drop(headerIndex + 1).forEachIndexed { index, row ->
            val values = headers.indices.associate { column -> headers[column] to row.getOrElse(column) { "" }.trim() }
            val status = find(values, "当前状态", "交易状态", "状态")
            if (status.containsAny("关闭", "撤销", "失败", "退款", "已退款")) {
                skipped++
                return@forEachIndexed
            }
            val direction = find(values, "收支", "收支类型", "类型", "交易类型")
            val type = when {
                direction.containsAny("收入", "收款", "到账") -> "income"
                direction.containsAny("支出", "付款", "消费") -> "expense"
                direction.containsAny("转账", "不计收支", "退款") -> null
                else -> "expense"
            }
            if (type == null) {
                skipped++
                return@forEachIndexed
            }
            val amount = parseAmount(find(values, "金额元", "金额", "交易金额", "支出金额"))
            if (amount == null) {
                skipped++
                return@forEachIndexed
            }
            val timestamp = parseTime(find(values, "交易时间", "交易创建时间", "付款时间", "时间", "日期"))
                ?: run {
                    skipped++
                    return@forEachIndexed
                }
            val sourceId = find(values, "交易单号", "交易号", "商户单号", "订单号")
                .ifBlank { "$provider-${timestamp}-${amount}-${index}" }
            val note = listOf(
                find(values, "交易对方", "对方", "商家名称"),
                find(values, "商品", "商品名称", "交易描述"),
                find(values, "备注")
            ).filter(String::isNotBlank).distinct().joinToString(" · ").take(80)
            val accountHint = find(values, "支付方式", "付款方式").ifBlank { provider }
            entries += ExternalBillEntry(sourceId, type, amount, note, timestamp, accountHint)
        }
        require(entries.isNotEmpty()) { "没有可导入的成功收支记录，请确认选择了微信或支付宝导出的 CSV" }
        return ExternalBillPreview(provider, entries, skipped)
    }

    private fun find(values: Map<String, String>, vararg names: String): String =
        names.firstNotNullOfOrNull { name -> values[normalizedHeader(name)]?.takeIf(String::isNotBlank) }.orEmpty()

    private fun normalizedHeader(value: String): String =
        value.replace("\ufeff", "").replace(Regex("[\\s()（）【】\\[\\]_.-]"), "").lowercase()

    private fun parseAmount(value: String): Long? = runCatching {
        val cleaned = value.replace(Regex("[^0-9.-]"), "")
        BigDecimal(cleaned).abs().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }.getOrNull()?.takeIf { it > 0 }

    private fun parseTime(value: String): Long? {
        val normalized = value.trim().replace('/', '-').replace(Regex("\\s+"), " ")
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")
        return formats.firstNotNullOfOrNull { pattern ->
            runCatching {
                val dateTime = if (pattern == "yyyy-MM-dd") LocalDateTime.parse("$normalized 00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                else LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern))
                dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
        }
    }

    private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)

    private fun parseCsv(raw: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < raw.length) {
            when (val char = raw[index]) {
                '"' -> {
                    if (quoted && raw.getOrNull(index + 1) == '"') {
                        cell.append('"')
                        index++
                    } else quoted = !quoted
                }
                ',' -> if (quoted) cell.append(char) else {
                    row += cell.toString()
                    cell.clear()
                }
                '\n' -> if (quoted) cell.append(char) else {
                    row += cell.toString().removeSuffix("\r")
                    rows += row
                    row = mutableListOf()
                    cell.clear()
                }
                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString().removeSuffix("\r")
            rows += row
        }
        return rows
    }
}
