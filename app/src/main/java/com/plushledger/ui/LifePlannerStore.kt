package com.plushledger.ui

import android.content.Context
import android.icu.util.ChineseCalendar
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.UUID

data class LifeEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String,
    val type: String,
    val note: String = "",
    val reminderDays: Int = 1
) {
    fun localDate(): LocalDate = LocalDate.parse(date)
}

data class WishPlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetMinor: Long,
    val savedMinor: Long = 0,
    val targetDate: String? = null,
    val note: String = ""
)

data class BirthdaySettings(
    val calendarMode: String = "solar",
    val reminderEnabled: Boolean = true,
    val reminderDays: Int = 1,
    val showInLifeCalendar: Boolean = true
)

data class CalendarDayInfo(
    val label: String,
    val isSolarTerm: Boolean = false,
    val isMajorFestival: Boolean = false,
    val isLegalHoliday: Boolean = false,
    val isAdjustedWorkday: Boolean = false
)

class LifePlannerStore(context: Context, private val userId: String) {
    private val preferences = context.getSharedPreferences("life_planner_$userId", Context.MODE_PRIVATE)

    fun events(): List<LifeEvent> = decodeArray(preferences.getString(KEY_EVENTS, "[]").orEmpty()) { json ->
        LifeEvent(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = json.optString("title"),
            date = json.optString("date"),
            type = json.optString("type", "other"),
            note = json.optString("note"),
            reminderDays = json.optInt("reminder_days", 1).coerceIn(0, 30)
        )
    }.filter { it.title.isNotBlank() && runCatching { it.localDate() }.isSuccess }.sortedBy { it.localDate() }

    fun saveEvent(event: LifeEvent): List<LifeEvent> {
        val updated = events().filterNot { it.id == event.id } + event
        saveEvents(updated)
        return events()
    }

    fun deleteEvent(id: String): List<LifeEvent> {
        saveEvents(events().filterNot { it.id == id })
        return events()
    }

    fun wishes(): List<WishPlan> = decodeArray(preferences.getString(KEY_WISHES, "[]").orEmpty()) { json ->
        WishPlan(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = json.optString("title"),
            targetMinor = json.optLong("target_minor", 0).coerceAtLeast(0),
            savedMinor = json.optLong("saved_minor", 0).coerceAtLeast(0),
            targetDate = json.optString("target_date").takeIf(String::isNotBlank),
            note = json.optString("note")
        )
    }.filter { it.title.isNotBlank() }.sortedWith(compareBy<WishPlan> { it.targetDate ?: "9999-12-31" }.thenBy { it.title })

    fun saveWish(wish: WishPlan): List<WishPlan> {
        val updated = wishes().filterNot { it.id == wish.id } + wish
        saveWishes(updated)
        return wishes()
    }

    fun deleteWish(id: String): List<WishPlan> {
        saveWishes(wishes().filterNot { it.id == id })
        return wishes()
    }

    fun birthdaySettings(): BirthdaySettings = BirthdaySettings(
        calendarMode = preferences.getString(KEY_BIRTHDAY_MODE, "solar") ?: "solar",
        reminderEnabled = preferences.getBoolean(KEY_BIRTHDAY_REMINDER, true),
        reminderDays = preferences.getInt(KEY_BIRTHDAY_REMINDER_DAYS, 1).coerceIn(0, 30),
        showInLifeCalendar = preferences.getBoolean(KEY_BIRTHDAY_LIFE_CALENDAR, true)
    )

    fun saveBirthdaySettings(settings: BirthdaySettings) {
        preferences.edit()
            .putString(KEY_BIRTHDAY_MODE, settings.calendarMode)
            .putBoolean(KEY_BIRTHDAY_REMINDER, settings.reminderEnabled)
            .putInt(KEY_BIRTHDAY_REMINDER_DAYS, settings.reminderDays.coerceIn(0, 30))
            .putBoolean(KEY_BIRTHDAY_LIFE_CALENDAR, settings.showInLifeCalendar)
            .apply()
    }

