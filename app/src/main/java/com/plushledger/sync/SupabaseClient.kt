package com.plushledger.sync

import com.plushledger.BuildConfig
import com.plushledger.auth.AuthChannel
import com.plushledger.data.AccountEntity
import com.plushledger.data.BookEntity
import com.plushledger.data.BudgetEntity
import com.plushledger.data.CategoryEntity
import com.plushledger.data.ProfileEntity
import com.plushledger.data.TransactionEntity
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RemoteSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String?
)

class SupabaseClient {
    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
    val isConfigured: Boolean = baseUrl.startsWith("https://") && anonKey.isNotBlank()

    suspend fun sendOtp(channel: AuthChannel, identifier: String, shouldCreateUser: Boolean = true) {
        val body = JSONObject().apply {
            put("create_user", shouldCreateUser)
            when (channel) {
                AuthChannel.PHONE -> put("phone", identifier)
                AuthChannel.EMAIL -> put("email", identifier)
            }
        }
        requestObject("POST", "/auth/v1/otp", body)
    }

    suspend fun signInWithPassword(email: String, password: String): RemoteSession {
        val json = requestObject(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
        )
        return json.toRemoteSession()
    }

    suspend fun verifyOtp(channel: AuthChannel, identifier: String, token: String): RemoteSession {
        val body = JSONObject().apply {
            put("token", token)
            put("type", if (channel == AuthChannel.PHONE) "sms" else "email")
            when (channel) {
                AuthChannel.PHONE -> put("phone", identifier)
                AuthChannel.EMAIL -> put("email", identifier)
            }
        }
        val json = requestObject("POST", "/auth/v1/verify", body)
        return json.toRemoteSession()
    }

    suspend fun updatePassword(accessToken: String, password: String) {
        requestObject(
            method = "PUT",
            path = "/auth/v1/user",
            body = JSONObject().put("password", password),
            accessToken = accessToken
        )
    }

    suspend fun refreshSession(refreshToken: String): RemoteSession {
        val json = requestObject(
            method = "POST",
            path = "/auth/v1/token?grant_type=refresh_token",
            body = JSONObject().put("refresh_token", refreshToken)
        )
        return json.toRemoteSession(refreshToken)
    }

    suspend fun deleteAccount(accessToken: String) {
        request(
            method = "POST",
            path = "/functions/v1/delete-account",
            body = JSONObject(),
            accessToken = accessToken
        )
    }

    suspend fun submitFeedback(accessToken: String, userId: String, email: String?, content: String) {
        val payload = JSONArray().put(
            JSONObject()
                .put("user_id", userId)
                .put("email", email)
                .put("content", content)
        )
        request(
            method = "POST",
            path = "/rest/v1/feedback",
            body = payload,
            accessToken = accessToken,
            prefer = "return=minimal"
        )
    }

    suspend fun fetchOfficialMessages(accessToken: String): List<JSONObject> =
        fetchRows("official_messages", accessToken)

    suspend fun upsertRows(table: String, rows: List<*>, accessToken: String) {
        val payload = JSONArray()
        rows.filterNotNull().forEach { payload.put(it.toJson()) }
        request(
            method = "POST",
            path = "/rest/v1/$table?on_conflict=id",
            body = payload,
            accessToken = accessToken,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    suspend fun fetchRows(table: String, accessToken: String): List<JSONObject> {
        val text = request(
            method = "GET",
            path = "/rest/v1/$table?select=*&order=updated_at.asc",
            accessToken = accessToken
        )
        val array = if (text.isBlank()) JSONArray() else JSONArray(text)
        return List(array.length()) { index -> array.getJSONObject(index) }
    }

    private suspend fun requestObject(
        method: String,
        path: String,
        body: Any? = null,
        accessToken: String? = null,
        prefer: String? = null
    ): JSONObject {
        val text = request(method, path, body, accessToken, prefer)
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: Any? = null,
        accessToken: String? = null,
        prefer: String? = null
    ): String = withContext(Dispatchers.IO) {
        check(isConfigured) { "Supabase 未配置" }
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer ${accessToken ?: anonKey}")
            setRequestProperty("Content-Type", "application/json")
            prefer?.let { setRequestProperty("Prefer", it) }
            if (body != null) {
                doOutput = true
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }
        }

        val code = connection.responseCode
        val text = if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        if (code !in 200..299) error("Supabase 请求失败 $code: $text")
        text
    }
}

private fun JSONObject.toRemoteSession(fallbackRefreshToken: String? = null): RemoteSession = RemoteSession(
    userId = getJSONObject("user").getString("id"),
    accessToken = getString("access_token"),
    refreshToken = optString("refresh_token").ifBlank { fallbackRefreshToken }
)

private fun Any.toJson(): JSONObject = when (this) {
    is ProfileEntity -> JSONObject()
        .put("id", id)
        .put("display_name", displayName)
        .put("avatar_key", avatarKey)
        .put("phone", phone)
        .put("email", email)
        .put("wechat_bound", wechatBound)
        .put("qq_bound", qqBound)
        .put("agreement_version", agreementVersion)
        .put("agreed_at", agreedAt)
        .put("currency", currency)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
    is BookEntity -> JSONObject()
        .put("id", id)
        .put("user_id", userId)
        .put("name", name)
        .put("currency", currency)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
        .put("deleted_at", deletedAt)
    is AccountEntity -> JSONObject()
        .put("id", id)
        .put("user_id", userId)
        .put("book_id", bookId)
        .put("name", name)
        .put("kind", kind)
        .put("color_hex", colorHex)
        .put("initial_balance_minor", initialBalanceMinor)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
        .put("deleted_at", deletedAt)
    is CategoryEntity -> JSONObject()
        .put("id", id)
        .put("user_id", userId)
        .put("book_id", bookId)
        .put("name", name)
        .put("kind", kind)
        .put("color_hex", colorHex)
        .put("icon", icon)
        .put("sort_order", sortOrder)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
        .put("deleted_at", deletedAt)
    is TransactionEntity -> JSONObject()
        .put("id", id)
        .put("user_id", userId)
        .put("book_id", bookId)
        .put("type", type)
        .put("amount_minor", amountMinor)
        .put("currency", currency)
        .put("category_id", categoryId)
        .put("account_id", accountId)
        .put("to_account_id", toAccountId)
        .put("note", note)
        .put("occurred_at", occurredAt)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
        .put("deleted_at", deletedAt)
    is BudgetEntity -> JSONObject()
        .put("id", id)
        .put("user_id", userId)
        .put("book_id", bookId)
        .put("month", month)
        .put("category_id", categoryId)
        .put("limit_minor", limitMinor)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
        .put("deleted_at", deletedAt)
    else -> error("Unsupported sync row ${this::class.java.name}")
}
