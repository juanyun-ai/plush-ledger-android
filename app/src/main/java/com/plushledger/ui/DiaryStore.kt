package com.plushledger.ui

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class DiaryEntry(
    val date: String,
    val text: String,
    val mood: String,
    val status: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
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
        removeDeletedMarker(date)
        clearDraft(date)
        return updated
    }

    fun deleteEntry(date: String): List<DiaryEntry> {
        val updated = load().filterNot { it.date == date }
        save(updated)
        markDeleted(date)
        clearDraft(date)
        return updated
    }

    fun draft(date: String): DiaryEntry? = runCatching {
        current.getString("draft_$date", null)?.let { raw ->
            val item = JSONObject(raw)
            DiaryEntry(
                date = date,
                text = item.optString("text").trim(),
                mood = item.optString("mood", "开心"),
                status = item.optString("status")
            )
        }
    }.getOrNull()?.takeIf { it.text.isNotBlank() || it.status.isNotBlank() }

    fun saveDraft(date: String, text: String, mood: String, status: String = "") {
        val trimmed = text.trim()
        if (trimmed.isBlank() && status.isBlank()) {
            clearDraft(date)
            return
        }
        current.edit().putString(
            "draft_$date",
            JSONObject()
                .put("text", trimmed)
                .put("mood", mood)
                .put("status", status.trim())
                .toString()
        ).apply()
    }

    fun clearDraft(date: String) {
        current.edit().remove("draft_$date").apply()
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
                        .put("updated_at", entry.updatedAt)
                        .put("deleted_at", entry.deletedAt ?: JSONObject.NULL)
                )
            }
        }
        current.edit().putString("entries", payload.toString()).apply()
    }

    private fun markDeleted(date: String) {
        val deleted = deletedDates().toMutableMap()
        deleted[date] = System.currentTimeMillis()
        val payload = JSONObject()
        deleted.forEach { (key, value) -> payload.put(key, value) }
        current.edit().putString("deleted_entries", payload.toString()).apply()
    }

    private fun removeDeletedMarker(date: String) {
        val deleted = deletedDates().toMutableMap()
        if (deleted.remove(date) == null) return
        val payload = JSONObject()
        deleted.forEach { (key, value) -> payload.put(key, value) }
        current.edit().putString("deleted_entries", payload.toString()).apply()
    }

    private fun deletedDates(): Map<String, Long> = runCatching {
        val raw = current.getString("deleted_entries", "{}") ?: "{}"
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { key -> json.optLong(key, 0L) }.filterValues { it > 0L }
    }.getOrDefault(emptyMap())

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
                    status = item.optString("status"),
                    updatedAt = item.optLong("updated_at", 0L).takeIf { it > 0L } ?: 0L,
                    deletedAt = item.optLong("deleted_at", 0L).takeIf { it > 0L }
                )
                if (entry.date.isNotBlank() && entry.text.isNotBlank()) add(entry)
            }
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())
}
