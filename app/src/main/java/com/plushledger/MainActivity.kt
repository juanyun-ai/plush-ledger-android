package com.plushledger

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.plushledger.update.UpdateDownloadPhase
import com.plushledger.update.UpdateDownloadUiState
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
                downloadUpdate = updateManager::download,
                downloadState = updateManager.uiState,
                cancelDownload = updateManager::cancelDownload,
                retryDownload = updateManager::retryDownload,
                openExternalDownload = updateManager::openExternalDownload,
                dismissDownloadStatus = updateManager::dismissDownloadStatus
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
    downloadState: UpdateDownloadUiState,
    cancelDownload: () -> Unit,
    retryDownload: () -> Unit,
    openExternalDownload: () -> Unit,
    dismissDownloadStatus: () -> Unit,
    viewModel: LedgerViewModel = viewModel()
) {
    val state by viewModel.state
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val onboardingStore = remember { context.getSharedPreferences("onboarding", Context.MODE_PRIVATE) }
    var onboardingDone by rememberSaveable { mutableStateOf(onboardingStore.getBoolean("v075_seen", false)) }
    var confirmDisableUpdatePrompt by rememberSaveable { mutableStateOf(false) }

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

    PlushLedgerTheme(state.darkMode, state.themeTone) {
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
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 82.dp),
                    snackbar = { data -> PlushSnackbar(data.visuals.message) }
                )
                state.availableUpdate?.takeUnless { downloadState.isActive }?.let { update ->
                    AvailableUpdateDialog(
                        update = update,
                        onLater = viewModel::dismissUpdate,
                        onDisablePrompts = { confirmDisableUpdatePrompt = true },
                        onDownload = {
                            downloadUpdate(update)
                            viewModel.dismissUpdate()
                        }
                    )
                }
                if (confirmDisableUpdatePrompt) {
                    DisableUpdatePromptDialog(
                        onDismiss = { confirmDisableUpdatePrompt = false },
                        onConfirm = {
                            confirmDisableUpdatePrompt = false
                            viewModel.disableAutomaticUpdatePrompts()
                        }
                    )
                }
                if (downloadState.isVisible) {
                    UpdateDownloadStatusDialog(
                        state = downloadState,
                        onCancel = cancelDownload,
                        onRetry = retryDownload,
                        onExternalDownload = openExternalDownload,
                        onDismiss = dismissDownloadStatus
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailableUpdateDialog(
    update: com.plushledger.sync.AppVersionInfo,
    onLater: () -> Unit,
    onDisablePrompts: () -> Unit,
    onDownload: () -> Unit
) {
    val palette = LocalPlushPalette.current
    Dialog(onDismissRequest = { if (!update.isMandatory) onLater() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFFFFFCF7),
            border = BorderStroke(2.dp, Color(0xFFFFD8A0)),
            shadowElevation = 20.dp
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MascotArt(92.dp)
                Text("发现新版本 ${update.versionName}", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 25.sp)
                Text(update.releaseNotes.ifBlank { "修复问题并改进使用体验。" }, color = palette.ink, fontSize = 14.sp, lineHeight = 21.sp)
                Text("安装包约 ${update.fileSizeBytes / 1024 / 1024}MB，下载完成后由 Android 系统安装器确认更新。", color = palette.muted, fontSize = 12.sp, lineHeight = 18.sp)
                Text("稍后也可前往“我的 → 设置 → 检查更新”手动更新。", color = palette.moss, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                PlushButton("下载更新", Icons.Default.Refresh, Modifier.fillMaxWidth(), color = palette.rose, onClick = onDownload)
                if (!update.isMandatory) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onLater) { Text("稍后", color = palette.muted) }
                        TextButton(onClick = onDisablePrompts) { Text("以后不再提醒", color = palette.rose) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisableUpdatePromptDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val palette = LocalPlushPalette.current
    var seconds by remember { mutableIntStateOf(2) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1_000)
            seconds--
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFFFFFCF7),
            border = BorderStroke(1.5.dp, Color(0xFFFFD8A0)),
            shadowElevation = 18.dp
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MascotArt(76.dp)
                Text("关闭自动更新提醒？", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 23.sp)
                Text("确认后，新版本不再自动弹窗。你仍可在“我的 → 设置 → 检查更新”主动查看和下载。", color = palette.muted, fontSize = 13.sp, lineHeight = 20.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("取消") }
                    PlushButton(
                        if (seconds > 0) "请等待 ${seconds}s" else "确认关闭",
                        Icons.Default.CheckCircle,
                        Modifier.weight(1f),
                        enabled = seconds == 0,
                        color = palette.rose,
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}

@Composable
private fun PlushSnackbar(message: String) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surfaceAlt,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, palette.rose.copy(alpha = 0.42f)),
        shadowElevation = 14.dp
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = palette.surface, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_transparent),
                    contentDescription = null,
                    modifier = Modifier.padding(3.dp).size(42.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(message, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
            Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.pink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun UpdateDownloadStatusDialog(
    state: UpdateDownloadUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onExternalDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPlushPalette.current
    val active = state.isActive
    val knownProgress = state.progress.coerceIn(0, 100).takeIf { state.progress >= 0 }
    Dialog(onDismissRequest = { if (!active) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            color = Color(0xFFFFFCF7),
            border = BorderStroke(2.dp, Color(0xFFFFD8A0)),
            shadowElevation = 20.dp
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 4.dp) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_transparent),
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp).size(72.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (state.phase) {
                                UpdateDownloadPhase.FAILED -> "下载遇到问题"
                                UpdateDownloadPhase.CANCELLED -> "下载已取消"
                                UpdateDownloadPhase.VERIFYING -> "正在校验 v${state.versionName}"
                                else -> "正在下载 v${state.versionName}"
                            },
                            color = palette.ink,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            maxLines = 1
                        )
                        Text(state.message, color = palette.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC55D), modifier = Modifier.size(24.dp))
                }
                if (active) {
                    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF3E2), border = BorderStroke(1.dp, Color(0xFFFFD9A6))) {
                        if (knownProgress == null) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 4.dp, vertical = 2.dp),
                                color = palette.moss,
                                trackColor = Color.Transparent
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { knownProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 4.dp, vertical = 2.dp),
                                color = palette.moss,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (state.downloadedBytes > 0L) formatDownloadBytes(state.downloadedBytes) else "正在连接下载源", color = palette.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(knownProgress?.let { "$it%" } ?: "等待开始", color = palette.ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFFFE4BD)))
                    Text("♥ 安装包大小 ${if (state.totalBytes > 0L) formatDownloadBytes(state.totalBytes) else "获取中"}", color = palette.muted, fontSize = 13.sp)
                    Text("★ 只有收到真实文件字节后才会显示百分比。", color = palette.muted, fontSize = 13.sp)
                }
                when (state.phase) {
                    UpdateDownloadPhase.FAILED, UpdateDownloadPhase.CANCELLED -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state.phase == UpdateDownloadPhase.FAILED) {
                                OutlinedButton(onClick = onExternalDownload, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("浏览器下载") }
                            }
                            PlushButton("重新下载", Icons.Default.Refresh, Modifier.weight(1f), color = palette.rose, onClick = onRetry)
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭", color = palette.muted) }
                    }
                    else -> TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消下载", color = palette.rose, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun formatDownloadBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onPreview: () -> Unit) {
    val palette = LocalPlushPalette.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Box(Modifier.fillMaxWidth().height(284.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFBE3D), modifier = Modifier.align(Alignment.TopStart).padding(start = 36.dp, top = 28.dp).size(32.dp))
                Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF9D82), modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp, top = 20.dp).size(22.dp))
                Icon(Icons.Default.Favorite, null, tint = palette.moss, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 26.dp, top = 62.dp).size(20.dp))
                Image(
                    painter = painterResource(R.drawable.ic_launcher_transparent),
                    contentDescription = "绒绒记账",
                    modifier = Modifier.size(276.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
        item {
            Image(
                painter = painterResource(R.drawable.brand_wordmark),
                contentDescription = "绒绒记账",
                modifier = Modifier.fillMaxWidth(0.76f).height(66.dp),
                contentScale = ContentScale.Fit
            )
        }
        item {
            Text("♥ 温暖每一笔，记录每一天 ♥", color = Color(0xFFC18957), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("让每一次记录，都变得柔软而清晰", color = palette.muted, fontSize = 12.sp)
        }
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
            PlushButton("开始使用", Icons.Default.EditNote, Modifier.fillMaxWidth(), color = Color(0xFFFFA126), onClick = onStart)
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
            modifier = Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(20.dp)),
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
    var emailLoginMode by rememberSaveable { mutableStateOf("password") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var phoneOtp by rememberSaveable { mutableStateOf("") }
    var phoneCountryCode by rememberSaveable { mutableStateOf("+86") }
    var localName by rememberSaveable { mutableStateOf("") }
    var localPassword by rememberSaveable { mutableStateOf("") }
    var showAgreement by rememberSaveable { mutableStateOf(false) }
    var agreementRead by rememberSaveable { mutableStateOf(false) }
    var agreementChecked by rememberSaveable { mutableStateOf(false) }
    val palette = LocalPlushPalette.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_transparent),
                    contentDescription = "绒绒",
                    modifier = Modifier.size(150.dp),
                    contentScale = ContentScale.Fit
                )
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFBD42), modifier = Modifier.align(Alignment.CenterStart).padding(start = 38.dp).size(18.dp))
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF9A80), modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 18.dp).size(15.dp))
                Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.moss.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 40.dp, top = 24.dp).size(13.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.rose, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(7.dp))
                Image(
                    painter = painterResource(R.drawable.brand_wordmark),
                    contentDescription = "绒绒记账",
                    modifier = Modifier.width(200.dp).height(54.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(7.dp))
                Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.rose, modifier = Modifier.size(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC85A), modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(8.dp))
                Text("温暖每一笔，记录每一天", color = Color(0xFFC79B6A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC85A), modifier = Modifier.size(11.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(30.dp), spotColor = palette.rose.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(30.dp),
                color = palette.surface,
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    AuthModeTabs(mode) { selected ->
                        mode = selected
                        viewModel.showAuthPage(AuthPage.LOGIN)
                    }
                    Spacer(Modifier.height(12.dp))
                    when (mode) {
                        "local" -> {
                            Text("本地账本", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Spacer(Modifier.height(10.dp))
                            AuthTextField(localName, { localName = it.take(32) }, "用户名", Icons.Default.Person)
                            Spacer(Modifier.height(10.dp))
                            AuthTextField(localPassword, { localPassword = it.take(64) }, "密码", Icons.Default.Lock, password = true)
                            Spacer(Modifier.height(12.dp))
                            AuthPrimaryButton("进入本地账本", Icons.Default.Lock, localName.isNotBlank() && localPassword.isNotBlank()) {
                                viewModel.signInLocal(localName, localPassword)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("本地模式无需注册，数据只保存在当前设备。", color = palette.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        "phone" -> {
                            Text("手机号验证码登录", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                CountryCodeButton(phoneCountryCode, Modifier.width(102.dp)) { phoneCountryCode = it }
                                AuthTextField(
                                    phone,
                                    { phone = it.filter(Char::isDigit).take(15) },
                                    "请输入手机号",
                                    Icons.Default.Phone,
                                    modifier = Modifier.weight(1f),
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            OtpEntryRow(phoneOtp, { phoneOtp = it }, state.otpCooldown, state.isBusy, serviceEnabled = false) { }
                            Spacer(Modifier.height(12.dp))
                            AuthPrimaryButton("手机号登录（暂未开放）", Icons.Default.Phone, enabled = false) { }
                            Spacer(Modifier.height(12.dp))
                            Surface(shape = RoundedCornerShape(16.dp), color = palette.surfaceAlt, border = BorderStroke(1.dp, palette.border)) {
                                Text(
                                    "手机号注册、绑定和短信验证码服务暂未开放。当前请使用邮箱或本地模式，填写的手机号不会提交。",
                                    modifier = Modifier.padding(12.dp),
                                    color = palette.muted,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                            SocialLoginRow(viewModel, withDivider = true)
                        }
                        else -> when (state.authPage) {
                            AuthPage.LOGIN -> {
                                AuthSegment(emailLoginMode) { emailLoginMode = it }
                                Spacer(Modifier.height(12.dp))
                                AuthTextField(email, { email = it.trim() }, "邮箱", Icons.Default.AlternateEmail, keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                                Spacer(Modifier.height(10.dp))
                                if (emailLoginMode == "password") {
                                    AuthTextField(password, { password = it.take(64) }, "密码", Icons.Default.Lock, password = true)
                                    Spacer(Modifier.height(12.dp))
                                    AuthPrimaryButton("邮箱密码登录", Icons.Default.Lock, !state.isBusy && email.isNotBlank() && password.isNotBlank()) {
                                        viewModel.signInWithPassword(email, password)
                                    }
                                } else {
                                    OtpEntryRow(otp, { otp = it }, state.otpCooldown, state.isBusy) { viewModel.sendLoginOtp("email", email) }
                                    Spacer(Modifier.height(12.dp))
                                    AuthPrimaryButton("邮箱验证码登录", Icons.Default.Shield, !state.isBusy && email.isNotBlank() && otp.isNotBlank()) {
                                        viewModel.verifyLoginOtp("email", email, otp)
                                    }
                                }
                                TextButton(onClick = { viewModel.showAuthPage(AuthPage.RESET) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("忘记密码", color = Color(0xFFAA8060))
                                }
                                OutlinedButton(
                                    onClick = { viewModel.showAuthPage(AuthPage.REGISTER) },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    border = BorderStroke(1.dp, palette.rose)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = palette.rose)
                                    Spacer(Modifier.width(8.dp))
                                    Text("注册新账号", color = palette.rose, fontWeight = FontWeight.Bold)
                                }
                                SocialLoginRow(viewModel)
                            }
                            AuthPage.REGISTER, AuthPage.RESET -> {
                                val isReset = state.authPage == AuthPage.RESET
                                Text(if (isReset) "重置密码" else "注册新账号", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                Spacer(Modifier.height(10.dp))
                                AuthTextField(email, { email = it.trim() }, "邮箱", Icons.Default.AlternateEmail, keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                                Spacer(Modifier.height(10.dp))
                                OtpEntryRow(otp, { otp = it }, state.otpCooldown, state.isBusy) { viewModel.sendRegistrationOtp(email, isReset) }
                                if (!isReset) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = agreementChecked, onCheckedChange = { if (agreementRead) agreementChecked = it }, enabled = agreementRead)
                                        TextButton(onClick = { showAgreement = true }) { Text("用户协议与隐私政策", color = palette.rose) }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                AuthPrimaryButton(
                                    if (isReset) "验证并重置密码" else "验证并注册",
                                    Icons.Default.Shield,
                                    !state.isBusy && (isReset || agreementChecked)
                                ) { viewModel.verifyRegistrationOtp(email, otp, isReset) }
                                TextButton(onClick = { viewModel.showAuthPage(AuthPage.LOGIN) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("返回登录", color = palette.muted)
                                }
                            }
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
private fun AuthModeTabs(selected: String, onSelected: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth()) {
        listOf("email" to "邮箱", "phone" to "手机", "local" to "本地").forEach { (key, label) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelected(key) }.padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(label, color = if (selected == key) palette.rose else palette.ink, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(42.dp).height(3.dp).clip(RoundedCornerShape(4.dp)).background(if (selected == key) palette.rose else Color.Transparent))
            }
        }
    }
}

@Composable
private fun AuthSegment(selected: String, onSelected: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(shape = RoundedCornerShape(22.dp), color = palette.surfaceAlt, border = BorderStroke(1.dp, palette.border)) {
        Row(Modifier.fillMaxWidth().padding(2.dp)) {
            listOf("password" to "密码登录", "otp" to "验证码登录").forEach { (key, label) ->
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(19.dp)).clickable { onSelected(key) },
                    shape = RoundedCornerShape(19.dp),
                    color = if (selected == key) Color(0xFFFFF2DF) else Color.Transparent
                ) {
                    Text(label, modifier = Modifier.padding(vertical = 6.dp), color = if (selected == key) palette.rose else palette.ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier.fillMaxWidth(),
    password: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text
) {
    val palette = LocalPlushPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(46.dp),
        singleLine = true,
        placeholder = { Text(placeholder, color = palette.muted.copy(alpha = 0.72f)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = palette.muted) },
        shape = RoundedCornerShape(18.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.rose,
            unfocusedBorderColor = palette.border,
            focusedContainerColor = palette.surface,
            unfocusedContainerColor = palette.surface
        )
    )
}

@Composable
private fun AuthPrimaryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit
) {
    val palette = LocalPlushPalette.current
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier.height(50.dp)
            .shadow(if (enabled) 10.dp else 0.dp, shape, spotColor = palette.rose.copy(alpha = 0.3f))
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Color(0xFFFFBC2F), Color(0xFFFF7A21)))
                else Brush.horizontalGradient(listOf(palette.border, palette.border))
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1)
        }
    }
}

@Composable
private fun OtpEntryRow(
    code: String,
    onCodeChange: (String) -> Unit,
    cooldown: Int,
    busy: Boolean,
    serviceEnabled: Boolean = true,
    onSend: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AuthTextField(
            code,
            { onCodeChange(it.filter(Char::isDigit).take(8)) },
            "请输入验证码",
            Icons.Default.Shield,
            modifier = Modifier.weight(1.15f),
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
        AuthPrimaryButton(
            text = when {
                !serviceEnabled -> "暂未开放"
                cooldown > 0 -> "${cooldown}s"
                busy -> "发送中"
                else -> "获取验证码"
            },
            icon = Icons.Default.Refresh,
            enabled = serviceEnabled && cooldown == 0 && !busy,
            modifier = Modifier.weight(0.9f),
            onClick = onSend
        )
    }
}

@Composable
private fun CountryCodeButton(value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val palette = LocalPlushPalette.current
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Text(value, color = palette.rose, fontWeight = FontWeight.Black, fontSize = 17.sp)
    }
    if (showPicker) {
        CountryCodePicker(value, { showPicker = false }) {
            onChange(it)
            showPicker = false
        }
    }
}

@Composable
private fun CountryCodePicker(selectedCode: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.72f).height(380.dp),
            shape = RoundedCornerShape(28.dp),
            color = palette.surface,
            border = BorderStroke(1.dp, palette.border),
            shadowElevation = 20.dp
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = palette.moss, modifier = Modifier.size(12.dp))
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.rose, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("选择区号", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Spacer(Modifier.width(9.dp))
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.rose, modifier = Modifier.size(15.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = palette.moss, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(phoneCountryOptions) { item ->
                            val selected = selectedCode == item.code
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onSelect(item.code) },
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) Color(0xFFFFF6E7) else palette.surface.copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, if (selected) palette.rose else palette.border)
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.flag, fontSize = 15.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(item.name, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                    Text(item.code, color = palette.rose, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    if (selected) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Default.CheckCircle, contentDescription = "已选择", tint = palette.rose, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 3.dp)) { MascotArt(52.dp) }
            }
        }
    }
}

private data class PhoneCountry(val flag: String, val name: String, val code: String)

private val phoneCountryOptions = listOf(
    PhoneCountry("🇨🇳", "中国大陆", "+86"),
    PhoneCountry("🇭🇰", "中国香港", "+852"),
    PhoneCountry("🇲🇴", "中国澳门", "+853"),
    PhoneCountry("🇹🇼", "中国台湾", "+886"),
    PhoneCountry("🇺🇸🇨🇦", "美国/加拿大", "+1"),
    PhoneCountry("🇬🇧", "英国", "+44"),
    PhoneCountry("🇯🇵", "日本", "+81"),
    PhoneCountry("🇰🇷", "韩国", "+82"),
    PhoneCountry("🇸🇬", "新加坡", "+65"),
    PhoneCountry("🇦🇺", "澳大利亚", "+61"),
    PhoneCountry("🇳🇿", "新西兰", "+64"),
    PhoneCountry("🇩🇪", "德国", "+49")
)

@Composable
private fun SocialLoginRow(viewModel: LedgerViewModel, withDivider: Boolean = false) {
    Spacer(Modifier.height(8.dp))
    val palette = LocalPlushPalette.current
    if (withDivider) {
        Text("—  其他登录方式  —", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = palette.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { viewModel.socialLogin("微信") }, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, palette.border)) {
            Image(painterResource(R.drawable.logo_wechat), contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.size(6.dp))
            Text("微信", color = palette.ink, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { viewModel.socialLogin("QQ") }, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, palette.border)) {
            Image(painterResource(R.drawable.logo_qq_official), contentDescription = null, modifier = Modifier.size(30.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.size(6.dp))
            Text("QQ", color = palette.ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AgreementDialog(onDismiss: () -> Unit, onAccepted: () -> Unit) {
    var seconds by remember { mutableIntStateOf(3) }
    val uriHandler = LocalUriHandler.current
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
                item {
                    TextButton(onClick = { uriHandler.openUri("https://privacy.xiaoxing.online/privacy.html") }) {
                        Text("查看完整隐私政策")
                    }
                }
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
                            val selectedColor = if (item.tab == AppTab.MY) palette.pink else palette.rose
                            Column(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) selectedColor else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { viewModel.selectTab(item.tab) }
                                    .padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) androidx.compose.ui.graphics.Color.White else palette.muted,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    item.label,
                                    color = if (selected) androidx.compose.ui.graphics.Color.White else palette.muted,
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
                    onBills = { viewModel.selectTab(AppTab.BILLS) },
                    aiSuggestion = state.aiSuggestion,
                    isAiAnalyzing = state.isAiAnalyzing,
                    onAnalyzeAi = viewModel::analyzeAiEntry,
                    onSaveAi = viewModel::saveAiSuggestion,
                    onDismissAi = viewModel::clearAiSuggestion
                )
                AppTab.BILLS -> BillsScreen(
                    ledger = state.ledger,
                    selectedDate = state.selectedDate,
                    onMonth = viewModel::changeMonth,
                    onDate = viewModel::selectStatsDate,
                    onDelete = viewModel::deleteTransaction,
                    onUpdate = viewModel::updateTransaction
                )
                AppTab.RECORD -> RecordScreen(
                    state = state,
                    onBack = { viewModel.navigateBack() },
                    onAdd = viewModel::addTransaction,
                    onDefaultAccount = viewModel::setDefaultAccount,
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
