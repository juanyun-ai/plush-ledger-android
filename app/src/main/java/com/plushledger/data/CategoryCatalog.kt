package com.plushledger.data

import java.nio.charset.StandardCharsets
import java.util.UUID

data class CategorySpec(
    val key: String,
    val name: String,
    val kind: String,
    val icon: String,
    val colorHex: String,
    val parentKey: String? = null
)

/**
 * The canonical bookkeeping taxonomy. Keys are stable so every device can
 * recreate the same default hierarchy without relying on display names.
 */
object CategoryCatalog {
    private const val EXPENSE = "expense"
    private const val INCOME = "income"

    val specs: List<CategorySpec> = listOf(
        CategorySpec("expense.food", "餐饮", EXPENSE, "food", "#F7A54A"),
        CategorySpec("expense.transport", "交通", EXPENSE, "transport", "#76A9E8"),
        CategorySpec("expense.shopping", "购物", EXPENSE, "shopping", "#F48FB1"),
        CategorySpec("expense.daily", "日常", EXPENSE, "daily", "#8CC995"),
        CategorySpec("expense.entertainment", "娱乐", EXPENSE, "entertainment", "#A98BE8"),
        CategorySpec("expense.social", "人情社交", EXPENSE, "social", "#F19A78"),
        CategorySpec("expense.pet", "宠物", EXPENSE, "pet", "#C69A72"),
        CategorySpec("expense.study", "学习工作", EXPENSE, "study", "#7EA7D9"),
        CategorySpec("expense.health", "医疗健康", EXPENSE, "health", "#E88782"),
        CategorySpec("expense.other", "其他", EXPENSE, "other", "#B9B3AA"),

        CategorySpec("expense.food.breakfast", "早餐", EXPENSE, "breakfast", "#F7A54A", "expense.food"),
        CategorySpec("expense.food.meal", "正餐", EXPENSE, "meal", "#F4B86A", "expense.food"),
        CategorySpec("expense.food.delivery", "外卖", EXPENSE, "delivery", "#F29A74", "expense.food"),
        CategorySpec("expense.food.tea", "奶茶咖啡", EXPENSE, "tea", "#C98A62", "expense.food"),
        CategorySpec("expense.food.snacks", "零食", EXPENSE, "snacks", "#ECA75C", "expense.food"),
        CategorySpec("expense.food.gathering", "聚餐", EXPENSE, "gathering", "#D98972", "expense.food"),

        CategorySpec("expense.transport.commute", "通勤", EXPENSE, "commute", "#76A9E8", "expense.transport"),
        CategorySpec("expense.transport.metro", "公交地铁", EXPENSE, "metro", "#6F9ED6", "expense.transport"),
        CategorySpec("expense.transport.taxi", "打车", EXPENSE, "taxi", "#5F91D0", "expense.transport"),
        CategorySpec("expense.transport.rail", "火车高铁", EXPENSE, "rail", "#86B6E8", "expense.transport"),
        CategorySpec("expense.transport.bikepass", "单车月卡", EXPENSE, "bike_pass", "#79B9DB", "expense.transport"),

        CategorySpec("expense.shopping.daily", "日用百货", EXPENSE, "basket", "#F48FB1", "expense.shopping"),
        CategorySpec("expense.shopping.clothes", "服饰鞋包", EXPENSE, "clothes", "#E680A6", "expense.shopping"),
        CategorySpec("expense.shopping.digital", "数码配件", EXPENSE, "digital", "#9E98D8", "expense.shopping"),
        CategorySpec("expense.shopping.beauty", "美妆个护", EXPENSE, "beauty", "#DD8CB3", "expense.shopping"),

        CategorySpec("expense.daily.household", "生活用品", EXPENSE, "household", "#8CC995", "expense.daily"),
        CategorySpec("expense.daily.delivery", "快递物流", EXPENSE, "parcel", "#79B987", "expense.daily"),
        CategorySpec("expense.daily.phone", "话费网络", EXPENSE, "phone", "#6FB189", "expense.daily"),
        CategorySpec("expense.daily.utilities", "水电房租", EXPENSE, "utilities", "#69A98A", "expense.daily"),

        CategorySpec("expense.entertainment.game", "游戏", EXPENSE, "game", "#A98BE8", "expense.entertainment"),
        CategorySpec("expense.entertainment.media", "影视会员", EXPENSE, "media", "#9276D7", "expense.entertainment"),
        CategorySpec("expense.entertainment.travel", "旅游出行", EXPENSE, "travel", "#8D9DE0", "expense.entertainment"),
        CategorySpec("expense.entertainment.hobby", "兴趣爱好", EXPENSE, "hobby", "#B091E5", "expense.entertainment"),

        CategorySpec("expense.social.redpacket", "人情红包", EXPENSE, "redpacket", "#F19A78", "expense.social"),
        CategorySpec("expense.social.gift", "请客送礼", EXPENSE, "gift", "#E98770", "expense.social"),
        CategorySpec("expense.social.date", "恋爱约会", EXPENSE, "date", "#DE837E", "expense.social"),
        CategorySpec("expense.social.activity", "社交活动", EXPENSE, "social_activity", "#E89482", "expense.social"),

        CategorySpec("expense.pet.food", "宠物食品", EXPENSE, "pet_food", "#C69A72", "expense.pet"),
        CategorySpec("expense.pet.supplies", "宠物用品", EXPENSE, "pet_supplies", "#B88766", "expense.pet"),
        CategorySpec("expense.pet.health", "宠物医疗", EXPENSE, "pet_health", "#D28978", "expense.pet"),

        CategorySpec("expense.study.books", "书籍资料", EXPENSE, "books", "#7EA7D9", "expense.study"),
        CategorySpec("expense.study.course", "课程考试", EXPENSE, "course", "#7198CC", "expense.study"),
        CategorySpec("expense.study.stationery", "文具打印", EXPENSE, "stationery", "#91B4DF", "expense.study"),
        CategorySpec("expense.study.software", "软件工具", EXPENSE, "software", "#6C92C7", "expense.study"),
        CategorySpec("expense.study.ai_subscription", "AI软件订阅", EXPENSE, "ai_subscription", "#64B6A5", "expense.study"),

        CategorySpec("expense.health.medicine", "药品", EXPENSE, "medicine", "#E88782", "expense.health"),
        CategorySpec("expense.health.checkup", "就诊体检", EXPENSE, "checkup", "#DF7778", "expense.health"),
        CategorySpec("expense.health.fitness", "运动健身", EXPENSE, "fitness", "#D76F79", "expense.health"),

        CategorySpec("expense.other.temporary", "临时支出", EXPENSE, "temporary", "#B9B3AA", "expense.other"),
        CategorySpec("expense.other.miscellaneous", "杂项备用", EXPENSE, "miscellaneous", "#C5A36F", "expense.other"),
        CategorySpec("expense.other.unknown", "未分类", EXPENSE, "unknown", "#A69F96", "expense.other"),

        CategorySpec("income.salary", "工资", INCOME, "salary", "#5E9B83"),
        CategorySpec("income.rent", "房屋", INCOME, "home", "#E0A86E"),
        CategorySpec("income.parttime", "兼职", INCOME, "parttime", "#6E8DBF"),
        CategorySpec("income.investment", "理财", INCOME, "investment", "#8AA46D"),
        CategorySpec("income.gift", "礼金", INCOME, "gift_income", "#76A9A8")
    )

