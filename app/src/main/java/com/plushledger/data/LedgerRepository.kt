package com.plushledger.data

import android.content.Context
import com.plushledger.auth.SessionStore
import com.plushledger.sync.AppVersionInfo
import com.plushledger.sync.SupabaseClient
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LedgerRepository(
    private val context: Context,
    private val dao: LedgerDao,
    private val sessionStore: SessionStore,
    private val supabaseClient: SupabaseClient
) {
    suspend fun ensureUserWorkspace(userId: String, displayName: String, phone: String? = null, email: String? = null) {
        val now = now()
        val existingProfile = dao.getProfile(userId)
        if (existingProfile == null) {
            dao.upsertProfile(
                ProfileEntity(
                    id = userId,
                    displayName = displayName,
                    phone = phone,
                    email = email,
                    role = "user",
                    membershipTier = "free",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        val book = dao.getDefaultBook(userId)
        val bookId = book?.id ?: newId()
        if (book == null) {
            dao.upsertBook(
                BookEntity(
                    id = bookId,
                    userId = userId,
                    name = "日常账本",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        if (dao.accountCount(userId) == 0) {
            dao.upsertAccounts(defaultAccounts(userId, bookId, now))
        }
        migrateDefaultAccounts(userId, bookId, now)
        if (dao.categoryCount(userId) == 0) {
            dao.upsertCategories(defaultCategories(userId, bookId, now))
        }
    }

    fun observeState(userId: String, month: YearMonth): Flow<LedgerState> {
        val monthText = month.toString()
        val ledgerFlow = combine(
            dao.observeBooks(userId),
            dao.observeAccounts(userId),
            dao.observeCategories(userId),
            dao.observeTransactions(userId),
            dao.observeBudgets(userId, monthText)
        ) { books, accounts, categories, transactions, budgets ->
            val monthTransactions = transactions.filter {
                YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()) == month
            }
            val expense = monthTransactions.filter { it.type == "expense" }.sumOf { it.amountMinor }
            val income = monthTransactions.filter { it.type == "income" }.sumOf { it.amountMinor }
            val balance = accounts.sumOf { it.initialBalanceMinor } + transactions.sumOf {
                when (it.type) {
                    "income" -> it.amountMinor
                    "expense" -> -it.amountMinor
                    else -> 0
                }
            }
            val categoriesById = categories.associateBy { it.id }
            val categorySpend = monthTransactions
                .filter { it.type == "expense" && it.categoryId != null }
                .groupBy { it.categoryId }
                .mapNotNull { (categoryId, records) ->
                    val category = categoriesById[categoryId] ?: return@mapNotNull null
                    CategorySpend(category, records.sumOf { it.amountMinor })
                }
                .sortedByDescending { it.amountMinor }
            val budgetLimit = budgets.sumOf { it.limitMinor }
            LedgerState(
                books = books,
                accounts = accounts,
                categories = categories,
                transactions = transactions,
                budgets = budgets,
                summary = LedgerSummary(
                    incomeMinor = income,
                    expenseMinor = expense,
                    balanceMinor = balance,
                    budgetLimitMinor = budgetLimit,
                    budgetUsedMinor = expense
                ),
                categorySpend = categorySpend
            )
        }
        return combine(ledgerFlow, dao.observeProfile(userId)) { ledgerState, profile ->
            ledgerState.copy(profile = profile)
        }
    }

    suspend fun addTransaction(
        userId: String,
        type: String,
        amountMinor: Long,
        categoryId: String?,
        accountId: String,
        toAccountId: String?,
        note: String,
        occurredAt: Long
    ) {
        val book = dao.getDefaultBook(userId) ?: return
        val now = now()
        dao.upsertTransaction(
            TransactionEntity(
                id = newId(),
                userId = userId,
                bookId = book.id,
                type = type,
                amountMinor = amountMinor,
                categoryId = categoryId,
                accountId = accountId,
                toAccountId = toAccountId,
                note = note.trim(),
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteTransaction(userId: String, id: String) {
        dao.softDeleteTransaction(id, userId, now())
    }

    suspend fun deleteAccount(userId: String, id: String) {
        dao.softDeleteAccount(id, userId, now())
    }

    suspend fun deleteCategory(userId: String, id: String) {
        dao.softDeleteCategory(id, userId, now())
    }

    suspend fun updateProfile(userId: String, displayName: String, avatarKey: String) {
        val current = dao.getProfile(userId) ?: return
        dao.upsertProfile(
            current.copy(
                displayName = displayName.trim().ifBlank { current.displayName },
                avatarKey = avatarKey,
                updatedAt = now(),
                syncState = SYNC_DIRTY
            )
        )
        sessionStore.updateDisplayName(displayName.trim().ifBlank { current.displayName })
    }

    suspend fun saveAvatar(userId: String, jpegBytes: ByteArray): String {
        val profile = dao.getProfile(userId) ?: error("用户资料不存在")
        val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
        val localFile = File(avatarDir, "$userId.jpg")
        withContext(Dispatchers.IO) { localFile.writeBytes(jpegBytes) }

        val session = sessionStore.currentSession()
        val avatarKey = if (session?.accessToken != null) {
            withFreshAccessToken { token -> supabaseClient.uploadAvatar(token, userId, jpegBytes) }
        } else {
            "file:${localFile.absolutePath}"
        }
        updateProfile(userId, profile.displayName, avatarKey)
        return if (avatarKey.startsWith("file:")) {
            "file://${localFile.absolutePath}"
        } else {
            withFreshAccessToken { token -> supabaseClient.createAvatarSignedUrl(token, avatarKey) }
        }
    }

    suspend fun resolveAvatarUrl(avatarKey: String?): String? {
        if (avatarKey.isNullOrBlank() || avatarKey == "sunny") return null
        if (avatarKey.startsWith("file:")) return "file://${avatarKey.removePrefix("file:")}"
        if (sessionStore.currentSession()?.accessToken == null) return null
        return runCatching {
            withFreshAccessToken { token -> supabaseClient.createAvatarSignedUrl(token, avatarKey) }
        }.getOrNull()
    }

    suspend fun latestAppVersion(): AppVersionInfo? =
        if (supabaseClient.isConfigured) supabaseClient.fetchLatestAppVersion() else null

    private suspend fun <T> withFreshAccessToken(block: suspend (String) -> T): T {
        val session = sessionStore.currentSession() ?: error("登录状态已失效，请重新登录")
        val token = session.accessToken ?: error("本地模式不需要云端凭证")
        return runCatching { block(token) }.recoverCatching { error ->
            val authExpired = error.message.orEmpty().let {
                it.contains("401") || it.contains("403") || it.contains("exp", ignoreCase = true) ||
                    it.contains("jwt", ignoreCase = true)
            }
            if (!authExpired) throw error
            val refreshToken = session.refreshToken ?: error("登录已过期，请退出后重新登录")
            val refreshed = supabaseClient.refreshSession(refreshToken)
            val updated = session.copy(
                userId = refreshed.userId,
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken
            )
            sessionStore.saveSession(updated)
            block(refreshed.accessToken)
        }.getOrThrow()
    }

    suspend fun markAgreementAccepted(userId: String) {
        val current = dao.getProfile(userId) ?: return
        val acceptedAt = now()
        dao.upsertProfile(
            current.copy(
                agreementVersion = CURRENT_AGREEMENT_VERSION,
                agreedAt = acceptedAt,
                updatedAt = acceptedAt,
                syncState = SYNC_DIRTY
            )
        )
    }

    suspend fun addAccount(userId: String, name: String, kind: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val book = dao.getDefaultBook(userId) ?: return
        val now = now()
        dao.upsertAccount(
            AccountEntity(
                id = newId(),
                userId = userId,
                bookId = book.id,
                name = trimmed,
                kind = kind,
                colorHex = "#D77B8C",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun addCategory(userId: String, name: String, kind: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val book = dao.getDefaultBook(userId) ?: return
        val now = now()
        dao.upsertCategory(
            CategoryEntity(
                id = newId(),
                userId = userId,
                bookId = book.id,
                name = trimmed,
                kind = kind,
                colorHex = if (kind == "expense") "#C86F7E" else "#5E9B83",
                icon = if (kind == "expense") "tag" else "spark",
                sortOrder = 999,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun setBudget(userId: String, month: YearMonth, categoryId: String?, limitMinor: Long) {
        val book = dao.getDefaultBook(userId) ?: return
        val now = now()
        dao.upsertBudget(
            BudgetEntity(
                id = "${userId}_${month}_${categoryId ?: "total"}",
                userId = userId,
                bookId = book.id,
                month = month.toString(),
                categoryId = categoryId,
                limitMinor = limitMinor,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun exportCsv(userId: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(dir, "plush-ledger-${LocalDate.now()}.csv")
        val records = dao.transactionsSnapshot(userId)
        file.writeText(
            buildString {
                appendLine("id,type,amount_cny,note,occurred_at")
                records.forEach {
                    val amount = Money.formatCny(it.amountMinor).replace(",", "")
                    appendLine("${it.id},${it.type},$amount,\"${it.note.replace("\"", "\"\"")}\",${it.occurredAt}")
                }
            }
        )
        file
    }

    suspend fun syncNow(): String {
        val session = sessionStore.currentSession() ?: return "未登录"
        if (!supabaseClient.isConfigured) return "Supabase 未配置，本地数据已安全保存"
        val token = session.accessToken ?: return "本地模式无需云同步"

        return runCatching {
            syncWithToken(session.userId, token)
        }.recoverCatching { error ->
            if (!error.message.orEmpty().contains("401")) throw error
            val refreshToken = session.refreshToken ?: throw error
            val refreshed = supabaseClient.refreshSession(refreshToken)
            sessionStore.saveSession(
                session.copy(
                    userId = refreshed.userId,
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken
                )
            )
            syncWithToken(refreshed.userId, refreshed.accessToken)
        }.getOrThrow()
    }

    private suspend fun syncWithToken(userId: String, token: String): String {
        syncTable("profiles", dao.dirtyProfiles(userId), token) { it.id }.also { if (it.isNotEmpty()) dao.markProfilesSynced(it) }
        syncTable("books", dao.dirtyBooks(userId), token) { it.id }.also { if (it.isNotEmpty()) dao.markBooksSynced(it) }
        syncTable("accounts", dao.dirtyAccounts(userId), token) { it.id }.also { if (it.isNotEmpty()) dao.markAccountsSynced(it) }
        syncTable("categories", dao.dirtyCategories(userId), token) { it.id }.also { if (it.isNotEmpty()) dao.markCategoriesSynced(it) }
        syncTable("transactions", dao.dirtyTransactions(userId), token) { it.id }.also { if (it.isNotEmpty()) dao.markTransactionsSynced(it) }
        syncTable("budgets", dao.dirtyBudgets(userId), token) { it.id }.also { if (it.isNotEmpty()) dao.markBudgetsSynced(it) }
        restoreFromCloud(token)
        return "同步完成"
    }

    suspend fun loadOfficialMessages(): List<OfficialMessage> {
        val session = sessionStore.currentSession() ?: return builtInMessages()
        val token = session.accessToken ?: return builtInMessages()
        return runCatching {
            supabaseClient.fetchOfficialMessages(token).map { json ->
                OfficialMessage(
                    id = json.getString("id"),
                    title = json.getString("title"),
                    body = json.getString("body"),
                    createdAt = json.optLong("created_at", now())
                )
            }.ifEmpty { builtInMessages() }
        }.getOrElse { builtInMessages() }
    }

    suspend fun submitFeedback(content: String) {
        val session = sessionStore.currentSession() ?: error("请先登录云端账号")
        val token = session.accessToken ?: error("本地模式暂不能发送建议")
        val trimmed = content.trim()
        require(trimmed.length in 5..500) { "建议内容需要 5-500 个字" }
        supabaseClient.submitFeedback(token, session.userId, session.email, trimmed)
    }

    suspend fun deleteCloudAccount() {
        val session = sessionStore.currentSession() ?: return
        session.accessToken?.let { supabaseClient.deleteAccount(it) }
        clearLocalUserData(session.userId)
        sessionStore.clearSession()
    }

    suspend fun clearLocalUserData(userId: String) {
        dao.deleteLocalTransactions(userId)
        dao.deleteLocalBudgets(userId)
        dao.deleteLocalCategories(userId)
        dao.deleteLocalAccounts(userId)
        dao.deleteLocalBooks(userId)
        dao.deleteLocalProfile(userId)
    }

    suspend fun restoreFromCloud(token: String) {
        if (!supabaseClient.isConfigured) return

        val profiles = supabaseClient.fetchRows("profiles", token).map { it.toProfile() }
        profiles.forEach { dao.upsertProfile(it) }
        profiles.map { it.id }.takeIf { it.isNotEmpty() }?.let { dao.markProfilesSynced(it) }

        val books = supabaseClient.fetchRows("books", token).map { it.toBook() }
        books.forEach { dao.upsertBook(it) }
        books.map { it.id }.takeIf { it.isNotEmpty() }?.let { dao.markBooksSynced(it) }

        val accounts = supabaseClient.fetchRows("accounts", token).map { it.toAccount() }
        dao.upsertAccounts(accounts)
        accounts.map { it.id }.takeIf { it.isNotEmpty() }?.let { dao.markAccountsSynced(it) }

        val categories = supabaseClient.fetchRows("categories", token).map { it.toCategory() }
        dao.upsertCategories(categories)
        categories.map { it.id }.takeIf { it.isNotEmpty() }?.let { dao.markCategoriesSynced(it) }

        val transactions = supabaseClient.fetchRows("transactions", token).map { it.toTransaction() }
        dao.upsertTransactions(transactions)
        transactions.map { it.id }.takeIf { it.isNotEmpty() }?.let { dao.markTransactionsSynced(it) }

        val budgets = supabaseClient.fetchRows("budgets", token).map { it.toBudget() }
        dao.upsertBudgets(budgets)
        budgets.map { it.id }.takeIf { it.isNotEmpty() }?.let { dao.markBudgetsSynced(it) }
    }

    private suspend fun <T> syncTable(table: String, rows: List<T>, token: String, id: (T) -> String): List<String> {
        if (rows.isEmpty()) return emptyList()
        supabaseClient.upsertRows(table, rows, token)
        return rows.map(id)
    }

    private fun defaultAccounts(userId: String, bookId: String, now: Long) = listOf(
        AccountEntity(newId(), userId, bookId, "银行卡", "bank", "#6E8DBF", createdAt = now, updatedAt = now),
        AccountEntity(newId(), userId, bookId, "微信", "wechat", "#5E9B83", createdAt = now, updatedAt = now),
        AccountEntity(newId(), userId, bookId, "支付宝", "alipay", "#5C8ED6", createdAt = now, updatedAt = now)
    )

    private suspend fun migrateDefaultAccounts(userId: String, bookId: String, now: Long) {
        val accounts = dao.accountsSnapshot(userId)
        accounts.filter { it.name == "零钱" || it.name == "支付宝/微信" }.forEach {
            dao.softDeleteAccount(it.id, userId, now)
        }
        val activeNames = dao.accountsSnapshot(userId).map { it.name }.toSet()
        val missing = defaultAccounts(userId, bookId, now).filter { it.name !in activeNames }
        if (missing.isNotEmpty()) dao.upsertAccounts(missing)
    }

    private fun defaultCategories(userId: String, bookId: String, now: Long): List<CategoryEntity> {
        val expense = listOf("餐饮", "交通", "购物", "住房", "娱乐", "医疗", "学习", "人情")
        val income = listOf("工资", "兼职", "理财", "礼金")
        return expense.mapIndexed { index, name ->
            CategoryEntity(newId(), userId, bookId, name, "expense", expenseColors[index % expenseColors.size], "tag", index, now, now)
        } + income.mapIndexed { index, name ->
            CategoryEntity(newId(), userId, bookId, name, "income", incomeColors[index % incomeColors.size], "spark", index, now, now)
        }
    }

    private fun now() = System.currentTimeMillis()
    private fun newId() = UUID.randomUUID().toString()

    private fun builtInMessages() = listOf(
        OfficialMessage(
            id = "welcome",
            title = "欢迎使用绒绒记账",
            body = "邮箱账号会同步到云端；本地模式只保存在当前设备。请定期确认同步状态。",
            createdAt = now()
        )
    )
}

data class LedgerState(
    val profile: ProfileEntity? = null,
    val books: List<BookEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val summary: LedgerSummary = LedgerSummary(),
    val categorySpend: List<CategorySpend> = emptyList()
)

data class OfficialMessage(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long
)

private const val CURRENT_AGREEMENT_VERSION = "2026-06-12"

private val expenseColors = listOf("#C86F7E", "#D99676", "#B86A77", "#E0A0A8", "#A86E89")
private val incomeColors = listOf("#5E9B83", "#6E8DBF", "#8AA46D", "#76A9A8")

private fun JSONObject.toProfile() = ProfileEntity(
    id = getString("id"),
    displayName = getString("display_name"),
    avatarKey = optString("avatar_key", "sunny"),
    phone = nullableString("phone"),
    email = nullableString("email"),
    role = optString("role", "user"),
    membershipTier = optString("membership_tier", "free"),
    wechatBound = optBoolean("wechat_bound", false),
    qqBound = optBoolean("qq_bound", false),
    agreementVersion = nullableString("agreement_version"),
    agreedAt = nullableLong("agreed_at"),
    currency = optString("currency", "CNY"),
    createdAt = getLong("created_at"),
    updatedAt = getLong("updated_at"),
    syncState = SYNC_SYNCED
)

private fun JSONObject.toBook() = BookEntity(
    id = getString("id"),
    userId = getString("user_id"),
    name = getString("name"),
    currency = optString("currency", "CNY"),
    createdAt = getLong("created_at"),
    updatedAt = getLong("updated_at"),
    deletedAt = nullableLong("deleted_at"),
    syncState = SYNC_SYNCED
)

private fun JSONObject.toAccount() = AccountEntity(
    id = getString("id"),
    userId = getString("user_id"),
    bookId = getString("book_id"),
    name = getString("name"),
    kind = getString("kind"),
    colorHex = optString("color_hex", "#D77B8C"),
    initialBalanceMinor = optLong("initial_balance_minor", 0),
    createdAt = getLong("created_at"),
    updatedAt = getLong("updated_at"),
    deletedAt = nullableLong("deleted_at"),
    syncState = SYNC_SYNCED
)

private fun JSONObject.toCategory() = CategoryEntity(
    id = getString("id"),
    userId = getString("user_id"),
    bookId = getString("book_id"),
    name = getString("name"),
    kind = getString("kind"),
    colorHex = optString("color_hex", "#C86F7E"),
    icon = optString("icon", "tag"),
    sortOrder = optInt("sort_order", 0),
    createdAt = getLong("created_at"),
    updatedAt = getLong("updated_at"),
    deletedAt = nullableLong("deleted_at"),
    syncState = SYNC_SYNCED
)

private fun JSONObject.toTransaction() = TransactionEntity(
    id = getString("id"),
    userId = getString("user_id"),
    bookId = getString("book_id"),
    type = getString("type"),
    amountMinor = getLong("amount_minor"),
    currency = optString("currency", "CNY"),
    categoryId = nullableString("category_id"),
    accountId = getString("account_id"),
    toAccountId = nullableString("to_account_id"),
    note = optString("note", ""),
    occurredAt = getLong("occurred_at"),
    createdAt = getLong("created_at"),
    updatedAt = getLong("updated_at"),
    deletedAt = nullableLong("deleted_at"),
    syncState = SYNC_SYNCED
)

private fun JSONObject.toBudget() = BudgetEntity(
    id = getString("id"),
    userId = getString("user_id"),
    bookId = getString("book_id"),
    month = getString("month"),
    categoryId = nullableString("category_id"),
    limitMinor = getLong("limit_minor"),
    createdAt = getLong("created_at"),
    updatedAt = getLong("updated_at"),
    deletedAt = nullableLong("deleted_at"),
    syncState = SYNC_SYNCED
)

private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else optString(name)
private fun JSONObject.nullableLong(name: String): Long? = if (isNull(name)) null else optLong(name)
