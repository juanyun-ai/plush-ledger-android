package com.plushledger.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class AiLedgerAnalysis(
    val sourceText: String,
    val type: String,
    val amountMinor: Long,
    val categoryId: String?,
    val categoryLabel: String,
    val accountId: String?,
    val accountLabel: String,
    val note: String,
    val occurredAt: Long,
    val cloudAssisted: Boolean,
    val notice: String? = null
)

data class AiLedgerBatch(
    val sourceText: String,
    val entries: List<AiLedgerAnalysis>,
    val cloudAssisted: Boolean,
    val notice: String? = null
)

object LocalAiLedgerParser {
    private val currencyAmountPattern = Regex(
        "(?:[¥￥]\\s*(\\d+(?:\\.\\d{1,2})?)|(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块(?:钱)?|rmb))",
        RegexOption.IGNORE_CASE
    )
    private val bareNumberPattern = Regex("\\d+(?:\\.\\d{1,2})?")

    fun parse(text: String, categories: List<CategoryEntity>, accounts: List<AccountEntity>): AiLedgerAnalysis? =
        parseAll(text, categories, accounts).firstOrNull()

    fun parseAll(text: String, categories: List<CategoryEntity>, accounts: List<AccountEntity>): List<AiLedgerAnalysis> {
        val source = text.trim().take(160)
        if (source.isBlank()) return emptyList()
        return splitIntoEntries(source)
            .mapNotNull { entry -> parseSingle(entry, categories, accounts) }
            .distinctBy { listOf(it.type, it.amountMinor, it.note, it.occurredAt) }
            .take(8)
    }

    private fun parseSingle(source: String, categories: List<CategoryEntity>, accounts: List<AccountEntity>): AiLedgerAnalysis? {
        val amount = parseAmount(source) ?: return null
        val type = if (listOf("收入", "工资", "兼职", "收款", "到账", "报销", "理财", "收益", "利息", "稿费", "转租", "租金").any(source::contains)) "income" else "expense"
        val category = chooseCategory(source, type, categories)
        val account = chooseAccount(source, accounts)
        return AiLedgerAnalysis(
            sourceText = source,
            type = type,
            amountMinor = amount,
            categoryId = category?.id,
            categoryLabel = category?.name ?: "无法归类",
            accountId = account?.id,
            accountLabel = account?.name ?: "默认账户",
            note = source,
            occurredAt = parseOccurredAt(source),
            cloudAssisted = false,
            notice = "已使用本地规则预填；配置 AI 服务后可识别更自然的表达。"
        )
    }

    /**
     * 优先按中文语句拆分。没有句号时，按金额和其前面的短语拆分，能覆盖
     * “昨天咖啡 15 元，今天买菜 30 元”这类一口气输入的常见表达。
     */
    private fun splitIntoEntries(source: String): List<String> {
        val sentences = source.split(Regex("[；;。\\n]+"))
            .map(String::trim)
            .filter(String::isNotBlank)
        return sentences.flatMap { sentence ->
            val amounts = currencyAmountPattern.findAll(sentence).toList()
            if (amounts.size <= 1) return@flatMap listOf(sentence)
            amounts.map { amount ->
                val beforeAmount = sentence.substring(0, amount.range.first)
                val delimiter = beforeAmount.lastIndexOfAny(charArrayOf('，', ',', '、', '；', ';'))
                val start = (delimiter + 1).coerceAtLeast(0)
                val afterAmount = sentence.substring(amount.range.last + 1)
                val accountTail = accountKeyword.find(afterAmount)?.let { match ->
                    if (match.range.first <= 8) "，${match.value}" else ""
                }.orEmpty()
                (sentence.substring(start, amount.range.last + 1) + accountTail).trim()
            }
        }.filter(String::isNotBlank)
    }

    fun resolveCategory(
        type: String,
        categoryName: String?,
        parentName: String?,
        categories: List<CategoryEntity>,
        sourceText: String
    ): CategoryEntity? {
        val candidates = categories.filter { it.kind == type }
        val explicit = explicitlyNamedCategory(sourceText, candidates)
        if (explicit != null) return explicit
        val direct = categoryName?.trim()?.takeIf(String::isNotBlank)?.let { name ->
            candidates.firstOrNull { it.name == name }
        }
        if (direct != null) return direct
        val parent = parentName?.trim()?.takeIf(String::isNotBlank)?.let { name ->
            candidates.firstOrNull { it.name == name && it.parentId == null }
        }
        if (parent != null) {
            return candidates.firstOrNull { it.parentId == parent.id } ?: parent
        }
        return chooseCategory(sourceText, type, categories)
    }

    fun resolveAccount(accountName: String?, accounts: List<AccountEntity>, sourceText: String): AccountEntity? {
        val explicit = explicitlyNamedAccount(sourceText, accounts)
        if (explicit != null) return explicit
        val direct = accountName?.trim()?.takeIf(String::isNotBlank)?.let { name ->
            accounts.firstOrNull { it.name == name || it.name.contains(name) || name.contains(it.name) }
        }
        return direct ?: chooseAccount(sourceText, accounts)
    }