    private fun saveEvents(events: List<LifeEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("id", event.id)
                    .put("title", event.title)
                    .put("date", event.date)
                    .put("type", event.type)
                    .put("note", event.note)
                    .put("reminder_days", event.reminderDays)
            )
        }
        preferences.edit().putString(KEY_EVENTS, array.toString()).apply()
    }

    private fun saveWishes(wishes: List<WishPlan>) {
        val array = JSONArray()
        wishes.forEach { wish ->
            array.put(
                JSONObject()
                    .put("id", wish.id)
                    .put("title", wish.title)
                    .put("target_minor", wish.targetMinor)
                    .put("saved_minor", wish.savedMinor)
                    .put("target_date", wish.targetDate)
                    .put("note", wish.note)
            )
        }
        preferences.edit().putString(KEY_WISHES, array.toString()).apply()
    }

    private fun <T> decodeArray(value: String, mapper: (JSONObject) -> T): List<T> = runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until array.length()) add(mapper(array.getJSONObject(index)))
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_EVENTS = "events"
        private const val KEY_WISHES = "wishes"
        private const val KEY_BIRTHDAY_MODE = "birthday_mode"
        private const val KEY_BIRTHDAY_REMINDER = "birthday_reminder"
        private const val KEY_BIRTHDAY_REMINDER_DAYS = "birthday_reminder_days"
        private const val KEY_BIRTHDAY_LIFE_CALENDAR = "birthday_life_calendar"
    }
}

data class LunarDate(val month: Int, val day: Int, val isLeapMonth: Boolean) {
    fun display(): String = "${if (isLeapMonth) "闰" else ""}${LUNAR_MONTHS[month - 1]}${LUNAR_DAYS[day - 1]}"
}

