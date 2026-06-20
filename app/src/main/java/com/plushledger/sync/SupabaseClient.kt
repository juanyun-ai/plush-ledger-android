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

data class RemoteIdentity(
    val email: String?,
    val phone: String?
)

data class AppVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val backupApkUrl: String?,
    val sha256: String,
    val fileSizeBytes: Long,
    val releaseNotes: String,
    val isMandatory: Boolean
)

data class MembershipOrderInfo(
    val id: String,
    val provider: String,
    val amountMinor: Long,
    val status: String,
    val providerOrderId: String?,
    val createdAt: Long
)

data class RemoteAiLedgerParse(
    val type: String,
    val amountMinor: Long,
    val categoryName: String?,
    val parentCategoryName: String?,
    val accountName: String?,
    val note: String?,
    val occurredAt: Long?
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

    suspend fun requestIdentityChange(accessToken: String, channel: AuthChannel, identifier: String) {
        val body = JSONObject().put(
            if (channel == AuthChannel.EMAIL) "email" else "phone",
            identifier
        )
        requestObject("PUT", "/auth/v1/user", body, accessToken)
    }

    suspend fun verifyIdentityChange(accessToken: String, channel: AuthChannel, identifier: String, token: String) {
        val body = JSONObject()
            .put("type", if (channel == AuthChannel.EMAIL) "email_change" else "phone_change")
            .put("token", token)
            .put(if (channel == AuthChannel.EMAIL) "email" else "phone", identifier)
        requestObject("POST", "/auth/v1/verify", body, accessToken)
    }

    suspend fun fetchCurrentIdentity(accessToken: String): RemoteIdentity {
        val user = requestObject("GET", "/auth/v1/user", accessToken = accessToken)
        return RemoteIdentity(
            email = user.optString("email").takeIf(String::isNotBlank),
            phone = user.optString("phone").takeIf(String::isNotBlank)
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

    suspend fun parseAiLedger(
        accessToken: String,
        text: String,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>
    ): RemoteAiLedgerParse {
        val byId = categories.associateBy { it.id }
        val categoryPayload = JSONArray().apply {
            categories.filter { it.kind == "expense" || it.kind == "income" }.forEach { category ->
                put(
                    JSONObject()
                        .put("name", category.name)
                        .put("type", category.kind)
                        .put("parent", category.parentId?.let(byId::get)?.name)
                )
            }
        }
        val accountPayload = JSONArray().apply {
            accounts.forEach { account -> put(account.name) }
        }
        val response = requestObject(
            method = "POST",
            path = "/functions/v1/ai-ledger-parse",
            body = JSONObject()
                .put("text", text.take(160))
                .put("categories", categoryPayload)
                .put("accounts", accountPayload),
            accessToken = accessToken
        )
        return RemoteAiLedgerParse(
            type = response.optString("type", "expense"),
            amountMinor = response.optLong("amount_minor", 0),
            categoryName = response.optString("category_name").takeIf(String::isNotBlank),
            parentCategoryName = response.optString("category_parent").takeIf(String::isNotBlank),
            accountName = response.optString("account_name").takeIf(String::isNotBlank),
            note = response.optString("note").takeIf(String::isNotBlank),
            occurredAt = response.optLong("occurred_at", 0).takeIf { it > 0 }
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

    suspend fun createMembershipOrder(
        accessToken: String,
        userId: String,
        provider: String,
        providerOrderId: String
    ): MembershipOrderInfo {
        val payload = JSONArray().put(
            JSONObject()
                .put("user_id", userId)
                .put("provider", provider)
                .put("amount_minor", 1)
                .put("status", "pending")
                .put("provider_order_id", providerOrderId)
        )
        val text = request(
            method = "POST",
            path = "/rest/v1/membership_orders",
            body = payload,
            accessToken = accessToken,
            prefer = "return=representation"
        )
        val rows = if (text.isBlank()) JSONArray() else JSONArray(text)
        check(rows.length() > 0) { "会员订单创建失败" }
        return rows.getJSONObject(0).toMembershipOrderInfo()
    }

    suspend fun fetchOfficialMessages(accessToken: String? = null): List<JSONObject> {
        val text = request(
            method = "GET",
            path = "/rest/v1/official_messages?select=*&order=created_at.desc",
            accessToken = accessToken
        )
        val array = if (text.isBlank()) JSONArray() else JSONArray(text)
        return List(array.length()) { index -> array.getJSONObject(index) }
    }

    suspend fun fetchLatestAppVersion(): AppVersionInfo? {
        val text = request(
            method = "GET",
            path = "/rest/v1/app_versions?select=*&platform=eq.android&active=eq.true&order=version_code.desc&limit=1"
        )
        val rows = if (text.isBlank()) JSONArray() else JSONArray(text)
        if (rows.length() == 0) return null
        return rows.getJSONObject(0).toAppVersionInfo()
    }

    suspend fun fetchAppVersion(versionCode: Int): AppVersionInfo? {
        val text = request(
            method = "GET",
            path = "/rest/v1/app_versions?select=*&platform=eq.android&version_code=eq.$versionCode&active=eq.true&limit=1"
        )
        val rows = if (text.isBlank()) JSONArray() else JSONArray(text)
        return if (rows.length() == 0) null else rows.getJSONObject(0).toAppVersionInfo()
    }

    suspend fun uploadAvatar(accessToken: String, userId: String, bytes: ByteArray): String {
        val path = "$userId/avatar.jpg"
        uploadBinary(
            path = "/storage/v1/object/avatars/$path",
            bytes = bytes,
            contentType = "image/jpeg",
            accessToken = accessToken
        )
        return path
    }

    suspend fun createAvatarSignedUrl(accessToken: String, path: String): String {
        val json = requestObject(
            method = "POST",
            path = "/storage/v1/object/sign/avatars/$path",
            body = JSONObject().put("expiresIn", 3600),
            accessToken = accessToken
        )
        val signed = json.optString("signedURL").ifBlank { json.getString("signedUrl") }
        return "$baseUrl/storage/v1$signed"
    }

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

    private suspend fun uploadBinary(
        path: String,
        bytes: ByteArray,
        contentType: String,
        accessToken: String
    ) = withContext(Dispatchers.IO) {
        check(isConfigured) { "Supabase 未配置" }
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("x-upsert", "true")
            setFixedLengthStreamingMode(bytes.size)
        }
        connection.outputStream.use { it.write(bytes) }
        val code = connection.responseCode
        if (code !in 200..299) {
            val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("头像上传失败 $code: $text")
        }
    }
}

private fun JSONObject.toRemoteSession(fallbackRefreshToken: String? = null): RemoteSession = RemoteSession(
    userId = getJSONObject("user").getString("id"),
    accessToken = getString("access_token"),
    refreshToken = optString("refresh_token").ifBlank { fallbackRefreshToken }
)

private fun JSONObject.toAppVersionInfo() = AppVersionInfo(
    versionCode = getInt("version_code"),
    versionName = getString("version_name"),
    apkUrl = getString("apk_url"),
    backupApkUrl = optString("backup_apk_url").takeIf(String::isNotBlank),
    sha256 = getString("sha256"),
    fileSizeBytes = getLong("file_size_bytes"),
    releaseNotes = optString("release_notes"),
    isMandatory = optBoolean("is_mandatory", false)
)

private fun JSONObject.toMembershipOrderInfo() = MembershipOrderInfo(
    id = getString("id"),
    provider = getString("provider"),
    amountMinor = getLong("amount_minor"),
    status = getString("status"),
    providerOrderId = optString("provider_order_id").takeIf(String::isNotBlank),
    createdAt = getLong("created_at")
)

private fun Any.toJson(): JSONObject = when (this) {
    is ProfileEntity -> JSONObject()
        .put("id", id)
        .put("display_name", displayName)
        .put("avatar_key", avatarKey)
        .put("phone", phone)
        .put("email", email)
        .put("age", age)
        .put("birth_date", birthDate)
        .put("gender", gender)
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
        .put("parent_id", parentId)
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
