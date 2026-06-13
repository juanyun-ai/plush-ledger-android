package com.plushledger.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.plushledger.BuildConfig
import com.plushledger.R
import com.plushledger.data.OfficialMessage
import com.plushledger.sync.AppVersionInfo
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun InboxScreen(
    messages: List<OfficialMessage>,
    busy: Boolean,
    onFeedback: (String) -> Unit,
    onDownloadUpdate: (AppVersionInfo) -> Unit
) {
    val palette = LocalPlushPalette.current
    var feedback by rememberSaveable { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<AppVersionInfo?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle("官方消息", Icons.Default.Notifications) }
        items(messages, key = { it.id }) { message ->
            PlushCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlushBadge(Icons.Default.Inbox, palette.blue, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(message.title, fontWeight = FontWeight.Bold, color = palette.ink)
                        Text(
                            Instant.ofEpochMilli(message.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            color = palette.muted,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(message.body, color = palette.muted, lineHeight = 21.sp)
                message.updateInfo?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }?.let { update ->
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { pendingDownload = update },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("下载 v${update.versionName}")
                    }
                }
            }
        }
        if (messages.isEmpty()) item { Text("暂时没有新消息", color = palette.muted) }
        item { SectionTitle("写给开发者", Icons.Default.Email) }
        item {
            PlushCard {
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it.take(500) },
                    label = { Text("建议或问题") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
                Spacer(Modifier.height(10.dp))
                PlushButton("发送建议", Icons.Default.Send, Modifier.fillMaxWidth(), enabled = !busy && feedback.length >= 5) {
                    onFeedback(feedback)
                    feedback = ""
                }
            }
        }
    }
    pendingDownload?.let { update ->
        AlertDialog(
            onDismissRequest = { pendingDownload = null },
            title = { Text("下载 v${update.versionName}", fontWeight = FontWeight.Bold) },
            text = {
                Text("安装包约 ${update.fileSizeBytes / 1024 / 1024}MB。下载完成并通过安全校验后，将交由 Android 系统安装。")
            },
            dismissButton = { TextButton(onClick = { pendingDownload = null }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    onDownloadUpdate(update)
                    pendingDownload = null
                }) { Text("确认下载") }
            }
        )
    }
}

private enum class MyPage { ROOT, PROFILE, INBOX, SETTINGS, MEMBERSHIP, BUDGET, CATEGORY }

@Composable
fun MyScreen(
    state: UiState,
    biometricAvailable: Boolean,
    viewModel: LedgerViewModel,
    onDownloadUpdate: (AppVersionInfo) -> Unit
) {
    var page by rememberSaveable { mutableStateOf(MyPage.ROOT) }
    BackHandler(enabled = page != MyPage.ROOT) { page = MyPage.ROOT }
    when (page) {
        MyPage.ROOT -> MyRoot(
            state,
            onProfile = { page = MyPage.PROFILE },
            onInbox = { page = MyPage.INBOX; viewModel.refreshMailbox() },
            onSettings = { page = MyPage.SETTINGS },
            onMembership = { page = MyPage.MEMBERSHIP },
            onBudget = { page = MyPage.BUDGET },
            onCategory = { page = MyPage.CATEGORY },
            onDarkMode = viewModel::setDarkMode
        )
        MyPage.PROFILE -> ProfileScreen(
            state = state,
            onBack = { page = MyPage.ROOT },
            onSave = viewModel::updateProfile,
            onAvatar = viewModel::uploadAvatar,
            onBind = viewModel::socialLogin,
            onSendIdentityCode = viewModel::requestIdentityChange,
            onVerifyIdentity = viewModel::verifyIdentityChange
        )
        MyPage.INBOX -> Column(Modifier.fillMaxSize()) {
            BackHeader("消息与建议", onBack = { page = MyPage.ROOT })
            InboxScreen(state.officialMessages, state.isBusy, viewModel::submitFeedback, onDownloadUpdate)
        }
        MyPage.SETTINGS -> SettingsScreen(state, biometricAvailable, viewModel, onBack = { page = MyPage.ROOT })
        MyPage.MEMBERSHIP -> MembershipScreen(state, onBack = { page = MyPage.ROOT }, onPay = viewModel::startMembershipPurchase)
        MyPage.BUDGET -> BudgetManagementScreen(state.ledger, onBack = { page = MyPage.ROOT }, onBudget = viewModel::setBudget)
        MyPage.CATEGORY -> CategoryManagementScreen(
            ledger = state.ledger,
            onBack = { page = MyPage.ROOT },
            onAdd = viewModel::addCategory,
            onDelete = viewModel::deleteCategory
        )
    }
}