    private fun chooseCategory(text: String, type: String, categories: List<CategoryEntity>): CategoryEntity? {
        val candidates = categories.filter { it.kind == type }
        val explicit = explicitlyNamedCategory(text, candidates)
        if (explicit != null) return explicit
        val keywords = if (type == "income") incomeKeywords else expenseKeywords
        val matchedName = keywords.firstOrNull { (_, words) -> words.any(text::contains) }?.first
        if (matchedName != null) {
            candidates.firstOrNull { it.name == matchedName }?.let { return it }
        }
        return if (type == "income") {
            candidates.firstOrNull { it.name == "其他收入" }
                ?: candidates.firstOrNull { it.name == "工资" }
                ?: candidates.firstOrNull()
        } else {
            candidates.firstOrNull { it.name == "未分类" }
                ?: candidates.firstOrNull { it.name == "无法归类" }
                ?: candidates.firstOrNull { it.name == "临时支出" }
                ?: candidates.firstOrNull { it.name == "其他" }
        }
    }

    private fun explicitlyNamedCategory(text: String, candidates: List<CategoryEntity>): CategoryEntity? =
        candidates.sortedByDescending { it.name.length }
            .firstOrNull { it.name !in genericCategoryNames && text.contains(it.name, ignoreCase = true) }

    private fun explicitlyNamedAccount(text: String, accounts: List<AccountEntity>): AccountEntity? {
        accounts.sortedByDescending { it.name.length }
            .firstOrNull { text.contains(it.name, ignoreCase = true) }
            ?.let { return it }
        return when {
            text.contains("微信") -> accounts.firstOrNull { it.name.contains("微信") }
            text.contains("支付宝") -> accounts.firstOrNull { it.name.contains("支付宝") }
            text.contains("现金") -> accounts.firstOrNull { it.name.contains("现金") }
            text.contains("银行卡") || text.contains("银行") -> accounts.firstOrNull { it.name.contains("银行") }
            else -> null
        }
    }

    private fun chooseAccount(text: String, accounts: List<AccountEntity>): AccountEntity? =
        when {
            text.contains("微信") -> accounts.firstOrNull { it.name.contains("微信") }
            text.contains("支付宝") -> accounts.firstOrNull { it.name.contains("支付宝") }
            text.contains("现金") -> accounts.firstOrNull { it.name.contains("现金") }
            text.contains("银行卡") || text.contains("银行") -> accounts.firstOrNull { it.name.contains("银行") }
            else -> accounts.firstOrNull { it.name == "现金" } ?: accounts.firstOrNull()
        }

    private fun parseMinor(raw: String): Long? = runCatching {
        BigDecimal(raw).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }.getOrNull()?.takeIf { it > 0 }

    private fun parseAmount(text: String): Long? {
        currencyAmountPattern.findAll(text).mapNotNull { match ->
            match.groupValues.drop(1).firstOrNull(String::isNotBlank)?.let(::parseMinor)
        }.lastOrNull()?.let { return it }

        return bareNumberPattern.findAll(text)
            .filterNot { match ->
                val before = text.getOrNull(match.range.first - 1)
                val after = text.getOrNull(match.range.last + 1)
                before in dateNumberDelimiters || after in dateNumberDelimiters
            }
            .mapNotNull { parseMinor(it.value) }
            .lastOrNull()
    }

    private fun parseOccurredAt(text: String): Long {
        val today = LocalDate.now()
        val date = when {
            text.contains("大前天") -> today.minusDays(3)
            text.contains("昨天") -> today.minusDays(1)
            text.contains("前天") -> today.minusDays(2)
            Regex("(\\d{1,3})天前").find(text) != null -> {
                val days = Regex("(\\d{1,3})天前").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                today.minusDays(days)
            }
            text.contains("上个月") -> parseDayInMonth(text, YearMonth.from(today).minusMonths(1)) ?: today
            text.contains("本月") -> parseDayInMonth(text, YearMonth.from(today)) ?: today
            Regex("上周[一二三四五六日天]").find(text) != null -> parseWeekday(text, today, previousWeek = true)
            Regex("本周[一二三四五六日天]").find(text) != null -> parseWeekday(text, today, previousWeek = false)
            else -> parseExplicitDate(text, today) ?: today
        }
        val explicitTime = Regex("(?:上午|早上|中午|下午|晚上)?\\s*(\\d{1,2})(?:点|:)(\\d{1,2})?").find(text)
        val time = explicitTime?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: 12
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if ((text.contains("下午") || text.contains("晚上")) && hour in 1..11) hour += 12
            LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        } ?: when {
            text.contains("早餐") || text.contains("早上") -> LocalTime.of(8, 0)
            text.contains("午饭") || text.contains("中午") -> LocalTime.of(12, 0)
            text.contains("晚饭") || text.contains("晚上") -> LocalTime.of(19, 0)
            else -> LocalTime.now().withSecond(0).withNano(0)
        }
        return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun parseExplicitDate(text: String, today: LocalDate): LocalDate? {
        Regex("(?<!\\d)(\\d{4})\\s*(?:年|[-/.])\\s*(\\d{1,2})\\s*(?:月|[-/.])\\s*(\\d{1,2})\\s*日?")
            .find(text)?.let { match ->
                return runCatching {
                    LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
                }.getOrNull()
            }
        Regex("(?<!\\d)(\\d{1,2})\\s*(?:月|[-/.])\\s*(\\d{1,2})\\s*日?")
            .find(text)?.let { match ->
                return runCatching {
                    LocalDate.of(today.year, match.groupValues[1].toInt(), match.groupValues[2].toInt())
                }.getOrNull()
            }
        return null
    }

