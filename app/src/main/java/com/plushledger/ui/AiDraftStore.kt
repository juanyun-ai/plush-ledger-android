package com.plushledger.ui

import android.content.Context

/** Local-only scratchpad for an unfinished AI bookkeeping sentence. */
class AiDraftStore(context: Context, userId: String) {
    private val preferences = context.getSharedPreferences("ai_ledger_draft_$userId", Context.MODE_PRIVATE)

    fun load(): String = preferences.getString(KEY_TEXT, "").orEmpty()

    fun save(text: String) {
        val value = text.trim().take(500)
        if (value.isBlank()) clear() else preferences.edit().putString(KEY_TEXT, value).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_TEXT).apply()
    }

    private companion object {
        const val KEY_TEXT = "text"
    }
}