fun LocalDate.toLunarDate(): LunarDate {
    val calendar = ChineseCalendar().apply {
        timeInMillis = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    return LunarDate(
        month = calendar.get(Calendar.MONTH) + 1,
        day = calendar.get(Calendar.DAY_OF_MONTH),
        isLeapMonth = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1
    )
}

fun LocalDate.lunarLabel(): String = toLunarDate().display()

fun lunarToSolarDate(year: Int, month: Int, day: Int, leapMonth: Boolean = false): LocalDate? {
    if (year !in 1901..2100 || month !in 1..12 || day !in 1..30) return null
    var cursor = LocalDate.of(year, 1, 1)
    val end = LocalDate.of(year + 1, 3, 1)
    while (!cursor.isAfter(end)) {
        val lunar = cursor.toLunarDate()
        if (lunar.month == month && lunar.day == day && lunar.isLeapMonth == leapMonth) return cursor
        cursor = cursor.plusDays(1)
    }
    return null
}

fun LocalDate.calendarLabel(): String {
    calendarDayInfo().label.takeIf { it.isNotBlank() }?.let { return it }
    return lunarLabel()
}

fun LocalDate.calendarDayInfo(): CalendarDayInfo {
    val legal = LEGAL_HOLIDAYS[this]
    val workday = ADJUSTED_WORKDAYS[this]
    SOLAR_TERMS[monthValue to dayOfMonth]?.let {
        return CalendarDayInfo(it, isSolarTerm = true, isLegalHoliday = legal != null, isAdjustedWorkday = workday != null)
    }
    SOLAR_FESTIVALS[monthValue to dayOfMonth]?.let {
        return CalendarDayInfo(it, isMajorFestival = true, isLegalHoliday = legal != null, isAdjustedWorkday = workday != null)
    }
    val lunar = toLunarDate()
    LUNAR_FESTIVALS[lunar.month to lunar.day]?.let {
        return CalendarDayInfo(it, isMajorFestival = true, isLegalHoliday = legal != null, isAdjustedWorkday = workday != null)
    }
    return CalendarDayInfo(lunar.display(), isLegalHoliday = legal != null, isAdjustedWorkday = workday != null)
}

fun statutoryHolidayPlans(from: LocalDate = LocalDate.now()): List<LifeEvent> =
    LEGAL_HOLIDAY_STARTS
        .filter { !it.first.isBefore(from) }
        .map { (date, title) -> LifeEvent(id = "statutory_${date}", title = title, date = date.toString(), type = "statutory", note = "国家法定节假日") }
        .take(8)

private val LUNAR_MONTHS = listOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
private val LUNAR_DAYS = listOf(
    "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
    "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
)

private val SOLAR_FESTIVALS = mapOf(
    (1 to 1) to "元旦",
    (2 to 14) to "情人节",
    (3 to 8) to "妇女节",
    (4 to 4) to "清明",
    (5 to 1) to "劳动节",
    (5 to 4) to "青年节",
    (6 to 1) to "儿童节",
    (7 to 1) to "建党节",
    (8 to 1) to "建军节",
    (9 to 3) to "抗战胜利",
    (9 to 10) to "教师节",
    (10 to 1) to "国庆节",
    (10 to 24) to "联合国日",
    (11 to 11) to "双十一",
    (12 to 25) to "圣诞节"
)

private val LUNAR_FESTIVALS = mapOf(
    (1 to 1) to "春节",
    (1 to 15) to "元宵",
    (5 to 5) to "端午",
    (7 to 7) to "七夕",
    (7 to 15) to "中元",
    (8 to 15) to "中秋",
    (9 to 9) to "重阳",
    (12 to 8) to "腊八",
    (12 to 23) to "小年"
)

private val SOLAR_TERMS = mapOf(
    (1 to 5) to "小寒", (1 to 20) to "大寒",
    (2 to 4) to "立春", (2 to 19) to "雨水",
    (3 to 5) to "惊蛰", (3 to 20) to "春分",
    (4 to 4) to "清明", (4 to 20) to "谷雨",
    (5 to 5) to "立夏", (5 to 21) to "小满",
    (6 to 5) to "芒种", (6 to 21) to "夏至",
    (7 to 7) to "小暑", (7 to 22) to "大暑",
    (8 to 7) to "立秋", (8 to 23) to "处暑",
    (9 to 7) to "白露", (9 to 23) to "秋分",
    (10 to 8) to "寒露", (10 to 23) to "霜降",
    (11 to 7) to "立冬", (11 to 22) to "小雪",
    (12 to 7) to "大雪", (12 to 21) to "冬至"
)

private val LEGAL_HOLIDAYS: Map<LocalDate, String> = buildMap {
    fun mark(start: String, end: String, title: String) {
        var cursor = LocalDate.parse(start)
        val last = LocalDate.parse(end)
        while (!cursor.isAfter(last)) {
            put(cursor, title)
            cursor = cursor.plusDays(1)
        }
    }
    mark("2026-01-01", "2026-01-03", "元旦")
    mark("2026-02-15", "2026-02-23", "春节")
    mark("2026-04-04", "2026-04-06", "清明")
    mark("2026-05-01", "2026-05-05", "劳动节")
    mark("2026-06-19", "2026-06-21", "端午")
    mark("2026-09-25", "2026-09-27", "中秋")
    mark("2026-10-01", "2026-10-07", "国庆")
}

private val LEGAL_HOLIDAY_STARTS = listOf(
    LocalDate.parse("2026-01-01") to "元旦假期",
    LocalDate.parse("2026-02-15") to "春节假期",
    LocalDate.parse("2026-04-04") to "清明假期",
    LocalDate.parse("2026-05-01") to "劳动节假期",
    LocalDate.parse("2026-06-19") to "端午假期",
    LocalDate.parse("2026-09-25") to "中秋假期",
    LocalDate.parse("2026-10-01") to "国庆假期"
)

private val ADJUSTED_WORKDAYS = listOf(
    "2026-01-04",
    "2026-02-14",
    "2026-02-28",
    "2026-05-09",
    "2026-09-20",
    "2026-10-10"
).associate { LocalDate.parse(it) to "调休" }