    fun defaultCategories(userId: String, bookId: String, now: Long): List<CategoryEntity> {
        val ids = specs.associate { it.key to stableId(userId, it.key) }
        return specs.mapIndexed { index, spec ->
            CategoryEntity(
                id = ids.getValue(spec.key),
                userId = userId,
                bookId = bookId,
                name = spec.name,
                kind = spec.kind,
                colorHex = spec.colorHex,
                icon = spec.icon,
                sortOrder = index,
                createdAt = now,
                updatedAt = now,
                parentId = spec.parentKey?.let(ids::get)
            )
        }
    }

    fun rootCategories(categories: List<CategoryEntity>, kind: String): List<CategoryEntity> =
        categories.filter { it.kind == kind && it.parentId == null }.sortedBy { it.sortOrder }

    fun childrenOf(categories: List<CategoryEntity>, parentId: String?): List<CategoryEntity> =
        categories.filter { it.parentId == parentId }.sortedBy { it.sortOrder }

    fun rootOf(category: CategoryEntity, categoriesById: Map<String, CategoryEntity>): CategoryEntity =
        category.parentId?.let(categoriesById::get) ?: category

    fun legacyParentKey(name: String): String? = when (name) {
        "奶茶", "咖啡" -> "expense.food"
        "日常消费", "住房", "生活费" -> "expense.daily"
        "医疗" -> "expense.health"
        "学习" -> "expense.study"
        "人情" -> "expense.social"
        else -> null
    }

    fun legacyNamesFor(key: String): Set<String> = when (key) {
        "expense.other.unknown" -> setOf("无法归类")
        else -> emptySet()
    }

    private fun stableId(userId: String, key: String): String =
        UUID.nameUUIDFromBytes("rongrong-ledger:$userId:$key".toByteArray(StandardCharsets.UTF_8)).toString()
}
