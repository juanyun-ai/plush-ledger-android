package com.plushledger.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plushledger.BuildConfig
import com.plushledger.PlushLedgerApplication
import com.plushledger.auth.AuthChannel
import com.plushledger.auth.AuthOutcome
import com.plushledger.auth.UserSession
import com.plushledger.data.AiLedgerAnalysis
import com.plushledger.data.ExternalBillCsvParser
import com.plushledger.data.ExternalBillPreview
import com.plushledger.data.LedgerState
import com.plushledger.data.LocalAiLedgerParser
import com.plushledger.data.Money
import com.plushledger.data.OfficialMessage
import com.plushledger.sync.AppVersionInfo
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab { HOME, BILLS, RECORD, STATS, MY }
enum class AuthPage { LOGIN, REGISTER, RESET }

data class UiState(
    val session: UserSession? = null,
    val locked: Boolean = false,
    val passwordSetupSession: UserSession? = null,
    val passwordSetupIsRegistration: Boolean = false,
    val authPage: AuthPage = AuthPage.LOGIN,
    val otpCooldown: Int = 0,
    val selectedTab: AppTab = AppTab.HOME,
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val ledger: LedgerState = LedgerState(),
    val officialMessages: List<OfficialMessage> = emptyList(),
    val avatarUrl: String? = null,
    val availableUpdate: AppVersionInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val message: String? = null,
    val syncLabel: String = "本地保存",
    val secureScreen: Boolean = false,
    val lockOnLaunch: Boolean = false,
    val biometricUnlock: Boolean = false,
    val darkMode: Boolean = false,
    val themeTone: String = "warm",
    val automaticUpdatePrompts: Boolean = true,
    val defaultAccountId: String? = null,
    val exportPath: String? = null,
    val aiSuggestions: List<AiLedgerAnalysis> = emptyList(),
    val isAiAnalyzing: Boolean = false,
    val billImportPreview: ExternalBillPreview? = null,
    val isBusy: Boolean = false
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PlushLedgerApplication
    private val container = app.container
    private val auth = container.authRepository
    private val ledger = container.ledgerRepository
    private val sessions = container.sessionStore
    private var ledgerJob: Job? = null
    private var cooldownJob: Job? = null
    private var mailboxRefreshJob: Job? = null
    private var cloudRefreshJob: Job? = null
    private var lastAvatarKey: String? = null
    private val tabHistory = ArrayDeque<AppTab>()

    var state = androidx.compose.runtime.mutableStateOf(UiState())
        private set

    init {
        sessions.ensureVersion2Defaults()
        val session = sessions.currentSession()
        val lockOnLaunch = sessions.isLockOnLaunchEnabled()
        state.value = UiState(
            session = session,
            locked = session != null && lockOnLaunch && sessions.hasPin(),
            secureScreen = sessions.isSecureScreenEnabled(),
            lockOnLaunch = lockOnLaunch,
            biometricUnlock = sessions.isBiometricUnlockEnabled(),
            darkMode = sessions.isDarkModeEnabled(),
            themeTone = sessions.themeTone(),
            automaticUpdatePrompts = sessions.areAutomaticUpdatePromptsEnabled(),
            defaultAccountId = session?.let { sessions.defaultAccountId(it.userId) }
        )
        session?.let {
            viewModelScope.launch {
                ledger.ensureUserWorkspace(it.userId, it.displayName, it.phone, it.email)
                if (!state.value.locked) observeLedger(it.userId)
            }
            if (!state.value.locked) startCloudRefresh(it)
        }
        viewModelScope.launch {
            delay(2_000)
            checkForUpdates(silent = true)
        }
    }

    fun selectTab(tab: AppTab) {
        if (tab == state.value.selectedTab) return
        tabHistory.addLast(state.value.selectedTab)
        state.value = state.value.copy(selectedTab = tab, message = null)
        if (tab == AppTab.MY) refreshMailbox()
    }

    fun navigateBack(): Boolean {
        val previous = tabHistory.removeLastOrNull() ?: return false
        state.value = state.value.copy(selectedTab = previous, message = null)
        if (previous == AppTab.MY) refreshMailbox()
        return true
    }

    fun hasTabHistory(): Boolean = tabHistory.isNotEmpty()

    fun showAuthPage(page: AuthPage) {
        state.value = state.value.copy(authPage = page, message = null)
    }

    fun changeMonth(delta: Long) {
        val next = state.value.selectedMonth.plusMonths(delta)
        val currentDay = state.value.selectedDate.dayOfMonth
        val nextDate = next.atDay(currentDay.coerceAtMost(next.lengthOfMonth()))
        state.value = state.value.copy(selectedMonth = next, selectedDate = nextDate)
        state.value.session?.let { observeLedger(it.userId) }
    }

    fun selectStatsDate(date: LocalDate) {
        state.value = state.value.copy(selectedDate = date, selectedMonth = YearMonth.from(date))
        state.value.session?.let { observeLedger(it.userId) }
    }

    fun sendRegistrationOtp(email: String, isReset: Boolean = false) {
        if (state.value.otpCooldown > 0 || state.value.isBusy) return
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            when (val result = auth.sendOtp(AuthChannel.EMAIL, email, shouldCreateUser = !isReset)) {
                is AuthOutcome.OtpSent -> {
                    state.value = state.value.copy(isBusy = false, message = result.message)
                    startOtpCooldown()
                }
                is AuthOutcome.Failed -> state.value = state.value.copy(isBusy = false, message = result.message)
                else -> state.value = state.value.copy(isBusy = false)
            }
        }
    }

    fun verifyRegistrationOtp(email: String, token: String, isReset: Boolean = false) {
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            when (
                val result = auth.verifyOtp(
                    AuthChannel.EMAIL,
                    email,
                    token,
                    requirePasswordSetup = true
                )
            ) {
                is AuthOutcome.PasswordSetupRequired -> state.value = state.value.copy(
                    isBusy = false,
                    passwordSetupSession = result.session,
                    passwordSetupIsRegistration = !isReset,
                    message = result.message
                )
                is AuthOutcome.Failed -> state.value = state.value.copy(isBusy = false, message = result.message)
                is AuthOutcome.SignedIn -> completeSignIn(result.session, result.message)
                is AuthOutcome.OtpSent -> state.value = state.value.copy(isBusy = false, message = result.message)
            }
        }
    }

    fun signInWithPassword(email: String, password: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            when (val result = auth.signInWithPassword(email, password)) {
                is AuthOutcome.SignedIn -> completeSignIn(result.session, result.message)
                is AuthOutcome.Failed -> state.value = state.value.copy(isBusy = false, message = result.message)
                else -> state.value = state.value.copy(isBusy = false)
            }
        }
    }

    fun sendLoginOtp(channelName: String, identifier: String) {
        if (state.value.otpCooldown > 0 || state.value.isBusy) return
        val channel = if (channelName == "phone") AuthChannel.PHONE else AuthChannel.EMAIL
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            when (val result = auth.sendOtp(channel, identifier, shouldCreateUser = true)) {
                is AuthOutcome.OtpSent -> {
                    state.value = state.value.copy(isBusy = false, message = result.message)
                    startOtpCooldown()
                }
                is AuthOutcome.Failed -> state.value = state.value.copy(isBusy = false, message = result.message)
                else -> state.value = state.value.copy(isBusy = false)
            }
        }
    }

    fun verifyLoginOtp(channelName: String, identifier: String, token: String) {
        val channel = if (channelName == "phone") AuthChannel.PHONE else AuthChannel.EMAIL
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            when (val result = auth.verifyOtp(channel, identifier, token, requirePasswordSetup = false)) {
                is AuthOutcome.SignedIn -> completeSignIn(result.session, if (channel == AuthChannel.PHONE) "手机号登录成功" else "邮箱验证码登录成功")
                is AuthOutcome.Failed -> state.value = state.value.copy(isBusy = false, message = result.message)
                is AuthOutcome.PasswordSetupRequired -> state.value = state.value.copy(
                    isBusy = false,
                    passwordSetupSession = result.session,
                    passwordSetupIsRegistration = false,
                    message = result.message
                )
                is AuthOutcome.OtpSent -> state.value = state.value.copy(isBusy = false, message = result.message)
            }
        }
    }

    fun finishPasswordSetup(password: String, confirmation: String) {
        val pending = state.value.passwordSetupSession ?: return
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            when (val result = auth.setRemotePassword(pending, password, confirmation)) {
                is AuthOutcome.SignedIn -> {
                    val isRegistration = state.value.passwordSetupIsRegistration
                    completeSignIn(result.session, result.message)
                    if (isRegistration) {
                        ledger.markAgreementAccepted(result.session.userId)
                        app.enqueueImmediateSync()
                    }
                    state.value = state.value.copy(passwordSetupSession = null)
                }
                is AuthOutcome.Failed -> state.value = state.value.copy(isBusy = false, message = result.message)
                else -> state.value = state.value.copy(isBusy = false)
            }
        }
    }

    fun signInLocal(username: String, password: String) {
        when (val result = auth.signInLocal(username, password)) {
            is AuthOutcome.SignedIn -> viewModelScope.launch {
                completeSignIn(result.session, result.message)
                if (username.trim() == "体验用户") {
                    ledger.seedDemoData(result.session.userId)
                    state.value = state.value.copy(message = "已进入演示账本")
                }
            }
            is AuthOutcome.Failed -> state.value = state.value.copy(message = result.message)
            else -> Unit
        }
    }

    fun socialLogin(provider: String) {
        val name = when {
            provider.contains("微信") -> "微信"
            provider.contains("QQ", ignoreCase = true) -> "QQ"
            else -> provider
        }
        state.value = state.value.copy(message = "$name 绑定已准备接入；密钥只能放后端。Android 原生绑定还需要腾讯开放平台移动应用 AppID、包名签名和回调配置，未完成前不会写入假的绑定状态")
    }

    fun unlockWithPin(pin: String) {
        when (val result = auth.signInWithPin(pin)) {
            is AuthOutcome.SignedIn -> {
                state.value = state.value.copy(locked = false, message = result.message)
                observeLedger(result.session.userId)
                startCloudRefresh(result.session)
            }
            is AuthOutcome.Failed -> state.value = state.value.copy(message = result.message)
            else -> Unit
        }
    }

    fun unlockWithBiometric() {
        if (!state.value.biometricUnlock) return
        val session = sessions.currentSession() ?: return
        state.value = state.value.copy(locked = false, message = "已通过生物识别解锁")
        observeLedger(session.userId)
        startCloudRefresh(session)
    }

    fun setPin(pin: String) {
        when (val result = auth.setPin(pin)) {
            is AuthOutcome.SignedIn -> state.value = state.value.copy(message = result.message)
            is AuthOutcome.Failed -> state.value = state.value.copy(message = result.message)
            else -> Unit
        }
    }

    fun setLockOnLaunch(enabled: Boolean) {
        if (enabled && !sessions.hasPin()) {
            state.value = state.value.copy(message = "请先设置 PIN，再开启每次打开验证")
            return
        }
        sessions.setLockOnLaunchEnabled(enabled)
        if (!enabled) sessions.setBiometricUnlockEnabled(false)
        state.value = state.value.copy(lockOnLaunch = enabled, biometricUnlock = if (enabled) state.value.biometricUnlock else false)
    }

    fun setBiometricUnlock(enabled: Boolean) {
        if (enabled && !state.value.lockOnLaunch) {
            state.value = state.value.copy(message = "请先开启每次打开验证")
            return
        }
        sessions.setBiometricUnlockEnabled(enabled)
        state.value = state.value.copy(biometricUnlock = enabled)
    }

    fun addTransaction(
        type: String,
        amountText: String,
        categoryId: String?,
        accountId: String?,
        toAccountId: String?,
        note: String,
        occurredDateTime: java.time.LocalDateTime
    ): Boolean {
        val session = state.value.session
        if (session == null) {
            state.value = state.value.copy(message = "请先登录或进入本地模式")
            return false
        }
        val amount = Money.parseToMinor(amountText)
        if (amount == null) {
            state.value = state.value.copy(message = "金额需要大于 0")
            return false
        }
        if (type != "transfer" && categoryId == null) {
            state.value = state.value.copy(message = "请选择具体分类")
            return false
        }
        if (type != "transfer" && state.value.ledger.categories.none { it.id == categoryId && it.kind == type }) {
            state.value = state.value.copy(message = "分类已变化，请重新选择")
            return false
        }
        val account = accountId
            ?: state.value.defaultAccountId?.takeIf { id -> state.value.ledger.accounts.any { it.id == id } }
            ?: state.value.ledger.accounts.firstOrNull { it.name == "现金" }?.id
            ?: state.value.ledger.accounts.firstOrNull()?.id
        if (account == null) {
            state.value = state.value.copy(message = "请先添加账户")
            return false
        }
        viewModelScope.launch {
            runCatching {
                ledger.ensureUserWorkspace(session.userId, session.displayName, session.phone, session.email)
                ledger.addTransaction(
                    userId = session.userId,
                    type = type,
                    amountMinor = amount,
                    categoryId = categoryId,
                    accountId = account,
                    toAccountId = toAccountId,
                    note = note,
                    occurredAt = occurredDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            }.onSuccess {
                app.enqueueImmediateSync()
                state.value = state.value.copy(message = "记账成功")
            }.onFailure { error ->
                state.value = state.value.copy(message = "保存失败：${error.message.orEmpty().take(60)}")
            }
        }
        return true
    }

    fun analyzeAiEntry(text: String) {
        val trimmed = text.trim().take(160)
        if (trimmed.isBlank()) {
            state.value = state.value.copy(message = "请先输入一笔账目")
            return
        }
        if (state.value.isAiAnalyzing) return
        val currentLedger = state.value.ledger
        viewModelScope.launch {
            state.value = state.value.copy(isAiAnalyzing = true, aiSuggestions = emptyList(), message = null)
            runCatching { ledger.analyzeAiEntries(trimmed, currentLedger) }
                .onSuccess { suggestions ->
                    state.value = if (suggestions.isEmpty()) {
                        state.value.copy(isAiAnalyzing = false, message = "没有识别到金额，请试试“6月27日，瑞幸咖啡15元，微信支付”")
                    } else {
                        state.value.copy(isAiAnalyzing = false, aiSuggestions = suggestions)
                    }
                }
                .onFailure { error ->
                    state.value = state.value.copy(
                        isAiAnalyzing = false,
                        message = "智能识别暂时不可用：${error.message.orEmpty().take(60)}"
                    )
                }
        }
    }

    fun saveAiSuggestions(suggestions: List<AiLedgerAnalysis>) {
        val session = state.value.session
        if (session == null) {
            state.value = state.value.copy(message = "请先登录或进入本地模式")
            return
        }
        if (suggestions.isEmpty() || suggestions.any { it.amountMinor <= 0 }) {
            state.value = state.value.copy(message = "智能识别到的金额无效，请重新识别")
            return
        }
        val liveLedger = state.value.ledger
        val prepared = suggestions.mapNotNull { suggestion ->
            val categoryId = suggestion.categoryId
                ?.takeIf { id -> liveLedger.categories.any { it.id == id && it.kind == suggestion.type } }
                ?: LocalAiLedgerParser.resolveCategory(
                    type = suggestion.type,
                    categoryName = suggestion.categoryLabel,
                    parentName = null,
                    categories = liveLedger.categories,
                    sourceText = suggestion.sourceText
                )?.id
            val accountId = suggestion.accountId
                ?.takeIf { id -> liveLedger.accounts.any { it.id == id } }
                ?: state.value.defaultAccountId?.takeIf { id -> liveLedger.accounts.any { it.id == id } }
                ?: liveLedger.accounts.firstOrNull { it.name == "现金" }?.id
                ?: liveLedger.accounts.firstOrNull()?.id
            if (categoryId == null || accountId == null) null else Triple(suggestion, categoryId, accountId)
        }
        if (prepared.size != suggestions.size) {
            state.value = state.value.copy(message = "请检查每一笔的分类和账户后再确认")
            return
        }
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            runCatching {
                ledger.ensureUserWorkspace(session.userId, session.displayName, session.phone, session.email)
                prepared.forEach { (suggestion, categoryId, accountId) ->
                    ledger.addTransaction(
                        userId = session.userId,
                        type = suggestion.type,
                        amountMinor = suggestion.amountMinor,
                        categoryId = categoryId,
                        accountId = accountId,
                        toAccountId = null,
                        note = suggestion.note,
                        occurredAt = suggestion.occurredAt
                    )
                }
            }.onSuccess {
                app.enqueueImmediateSync()
                state.value = state.value.copy(isBusy = false, aiSuggestions = emptyList(), message = "智能记账已保存 ${prepared.size} 笔")
            }.onFailure { error ->
                state.value = state.value.copy(
                    isBusy = false,
                    message = "智能记账保存失败：${error.message.orEmpty().take(60)}"
                )
            }
        }
    }

    fun clearAiSuggestion() {
        state.value = state.value.copy(aiSuggestions = emptyList())
    }

    fun previewExternalBill(uri: Uri, provider: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        reader.readText()
                    } ?: error("无法读取账单文件")
                }
                ExternalBillCsvParser.parse(provider, raw)
            }.onSuccess { preview ->
                state.value = state.value.copy(isBusy = false, billImportPreview = preview)
            }.onFailure { error ->
                state.value = state.value.copy(isBusy = false, message = "账单导入失败：${error.message.orEmpty().take(80)}")
            }
        }
    }

    fun confirmExternalBillImport() {
        val session = state.value.session ?: return
        val preview = state.value.billImportPreview ?: return
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true)
            runCatching { ledger.importExternalBills(session.userId, preview) }
                .onSuccess { result ->
                    app.enqueueImmediateSync()
                    state.value = state.value.copy(
                        isBusy = false,
                        billImportPreview = null,
                        message = "已导入 ${result.imported} 笔账单${if (result.skipped > 0) "，跳过 ${result.skipped} 笔" else ""}"
                    )
                }
                .onFailure { error ->
                    state.value = state.value.copy(isBusy = false, message = "账单导入失败：${error.message.orEmpty().take(80)}")
                }
        }
    }

    fun dismissExternalBillImport() {
        state.value = state.value.copy(billImportPreview = null)
    }

    fun deleteTransaction(id: String) {
        val userId = state.value.session?.userId ?: return
        viewModelScope.launch {
            ledger.deleteTransaction(userId, id)
            app.enqueueImmediateSync()
            state.value = state.value.copy(message = "账目已删除")
        }
    }

    fun updateTransaction(
        id: String,
        amountText: String,
        categoryId: String?,
        accountId: String?,
        note: String,
        occurredDateTime: java.time.LocalDateTime
    ) {
        val session = state.value.session ?: return
        val amount = Money.parseToMinor(amountText)
        val account = accountId ?: state.value.ledger.accounts.firstOrNull()?.id
        if (amount == null || account == null) {
            state.value = state.value.copy(message = if (amount == null) "金额需要大于 0" else "请选择账户")
            return
        }
        viewModelScope.launch {
            runCatching {
                ledger.updateTransaction(
                    userId = session.userId,
                    id = id,
                    amountMinor = amount,
                    categoryId = categoryId,
                    accountId = account,
                    note = note,
                    occurredAt = occurredDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            }.onSuccess {
                app.enqueueImmediateSync()
                state.value = state.value.copy(message = "账目已更新")
            }.onFailure { error ->
                state.value = state.value.copy(message = "更新失败：${error.message.orEmpty().take(60)}")
            }
        }
    }

    fun addAccount(name: String, kind: String) {
        val session = state.value.session ?: return
        viewModelScope.launch {
            ledger.addAccount(session.userId, name, kind)
            app.enqueueImmediateSync()
            state.value = state.value.copy(message = "账户已添加")
        }
    }

    fun deleteAccount(id: String) {
        val userId = state.value.session?.userId ?: return
        viewModelScope.launch {
            ledger.deleteAccount(userId, id)
            app.enqueueImmediateSync()
            val nextDefault = state.value.defaultAccountId?.takeIf { it != id }
            state.value = state.value.copy(defaultAccountId = nextDefault, message = "账户已删除")
        }
    }

    fun setDefaultAccount(accountId: String) {
        val session = state.value.session ?: return
        if (state.value.ledger.accounts.none { it.id == accountId }) return
        sessions.setDefaultAccountId(session.userId, accountId)
        val name = state.value.ledger.accounts.firstOrNull { it.id == accountId }?.name ?: "账户"
        state.value = state.value.copy(defaultAccountId = accountId, message = "已设为默认账户：$name")
    }

    fun addCategory(name: String, kind: String) {
        val session = state.value.session ?: return
        viewModelScope.launch {
            ledger.addCategory(session.userId, name, kind)
            app.enqueueImmediateSync()
            state.value = state.value.copy(message = "分类已添加")
        }
    }

    fun deleteCategory(id: String) {
        val userId = state.value.session?.userId ?: return
        viewModelScope.launch {
            ledger.deleteCategory(userId, id)
            app.enqueueImmediateSync()
            state.value = state.value.copy(message = "分类已删除")
        }
    }

    fun moveCategory(id: String, direction: Int) {
        val userId = state.value.session?.userId ?: return
        viewModelScope.launch {
            ledger.moveCategory(userId, id, direction)
            app.enqueueImmediateSync()
            state.value = state.value.copy(message = "分类顺序已更新")
        }
    }

    fun setBudget(amountText: String, categoryId: String?) {
        val session = state.value.session ?: return
        val amount = Money.parseToMinor(amountText)
        if (amount == null) {
            state.value = state.value.copy(message = "预算金额需要大于 0")
            return
        }
        viewModelScope.launch {
            ledger.setBudget(session.userId, state.value.selectedMonth, categoryId, amount)
            app.enqueueImmediateSync()
            state.value = state.value.copy(message = "预算已保存")
        }
    }

    fun updateProfile(displayName: String, ageText: String, birthDate: String?, gender: String?) {
        val session = state.value.session ?: return
        val age = ageText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (ageText.isNotBlank() && (age == null || age !in 0..150)) {
            state.value = state.value.copy(message = "年龄需要填写 0-150 的整数")
            return
        }
        viewModelScope.launch {
            ledger.updateProfile(session.userId, displayName, age, birthDate, gender)
            app.enqueueImmediateSync()
            state.value = state.value.copy(
                session = sessions.currentSession(),
                message = "已保存本次修改"
            )
        }
    }

    fun changePassword(currentPassword: String, password: String, confirmation: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            when (val result = auth.changePassword(currentPassword, password, confirmation)) {
                is AuthOutcome.SignedIn -> state.value = state.value.copy(session = result.session, isBusy = false, message = result.message)
                is AuthOutcome.Failed -> state.value = state.value.copy(isBusy = false, message = result.message)
                else -> state.value = state.value.copy(isBusy = false)
            }
        }
    }

    fun requestIdentityChange(channelName: String, identifier: String) {
        if (state.value.otpCooldown > 0 || state.value.isBusy) return
        val channel = if (channelName == "email") AuthChannel.EMAIL else AuthChannel.PHONE
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            when (val result = auth.requestIdentityChange(channel, identifier)) {
                is AuthOutcome.OtpSent -> {
                    state.value = state.value.copy(isBusy = false, message = result.message)
                    startOtpCooldown()
                }
                is AuthOutcome.Failed -> state.value = state.value.copy(isBusy = false, message = result.message)
                else -> state.value = state.value.copy(isBusy = false)
            }
        }
    }

    fun verifyIdentityChange(channelName: String, identifier: String, code: String) {
        val channel = if (channelName == "email") AuthChannel.EMAIL else AuthChannel.PHONE
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            when (val result = auth.verifyIdentityChange(channel, identifier, code)) {
                is AuthOutcome.SignedIn -> {
                    ledger.updateProfileIdentity(result.session.userId, result.session.email, result.session.phone)
                    app.enqueueImmediateSync()
                    state.value = state.value.copy(session = result.session, isBusy = false, message = result.message)
                }
                is AuthOutcome.Failed -> state.value = state.value.copy(isBusy = false, message = result.message)
                else -> state.value = state.value.copy(isBusy = false)
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        val session = state.value.session ?: return
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            runCatching {
                val jpeg = readAvatarJpeg(uri)
                ledger.saveAvatar(session.userId, jpeg)
            }.onSuccess { url ->
                app.enqueueImmediateSync()
                state.value = state.value.copy(isBusy = false, avatarUrl = url, message = "头像已保存")
            }.onFailure {
                state.value = state.value.copy(isBusy = false, message = "头像保存失败：${it.message.orEmpty().take(80)}")
            }
        }
    }

    fun refreshAvatar() {
        val avatarKey = state.value.ledger.profile?.avatarKey
        viewModelScope.launch {
            val avatarUrl = ledger.resolveAvatarUrl(avatarKey)
            state.value = state.value.copy(avatarUrl = avatarUrl)
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        if (state.value.isCheckingUpdate) return
        viewModelScope.launch {
            state.value = state.value.copy(isCheckingUpdate = true)
            runCatching { ledger.latestAppVersion() }
                .onSuccess { latest ->
                    val newer = latest?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
                    val canPresent = !silent || state.value.automaticUpdatePrompts || newer?.isMandatory == true
                    state.value = state.value.copy(
                        isCheckingUpdate = false,
                        availableUpdate = if (canPresent) newer else null,
                        message = when {
                            newer != null -> if (silent) null else "发现新版本 ${newer.versionName}"
                            !silent -> "当前已是最新版本 ${BuildConfig.VERSION_NAME}"
                            else -> null
                        }
                    )
                }
                .onFailure {
                    state.value = state.value.copy(
                        isCheckingUpdate = false,
                        message = if (silent) null else "版本检查失败，请稍后重试"
                    )
                }
        }
    }

    fun dismissUpdate() {
        if (state.value.availableUpdate?.isMandatory == true) return
        state.value = state.value.copy(availableUpdate = null)
    }

    fun disableAutomaticUpdatePrompts() {
        sessions.setAutomaticUpdatePromptsEnabled(false)
        state.value = state.value.copy(
            automaticUpdatePrompts = false,
            availableUpdate = null,
            message = "已关闭自动更新提醒，可在“我的 → 设置 → 检查更新”手动更新"
        )
    }

    fun syncNow() {
        if (state.value.isBusy) return
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true)
            runCatching { ledger.syncNow() }
                .onSuccess { label -> state.value = state.value.copy(isBusy = false, syncLabel = label, message = label) }
                .onFailure { error ->
                    state.value = state.value.copy(
                        isBusy = false,
                        syncLabel = "同步失败",
                        message = "同步失败，本地数据仍然保留：${error.message.orEmpty().take(80)}"
                    )
                }
        }
    }

    fun refreshMailbox() {
        mailboxRefreshJob?.cancel()
        mailboxRefreshJob = viewModelScope.launch {
            val messages = ledger.loadOfficialMessages()
            state.value = state.value.copy(officialMessages = messages)
        }
    }

    fun submitFeedback(content: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true)
            runCatching { ledger.submitFeedback(content) }
                .onSuccess { state.value = state.value.copy(isBusy = false, message = "建议已发送，感谢你认真告诉我") }
                .onFailure { state.value = state.value.copy(isBusy = false, message = it.message ?: "建议发送失败") }
        }
    }

    fun startMembershipPurchase(provider: String, reference: String) {
        val profile = state.value.ledger.profile
        if (profile?.role == "admin" || profile?.membershipTier == "permanent") {
            state.value = state.value.copy(message = "当前账号已经拥有永久权益")
            return
        }
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true, message = null)
            runCatching { ledger.createMembershipOrder(provider, reference) }
                .onSuccess {
                    app.enqueueImmediateSync()
                    state.value = state.value.copy(
                        isBusy = false,
                        message = "$it，管理员确认到账后会升级永久会员"
                    )
                }
                .onFailure {
                    state.value = state.value.copy(isBusy = false, message = it.message ?: "会员订单提交失败")
                }
        }
    }

    fun setSecureScreen(enabled: Boolean) {
        sessions.setSecureScreenEnabled(enabled)
        state.value = state.value.copy(secureScreen = enabled)
    }

    fun setDarkMode(enabled: Boolean) {
        sessions.setDarkModeEnabled(enabled)
        state.value = state.value.copy(darkMode = enabled)
    }

    fun setThemeTone(tone: String) {
        sessions.setThemeTone(tone)
        state.value = state.value.copy(themeTone = tone, message = "主题已切换为 ${plushThemeName(tone)}")
    }

    fun exportCsv(pin: String) {
        val session = state.value.session ?: return
        if (!sessions.hasPin()) {
            state.value = state.value.copy(message = "请先在安全设置中设置 PIN")
            return
        }
        if (!sessions.verifyPin(pin)) {
            state.value = state.value.copy(message = "导出需要正确 PIN")
            return
        }
        viewModelScope.launch {
            val file = ledger.exportCsv(session.userId)
            state.value = state.value.copy(exportPath = file.absolutePath, message = "已导出")
        }
    }

    fun signOut() {
        auth.signOut()
        ledgerJob?.cancel()
        cloudRefreshJob?.cancel()
        lastAvatarKey = null
        state.value = UiState(
            secureScreen = sessions.isSecureScreenEnabled(),
            lockOnLaunch = sessions.isLockOnLaunchEnabled(),
            biometricUnlock = sessions.isBiometricUnlockEnabled(),
            darkMode = sessions.isDarkModeEnabled(),
            themeTone = sessions.themeTone()
        )
    }

    fun deleteAccountPermanently() {
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true)
            runCatching { ledger.deleteCloudAccount() }
                .onSuccess {
                    ledgerJob?.cancel()
                    cloudRefreshJob?.cancel()
                    state.value = UiState(
                        message = "账号和云端数据已注销",
                        secureScreen = sessions.isSecureScreenEnabled(),
                        darkMode = sessions.isDarkModeEnabled(),
                        themeTone = sessions.themeTone()
                    )
                }
                .onFailure {
                    state.value = state.value.copy(isBusy = false, message = "注销失败：${it.message.orEmpty().take(100)}")
                }
        }
    }

    fun clearMessage() {
        state.value = state.value.copy(message = null)
    }

    private fun startOtpCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (second in 60 downTo 1) {
                state.value = state.value.copy(otpCooldown = second)
                delay(1_000)
            }
            state.value = state.value.copy(otpCooldown = 0)
        }
    }

    private suspend fun completeSignIn(session: UserSession, message: String) {
        var syncWarning: String? = null
        session.accessToken?.let { token ->
            runCatching { ledger.restoreFromCloud(token) }
                .onFailure { syncWarning = "登录成功，但首次云恢复失败；本地数据仍安全保留" }
        }
        ledger.ensureUserWorkspace(session.userId, session.displayName, session.phone, session.email)
        app.enqueueImmediateSync()
        state.value = state.value.copy(
            session = sessions.currentSession() ?: session,
            locked = false,
            passwordSetupSession = null,
            isBusy = false,
            message = syncWarning ?: message,
            selectedTab = AppTab.HOME,
            defaultAccountId = sessions.defaultAccountId(session.userId)
        )
        tabHistory.clear()
        observeLedger(session.userId)
        startCloudRefresh(session)
    }

    private fun startCloudRefresh(session: UserSession?) {
        cloudRefreshJob?.cancel()
        if (session?.accessToken.isNullOrBlank()) return
        cloudRefreshJob = viewModelScope.launch {
            delay(8_000)
            while (true) {
                if (!state.value.isBusy && !state.value.locked) {
                    runCatching { ledger.syncNow() }
                        .onSuccess { label -> state.value = state.value.copy(syncLabel = label) }
                }
                delay(30_000)
            }
        }
    }

    private fun observeLedger(userId: String) {
        ledgerJob?.cancel()
        ledgerJob = viewModelScope.launch {
            ledger.observeState(userId, state.value.selectedMonth).collectLatest {
                val activeAccountIds = it.accounts.map { account -> account.id }.toSet()
                val preferred = state.value.defaultAccountId?.takeIf { id -> id in activeAccountIds }
                    ?: sessions.defaultAccountId(userId)?.takeIf { id -> id in activeAccountIds }
                    ?: it.accounts.firstOrNull { account -> account.name == "现金" }?.id
                    ?: it.accounts.firstOrNull()?.id
                if (preferred != null && preferred != state.value.defaultAccountId) {
                    sessions.setDefaultAccountId(userId, preferred)
                }
                state.value = state.value.copy(ledger = it, defaultAccountId = preferred)
                val avatarKey = it.profile?.avatarKey
                if (avatarKey != lastAvatarKey || state.value.avatarUrl.isNullOrBlank()) {
                    lastAvatarKey = avatarKey
                    val avatarUrl = ledger.resolveAvatarUrl(avatarKey)
                    state.value = state.value.copy(avatarUrl = avatarUrl)
                }
            }
        }
    }

    private suspend fun readAvatarJpeg(uri: Uri): ByteArray = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val source = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取图片")
        require(source.size <= 12 * 1024 * 1024) { "图片不能超过 12MB" }
        val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size) ?: error("图片格式不支持")
        val maxSide = maxOf(bitmap.width, bitmap.height)
        val scaled = if (maxSide > 1024) {
            val ratio = 1024f / maxSide
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        ByteArrayOutputStream().use { output ->
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 86, output)) { "图片压缩失败" }
            output.toByteArray()
        }.also {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }
}
