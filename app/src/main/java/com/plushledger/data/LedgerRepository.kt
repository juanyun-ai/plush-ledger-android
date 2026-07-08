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
import org.json.JSONArray
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
                    accountNo = defaultAccountNo(userId),
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

    suspend fun updateTransaction(
        userId: String,
        id: String,
        amountMinor: Long,
        categoryId: String?,
        accountId: String,
        note: String,
        occurredAt: Long
    ) {
        val existing = dao.transactionsSnapshot(userId).firstOrNull { it.id == id } ?: error("账目不存在或已删除")
        require(amountMinor > 0) { "金额需要大于 0" }
        if (existing.type != "transfer") require(categoryId != null) { "请选择分类" }
        dao.upsertTransaction(
            existing.copy(
                amountMinor = amountMinor,
                categoryId = categoryId,
                accountId = accountId,
                note = note.trim().take(80),
                occurredAt = occurredAt,
                updatedAt = now(),
                syncState = SYNC_DIRTY
            )
        )
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
            val importedCategoryId = importedCategoryId(entry, categories)
            val account = accounts.firstOrNull { account ->
                entry.accountHint.contains(account.name) || account.name.contains(entry.accountHint)
            } ?: providerAccount
            TransactionEntity(
                id = deterministicImportId(userId, preview.provider, entry.sourceId),
                userId = userId,
                bookId = book.id,
                type = entry.type,
                amountMinor = entry.amountMinor,
                categoryId = importedCategoryId ?: recognition?.categoryId,
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

    private fun importedCategoryId(entry: ExternalBillEntry, categories: List<CategoryEntity>): String? {
        val categoryName = entry.categoryName.ifBlank {
            entry.categoryPath.split("/", ">").map(String::trim).filter(String::isNotBlank).lastOrNull().orEmpty()
        }
        if (categoryName.isBlank()) return null
        val parentName = entry.categoryParentName.ifBlank {
            entry.categoryPath.split("/", ">").map(String::trim).filter(String::isNotBlank).dropLast(1).lastOrNull().orEmpty()
        }
        val sameKind = categories.filter { it.kind == entry.type && it.deletedAt == null }
        val byId = categories.associateBy { it.id }
        return sameKind.firstOrNull { category ->
            category.name == categoryName && parentName.isNotBlank() && category.parentId?.let(byId::get)?.name == parentName
        }?.id ?: sameKind.firstOrNull { it.name == categoryName }?.id
    }

    suspend fun updateProfile(
        userId: String,
        displayName: String,
        age: Int?,
        birthDate: String?,
        gender: String?,
        province: String?,
        city: String?,
        accountNo: String?
    ) {
        val current = dao.getProfile(userId) ?: return
        val normalizedAccountNo = accountNo?.trim()?.takeIf { it.isNotBlank() } ?: current.accountNo ?: defaultAccountNo(userId)
        require(normalizedAccountNo.matches(Regex("^[A-Za-z]{6,8}$"))) { "用户ID仅支持 6-8 位英文字母" }
        val yearKey = LocalDate.now().year.toString()
        val changedYear = if (current.accountNoChangedMonth == yearKey) current.accountNoChangedMonth else yearKey
        val baseChangeCount = if (current.accountNoChangedMonth == yearKey) current.accountNoChangedCount else 0
        val accountChanged = !current.accountNo.equals(normalizedAccountNo, ignoreCase = true)
        if (accountChanged && baseChangeCount >= 2) error("用户ID一年最多修改 2 次")
        val nextDisplayName = displayName.trim().ifBlank { current.displayName }
        val nicknameChanged = nextDisplayName != current.displayName
        val session = sessionStore.currentSession()
        if (accountChanged && session?.accessToken != null) {
            val available = withFreshAccessToken { token ->
                supabaseClient.profileAccountNoAvailable(token, userId, normalizedAccountNo)
            }
            if (!available) error("这个账号编号已经被使用了，请换一个")
        }
        if (nicknameChanged) {
            val since = now() - 180L * 24L * 60L * 60L * 1000L
            val localCount = nicknameHistory(userId).count { it >= since }
            val remoteCount = if (session?.accessToken != null) {
                runCatching {
                    withFreshAccessToken { token -> supabaseClient.countNicknameHistorySince(token, userId, since) }
                }.getOrDefault(localCount)
            } else {
                localCount
            }
            if (maxOf(localCount, remoteCount) >= 3) error("昵称每 180 天最多修改 3 次")
        }
        dao.upsertProfile(
            current.copy(
                displayName = nextDisplayName,
                age = age,
                birthDate = birthDate,
                gender = gender,
                province = province?.trim()?.takeIf { it.isNotBlank() },
                city = city?.trim()?.takeIf { it.isNotBlank() },
                accountNo = normalizedAccountNo,
                accountNoChangedMonth = if (accountChanged) changedYear else current.accountNoChangedMonth,
                accountNoChangedCount = if (accountChanged) baseChangeCount + 1 else current.accountNoChangedCount,
                updatedAt = now(),
                syncState = SYNC_DIRTY
            )
        )
        if (nicknameChanged) {
            rememberNicknameChange(userId)
            if (session?.accessToken != null) {
                runCatching {
                    withFreshAccessToken { token ->
                        supabaseClient.insertNicknameHistory(token, userId, current.displayName, nextDisplayName, now())
                    }
                }
            }
        }
        sessionStore.updateDisplayName(nextDisplayName)
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

    suspend fun recordAppActivity(eventType: String = "app_open") {
        val session = sessionStore.currentSession() ?: return
        if (session.accessToken.isNullOrBlank()) return
        runCatching {
            withFreshAccessToken { token ->
                supabaseClient.recordAppActivity(token, session.userId, eventType)
            }
        }
    }

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

    suspend fun moveCategoryTo(userId: String, categoryId: String, targetIndex: Int) {
        val all = dao.categoriesSnapshot(userId)
        val current = all.firstOrNull { it.id == categoryId } ?: return
        val siblings = all.filter { it.kind == current.kind && it.parentId == current.parentId }.sortedBy { it.sortOrder }
        val from = siblings.indexOfFirst { it.id == categoryId }
        if (from == -1) return
        val to = targetIndex.coerceIn(0, siblings.lastIndex)
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
            demoTransaction(userId, book.id, "expense", 3_200, category["午餐"]?.id, cash.id, "今天 · 12:30", today.atTime(12, 30), now),
            demoTransaction(userId, book.id, "expense", 1_850, category["公交地铁"]?.id, cash.id, "今天 · 08:15", today.atTime(8, 15), now),
            demoTransaction(userId, book.id, "expense", 16_800, category["日用百货"]?.id, accounts["支付宝"]?.id ?: cash.id, "昨天 · 20:45", yesterday.atTime(20, 45), now),
            demoTransaction(userId, book.id, "income", 120_000, category["兼职"]?.id ?: category["工资"]?.id, accounts["银行卡"]?.id ?: cash.id, "昨天 · 18:30", yesterday.atTime(18, 30), now),
            demoTransaction(userId, book.id, "expense", 8_700, category["书籍资料"]?.id, cash.id, "买书 · 设计心理学", day3.atTime(10, 30), now),
            demoTransaction(userId, book.id, "expense", 10_000, category["买菜"]?.id, accounts["微信"]?.id ?: cash.id, "买菜 · 超市", day3.atTime(12, 20), now),
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

    suspend fun mergeLocalWorkspaceIntoRemote(sourceUserId: String?, targetUserId: String) {
        val source = sourceUserId?.takeIf { it.isNotBlank() && it != targetUserId } ?: return
        if (dao.getProfile(source) == null && dao.transactionsSnapshot(source).isEmpty()) return
        ensureUserWorkspace(targetUserId, sessionStore.currentSession()?.displayName ?: "绒绒用户", sessionStore.currentSession()?.phone, sessionStore.currentSession()?.email)
        val targetBook = dao.getDefaultBook(targetUserId) ?: return
        val now = now()

        val targetAccountsByName = dao.accountsSnapshot(targetUserId).associateBy { it.name }.toMutableMap()
        val sourceAccounts = dao.accountsSnapshot(source)
        sourceAccounts.forEach { account ->
            if (targetAccountsByName[account.name] == null) {
                val copied = account.copy(id = mergedId(targetUserId, account.id), userId = targetUserId, bookId = targetBook.id, updatedAt = now, syncState = SYNC_DIRTY)
                dao.upsertAccount(copied)
                targetAccountsByName[copied.name] = copied
            }
        }

        val targetCategoriesByKey = dao.categoriesSnapshot(targetUserId).associateBy { "${it.kind}:${it.name}" }.toMutableMap()
        val sourceCategories = dao.categoriesSnapshot(source)
        sourceCategories.sortedBy { it.parentId != null }.forEach { category ->
            val key = "${category.kind}:${category.name}"
            if (targetCategoriesByKey[key] == null) {
                val parent = category.parentId?.let { parentId ->
                    sourceCategories.firstOrNull { it.id == parentId }?.let { parent -> targetCategoriesByKey["${parent.kind}:${parent.name}"] }
                }
                val copied = category.copy(
                    id = mergedId(targetUserId, category.id),
                    userId = targetUserId,
                    bookId = targetBook.id,
                    parentId = parent?.id,
                    updatedAt = now,
                    syncState = SYNC_DIRTY
                )
                dao.upsertCategory(copied)
                targetCategoriesByKey[key] = copied
            }
        }

        val accountBySourceId = sourceAccounts.associate { sourceAccount ->
            sourceAccount.id to (targetAccountsByName[sourceAccount.name]?.id ?: targetAccountsByName.values.firstOrNull()?.id)
        }
        val categoryBySourceId = sourceCategories.associate { sourceCategory ->
            sourceCategory.id to targetCategoriesByKey["${sourceCategory.kind}:${sourceCategory.name}"]?.id
        }
        val mergedTransactions = dao.transactionsSnapshot(source).mapNotNull { transaction ->
            val accountId = accountBySourceId[transaction.accountId] ?: return@mapNotNull null
            transaction.copy(
                id = mergedId(targetUserId, transaction.id),
                userId = targetUserId,
                bookId = targetBook.id,
                accountId = accountId,
                toAccountId = transaction.toAccountId?.let(accountBySourceId::get),
                categoryId = transaction.categoryId?.let(categoryBySourceId::get),
                updatedAt = now,
                syncState = SYNC_DIRTY
            )
        }
        dao.upsertTransactions(mergedTransactions)

        val mergedBudgets = dao.budgetsSnapshot(source).map { budget ->
            budget.copy(
                id = mergedId(targetUserId, budget.id),
                userId = targetUserId,
                bookId = targetBook.id,
                categoryId = budget.categoryId?.let(categoryBySourceId::get),
                updatedAt = now,
                syncState = SYNC_DIRTY
            )
        }
        dao.upsertBudgets(mergedBudgets)
        mergeLocalDiaries(source, targetUserId)
    }

    private suspend fun syncWithToken(userId: String, token: String): String {
        val results = mutableListOf<SyncTableResult>()
        val profiles = dao.dirtyProfiles(userId).map { it.sanitizedForCloud() }
        results += syncTable("profiles", profiles, token, { it.id }).also { if (it.syncedIds.isNotEmpty()) dao.markProfilesSynced(it.syncedIds) }
        results += syncTable("books", dao.dirtyBooks(userId), token, { it.id }).also { if (it.syncedIds.isNotEmpty()) dao.markBooksSynced(it.syncedIds) }
        results += syncTable("accounts", dao.dirtyAccounts(userId), token, { it.id }).also { if (it.syncedIds.isNotEmpty()) dao.markAccountsSynced(it.syncedIds) }
        val categories = dao.dirtyCategories(userId).sortedBy { it.parentId != null }
        results += syncTable("categories", categories, token, { it.id }, { it.kind in setOf("expense", "income") }).also { if (it.syncedIds.isNotEmpty()) dao.markCategoriesSynced(it.syncedIds) }
        results += syncTable("transactions", dao.dirtyTransactions(userId), token, { it.id }, { it.amountMinor > 0 && it.type in setOf("expense", "income", "transfer") }).also { if (it.syncedIds.isNotEmpty()) dao.markTransactionsSynced(it.syncedIds) }
        results += syncTable("budgets", dao.dirtyBudgets(userId), token, { it.id }, { it.limitMinor > 0 && it.month.matches(Regex("^\\d{4}-\\d{2}$")) }).also { if (it.syncedIds.isNotEmpty()) dao.markBudgetsSynced(it.syncedIds) }
        syncDiaryEntries(userId, token)
        runCatching { supabaseClient.updateProfileClientInfo(token, userId) }
        restoreFromCloud(token)
        val skipped = results.sumOf { it.skippedCount }
        return if (skipped == 0) "同步完成" else "同步完成，$skipped 条旧版异常记录仅保留在本机"
    }

    suspend fun loadOfficialMessages(): List<OfficialMessage> {
        val cached = cachedOfficialMessages()
        val session = sessionStore.currentSession()
        val remoteRows = runCatching {
            val token = session?.accessToken?.let {
                withFreshAccessToken { refreshed -> refreshed }
            }
            supabaseClient.fetchOfficialMessages(token)
        }.getOrElse { emptyList() }
        val remote = mutableListOf<OfficialMessage>()
        for (row in remoteRows) {
            officialMessageFromJson(row)?.let(remote::add)
        }
        if (remote.isNotEmpty()) cacheOfficialMessages(remote)
        return (remote + cached)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .ifEmpty { builtInMessages() }
    }

    suspend fun analyzeAiEntries(text: String, ledgerState: LedgerState): List<AiLedgerAnalysis> {
        val local = LocalAiLedgerParser.parseAll(text, ledgerState.categories, ledgerState.accounts)
        if (local.isEmpty()) return emptyList()
        val session = sessionStore.currentSession()
        val token = session?.accessToken ?: return local
        val remote = runCatching {
            supabaseClient.parseAiLedger(token, text, ledgerState.categories, ledgerState.accounts)
        }.getOrNull() ?: return local
        if (remote.entries.isEmpty()) return local
        return remote.entries.mapIndexedNotNull { index, entry ->
            val seed = local.getOrNull(index) ?: local.lastOrNull() ?: return@mapIndexedNotNull null
            val type = entry.type.takeIf { it == "income" || it == "expense" } ?: seed.type
            val category = LocalAiLedgerParser.resolveCategory(
                type = type,
                categoryName = entry.categoryName,
                parentName = entry.parentCategoryName,
                categories = ledgerState.categories,
                sourceText = seed.sourceText
            )
            val account = LocalAiLedgerParser.resolveAccount(entry.accountName, ledgerState.accounts, seed.sourceText)
            AiLedgerAnalysis(
                sourceText = seed.sourceText,
                type = type,
                amountMinor = entry.amountMinor,
                categoryId = category?.id,
                categoryLabel = category?.name ?: "无法归类",
                accountId = account?.id,
                accountLabel = account?.name ?: "默认账户",
                note = entry.note ?: seed.note,
                // The local parser owns dates so a model never changes an explicit
                // "6月7日" into a hallucinated year or month.
                occurredAt = seed.occurredAt,
                cloudAssisted = true
            )
        }.ifEmpty { local }
    }

    suspend fun analyzeAiEntry(text: String, ledgerState: LedgerState): AiLedgerAnalysis? =
        analyzeAiEntries(text, ledgerState).firstOrNull()

    suspend fun submitFeedback(content: String, page: String = "about") {
        val session = sessionStore.currentSession()
        val trimmed = content.trim()
        require(trimmed.length in 5..500) { "建议内容需要 5-500 个字" }
        supabaseClient.submitFeedback(session?.accessToken, session?.email, trimmed, page)
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

        sessionStore.currentSession()?.userId?.let { userId ->
            restoreDiaryEntries(userId, token)
        }
    }

    private suspend fun syncDiaryEntries(userId: String, token: String) {
        val remote = runCatching { supabaseClient.fetchRows("diary_entries", token) }.getOrElse { return }
        val merged = mergeDiaryRows(localDiaryRows(userId), remote)
        saveLocalDiaryRows(userId, merged)
        supabaseClient.upsertJsonRows("diary_entries", merged.map { it.toCloudDiaryRow(userId) }, token)
    }

    private suspend fun restoreDiaryEntries(userId: String, token: String) {
        val remote = runCatching { supabaseClient.fetchRows("diary_entries", token) }.getOrElse { return }
        saveLocalDiaryRows(userId, mergeDiaryRows(localDiaryRows(userId), remote))
    }

    private fun mergeLocalDiaries(sourceUserId: String, targetUserId: String) {
        val merged = mergeDiaryRows(localDiaryRows(targetUserId), localDiaryRows(sourceUserId))
        saveLocalDiaryRows(targetUserId, merged)
    }

    private fun localDiaryPrefs(userId: String) =
        context.getSharedPreferences("rongrong_diary_$userId", Context.MODE_PRIVATE)

    private fun localDiaryRows(userId: String): List<JSONObject> {
        val prefs = localDiaryPrefs(userId)
        val entries = runCatching {
            val array = JSONArray(prefs.getString("entries", "[]") ?: "[]")
            List(array.length()) { index -> array.getJSONObject(index) }
        }.getOrDefault(emptyList())
        val deleted = runCatching {
            val json = JSONObject(prefs.getString("deleted_entries", "{}") ?: "{}")
            json.keys().asSequence().map { key ->
                val value = json.opt(key)
                if (value is JSONObject) {
                    JSONObject()
                        .put("id", value.optString("id", key))
                        .put("date", value.optString("date").ifBlank { key.takeIf { it.length == 10 } ?: LocalDate.now().toString() })
                        .put("text", "")
                        .put("mood", "开心")
                        .put("status", "")
                        .put("updated_at", value.optLong("updated_at", value.optLong("deleted_at", now())))
                        .put("deleted_at", value.optLong("deleted_at", value.optLong("updated_at", now())))
                } else {
                    JSONObject()
                        .put("id", "legacy:$key")
                        .put("date", key)
                        .put("text", "")
                        .put("mood", "开心")
                        .put("status", "")
                        .put("updated_at", json.optLong(key))
                        .put("deleted_at", json.optLong(key))
                }
            }.toList()
        }.getOrDefault(emptyList())
        return entries + deleted
    }

    private fun mergeDiaryRows(a: List<JSONObject>, b: List<JSONObject>): List<JSONObject> =
        (a + b)
            .filter { it.optString("date").isNotBlank() }
            .groupBy { it.optString("id").ifBlank { "legacy:${it.optString("date")}" } }
            .map { (_, rows) -> rows.maxBy { it.optLong("updated_at", it.optLong("created_at", 0L)) } }
            .dedupeDiaryContentRows()
            .sortedWith(compareByDescending<JSONObject> { it.optString("date") }.thenByDescending { it.optLong("updated_at", 0L) })
            .take(240)

    private fun saveLocalDiaryRows(userId: String, rows: List<JSONObject>) {
        val entries = JSONArray()
        val deleted = JSONObject()
        rows.forEach { row ->
            val date = row.optString("date")
            val id = row.optString("id").ifBlank { mergedId(userId, "diary:$date:${row.optLong("created_at", row.optLong("updated_at", 0L))}") }
            val deletedAt = row.optLong("deleted_at", 0L)
            if (deletedAt > 0L) {
                deleted.put(
                    id,
                    JSONObject()
                        .put("id", id)
                        .put("date", date)
                        .put("updated_at", row.optLong("updated_at", deletedAt))
                        .put("deleted_at", deletedAt)
                )
            } else if (row.optString("text").isNotBlank()) {
                entries.put(
                    JSONObject()
                        .put("id", id)
                        .put("date", date)
                        .put("text", row.optString("text").trim())
                        .put("mood", row.optString("mood", "开心"))
                        .put("status", row.optString("status"))
                        .put("created_at", row.optLong("created_at", row.optLong("updated_at", now())))
                        .put("updated_at", row.optLong("updated_at", now()))
                )
            }
        }
        localDiaryPrefs(userId).edit()
            .putString("entries", entries.toString())
            .putString("deleted_entries", deleted.toString())
            .apply()
    }

    private fun JSONObject.toCloudDiaryRow(userId: String): JSONObject {
        val date = optString("date")
        val updatedAt = optLong("updated_at", now()).takeIf { it > 0L } ?: now()
        val id = optString("id").ifBlank { mergedId(userId, "diary:$date:${optLong("created_at", updatedAt)}:${optString("text").hashCode()}") }
        return JSONObject()
            .put("id", id)
            .put("user_id", userId)
            .put("date", date)
            .put("text", optString("text").take(1000))
            .put("mood", optString("mood", "开心").take(40))
            .put("status", optString("status").take(40))
            .put("created_at", optLong("created_at", updatedAt).takeIf { it > 0L } ?: updatedAt)
            .put("updated_at", updatedAt)
            .put("deleted_at", optLong("deleted_at", 0L).takeIf { it > 0L } ?: JSONObject.NULL)
    }

    private fun List<JSONObject>.dedupeDiaryContentRows(): List<JSONObject> {
        val activeByContent = LinkedHashMap<String, JSONObject>()
        val tombstones = mutableListOf<JSONObject>()
        forEach { row ->
            val deletedAt = row.optLong("deleted_at", 0L)
            if (deletedAt > 0L || row.optString("text").isBlank()) {
                tombstones += row
                return@forEach
            }
            val key = listOf(
                row.optString("date"),
                row.optString("text").replace(Regex("\\s+"), " ").trim(),
                row.optString("mood", "开心").trim(),
                row.optString("status").trim()
            ).joinToString("|")
            val current = activeByContent[key]
            if (current == null || row.optLong("updated_at", 0L) >= current.optLong("updated_at", 0L)) {
                activeByContent[key] = row
            }
        }
        return tombstones + activeByContent.values
    }

    private fun mergedId(userId: String, seed: String): String =
        UUID.nameUUIDFromBytes("rongrong-ledger:$userId:$seed".toByteArray()).toString()

    private suspend fun <T> syncTable(
        table: String,
        rows: List<T>,
        token: String,
        id: (T) -> String,
        isValid: (T) -> Boolean = { true }
    ): SyncTableResult {
        if (rows.isEmpty()) return SyncTableResult()
        val validRows = rows.filter(isValid)
        var skipped = rows.size - validRows.size
        if (validRows.isEmpty()) return SyncTableResult(skippedCount = skipped)
        return runCatching {
            supabaseClient.upsertRows(table, validRows, token)
            SyncTableResult(validRows.map(id), skipped)
        }.getOrElse { batchError ->
            if (!batchError.message.orEmpty().contains("400")) throw batchError
            val synced = mutableListOf<String>()
            var firstFailure: Throwable? = null
            validRows.forEach { row ->
                runCatching { supabaseClient.upsertRows(table, listOf(row), token) }
                    .onSuccess { synced += id(row) }
                    .onFailure { error ->
                        skipped++
                        if (firstFailure == null) firstFailure = error
                    }
            }
            if (synced.isEmpty() && firstFailure != null) {
                val detail = firstFailure?.message.orEmpty().substringAfter("Supabase 请求失败 400:").trim().take(180)
                error("${cloudTableLabel(table)}无法写入云端：${detail.ifBlank { "请检查云端字段和约束" }}")
            }
            SyncTableResult(synced, skipped)
        }
    }

    private data class SyncTableResult(
        val syncedIds: List<String> = emptyList(),
        val skippedCount: Int = 0
    )

    private fun ProfileEntity.sanitizedForCloud(): ProfileEntity = copy(
        displayName = displayName.trim().ifBlank { "绒绒用户" }.take(40),
        age = age?.takeIf { it in 0..150 },
        birthDate = birthDate?.takeIf { it.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) },
        gender = gender?.takeIf { it in setOf("female", "male", "other", "prefer_not") },
        province = province?.trim()?.takeIf { it.length <= 30 },
        city = city?.trim()?.takeIf { it.length <= 40 },
        accountNo = accountNo?.trim()?.takeIf { it.matches(Regex("^[A-Za-z]{6,8}$")) },
        currency = currency.takeIf { it.length == 3 } ?: "CNY"
    )

    private fun cloudTableLabel(table: String): String = when (table) {
        "profiles" -> "用户资料"
        "books" -> "账本"
        "accounts" -> "账户"
        "categories" -> "分类"
        "transactions" -> "账目"
        "budgets" -> "预算"
        else -> table
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
    private fun nicknamePrefs() =
        context.getSharedPreferences("rongrong_profile_limits", Context.MODE_PRIVATE)

    private fun nicknameHistory(userId: String): List<Long> =
        nicknamePrefs()
            .getString("nickname_history_$userId", "")
            .orEmpty()
            .split(",")
            .mapNotNull { it.toLongOrNull() }

    private fun rememberNicknameChange(userId: String) {
        val since = now() - 180L * 24L * 60L * 60L * 1000L
        val history = (nicknameHistory(userId).filter { it >= since } + now()).takeLast(6)
        nicknamePrefs().edit().putString("nickname_history_$userId", history.joinToString(",")).apply()
    }

    private fun newId() = UUID.randomUUID().toString()
    private fun deterministicImportId(userId: String, provider: String, sourceId: String): String =
        UUID.nameUUIDFromBytes("rongrong-ledger:import:$userId:$provider:$sourceId".toByteArray()).toString()
    private fun cachedAvatarFile(userId: String) = File(File(context.filesDir, "avatars"), "$userId.jpg")

    private suspend fun officialMessageFromJson(json: JSONObject): OfficialMessage? = runCatching {
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
            // A missing historical version must not erase the complete inbox.
            updateInfo = versionCode?.let { runCatching { supabaseClient.fetchAppVersion(it) }.getOrNull() }
        )
    }.getOrNull()

    private fun cachedOfficialMessages(): List<OfficialMessage> = runCatching {
        val raw = context.getSharedPreferences(OFFICIAL_MESSAGES_PREFS, Context.MODE_PRIVATE)
            .getString(OFFICIAL_MESSAGES_KEY, null)
            ?: return emptyList()
        val values = JSONArray(raw)
        List(values.length()) { index ->
            val message = values.getJSONObject(index)
            OfficialMessage(
                id = message.getString("id"),
                title = message.getString("title"),
                body = message.getString("body"),
                createdAt = message.getLong("created_at")
            )
        }
    }.getOrElse { emptyList() }

    private fun cacheOfficialMessages(messages: List<OfficialMessage>) {
        val values = JSONArray()
        messages.sortedByDescending { it.createdAt }.take(60).forEach { message ->
            values.put(
                JSONObject()
                    .put("id", message.id)
                    .put("title", message.title)
                    .put("body", message.body)
                    .put("created_at", message.createdAt)
            )
        }
        context.getSharedPreferences(OFFICIAL_MESSAGES_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(OFFICIAL_MESSAGES_KEY, values.toString())
            .apply()
    }

    private fun builtInMessages(): List<OfficialMessage> {
        val createdAt = now()
        return listOf(
            OfficialMessage(
                id = "release_1_1_0_builtin",
                title = "绒绒记账 v1.1.0 更新",
                body = "1. 绒绒日记登录后支持云端同步，同一账号换设备也能看到已保存日记。\n2. 手机号验证码登录链路补强，登录后会把当前设备的本地/旧账号账本合并到新云账号。\n3. App 固定浅色模式，用户身份文案改为“永久用户”，关于我们新增小程序入口并更新联系邮箱。\n4. 统计圆环图支持点击查看分类明细，日记分享卡换成省份插图高清版并显示真实日期。",
                createdAt = createdAt
            ),
            OfficialMessage(
                id = "release_1_0_9_builtin",
                title = "绒绒记账 v1.0.9 更新",
                body = "1. 管理后台新增 App / 小程序来源筛选，反馈、官方消息、版本更新和远程配置都能分开查看。\n2. App 多个页面换上不同动作的绒绒形象，欢迎、记账、统计、日记和设置页更有区分度。\n3. 绒绒日记主视觉换成小程序同款风格，并使用透明动作图避免白底和边框。\n4. 后台布局优化，官方消息和版本更新的左侧表单不再被右侧列表撑得过长。",
                createdAt = createdAt - 1
            ),
            OfficialMessage(
                id = "release_1_0_8_builtin",
                title = "绒绒记账 v1.0.8 更新",
                body = "1. AI 记账增强日期和退费识别，支持“7月1号收到退费”这类表达。\n2. 收入分类新增“退税退费”和“其他”，并补上对应毛绒风图标。\n3. 记账与编辑页不再要求精确时间，只保留日期。\n4. 统计页新增月报、季度报和年度报，底部提示会按真实账目动态生成。",
                createdAt = createdAt - 2
            ),
            OfficialMessage(
                id = "release_1_0_7_builtin",
                title = "绒绒记账 v1.0.7 更新",
                body = "1. 本地模式和登录模式的用户反馈都改为 App 内直达开发者后台，不再依赖邮箱。\n2. 关于我们页新增在线留言框，支持清空、暂存、取消和发送。\n3. 联系与注销说明改为优先使用 App 内在线留言，避免用户反馈丢失。",
                createdAt = createdAt - 3
            ),
            OfficialMessage(
                id = "release_1_0_6_builtin",
                title = "绒绒记账 v1.0.6 更新",
                body = "1. 分类图标更新：买菜、咖啡、早餐、晚餐、飞机、轮渡和生日礼物换成新版毛绒风图标。\n2. 餐饮分类调整为早餐、午餐、晚餐和咖啡，交通新增飞机和轮渡，日常新增买菜，人情社交新增生日礼物。\n3. 我的页“连续记账”改为“累计记账”，按真实记账日期数展示；货币单位选择恢复国旗显示。",
                createdAt = createdAt - 4
            ),
            OfficialMessage(
                id = "release_1_0_5_builtin",
                title = "绒绒记账 v1.0.5 更新",
                body = "1. AI 软件订阅分类图标换成新版毛绒风图标。\n2. 绒绒日记首卡文案、编辑弹窗和社交分享卡片按新设计重新排版。\n3. QQ 登录和绑定图标改为矢量企鹅，避免出现剪贴小方块。\n4. 主题选择页继续优化毛绒双列布局，并新增产品下载页作为下载兜底。",
                createdAt = createdAt - 5
            ),
            OfficialMessage(
                id = "release_1_0_4_builtin",
                title = "绒绒记账 v1.0.4 更新",
                body = "1. 预算管理支持剩余预算显示为负数，并展示超出预算比例。\n2. 学习工作新增 AI 软件订阅分类，AI 识别也会优先匹配常见 AI 订阅支出。\n3. 绒绒日记支持近期日记左滑删除，状态可选择不设置，清空按钮显示更稳定。\n4. 日记首页卡片和社交分享卡片重新排版，二维码保持真实可扫。",
                createdAt = createdAt - 6
            ),
            OfficialMessage(
                id = "release_1_0_3_builtin",
                title = "绒绒记账 v1.0.3 更新",
                body = "1. AI 记账暂存后会提示并自动关闭弹窗，同时新增清空输入。\n2. 生活日历增加节气、休班角标、周末蓝色日期、今日按钮和法定假期倒计时。\n3. 纪念日拆分为独立专区，历史日记改为弹窗编辑，日记支持暂存和一键清空。\n4. 状态选择和日记分享卡片继续按新设计优化，二维码保持真实可扫。",
                createdAt = createdAt - 7
            ),
            OfficialMessage(
                id = "release_1_0_2_builtin",
                title = "绒绒记账 v1.0.2 更新",
                body = "1. AI 记账支持一句话识别多笔账，并可暂存输入草稿。\n2. 生活日历增加节日农历、选中日期、全年重要日子和备注展开。\n3. 用户生日支持阳历/农历互换，日记支持状态、编辑和真实分享二维码。\n4. 云账号前台自动轻量同步，减少多设备等待。",
                createdAt = createdAt - 4
            ),
            OfficialMessage(
                id = "welcome",
                title = "欢迎使用绒绒记账",
                body = "邮箱账号会同步到云端；本地模式只保存在当前设备。请定期确认同步状态。",
                createdAt = createdAt - 5
            )
        )
    }
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
private const val OFFICIAL_MESSAGES_PREFS = "official_messages"
private const val OFFICIAL_MESSAGES_KEY = "cached_messages"

private val expenseColors = listOf("#C86F7E", "#D99676", "#B86A77", "#E0A0A8", "#A86E89")
private val incomeColors = listOf("#5E9B83", "#6E8DBF", "#8AA46D", "#76A9A8")

private fun defaultAccountNo(userId: String): String =
    buildString {
        append("RR")
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val source = userId.filter(Char::isLetter).uppercase(Locale.ROOT)
        append(source.take(6))
        var seed = userId.fold(17) { acc, ch -> acc * 31 + ch.code }.toUInt().toLong()
        while (length < 8) {
            append(letters[(seed % letters.length).toInt()])
            seed = seed / letters.length + 11
        }
    }.take(8)

private fun JSONObject.toProfile() = ProfileEntity(
    id = getString("id"),
    displayName = getString("display_name"),
    avatarKey = optString("avatar_key", "sunny"),
    phone = nullableString("phone"),
    email = nullableString("email"),
    age = nullableInt("age"),
    birthDate = nullableString("birth_date"),
    gender = nullableString("gender"),
    province = nullableString("province"),
    city = nullableString("city"),
    accountNo = nullableString("account_no"),
    accountNoChangedMonth = nullableString("account_no_changed_month"),
    accountNoChangedCount = nullableInt("account_no_changed_count") ?: 0,
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
