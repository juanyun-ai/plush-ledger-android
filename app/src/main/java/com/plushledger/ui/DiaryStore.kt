package com.plushledger.ui

import android.content.Context
import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class DiaryEntry(
    val date: String,
    val text: String,
    val mood: String,
    val status: String = "",
    val id: String = newDiaryId(date),
    val createdAt: Long = System.currentTimeMillis(),
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
        addEntry(LocalDate.now().toString(), text, mood, status)

    fun addEntry(date: String, text: String, mood: String, status: String = ""): List<DiaryEntry> {
        val entry = DiaryEntry(date = date, text = text.trim(), mood = mood, status = status.trim())
        val updated = (listOf(entry) + load())
            .dedupeActiveEntries()
            .take(180)
        save(updated)
        clearDraft(date)
        return updated
    }

    fun saveEntry(entry: DiaryEntry): List<DiaryEntry> {
        val now = System.currentTimeMillis()
        val normalized = entry.copy(
            id = entry.id.ifBlank { newDiaryId(entry.date) },
            text = entry.text.trim(),
            mood = entry.mood,
            status = entry.status.trim(),
            updatedAt = now
        )
        val updated = (listOf(normalized) + load().filterNot { it.id == normalized.id })
            .sortedWith(compareByDescending<DiaryEntry> { it.date }.thenByDescending { it.updatedAt })
            .dedupeActiveEntries()
            .take(180)
        save(updated)
        clearEntryDraft(normalized.id)
        return updated
    }

    fun saveEntry(date: String, text: String, mood: String, status: String = ""): List<DiaryEntry> =
        addEntry(date, text, mood, status)

    fun deleteEntry(entry: DiaryEntry): List<DiaryEntry> {
        val updated = load().filterNot { it.id == entry.id }
        save(updated)
        markDeleted(entry)
        clearDraft(entry.date)
        clearEntryDraft(entry.id)
        return updated
    }

    fun mergeEntries(ids: Set<String>): List<DiaryEntry> {
        val all = load()
        val selected = all.filter { it.id in ids }.sortedWith(compareBy<DiaryEntry> { it.date }.thenBy { it.createdAt })
        if (selected.size < 2 || selected.map { it.date }.distinct().size != 1) return all
        val date = selected.first().date
        val latest = selected.maxBy { it.updatedAt }
        val merged = DiaryEntry(
            date = date,
            text = selected.joinToString("\n\n") { it.text },
            mood = latest.mood,
            status = latest.status,
            id = newDiaryId(date),
            createdAt = selected.minOf { it.createdAt },
            updatedAt = System.currentTimeMillis()
        )
        selected.forEach(::markDeleted)
        selected.forEach { clearEntryDraft(it.id) }
        val updated = (listOf(merged) + all.filterNot { it.id in ids })
            .sortedWith(compareByDescending<DiaryEntry> { it.date }.thenByDescending { it.updatedAt })
            .dedupeActiveEntries()
            .take(180)
        save(updated)
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

    fun entryDraft(entryId: String): DiaryEntry? = runCatching {
        current.getString("entry_draft_$entryId", null)?.let { raw ->
            val item = JSONObject(raw)
            DiaryEntry(
                date = item.optString("date"),
                text = item.optString("text"),
                mood = item.optString("mood", "开心"),
                status = item.optString("status"),
                id = item.optString("id", entryId),
                createdAt = item.optLong("created_at", 0L),
                updatedAt = item.optLong("updated_at", 0L)
            )
        }
    }.getOrNull()

    fun saveEntryDraft(entry: DiaryEntry) {
        val now = System.currentTimeMillis()
        current.edit().putString(
            "entry_draft_${entry.id}",
            JSONObject()
                .put("date", entry.date)
                .put("id", entry.id)
                .put("text", entry.text)
                .put("mood", entry.mood)
                .put("status", entry.status.trim())
                .put("created_at", entry.createdAt.takeIf { it > 0L } ?: now)
                .put("updated_at", now)
                .toString()
        ).apply()
    }

    fun clearEntryDraft(entryId: String) {
        current.edit().remove("entry_draft_$entryId").apply()
    }

    private fun save(entries: List<DiaryEntry>) {
        val cleaned = entries.dedupeActiveEntries()
        val payload = JSONArray().apply {
            cleaned.forEach { entry ->
                put(
                    JSONObject()
                        .put("date", entry.date)
                        .put("id", entry.id)
                        .put("text", entry.text)
                        .put("mood", entry.mood)
                        .put("status", entry.status)
                        .put("created_at", entry.createdAt)
                        .put("updated_at", entry.updatedAt)
                        .put("deleted_at", entry.deletedAt ?: JSONObject.NULL)
                )
            }
        }
        current.edit().putString("entries", payload.toString()).apply()
    }

    private fun markDeleted(entry: DiaryEntry) {
        val deleted = deletedEntries().toMutableMap()
        deleted[entry.id] = entry.copy(text = "", deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        val payload = JSONObject()
        deleted.forEach { (key, value) ->
            payload.put(
                key,
                JSONObject()
                    .put("id", value.id)
                    .put("date", value.date)
                    .put("updated_at", value.updatedAt)
                    .put("deleted_at", value.deletedAt ?: value.updatedAt)
            )
        }
        current.edit().putString("deleted_entries", payload.toString()).apply()
    }

    private fun deletedEntries(): Map<String, DiaryEntry> = runCatching {
        val raw = current.getString("deleted_entries", "{}") ?: "{}"
        val json = JSONObject(raw)
        json.keys().asSequence().mapNotNull { key ->
            val value = json.opt(key)
            if (value is JSONObject) {
                val id = value.optString("id", key)
                val date = value.optString("date").ifBlank { key.takeIf { it.length == 10 } ?: LocalDate.now().toString() }
                id to DiaryEntry(
                    date = date,
                    text = "",
                    mood = "开心",
                    id = id,
                    createdAt = value.optLong("created_at", value.optLong("updated_at", 0L)),
                    updatedAt = value.optLong("updated_at", value.optLong("deleted_at", 0L)),
                    deletedAt = value.optLong("deleted_at", 0L).takeIf { it > 0L }
                )
            } else {
                val deletedAt = json.optLong(key, 0L)
                if (deletedAt > 0L) {
                    key to DiaryEntry(date = key, text = "", mood = "开心", id = "legacy:$key", updatedAt = deletedAt, deletedAt = deletedAt)
                } else {
                    null
                }
            }
        }.toMap()
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
                    id = item.optString("id").ifBlank { "legacy:${item.optString("date")}:${item.optLong("updated_at", 0L)}" },
                    createdAt = item.optLong("created_at", 0L).takeIf { it > 0L } ?: item.optLong("updated_at", 0L).takeIf { it > 0L } ?: 0L,
                    updatedAt = item.optLong("updated_at", 0L).takeIf { it > 0L } ?: 0L,
                    deletedAt = item.optLong("deleted_at", 0L).takeIf { it > 0L }
                )
                if (entry.date.isNotBlank() && entry.text.isNotBlank()) add(entry)
            }
        }
            .dedupeActiveEntries()
            .sortedWith(compareByDescending<DiaryEntry> { it.date }.thenByDescending { it.updatedAt })
    }.getOrDefault(emptyList())
}

private fun newDiaryId(date: String): String =
    "diary:${date}:${UUID.randomUUID()}"

private fun List<DiaryEntry>.dedupeActiveEntries(): List<DiaryEntry> {
    val latestById = valuesByLatest { it.id }
    return latestById
        .values
        .toList()
        .valuesByLatest { it.contentSignature() }
        .values
        .sortedWith(compareByDescending<DiaryEntry> { it.date }.thenByDescending { it.updatedAt })
}

private fun List<DiaryEntry>.valuesByLatest(key: (DiaryEntry) -> String): LinkedHashMap<String, DiaryEntry> {
    val result = LinkedHashMap<String, DiaryEntry>()
    forEach { entry ->
        val current = result[key(entry)]
        if (current == null || entry.updatedAt >= current.updatedAt) {
            result[key(entry)] = entry
        }
    }
    return result
}

private fun DiaryEntry.contentSignature(): String =
    listOf(
        date,
        text.replace(Regex("\\s+"), " ").trim(),
        mood.trim(),
        status.trim()
    ).joinToString("|")
