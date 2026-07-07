package com.plushledger.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

data class ExternalBillEntry(
    val sourceId: String,
    val type: String,
    val amountMinor: Long,
    val note: String,
    val occurredAt: Long,
    val accountHint: String,
    val categoryName: String = "",
    val categoryParentName: String = "",
    val categoryPath: String = ""
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

/** Parses Rongrong exports and user-exported payment CSV locally. It never accesses either account. */
object ExternalBillCsvParser {
    fun parse(provider: String, raw: String): ExternalBillPreview = parse(provider, raw, "")

    fun parse(provider: String, raw: String, fileName: String): ExternalBillPreview {
        require(raw.length <= 8 * 1024 * 1024) { "账单文件过大，请先在支付平台按月份导出" }
        val content = raw.trimStart('\ufeff', ' ', '\r', '\n', '\t')
        if (content.startsWith("{") || content.startsWith("[") || fileName.endsWith(".json", ignoreCase = true)) {
            return parseRongrongJson(provider, content)
        }
        val rows = parseCsv(raw).filter { row -> row.any { it.isNotBlank() } }
        require(rows.size >= 2) { "没有识别到可导入的账单 CSV" }
        findRongrongHeader(rows)?.let { return parseRongrongRows(provider, rows, it) }
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

    private fun parseRongrongJson(provider: String, raw: String): ExternalBillPreview {
        val entries = mutableListOf<ExternalBillEntry>()
        if (raw.startsWith("[")) {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toRongrongEntry(provider, null, null, index)?.let(entries::add)
            }
        } else {
            val root = JSONObject(raw)
            val ledger = root.optJSONObject("ledger") ?: root
            val categories = ledger.optJSONArray("categories").asObjectMap()
            val accounts = ledger.optJSONArray("accounts").asObjectMap()
            val transactions = ledger.optJSONArray("transactions") ?: JSONArray()
            for (index in 0 until transactions.length()) {
                transactions.optJSONObject(index)?.toRongrongEntry(provider, categories, accounts, index)?.let(entries::add)
            }
        }
        require(entries.isNotEmpty()) { "没有识别到绒绒记账导出的账目记录" }
        return ExternalBillPreview("绒绒备份", entries, 0)
    }

    private fun JSONObject.toRongrongEntry(
        provider: String,
        categories: Map<String, JSONObject>?,
        accounts: Map<String, JSONObject>?,
        index: Int
    ): ExternalBillEntry? {
        val type = normalizeType(optString("type", optString("type_label")))
        if (type !in setOf("income", "expense")) return null
        val amount = optLong("amountMinor", optLong("amount_minor", 0L)).takeIf { it > 0L }
            ?: parseAmount(optString("amount", optString("amount_cny"))) ?: return null
        val category = optString("categoryId", optString("category_id")).let { categories?.get(it) }
        val parent = category?.optString("parentId", category.optString("parent_id"))?.let { categories?.get(it) }
        val account = optString("accountId", optString("account_id")).let { accounts?.get(it) }
        val occurred = optLong("occurredAt", optLong("occurred_at", 0L)).takeIf { it > 0L }
            ?: optLong("createdAt", optLong("created_at", 0L)).takeIf { it > 0L }
            ?: parseTime(optString("datetime", optString("date")))
            ?: System.currentTimeMillis()
        val categoryName = optString("categoryName", optString("category"))
            .ifBlank { category?.optString("name").orEmpty() }
        val parentName = optString("categoryParentName", optString("category_parent"))
            .ifBlank { parent?.optString("name").orEmpty() }
        return ExternalBillEntry(
            sourceId = optString("sourceId", optString("id")).ifBlank { "$provider-$occurred-$amount-$index" },
            type = type,
            amountMinor = amount,
            note = optString("note").take(80),
            occurredAt = occurred,
            accountHint = optString("accountName", optString("account")).ifBlank { account?.optString("name").orEmpty() }.ifBlank { provider },
            categoryName = categoryName,
            categoryParentName = parentName,
            categoryPath = optString("categoryPath", optString("category_path"))
        )
    }

    private fun JSONArray?.asObjectMap(): Map<String, JSONObject> {
        if (this == null) return emptyMap()
        val result = mutableMapOf<String, JSONObject>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            result[id] = item
        }
        return result
    }

    private fun findRongrongHeader(rows: List<List<String>>): Int? =
        rows.indexOfFirst { row ->
            val headers = row.map(::normalizedHeader)
            headers.any { it == "amountminor" || it == "amountcny" || it == "金额分" || it == "金额" } &&
                headers.any { it == "type" || it == "typelabel" || it == "类型" } &&
                headers.any { it == "category" || it == "categorypath" || it == "分类" || it == "分类路径" }
        }.takeIf { it >= 0 }

    private fun parseRongrongRows(provider: String, rows: List<List<String>>, headerIndex: Int): ExternalBillPreview {
        val headers = rows[headerIndex].map(::normalizedHeader)
        val entries = mutableListOf<ExternalBillEntry>()
        var skipped = 0
        rows.drop(headerIndex + 1).forEachIndexed { index, row ->
            val values = headers.indices.associate { column -> headers[column] to row.getOrElse(column) { "" }.trim() }
            val type = normalizeType(find(values, "type", "type_label", "类型"))
            if (type !in setOf("income", "expense")) {
                skipped++
                return@forEachIndexed
            }
            val amount = parseMinor(find(values, "amount_minor", "金额分"))
                ?: parseAmount(find(values, "amount_cny", "amount", "金额"))
            if (amount == null || amount <= 0L) {
                skipped++
                return@forEachIndexed
            }
            val date = find(values, "date", "日期")
            val time = find(values, "time", "时间")
            val timestamp = parseFlexibleTime(
                find(values, "occurred_at", "datetime", "完整时间", "时间戳", "created_at", "updated_at"),
                date,
                time
            )
            if (timestamp == null) {
                skipped++
                return@forEachIndexed
            }
            val categoryPath = find(values, "category_path", "分类路径")
            val pathParts = categoryPath.split("/", ">").map(String::trim).filter(String::isNotBlank)
            val categoryName = find(values, "category", "category_name", "分类").ifBlank { pathParts.lastOrNull().orEmpty() }
            val parentName = find(values, "category_parent", "主类").ifBlank {
                pathParts.dropLast(1).lastOrNull().orEmpty()
            }
            entries += ExternalBillEntry(
                sourceId = find(values, "id", "source_id", "流水号").ifBlank { "$provider-$timestamp-$amount-$index" },
                type = type,
                amountMinor = amount,
                note = find(values, "note", "备注").take(80),
                occurredAt = timestamp,
                accountHint = find(values, "account", "account_name", "账户").ifBlank { provider },
                categoryName = categoryName,
                categoryParentName = parentName,
                categoryPath = categoryPath
            )
        }
        require(entries.isNotEmpty()) { "没有识别到绒绒记账导出的账目记录" }
        return ExternalBillPreview("绒绒备份", entries, skipped)
    }

    private fun find(values: Map<String, String>, vararg names: String): String =
        names.firstNotNullOfOrNull { name -> values[normalizedHeader(name)]?.takeIf(String::isNotBlank) }.orEmpty()

    private fun normalizedHeader(value: String): String =
        value.replace("\ufeff", "").replace(Regex("[\\s()（）【】\\[\\]_.-]"), "").lowercase()

    private fun parseAmount(value: String): Long? = runCatching {
        val cleaned = value.replace(Regex("[^0-9.-]"), "")
        BigDecimal(cleaned).abs().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }.getOrNull()?.takeIf { it > 0 }

    private fun parseMinor(value: String): Long? = runCatching {
        value.replace(Regex("[^0-9-]"), "").toLong()
    }.getOrNull()?.let { kotlin.math.abs(it) }?.takeIf { it > 0 }

    private fun parseTime(value: String): Long? {
        val normalized = value.trim().replace('/', '-').replace(Regex("\\s+"), " ")
        if (normalized.matches(Regex("^\\d{10,13}$"))) {
            val number = normalized.toLong()
            return if (number < 10_000_000_000L) number * 1000 else number
        }
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")
        return formats.firstNotNullOfOrNull { pattern ->
            runCatching {
                val dateTime = if (pattern == "yyyy-MM-dd") LocalDateTime.parse("$normalized 00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                else LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern))
                dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
        }
    }

    private fun parseFlexibleTime(value: String, date: String, time: String): Long? =
        parseTime(value).takeIf { value.isNotBlank() }
            ?: parseTime(listOf(date, time).filter(String::isNotBlank).joinToString(" ")).takeIf { date.isNotBlank() }

    private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)

    private fun normalizeType(type: String): String {
        val value = type.lowercase()
        return when {
            value.contains("income") || value.contains("收入") || value.contains("收款") -> "income"
            value.contains("transfer") || value.contains("转账") -> "transfer"
            else -> "expense"
        }
    }

    private fun parseCsv(raw: String): List<List<String>> {
        val firstLine = raw.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val delimiter = if (firstLine.count { it == '\t' } > firstLine.count { it == ',' }) '\t' else ','
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
                delimiter -> if (quoted) cell.append(char) else {
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
