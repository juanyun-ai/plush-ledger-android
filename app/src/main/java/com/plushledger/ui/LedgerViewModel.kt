package com.plushledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plushledger.PlushLedgerApplication
import com.plushledger.auth.AuthChannel
import com.plushledger.auth.AuthOutcome
import com.plushledger.auth.UserSession
import com.plushledger.data.LedgerState
import com.plushledger.data.Money
import com.plushledger.data.OfficialMessage
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AppTab { HOME, RECORD, STATS, INBOX, MY }
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
    val message: String? = null,
    val syncLabel: String = "本地保存",
    val secureScreen: Boolean = false,
    val lockOnLaunch: Boolean = false,
    val biometricUnlock: Boolean = false,
    val darkMode: Boolean = false,
    val exportPath: String? = null,
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
            darkMode = sessions.isDarkModeEnabled()
        )
        session?.let {
            viewModelScope.launch {
                ledger.ensureUserWorkspace(it.userId, it.displayName, it.phone, it.email)
                if (!state.value.locked) observeLedger(it.userId)
            }
        }
    }

    fun selectTab(tab: AppTab) {
        state.value = state.value.copy(selectedTab = tab, message = null)
        if (tab == AppTab.INBOX) refreshMailbox()
    }

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
            is AuthOutcome.SignedIn -> viewModelScope.launch { completeSignIn(result.session, result.message) }
            is AuthOutcome.Failed -> state.value = state.value.copy(message = result.message)
            else -> Unit
        }
    }

    fun socialLogin(provider: String) {
        state.value = state.value.copy(message = "$provider 登录需要开放平台 AppID 和审核，当前尚未启用")
    }

    fun unlockWithPin(pin: String) {
        when (val result = auth.signInWithPin(pin)) {
            is AuthOutcome.SignedIn -> {
                state.value = state.value.copy(locked = false, message = result.message)
                observeLedger(result.session.userId)
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
        occurredDate: LocalDate
    ) {
        val session = state.value.session ?: return
        val amount = Money.parseToMinor(amountText)
        if (amount == null) {
            state.value = state.value.copy(message = "金额需要大于 0")
            return
        }
        val account = accountId ?: state.value.ledger.accounts.firstOrNull()?.id
        if (account == null) {
            state.value = state.value.copy(message = "请先添加账户")
            return
        }
        viewModelScope.launch {
            ledger.addTransaction(
                userId = session.userId,
                type = type,
                amountMinor = amount,
                categoryId = categoryId,
                accountId = account,
                toAccountId = toAccountId,
                note = note,
                occurredAt = occurredDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            app.enqueueImmediateSync()
            state.value = state.value.copy(message = "记账成功")
        }
    }

    fun deleteTransaction(id: String) {
        val userId = state.value.session?.userId ?: return
        viewModelScope.launch {
            ledger.deleteTransaction(userId, id)
            app.enqueueImmediateSync()
            state.value = state.value.copy(message = "账目已删除")
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
            state.value = state.value.copy(message = "账户已删除")
        }
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

    fun updateProfile(displayName: String, avatarKey: String) {
        val session = state.value.session ?: return
        viewModelScope.launch {
            ledger.updateProfile(session.userId, displayName, avatarKey)
            app.enqueueImmediateSync()
            state.value = state.value.copy(
                session = sessions.currentSession(),
                message = "资料已保存"
            )
        }
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
        viewModelScope.launch {
            state.value = state.value.copy(officialMessages = ledger.loadOfficialMessages())
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

    fun startMembershipPurchase(provider: String) {
        val profile = state.value.ledger.profile
        if (profile?.role == "admin" || profile?.membershipTier == "permanent") {
            state.value = state.value.copy(message = "当前账号已经拥有永久权益")
            return
        }
        state.value = state.value.copy(message = "$provider 商户通道尚未完成审核，本次不会扣款")
    }

    fun setSecureScreen(enabled: Boolean) {
        sessions.setSecureScreenEnabled(enabled)
        state.value = state.value.copy(secureScreen = enabled)
    }

    fun setDarkMode(enabled: Boolean) {
        sessions.setDarkModeEnabled(enabled)
        state.value = state.value.copy(darkMode = enabled)
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
        state.value = UiState(
            secureScreen = sessions.isSecureScreenEnabled(),
            lockOnLaunch = sessions.isLockOnLaunchEnabled(),
            biometricUnlock = sessions.isBiometricUnlockEnabled(),
            darkMode = sessions.isDarkModeEnabled()
        )
    }

    fun deleteAccountPermanently() {
        viewModelScope.launch {
            state.value = state.value.copy(isBusy = true)
            runCatching { ledger.deleteCloudAccount() }
                .onSuccess {
                    ledgerJob?.cancel()
                    state.value = UiState(
                        message = "账号和云端数据已注销",
                        secureScreen = sessions.isSecureScreenEnabled(),
                        darkMode = sessions.isDarkModeEnabled()
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
            selectedTab = AppTab.HOME
        )
        observeLedger(session.userId)
    }

    private fun observeLedger(userId: String) {
        ledgerJob?.cancel()
        ledgerJob = viewModelScope.launch {
            ledger.observeState(userId, state.value.selectedMonth).collectLatest {
                state.value = state.value.copy(ledger = it)
            }
        }
    }
}
