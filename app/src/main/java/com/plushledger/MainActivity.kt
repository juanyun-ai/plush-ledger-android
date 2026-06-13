package com.plushledger

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plushledger.ui.AppTab
import com.plushledger.ui.AuthPage
import com.plushledger.ui.FabricBackdrop
import com.plushledger.ui.InboxScreen
import com.plushledger.ui.LedgerViewModel
import com.plushledger.ui.LocalPlushPalette
import com.plushledger.ui.MascotArt
import com.plushledger.ui.MyScreen
import com.plushledger.ui.PlushButton
import com.plushledger.ui.PlushCard
import com.plushledger.ui.PlushLedgerTheme
import com.plushledger.ui.RecordScreen
import com.plushledger.ui.BillsScreen
import com.plushledger.ui.StatsScreen
import com.plushledger.ui.HomeScreen
import com.plushledger.update.AppUpdateManager
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    private lateinit var updateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = AppUpdateManager(this)
        setContent {
            PlushLedgerApp(
                biometricAvailable = isBiometricAvailable(),
                requestBiometric = { onSuccess -> showBiometricPrompt(onSuccess) },
                setSecure = { secure ->
                    if (secure) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                },
                downloadUpdate = updateManager::download
            )
        }
    }

    override fun onStart() {
        super.onStart()
        updateManager.register()
    }

    override fun onStop() {
        updateManager.unregister()
        super.onStop()
    }

    private fun isBiometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁绒绒记账")
                .setSubtitle("使用系统生物识别")
                .setNegativeButtonText("取消")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }
}

@Composable
private fun PlushLedgerApp(
    biometricAvailable: Boolean,
    requestBiometric: (() -> Unit) -> Unit,
    setSecure: (Boolean) -> Unit,
    downloadUpdate: (com.plushledger.sync.AppVersionInfo) -> Unit,
    viewModel: LedgerViewModel = viewModel()
) {
    val state by viewModel.state
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val onboardingStore = remember { context.getSharedPreferences("onboarding", Context.MODE_PRIVATE) }
    var onboardingDone by rememberSaveable { mutableStateOf(onboardingStore.getBoolean("v075_seen", false)) }

    DisposableEffect(state.secureScreen) {
        setSecure(state.secureScreen)
        onDispose { }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    PlushLedgerTheme(state.darkMode) {
        Surface(Modifier.fillMaxSize(), color = LocalPlushPalette.current.background) {
            Box(Modifier.fillMaxSize()) {
                FabricBackdrop()
                when {
                    state.session == null && !onboardingDone -> WelcomeScreen(
                        onStart = {
                            onboardingStore.edit().putBoolean("v075_seen", true).apply()
                            onboardingDone = true
                        },
                        onPreview = {
                            onboardingStore.edit().putBoolean("v075_seen", true).apply()
                            onboardingDone = true
                            viewModel.signInLocal("体验用户", "plush075")
                        }
                    )
                    state.session == null -> AuthScreen(state, viewModel)
                    state.locked -> LockScreen(
                        biometricAvailable = biometricAvailable && state.biometricUnlock,
                        onUnlockPin = viewModel::unlockWithPin,
                        onBiometric = { requestBiometric { viewModel.unlockWithBiometric() } }
                    )
                    else -> LedgerShell(viewModel, biometricAvailable, downloadUpdate)
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp))
                state.availableUpdate?.let { update ->
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissUpdate() },
                        title = { Text("发现新版本 ${update.versionName}", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(update.releaseNotes.ifBlank { "修复问题并改进使用体验。" })
                                Text("安装包约 ${update.fileSizeBytes / 1024 / 1024}MB", color = LocalPlushPalette.current.muted)
                                Text("下载完成后将由 Android 系统安装器确认更新。", color = LocalPlushPalette.current.muted)
                            }
                        },
                        dismissButton = if (update.isMandatory) null else {
                            { TextButton(onClick = viewModel::dismissUpdate) { Text("稍后") } }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                downloadUpdate(update)
                                viewModel.dismissUpdate()
                            }) { Text("下载更新") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onPreview: () -> Unit) {
    val palette = LocalPlushPalette.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Spacer(Modifier.height(18.dp)) }
        item { MascotArt(190.dp) }
        item {
            Image(
                painter = painterResource(R.drawable.brand_wordmark),
                contentDescription = "绒绒记账",
                modifier = Modifier.fillMaxWidth(0.68f).height(58.dp),
                contentScale = ContentScale.Fit
            )
        }
        item { Text("让每一次记录，都变得柔软而清晰", color = palette.muted, fontSize = 14.sp) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WelcomeFeature(R.drawable.art_feature_record, "轻松记账", Modifier.weight(1f))
                WelcomeFeature(R.drawable.art_feature_chart, "清晰统计", Modifier.weight(1f))
                WelcomeFeature(R.drawable.art_feature_cloud, "安心同步", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(18.dp, 7.dp).clip(RoundedCornerShape(4.dp)).background(palette.rose))
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(palette.border))
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(palette.border))
            }
        }
        item {
            PlushButton("开始使用", Icons.Default.EditNote, Modifier.fillMaxWidth(), onClick = onStart)
            TextButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) { Text("先看看", color = palette.muted) }
        }
    }
}

