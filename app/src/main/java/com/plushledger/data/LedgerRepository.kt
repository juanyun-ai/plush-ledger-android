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
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        } else if ((existingProfile.avatarKey.isBlank() || existingProfile.avatarKey == "sunny") && cachedAvatarFile(userId).exists()) {
            dao.upsertProfile(
                existingProfile.copy(
                    avatarKey = "file:${cachedAvatarFile(userId).absolutePath}",
                    updatedAt = now,
                    syncState = SYNC_DIRTY
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
        migrateDefaultCategories(userId, bookId, now)
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
            val usageByCategory = transactions
                .mapNotNull { it.categoryId }
                .groupingBy { it }
                .eachCount()
            val orderedCategories = categories.sortedWith(
                compareBy<CategoryEntity> { if (it.kind == "expense") 0 else 1 }
                    .thenBy { if (it.parentId == null) 0 else 1 }
                    .thenByDescending { usageByCategory[it.id] ?: 0 }
                    .thenBy { it.sortOrder }
            )
            val categoriesById = orderedCategories.associateBy { it.id }
            val categorySpend = monthTransactions
                .filter { it.type == "expense" && it.categoryId != null }
                .groupBy { transaction ->
                    transaction.categoryId
                        ?.let(categoriesById::get)
                        ?.let { CategoryCatalog.rootOf(it, categoriesById).id }
                }
                .mapNotNull { (rootCategoryId, records) ->
                    val category = rootCategoryId?.let(categoriesById::get) ?: return@mapNotNull null
                    CategorySpend(
                        category = category,
                        amountMinor = records.sumOf { it.amountMinor },
                        memberCategoryIds = records.mapNotNull { it.categoryId }.toSet()
                    )
                }
                .sortedByDescending { it.amountMinor }
            val budgetLimit = budgets.sumOf { it.limitMinor }
            LedgerState(
                books = books,
                accounts = accounts,
                categories = orderedCategories,
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

    suspend fun importExternalBills(userId: String, preview: ExternalBillPreview): ExternalBillImportResult {
        val book = dao.getDefaultBook(userId) ?: error("默认账本不存在")
        val categories = dao.categoriesSnapshot(userId)
        val accounts = dao.accountsSnapshot(userId)
        val providerAccount = accounts.firstOrNull { account ->
            account.name.contains(preview.provider) || preview.provider.contains(account.name)
        } ?: accounts.firstOrNull { it.name == "现金" } ?: accounts.firstOrNull()
            ?: error("请先创建一个账户")
        val now = now()
        val records = preview.entries.map { entry ->
            val recognition = LocalAiLedgerParser.parse(
                "${entry.note} ${entry.amountMinor / 100.0} ${preview.provider} ${entry.accountHint}",
                categories,
                accounts
            )
            val account = accounts.firstOrNull { account ->
                entry.accountHint.contains(account.name) || account.name.contains(entry.accountHint)
            } ?: providerAccount
            TransactionEntity(
                id = deterministicImportId(userId, preview.provider, entry.sourceId),
                userId = userId,
                bookId = book.id,
                type = entry.type,
                amountMinor = entry.amountMinor,
                categoryId = recognition?.categoryId,
                accountId = account.id,
                note = entry.note.ifBlank { "${preview.provider}账单" },
                occurredAt = entry.occurredAt,
                createdAt = now,
                updatedAt = now
            )
        }
        dao.upsertTransactions(records)
        return ExternalBillImportResult(records.size, preview.skippedRows)
    }

    suspend fun updateProfile(userId: String, displayName: String, age: Int?, birthDate: String?, gender: String?) {
        val current = dao.getProfile(userId) ?: return
        dao.upsertProfile(
            current.copy(
                displayName = displayName.trim().ifBlank { current.displayName },
                age = age,
                birthDate = birthDate,
                gender = gender,
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
        dao.upsertProfile(profile.copy(avatarKey = avatarKey, updatedAt = now(), syncState = SYNC_DIRTY))
        return "file://${localFile.absolutePath}"
    }

    suspend fun resolveAvatarUrl(avatarKey: String?): String? {
        sessionStore.currentSession()?.userId?.let { userId ->
            val cached = cachedAvatarFile(userId)
            if (cached.exists()) return "file://${cached.absolutePath}"
        }
        if (avatarKey.isNullOrBlank() || avatarKey == "sunny") return null
        if (avatarKey.startsWith("file:")) return "file://${avatarKey.removePrefix("file:")}"
        if (sessionStore.currentSession()?.accessToken == null) return null
        return runCatching {
            withFreshAccessToken { token -> supabaseClient.createAvatarSignedUrl(token, avatarKey) }
        }.getOrNull()
    }

    suspend fun updateProfileIdentity(userId: String, email: String?, phone: String?) {
        val current = dao.getProfile(userId) ?: return
        dao.upsertProfile(
            current.copy(
                email = email,
                phone = phone,
                updatedAt = now(),
                syncState = SYNC_DIRTY
            )
        )
        sessionStore.updateIdentity(email, phone)
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

    suspend fun moveCategory(userId: String, categoryId: String, direction: Int) {
        if (direction == 0) return
        val all = dao.categoriesSnapshot(userId)
        val current = all.firstOrNull { it.id == categoryId } ?: return
        val siblings = all.filter { it.kind == current.kind && it.parentId == current.parentId }.sortedBy { it.sortOrder }
        val from = siblings.indexOfFirst { it.id == categoryId }
        if (from == -1) return
        val to = (from + direction).coerceIn(0, siblings.lastIndex)
        if (from == to) return
        val reordered = siblings.toMutableList().apply { add(to, removeAt(from)) }
        val now = now()
        dao.upsertCategories(
            reordered.mapIndexed { index, item ->
                item.copy(sortOrder = index, updatedAt = now, syncState = SYNC_DIRTY)
            }
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

    suspend fun seedDemoData(userId: String) {
        if (dao.transactionsSnapshot(userId).isNotEmpty()) return
        val book = dao.getDefaultBook(userId) ?: return
        val now = now()
        val activeAccounts = dao.accountsSnapshot(userId)
        val cash = activeAccounts.firstOrNull { it.name == "现金" } ?: AccountEntity(
            id = newId(),
            userId = userId,
            bookId = book.id,
            name = "现金",
            kind = "cash",
            colorHex = "#F9A35E",
            initialBalanceMinor = 125_650,
            createdAt = now,
            updatedAt = now
        ).also { dao.upsertAccount(it) }
        activeAccounts.firstOrNull { it.name == "微信" }?.let { dao.upsertAccount(it.copy(initialBalanceMinor = 68_000, updatedAt = now)) }
        activeAccounts.firstOrNull { it.name == "支付宝" }?.let { dao.upsertAccount(it.copy(initialBalanceMinor = 215_000, updatedAt = now)) }
        activeAccounts.firstOrNull { it.name.contains("银行") }?.let { dao.upsertAccount(it.copy(name = "银行卡", initialBalanceMinor = 350_000, updatedAt = now)) }

        val categories = defaultCategoriesForUser(userId)
        val category = categories.associateBy { it.name }
        val accounts = dao.accountsSnapshot(userId).associateBy { it.name }
        val month = YearMonth.now()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val day3 = month.atDay((today.dayOfMonth - 2).coerceAtLeast(1))
        val day4 = month.atDay((today.dayOfMonth - 3).coerceAtLeast(1))
        val day5 = month.atDay((today.dayOfMonth - 4).coerceAtLeast(1))

        val rows = listOf(
            demoTransaction(userId, book.id, "expense", 3_200, category["正餐"]?.id, cash.id, "今天 · 12:30", today.atTime(12, 30), now),
            demoTransaction(userId, book.id, "expense", 1_850, category["公交地铁"]?.id, cash.id, "今天 · 08:15", today.atTime(8, 15), now),
            demoTransaction(userId, book.id, "expense", 16_800, category["日用百货"]?.id, accounts["支付宝"]?.id ?: cash.id, "昨天 · 20:45", yesterday.atTime(20, 45), now),
            demoTransaction(userId, book.id, "income", 120_000, category["兼职"]?.id ?: category["工资"]?.id, accounts["银行卡"]?.id ?: cash.id, "昨天 · 18:30", yesterday.atTime(18, 30), now),
            demoTransaction(userId, book.id, "expense", 8_700, category["书籍资料"]?.id, cash.id, "买书 · 设计心理学", day3.atTime(10, 30), now),
            demoTransaction(userId, book.id, "expense", 10_000, category["生活用品"]?.id, accounts["微信"]?.id ?: cash.id, "生活用品 · 超市", day3.atTime(12, 20), now),
            demoTransaction(userId, book.id, "expense", 26_000, category["影视会员"]?.id, cash.id, "电影 · 周末放松", day4.atTime(18, 30), now),
            demoTransaction(userId, book.id, "expense", 59_100, category["水电房租"]?.id, accounts["银行卡"]?.id ?: cash.id, "房租 · 月度", day5.atTime(9, 0), now),
            demoTransaction(userId, book.id, "income", 248_000, category["工资"]?.id, accounts["银行卡"]?.id ?: cash.id, "6月工资", day5.atTime(9, 30), now)
        )
        dao.upsertTransactions(rows)
        dao.upsertBudgets(
            listOf(
                BudgetEntity(newId(), userId, book.id, month.toString(), null, 600_000, now, now),
                BudgetEntity(newId(), userId, book.id, month.toString(), category["餐饮"]?.id, 150_000, now, now),
                BudgetEntity(newId(), userId, book.id, month.toString(), category["交通"]?.id, 100_000, now, now),
                BudgetEntity(newId(), userId, book.id, month.toString(), category["购物"]?.id, 180_000, now, now),
                BudgetEntity(newId(), userId, book.id, month.toString(), category["娱乐"]?.id, 80_000, now, now)
            )
        )
    }

    private suspend fun defaultCategoriesForUser(userId: String): List<CategoryEntity> {
        val existing = dao.categoriesSnapshot(userId)
        return existing
    }

    private fun demoTransaction(
        userId: String,
        bookId: String,
        type: String,
        amountMinor: Long,
        categoryId: String?,
        accountId: String,
        note: String,
        dateTime: java.time.LocalDateTime,
        now: Long
    ) = TransactionEntity(
        id = newId(),
        userId = userId,
        bookId = bookId,
        type = type,
        amountMinor = amountMinor,
        categoryId = categoryId,
        accountId = accountId,
        note = note,
        occurredAt = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        createdAt = now,
        updatedAt = now
    )

    suspend fun exportCsv(userId: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(dir, "plush-ledger-${LocalDate.now()}.csv")
        val records = dao.transactionsSnapshot(userId)
        val categories = dao.categoriesSnapshot(userId).associateBy { it.id }
        val accounts = dao.accountsSnapshot(userId).associateBy { it.id }
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        file.writeText(
            buildString {
                appendLine("\ufeffid,date,time,datetime,occurred_at,created_at,updated_at,type,type_label,category,category_parent,category_parent_id,category_path,category_level,category_kind,category_icon,category_color,account,account_kind,to_account,amount_minor,amount_cny,currency,note")
                records.forEach {
                    val occurred = Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())
                    val category = it.categoryId?.let(categories::get)
                    val parentCategory = category?.parentId?.let(categories::get)
                    val account = accounts[it.accountId]
                    val toAccount = accounts[it.toAccountId]
                    val amount = "%.2f".format(Locale.US, it.amountMinor / 100.0)
                    val typeLabel = when (it.type) {
                        "income" -> "收入"
                        "transfer" -> "转账"
                        else -> "支出"
                    }
                    appendLine(
                        listOf(
                            it.id,
                            occurred.toLocalDate().toString(),
                            occurred.toLocalTime().format(timeFormatter),
                            occurred.format(dateTimeFormatter),
                            it.occurredAt.toString(),
                            it.createdAt.toString(),
                            it.updatedAt.toString(),
                            it.type,
                            typeLabel,
                            category?.name.orEmpty(),
                            parentCategory?.name.orEmpty(),
                            parentCategory?.id.orEmpty(),
                            listOfNotNull(parentCategory?.name, category?.name).joinToString("/"),
                            if (parentCategory == null) "1" else "2",
                            category?.kind.orEmpty(),
                            category?.icon.orEmpty(),
                            category?.colorHex.orEmpty(),
                            account?.name.orEmpty(),
                            account?.kind.orEmpty(),
                            toAccount?.name.orEmpty(),
                            it.amountMinor.toString(),
                            amount,
                            it.currency,
                            it.note
                        ).joinToString(",") { value -> csvCell(value) }
                    )
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
        val token = sessionStore.currentSession()?.accessToken
        return runCatching {
            supabaseClient.fetchOfficialMessages(token).map { json ->
                val sourceKey = json.nullableString("source_key")
                val versionCode = sourceKey
                    ?.takeIf { it.startsWith("release:android:") }
                    ?.substringAfterLast(':')
                    ?.toIntOrNull()
                OfficialMessage(
                    id = json.getString("id"),
                    title = json.getString("title"),
                    body = json.getString("body"),
                    createdAt = json.optLong("created_at", now()),
                    updateInfo = versionCode?.let { supabaseClient.fetchAppVersion(it) }
                )
            }.ifEmpty { builtInMessages() }
        }.getOrElse { builtInMessages() }
    }

    suspend fun analyzeAiEntry(text: String, ledgerState: LedgerState): AiLedgerAnalysis? {
        val local = LocalAiLedgerParser.parse(text, ledgerState.categories, ledgerState.accounts) ?: return null
        val session = sessionStore.currentSession()
        val token = session?.accessToken ?: return local
        val remote = runCatching {
            supabaseClient.parseAiLedger(token, text, ledgerState.categories, ledgerState.accounts)
        }.getOrNull() ?: return local
        if (remote.amountMinor <= 0) return local
        val type = remote.type.takeIf { it == "income" || it == "expense" } ?: local.type
        val category = LocalAiLedgerParser.resolveCategory(
            type = type,
            categoryName = remote.categoryName,
            parentName = remote.parentCategoryName,
            categories = ledgerState.categories,
            sourceText = text
        )
        val account = LocalAiLedgerParser.resolveAccount(remote.accountName, ledgerState.accounts, text)
        return AiLedgerAnalysis(
            sourceText = text.trim().take(160),
            type = type,
            amountMinor = remote.amountMinor,
            categoryId = category?.id,
            categoryLabel = category?.name ?: "无法归类",
            accountId = account?.id,
            accountLabel = account?.name ?: "默认账户",
            note = remote.note ?: text.trim().take(80),
            occurredAt = remote.occurredAt ?: System.currentTimeMillis(),
            cloudAssisted = true
        )
    }

    suspend fun submitFeedback(content: String) {
        val session = sessionStore.currentSession() ?: error("请先登录云端账号")
        val token = session.accessToken ?: error("本地模式暂不能发送建议")
        val trimmed = content.trim()
        require(trimmed.length in 5..500) { "建议内容需要 5-500 个字" }
        supabaseClient.submitFeedback(token, session.userId, session.email, trimmed)
    }

    suspend fun createMembershipOrder(providerName: String, reference: String): String {
        val session = sessionStore.currentSession() ?: error("请先登录云端账号")
        if (session.accessToken == null) error("会员充值需要先使用邮箱或手机号登录云端账号")
        val provider = when {
            providerName.contains("微信") || providerName.equals("wechat", ignoreCase = true) -> "wechat"
            providerName.contains("支付宝") || providerName.equals("alipay", ignoreCase = true) -> "alipay"
            else -> error("请选择微信或支付宝")
        }
        val trimmed = reference.trim()
        require(trimmed.length in 2..80) { "请填写交易单号后 6 位、付款备注或截图编号，方便核验" }
        val order = withFreshAccessToken { token ->
            supabaseClient.createMembershipOrder(token, session.userId, provider, trimmed)
        }
        return "会员付款核验已提交：${order.id.take(8)}"
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
        AccountEntity(newId(), userId, bookId, "现金", "cash", "#F9A35E", createdAt = now, updatedAt = now),
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

    private suspend fun migrateDefaultCategories(userId: String, bookId: String, now: Long) {
        val existing = dao.categoriesSnapshot(userId)
        val defaults = CategoryCatalog.defaultCategories(userId, bookId, now)
        val existingByName = existing.groupBy { it.kind to it.name }
        val resolvedIds = mutableMapOf<String, String>()
        CategoryCatalog.specs.filter { it.parentKey == null }.forEach { spec ->
            val default = defaults.first { it.name == spec.name && it.kind == spec.kind }
            val existingRoot = existingByName[spec.kind to spec.name]?.firstOrNull()
            resolvedIds[spec.key] = existingRoot?.id ?: default.id
        }

        val merged = defaults.map { default ->
            val spec = CategoryCatalog.specs.first { it.name == default.name && it.kind == default.kind && it.icon == default.icon }
            val existingMatch = sequenceOf(default.name)
                .plus(CategoryCatalog.legacyNamesFor(spec.key).asSequence())
                .mapNotNull { name -> existingByName[default.kind to name]?.firstOrNull() }
                .firstOrNull()
            val parentId = spec.parentKey?.let(resolvedIds::get) ?: default.parentId
            val target = (existingMatch ?: default).copy(
                userId = userId,
                bookId = bookId,
                name = default.name,
                kind = default.kind,
                colorHex = default.colorHex,
                icon = default.icon,
                sortOrder = default.sortOrder,
                parentId = parentId,
                updatedAt = now,
                syncState = SYNC_DIRTY
            )
            target
        }.toMutableList()

        existing.forEach { category ->
            val parentKey = CategoryCatalog.legacyParentKey(category.name) ?: return@forEach
            val parentId = resolvedIds[parentKey] ?: return@forEach
            if (category.parentId != parentId && merged.none { it.id == category.id }) {
                merged += category.copy(parentId = parentId, updatedAt = now, syncState = SYNC_DIRTY)
            }
        }

        val changed = merged
            .groupBy { it.id }
            .mapValues { (_, rows) -> rows.last() }
            .values
            .filter { candidate ->
                val current = existing.firstOrNull { it.id == candidate.id }
                current == null || current.name != candidate.name || current.kind != candidate.kind ||
                    current.colorHex != candidate.colorHex || current.icon != candidate.icon ||
                    current.sortOrder != candidate.sortOrder || current.parentId != candidate.parentId
            }
        if (changed.isNotEmpty()) dao.upsertCategories(changed)
    }

    private fun now() = System.currentTimeMillis()
    private fun newId() = UUID.randomUUID().toString()
    private fun deterministicImportId(userId: String, provider: String, sourceId: String): String =
        UUID.nameUUIDFromBytes("rongrong-ledger:import:$userId:$provider:$sourceId".toByteArray()).toString()
    private fun cachedAvatarFile(userId: String) = File(File(context.filesDir, "avatars"), "$userId.jpg")

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
    val createdAt: Long,
    val updateInfo: AppVersionInfo? = null
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
    age = nullableInt("age"),
    birthDate = nullableString("birth_date"),
    gender = nullableString("gender"),
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
    syncState = SYNC_SYNCED,
    parentId = nullableString("parent_id")
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
private fun JSONObject.nullableInt(name: String): Int? = if (isNull(name)) null else optInt(name)
private fun JSONObject.nullableLong(name: String): Long? = if (isNull(name)) null else optLong(name)
private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
