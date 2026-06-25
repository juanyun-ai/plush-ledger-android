package com.plushledger.ui

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class DiaryEntry(
    val date: String,
    val text: String,
    val mood: String,
    val status: String = ""
)

class DiaryStore(context: Context, private val userId: String) {
    private val current = context.getSharedPreferences("rongrong_diary_$userId", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("pet_rongrong_v3_$userId", Context.MODE_PRIVATE)

    init {
        migrateLegacyEntries()
    }

    fun load(): List<DiaryEntry> = decode(current.getString("entries", "[]"))

    fun saveToday(text: String, mood: String, status: String = ""): List<DiaryEntry> =
        saveEntry(LocalDate.now().toString(), text, mood, status)

    fun saveEntry(date: String, text: String, mood: String, status: String = ""): List<DiaryEntry> {
        val updated = (listOf(DiaryEntry(date, text.trim(), mood, status.trim())) + load().filterNot { it.date == date })
            .take(180)
        save(updated)
        return updated
    }

    private fun save(entries: List<DiaryEntry>) {
        val payload = JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject()
                        .put("date", entry.date)
                        .put("text", entry.text)
                        .put("mood", entry.mood)
                        .put("status", entry.status)
                )
            }
        }
        current.edit().putString("entries", payload.toString()).apply()
    }

    private fun migrateLegacyEntries() {
        if (current.contains("entries")) return
        val migrated = decode(legacy.getString("diaries", "[]"))
        save(migrated)
    }

    private fun decode(raw: String?): List<DiaryEntry> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val entry = DiaryEntry(
                    date = item.optString("date"),
                    text = item.optString("text").trim(),
                    mood = item.optString("mood", "开心"),
                    status = item.optString("status")
                )
                if (entry.date.isNotBlank() && entry.text.isNotBlank()) add(entry)
            }
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())
}
