package com.plushledger.ui

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Local, user-scoped preferences for pet customization and diary entries. */
data class PetDiaryEntry(
    val date: String,
    val text: String,
    val mood: String
)

data class PetPreferences(
    val mood: String = "元气",
    val lastInteractionDate: String = "",
    val outfit: String = "暖绒围巾",
    val skin: String = "暖阳奶油",
    val room: String = "奶油小窝",
    val frame: String = "默认头像框",
    val title: String = "绒绒伙伴",
    val diaries: List<PetDiaryEntry> = emptyList()
)

class PetStore(context: Context, userId: String) {
    private val preferences = context.getSharedPreferences("pet_rongrong_v3_$userId", Context.MODE_PRIVATE)

    fun load(): PetPreferences = runCatching {
        val entries = JSONArray(preferences.getString("diaries", "[]"))
        val diaries = buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                add(PetDiaryEntry(item.optString("date"), item.optString("text"), item.optString("mood", "元气")))
            }
        }.filter { it.date.isNotBlank() && it.text.isNotBlank() }
        PetPreferences(
            mood = preferences.getString("mood", "元气") ?: "元气",
            lastInteractionDate = preferences.getString("last_interaction", "") ?: "",
            outfit = preferences.getString("outfit", "暖绒围巾") ?: "暖绒围巾",
            skin = preferences.getString("skin", "暖阳奶油") ?: "暖阳奶油",
            room = preferences.getString("room", "奶油小窝") ?: "奶油小窝",
            frame = preferences.getString("frame", "默认头像框") ?: "默认头像框",
            title = preferences.getString("title", "绒绒伙伴") ?: "绒绒伙伴",
            diaries = diaries.sortedByDescending { it.date }
        )
    }.getOrElse { PetPreferences() }

    fun save(value: PetPreferences) {
        val array = JSONArray().apply {
            value.diaries.forEach { entry ->
                put(JSONObject().apply {
                    put("date", entry.date)
                    put("text", entry.text)
                    put("mood", entry.mood)
                })
            }
        }
        preferences.edit()
            .putString("mood", value.mood)
            .putString("last_interaction", value.lastInteractionDate)
            .putString("outfit", value.outfit)
            .putString("skin", value.skin)
            .putString("room", value.room)
            .putString("frame", value.frame)
            .putString("title", value.title)
            .putString("diaries", array.toString())
            .apply()
    }

    fun saveDiary(current: PetPreferences, text: String, mood: String): PetPreferences {
        val date = LocalDate.now().toString()
        val entry = PetDiaryEntry(date = date, text = text.trim(), mood = mood)
        val updated = (listOf(entry) + current.diaries.filterNot { it.date == date }).take(60)
        return current.copy(diaries = updated)
    }
}