@Composable
private fun MyRoot(
    state: UiState,
    onProfile: () -> Unit,
    onInbox: () -> Unit,
    onSettings: () -> Unit,
    onMembership: () -> Unit,
    onBudget: () -> Unit,
    onCategory: () -> Unit,
    onDarkMode: (Boolean) -> Unit
) {
    val palette = LocalPlushPalette.current
    val profile = state.ledger.profile
    val badge = membershipLabel(profile?.role, profile?.membershipTier)
    val monthCount = state.ledger.transactions.count { java.time.YearMonth.from(it.localDateForProfile()) == java.time.YearMonth.now() }
    val streakDays = state.ledger.transactions.map { it.localDateForProfile() }.distinct().size.coerceAtMost(99)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.brand_wordmark),
                    contentDescription = "绒绒记账",
                    modifier = Modifier.width(96.dp).height(30.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "设置", tint = palette.ink) }
            }
        }
        item {
            PlushCard(Modifier.fillMaxWidth().clickable(onClick = onProfile), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(state.avatarUrl, 58.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile?.displayName ?: state.session?.displayName ?: "绒绒用户",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("享受每一次记录的好习惯～", color = palette.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(badge, color = badgeColor(profile?.role, profile?.membershipTier), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    MascotArt(76.dp)
                }
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(18.dp), color = palette.surface, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileMetric("连续记账", "$streakDays 天", palette.rose, Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(58.dp).background(palette.border))
                        ProfileMetric("本月已记录", "$monthCount 笔", palette.moss, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            PlushCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                Box(Modifier.fillMaxWidth().clickable(onClick = onBudget).padding(6.dp)) {
                    MenuRow(Icons.Default.Paid, "预算管理", "", palette.rose)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Box(Modifier.fillMaxWidth().clickable(onClick = onCategory).padding(6.dp)) {
                    MenuRow(Icons.Default.Badge, "分类管理", "", palette.moss)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Box(Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(6.dp)) {
                    MenuRow(Icons.Default.Download, "数据导出", "", palette.rose)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Box(Modifier.fillMaxWidth().clickable { onDarkMode(!state.darkMode) }.padding(6.dp)) {
                    MenuRow(Icons.Default.DarkMode, "深色模式", if (state.darkMode) "已开启" else "跟随系统", palette.lilac)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Box(Modifier.fillMaxWidth().clickable(onClick = onInbox).padding(6.dp)) {
                    MenuRow(Icons.Default.Notifications, "通知提醒", if (state.officialMessages.isEmpty()) "已开启" else "${state.officialMessages.size} 条消息", palette.coral)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Box(Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(6.dp)) {
                    MenuRow(Icons.Default.AccountCircle, "关于我们", "", palette.blue)
                }
            }
        }
        item {
            PlushCard(padding = 16.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每一笔记录，都是生活的温柔片段", fontWeight = FontWeight.Bold, color = palette.ink)
                        Spacer(Modifier.height(6.dp))
                        Text(if (state.session?.accessToken != null) "已开启云端同步，安心记录每一天～" else "当前为本地模式，请记得及时导出备份～", color = palette.muted, fontSize = 12.sp)
                    }
                    Image(painterResource(R.drawable.art_plant), contentDescription = null, modifier = Modifier.size(72.dp), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ProfileScreen(
    state: UiState,
    onBack: () -> Unit,
    onSave: (String, String, String?, String?) -> Unit,
    onAvatar: (android.net.Uri) -> Unit,
    onBind: (String) -> Unit,
    onSendIdentityCode: (String, String) -> Unit,
    onVerifyIdentity: (String, String, String) -> Unit
) {
    val palette = LocalPlushPalette.current
    val profile = state.ledger.profile
    val context = LocalContext.current
    var nickname by rememberSaveable(profile?.displayName) { mutableStateOf(profile?.displayName ?: state.session?.displayName.orEmpty()) }
    var age by rememberSaveable(profile?.age) { mutableStateOf(profile?.age?.toString().orEmpty()) }
    var birthDate by rememberSaveable(profile?.birthDate) { mutableStateOf(profile?.birthDate.orEmpty()) }
    var gender by rememberSaveable(profile?.gender) { mutableStateOf(profile?.gender ?: "prefer_not") }
    var identityChannel by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onAvatar) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("我的资料", onBack) }
        item {
            PlushCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(state.avatarUrl, 82.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("头像", fontWeight = FontWeight.Bold, color = palette.ink)
                        Text("支持 JPG、PNG 和 WebP，最大 12MB", color = palette.muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "从相册选择头像", tint = palette.rose)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(nickname, { nickname = it.take(24) }, label = { Text("昵称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    age,
                    { age = it.filter(Char::isDigit).take(3) },
                    label = { Text("年龄") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val initial = runCatching { LocalDate.parse(birthDate) }.getOrDefault(LocalDate.now().minusYears(20))
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val selected = LocalDate.of(year, month + 1, day)
                                birthDate = selected.toString()
                                age = Period.between(selected, LocalDate.now()).years.coerceAtLeast(0).toString()
                            },
                            initial.year,
                            initial.monthValue - 1,
                            initial.dayOfMonth
                        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Badge, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (birthDate.isBlank()) "选择生日" else "生日  $birthDate")
                }
                Spacer(Modifier.height(12.dp))
                Text("性别", color = palette.muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("female" to "女", "male" to "男", "other" to "其他", "prefer_not" to "不公开").forEach { (key, label) ->
                        SoftChip(label, gender == key, palette.rose) { gender = key }
                    }
                }
                Spacer(Modifier.height(14.dp))
                PlushButton("保存资料", Icons.Default.Save, Modifier.fillMaxWidth()) {
                    onSave(nickname, age, birthDate.ifBlank { null }, gender)
                }
            }
        }
        item {
            PlushCard {
                InfoRow(Icons.Default.Badge, "身份", membershipLabel(profile?.role, profile?.membershipTier))
                IdentityRow(Icons.Default.Phone, "手机号", profile?.phone ?: state.session?.phone ?: "未绑定", state.session?.accessToken != null) { identityChannel = "phone" }
                IdentityRow(Icons.Default.Email, "邮箱", profile?.email ?: state.session?.email ?: "本地账号", state.session?.accessToken != null) { identityChannel = "email" }
            }
        }
        item {
            PlushCard {
                BindRow("微信", profile?.wechatBound == true) { onBind("微信绑定") }
                BindRow("QQ", profile?.qqBound == true) { onBind("QQ绑定") }
            }
        }
    }
    identityChannel?.let { channel ->
        IdentityChangeDialog(
            channel = channel,
            cooldown = state.otpCooldown,
            busy = state.isBusy,
            onDismiss = { identityChannel = null },
            onSend = { onSendIdentityCode(channel, it) },
            onVerify = { value, code -> onVerifyIdentity(channel, value, code) }
        )
    }
}

@Composable
private fun SettingsScreen(state: UiState, biometricAvailable: Boolean, viewModel: LedgerViewModel, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    var pin by rememberSaveable { mutableStateOf("") }
    var exportPin by rememberSaveable { mutableStateOf("") }
    var showSignOut by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var deleteSeconds by remember { mutableIntStateOf(15) }

    LaunchedEffect(showDelete) {
        if (!showDelete) return@LaunchedEffect
        deleteSeconds = 15
        while (deleteSeconds > 0) {
            delay(1_000)
            deleteSeconds--
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("设置", onBack) }
        item {
            PlushCard {
                ToggleRow(Icons.Default.DarkMode, "夜间模式", state.darkMode, viewModel::setDarkMode)
                ToggleRow(Icons.Default.Security, "隐私防截图", state.secureScreen, viewModel::setSecureScreen)
            }
        }
        item {
            PlushCard {
                Text("打开验证", fontWeight = FontWeight.Bold, color = palette.ink)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    pin,
                    { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("设置或更换 PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.setPin(pin); pin = "" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存 PIN")
                }
                ToggleRow(Icons.Default.Shield, "每次打开输入密码", state.lockOnLaunch, viewModel::setLockOnLaunch)
                ToggleRow(Icons.Default.Fingerprint, "使用生物识别解锁", state.biometricUnlock, viewModel::setBiometricUnlock, enabled = biometricAvailable && state.lockOnLaunch)
            }
        }
        item {
            PlushCard {
                ActionRow(Icons.Default.CloudSync, "立即同步", state.syncLabel, palette.blue, viewModel::syncNow)
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    Icons.Default.SystemUpdate,
                    "检查更新",
                    if (state.isCheckingUpdate) "正在检查" else "当前版本 ${BuildConfig.VERSION_NAME}",
                    palette.moss
                ) { viewModel.checkForUpdates() }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(exportPin, { exportPin = it.filter(Char::isDigit).take(12) }, label = { Text("导出 PIN") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.exportCsv(exportPin) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导出 CSV")
                }
                state.exportPath?.let { Text(it, color = palette.muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        item {
            PlushCard {
                ActionRow(Icons.Default.Logout, "退出登录", "保留本机数据", palette.rose) { showSignOut = true }
                Spacer(Modifier.height(8.dp))
                ActionRow(Icons.Default.DeleteForever, "注销账号", "永久删除云端与本机数据", palette.rose) { showDelete = true }
            }
        }
    }

    if (showSignOut) {
        ConfirmDialog("退出登录", "确定要退出当前账号吗？本机账目不会删除。", confirmText = "退出", onDismiss = { showSignOut = false }) {
            viewModel.signOut()
            showSignOut = false
        }
    }
    if (showDelete) {
        ConfirmDialog(
            title = "注销账号",
            message = "注销后账号、云端账目和本机数据都无法恢复。",
            confirmText = if (deleteSeconds == 0) "确认永久注销" else "请等待 ${deleteSeconds}s",
            confirmEnabled = deleteSeconds == 0,
            onDismiss = { showDelete = false }
        ) {
            viewModel.deleteAccountPermanently()
            showDelete = false
        }
    }
}

@Composable
private fun MembershipScreen(state: UiState, onBack: () -> Unit, onPay: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    val profile = state.ledger.profile
    val hasRights = profile?.role == "admin" || profile?.membershipTier == "permanent"
    var showPayment by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("会员", onBack) }
        item {
            PlushCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlushBadge(if (profile?.role == "admin") Icons.Default.Shield else Icons.Default.Star, palette.rose, 54.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(membershipLabel(profile?.role, profile?.membershipTier), fontWeight = FontWeight.Black, fontSize = 22.sp, color = palette.ink)
                        Text(if (hasRights) "权益永久有效" else "一次购买，永久有效", color = palette.muted)
                    }
                }
                if (!hasRights) {
                    Spacer(Modifier.height(16.dp))
                    Text("¥0.01", fontWeight = FontWeight.Black, fontSize = 34.sp, color = palette.rose)
                    Spacer(Modifier.height(10.dp))
                    PlushButton("开通永久会员", Icons.Default.Paid, Modifier.fillMaxWidth()) { showPayment = true }
                }
            }
        }
        item {
            PlushCard {
                InfoRow(Icons.Default.Star, "永久铭牌", "已包含")
                InfoRow(Icons.Default.CloudSync, "优先云同步", "已包含")
                InfoRow(Icons.Default.CreditCard, "账单智能导入", "商户接口开放后提供")
            }
        }
    }
    if (showPayment) {
        AlertDialog(
            onDismissRequest = { showPayment = false },
            title = { Text("选择支付方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onPay("微信支付"); showPayment = false }, modifier = Modifier.fillMaxWidth()) { Text("微信支付  ¥0.01") }
                    OutlinedButton(onClick = { onPay("支付宝"); showPayment = false }, modifier = Modifier.fillMaxWidth()) { Text("支付宝  ¥0.01") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPayment = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
        Text(title, fontWeight = FontWeight.Black, fontSize = 22.sp, color = palette.ink)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, color: Color) {
    val palette = LocalPlushPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlushBadge(icon, color, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = palette.ink)
            if (subtitle.isNotBlank()) Text(subtitle, color = palette.muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.muted)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = palette.ink)
            Text(subtitle, color = palette.muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.muted)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (enabled) palette.blue else palette.muted)
        Spacer(Modifier.width(10.dp))
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = palette.ink)
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = palette.blue)
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), color = palette.muted)
        Text(value, color = palette.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun IdentityRow(icon: ImageVector, label: String, value: String, enabled: Boolean, onChange: () -> Unit) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = palette.blue)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = palette.muted, fontSize = 12.sp)
            Text(value, color = palette.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onChange, enabled = enabled) { Text(if (enabled) "换绑" else "本地模式") }
    }
}

@Composable
private fun IdentityChangeDialog(
    channel: String,
    cooldown: Int,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    onVerify: (String, String) -> Unit
) {
    var value by rememberSaveable(channel) { mutableStateOf("") }
    var code by rememberSaveable(channel) { mutableStateOf("") }
    val isEmail = channel == "email"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEmail) "换绑邮箱" else "换绑手机号", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isEmail) "邮件可能包含验证码或确认链接。输入验证码，或点完确认链接后返回这里继续。"
                    else "手机号请填写国际格式，例如 +8613800138000。",
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value,
                    { value = it.trim().take(80) },
                    label = { Text(if (isEmail) "新邮箱" else "新手机号") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (isEmail) KeyboardType.Email else KeyboardType.Phone)
                )
                OutlinedButton(
                    onClick = { onSend(value) },
                    enabled = !busy && cooldown == 0 && value.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (cooldown > 0) "${cooldown}s 后可重发" else "获取验证码")
                }
                OutlinedTextField(
                    code,
                    { code = it.filter(Char::isDigit).take(8) },
                    label = { Text("验证码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            TextButton(onClick = { onVerify(value, code) }, enabled = !busy && value.isNotBlank() && (isEmail || code.length >= 4)) {
                Text(if (isEmail && code.isBlank()) "已点链接，完成换绑" else "确认换绑")
            }
        }
    )
}

@Composable
private fun BindRow(provider: String, bound: Boolean, onBind: () -> Unit) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (provider == "微信") Icons.Default.ChatBubble else Icons.Default.AccountCircle, contentDescription = null, tint = palette.moss)
        Spacer(Modifier.width(10.dp))
        Text(provider, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onBind, enabled = !bound) { Text(if (bound) "已绑定" else "绑定") }
    }
}

@Composable
private fun Avatar(url: String?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFFFFC85C)), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
        } else {
            AsyncImage(
                model = url,
                contentDescription = "用户头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_launcher),
                error = painterResource(R.drawable.ic_launcher),
                fallback = painterResource(R.drawable.ic_launcher)
            )
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f)).padding(8.dp)) {
        Text(label, color = palette.muted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun com.plushledger.data.TransactionEntity.localDateForProfile(): java.time.LocalDate =
    java.time.Instant.ofEpochMilli(occurredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

private fun membershipLabel(role: String?, tier: String?): String = when {
    role == "admin" -> "管理员"
    tier == "permanent" -> "永久会员"
    else -> "免费用户"
}

private fun badgeColor(role: String?, tier: String?): Color = when {
    role == "admin" -> Color(0xFFD29A32)
    tier == "permanent" -> Color(0xFFC86F7E)
    else -> Color(0xFF7B6C70)
}