@Composable
private fun WelcomeFeature(res: Int, label: String, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(res),
            contentDescription = label,
            modifier = Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(6.dp))
        Text(label, color = palette.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AuthScreen(state: com.plushledger.ui.UiState, viewModel: LedgerViewModel) {
    state.passwordSetupSession?.let {
        PasswordSetupScreen(
            isRegistration = state.passwordSetupIsRegistration,
            busy = state.isBusy,
            onSave = viewModel::finishPasswordSetup
        )
        return
    }

    var mode by rememberSaveable { mutableStateOf("email") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var localName by rememberSaveable { mutableStateOf("") }
    var localPassword by rememberSaveable { mutableStateOf("") }
    var showAgreement by rememberSaveable { mutableStateOf(false) }
    var agreementRead by rememberSaveable { mutableStateOf(false) }
    var agreementChecked by rememberSaveable { mutableStateOf(false) }
    val palette = LocalPlushPalette.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Image(
                painter = painterResource(R.drawable.brand_logo),
                contentDescription = "绒绒记账",
                modifier = Modifier.fillMaxWidth(0.76f).heightIn(max = 210.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(14.dp))
            Text("绒绒记账", fontSize = 30.sp, fontWeight = FontWeight.Black, color = palette.ink)
            Spacer(Modifier.height(18.dp))
            PlushCard(Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = if (mode == "email") 0 else 1) {
                    Tab(mode == "email", { mode = "email" }, text = { Text("邮箱账号") })
                    Tab(mode == "local", { mode = "local" }, text = { Text("本地模式") })
                }
                Spacer(Modifier.height(14.dp))
                if (mode == "local") {
                    OutlinedTextField(localName, { localName = it.take(32) }, label = { Text("用户名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        localPassword,
                        { localPassword = it.take(64) },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
                    )
                    Spacer(Modifier.height(14.dp))
                    PlushButton("进入本地账本", Icons.Default.Lock, Modifier.fillMaxWidth()) {
                        viewModel.signInLocal(localName, localPassword)
                    }
                } else {
                    when (state.authPage) {
                        AuthPage.LOGIN -> {
                            OutlinedTextField(email, { email = it.trim() }, label = { Text("邮箱") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                password,
                                { password = it.take(64) },
                                label = { Text("密码") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            Spacer(Modifier.height(14.dp))
                            PlushButton("登录", Icons.Default.Lock, Modifier.fillMaxWidth(), enabled = !state.isBusy) {
                                viewModel.signInWithPassword(email, password)
                            }
                            TextButton(onClick = { viewModel.showAuthPage(AuthPage.RESET) }, modifier = Modifier.fillMaxWidth()) { Text("忘记密码") }
                            OutlinedButton(onClick = { viewModel.showAuthPage(AuthPage.REGISTER) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("注册新账号")
                            }
                            SocialLoginRow(viewModel)
                        }
                        AuthPage.REGISTER, AuthPage.RESET -> {
                            val isReset = state.authPage == AuthPage.RESET
                            Text(if (isReset) "重置密码" else "注册账号", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = palette.ink)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(email, { email = it.trim() }, label = { Text("邮箱") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(otp, { otp = it.filter(Char::isDigit).take(8) }, label = { Text("验证码") }, modifier = Modifier.weight(1f), singleLine = true)
                                PlushButton(
                                    text = when {
                                        state.otpCooldown > 0 -> "${state.otpCooldown}s"
                                        state.isBusy -> "发送中"
                                        else -> "获取验证码"
                                    },
                                    icon = Icons.Default.Refresh,
                                    modifier = Modifier.weight(0.8f),
                                    enabled = state.otpCooldown == 0 && !state.isBusy
                                ) { viewModel.sendRegistrationOtp(email, isReset) }
                            }
                            if (!isReset) {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = agreementChecked,
                                        onCheckedChange = { if (agreementRead) agreementChecked = it },
                                        enabled = agreementRead
                                    )
                                    TextButton(onClick = { showAgreement = true }) { Text("用户协议与隐私政策") }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            PlushButton(
                                text = if (isReset) "验证并重置密码" else "注册",
                                icon = Icons.Default.Shield,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isBusy && (isReset || agreementChecked)
                            ) { viewModel.verifyRegistrationOtp(email, otp, isReset) }
                            TextButton(onClick = { viewModel.showAuthPage(AuthPage.LOGIN) }, modifier = Modifier.fillMaxWidth()) { Text("返回登录") }
                        }
                    }
                }
            }
        }
    }

    if (showAgreement) {
        AgreementDialog(
            onDismiss = { showAgreement = false },
            onAccepted = {
                agreementRead = true
                agreementChecked = true
                showAgreement = false
            }
        )
    }
}

@Composable
private fun SocialLoginRow(viewModel: LedgerViewModel) {
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { viewModel.socialLogin("微信") }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.ChatBubble, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("微信")
        }
        OutlinedButton(onClick = { viewModel.socialLogin("QQ") }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.AlternateEmail, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("QQ")
        }
    }
}

@Composable
private fun AgreementDialog(onDismiss: () -> Unit, onAccepted: () -> Unit) {
    var seconds by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1_000)
            seconds--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("用户协议与隐私政策", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("1. 云端账号的数据将存储于 Supabase 托管数据库；本地模式的数据仅保存在当前设备。") }
                item { Text("2. 我们处理邮箱、昵称、账目、预算和设备同步所必需的信息，不用于广告画像。") }
                item { Text("3. 用户可导出数据、退出登录或申请注销；注销后云端数据将删除且无法恢复。") }
                item { Text("4. 用户应妥善保护密码和验证码，不得利用本应用从事违法活动。") }
                item { Text("5. 邮件服务商会处理验证码投递信息，但不会获得账目内容。") }
                item { Text("6. 服务和条款发生重要变化时，将通过应用信箱通知。") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            TextButton(onClick = onAccepted, enabled = seconds == 0) {
                Text(if (seconds == 0) "我已知晓" else "我已知晓（${seconds}s）")
            }
        }
    )
}

@Composable
private fun PasswordSetupScreen(isRegistration: Boolean, busy: Boolean, onSave: (String, String) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    val palette = LocalPlushPalette.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        PlushCard(Modifier.fillMaxWidth()) {
            Text(if (isRegistration) "设置登录密码" else "设置新密码", fontWeight = FontWeight.Black, fontSize = 24.sp, color = palette.ink)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(password, { password = it.take(64) }, label = { Text("8-64 位字母与数字") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(confirmation, { confirmation = it.take(64) }, label = { Text("再次输入密码") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Spacer(Modifier.height(14.dp))
            PlushButton("保存密码", Icons.Default.Lock, Modifier.fillMaxWidth(), enabled = !busy) { onSave(password, confirmation) }
        }
    }
}

@Composable
private fun LockScreen(biometricAvailable: Boolean, onUnlockPin: (String) -> Unit, onBiometric: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        PlushCard(Modifier.fillMaxWidth()) {
            Text("账本已锁定", fontWeight = FontWeight.Black, fontSize = 26.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, label = { Text("PIN") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(14.dp))
            PlushButton("解锁", Icons.Default.Lock, Modifier.fillMaxWidth()) { onUnlockPin(pin) }
            if (biometricAvailable) TextButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) { Text("使用生物识别") }
        }
    }
}

@Composable
private fun LedgerShell(
    viewModel: LedgerViewModel,
    biometricAvailable: Boolean,
    downloadUpdate: (com.plushledger.sync.AppVersionInfo) -> Unit
) {
    val state by viewModel.state
    val palette = LocalPlushPalette.current
    BackHandler(enabled = state.selectedTab != AppTab.HOME || viewModel.hasTabHistory()) {
        viewModel.navigateBack()
    }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            if (state.selectedTab != AppTab.RECORD) {
                Surface(
                    modifier = Modifier.navigationBarsPadding(),
                    color = palette.surface,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 18.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        navItems.forEach { item ->
                            val selected = state.selectedTab == item.tab
                            Column(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.selectTab(item.tab) }.padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) palette.rose else palette.muted,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    item.label,
                                    color = if (selected) palette.rose else palette.muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.selectedTab) {
                AppTab.HOME -> HomeScreen(
                    state.ledger,
                    state.selectedDate,
                    viewModel::changeMonth,
                    viewModel::selectStatsDate,
                    viewModel::deleteTransaction,
                    onRecord = { viewModel.selectTab(AppTab.RECORD) },
                    onBills = { viewModel.selectTab(AppTab.BILLS) }
                )
                AppTab.BILLS -> BillsScreen(state.ledger, state.selectedDate, viewModel::changeMonth, viewModel::selectStatsDate, viewModel::deleteTransaction)
                AppTab.RECORD -> RecordScreen(
                    state = state,
                    onBack = { viewModel.navigateBack() },
                    onAdd = viewModel::addTransaction,
                    onBudget = viewModel::setBudget,
                    onAddAccount = viewModel::addAccount,
                    onDeleteAccount = viewModel::deleteAccount,
                    onAddCategory = viewModel::addCategory,
                    onDeleteCategory = viewModel::deleteCategory,
                    onDeleteTransaction = viewModel::deleteTransaction
                )
                AppTab.STATS -> StatsScreen(state.ledger, state.selectedDate, viewModel::changeMonth, viewModel::selectStatsDate)
                AppTab.MY -> MyScreen(state, biometricAvailable, viewModel, downloadUpdate)
            }
            if (state.selectedTab == AppTab.HOME) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 14.dp)
                ) {
                    PlushButton(
                        "记一笔",
                        Icons.Default.EditNote,
                        Modifier.width(154.dp),
                        onClick = { viewModel.selectTab(AppTab.RECORD) }
                    )
                }
            }
        }
    }
}

private data class NavItem(val tab: AppTab, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val navItems = listOf(
    NavItem(AppTab.HOME, "首页", Icons.Default.Home),
    NavItem(AppTab.BILLS, "账单", Icons.Default.ReceiptLong),
    NavItem(AppTab.STATS, "统计", Icons.Default.PieChart),
    NavItem(AppTab.MY, "我的", Icons.Default.Person)
)