    private fun parseDayInMonth(text: String, month: YearMonth): LocalDate? {
        val day = Regex("(?:上个月|本月)\\s*(\\d{1,2})\\s*(?:号|日)").find(text)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return runCatching { month.atDay(day) }.getOrNull()
    }

    private fun parseWeekday(text: String, today: LocalDate, previousWeek: Boolean): LocalDate {
        val dayText = Regex("(?:上周|本周)([一二三四五六日天])").find(text)?.groupValues?.get(1) ?: "一"
        val dayOffset = weekdayNames.indexOf(dayText).coerceAtLeast(0).toLong()
        val currentMonday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        return currentMonday.minusWeeks(if (previousWeek) 1 else 0).plusDays(dayOffset)
    }

    private val expenseKeywords = listOf(
        "奶茶咖啡" to listOf("奶茶", "咖啡", "茶饮", "瑞幸", "星巴克", "喜茶", "蜜雪"),
        "早餐" to listOf("早餐", "早饭", "豆浆", "包子"),
        "外卖" to listOf("外卖", "美团", "饿了么"),
        "正餐" to listOf("午饭", "晚饭", "吃饭", "餐厅", "火锅", "面馆", "食堂", "盖饭", "米线", "烧烤"),
        "零食" to listOf("零食", "薯片", "饮料"),
        "聚餐" to listOf("聚餐", "团建"),
        "公交地铁" to listOf("公交", "地铁"),
        "打车" to listOf("打车", "滴滴", "出租车"),
        "火车高铁" to listOf("高铁", "火车", "机票"),
        "日用百货" to listOf("超市", "百货", "日用", "买菜"),
        "服饰鞋包" to listOf("衣服", "鞋", "服饰", "包包", "手提包", "双肩包", "钱包"),
        "数码配件" to listOf("数码", "耳机", "手机壳"),
        "美妆个护" to listOf("美妆", "护肤", "化妆"),
        "生活用品" to listOf("纸巾", "洗衣", "生活用品"),
        "快递物流" to listOf("快递", "物流"),
        "话费网络" to listOf("话费", "流量", "宽带"),
        "水电房租" to listOf("房租", "水费", "电费", "燃气"),
        "游戏" to listOf("游戏", "游戏充值"),
        "影视会员" to listOf("电影", "视频会员", "影视"),
        "旅游出行" to listOf("旅游", "酒店", "景点"),
        "兴趣爱好" to listOf("摄影", "画画", "乐器"),
        "人情红包" to listOf("红包", "随礼", "份子钱"),
        "请客送礼" to listOf("送礼", "请客"),
        "恋爱约会" to listOf("约会", "恋爱", "对象"),
        "宠物食品" to listOf("猫粮", "狗粮", "宠物食品"),
        "宠物用品" to listOf("猫砂", "宠物用品"),
        "宠物医疗" to listOf("宠物医院", "驱虫", "疫苗"),
        "书籍资料" to listOf("书", "资料"),
        "课程考试" to listOf("课程", "考试", "报名"),
        "文具打印" to listOf("文具", "打印"),
        "AI软件订阅" to listOf("ai", "AI", "ChatGPT", "chatgpt", "GPT", "OpenAI", "openai", "Claude", "claude", "DeepSeek", "deepseek", "Kimi", "kimi", "豆包", "通义", "模型", "AI软件", "ai软件", "AI订阅", "ai订阅"),
        "软件工具" to listOf("软件", "工具", "订阅"),
        "药品" to listOf("药", "药店", "感冒药", "处方"),
        "就诊体检" to listOf("医院", "体检", "挂号"),
        "运动健身" to listOf("健身", "运动", "跑步")
    )

    private val incomeKeywords = listOf(
        "工资" to listOf("工资", "薪水"),
        "房屋" to listOf("房屋转租", "转租", "房租收入", "租金收入", "收租", "出租房"),
        "兼职" to listOf("兼职", "稿费", "副业"),
        "理财" to listOf("理财", "利息", "收益"),
        "礼金" to listOf("礼金", "红包", "礼物")
    )

    private val genericCategoryNames = setOf("其他", "其他收入", "未分类", "无法归类", "临时支出")
    private val dateNumberDelimiters = setOf('年', '月', '日', '号', '点', ':', '：', '/', '-', '.', '~', '～')
    private val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日", "天")
    private val accountKeyword = Regex("微信(?:支付)?|支付宝|现金|银行卡|银行")
}
