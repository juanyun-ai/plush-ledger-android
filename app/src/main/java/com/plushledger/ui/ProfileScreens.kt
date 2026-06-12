package com.plushledger.ui

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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun InboxScreen(messages: List<OfficialMessage>, busy: Boolean, onFeedback: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    var feedback by rememberSaveable { mutableStateOf("") }
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
}

private enum class MyPage { ROOT, PROFILE, INBOX, SETTINGS, MEMBERSHIP }

@Composable
fun MyScreen(state: UiState, biometricAvailable: Boolean, viewModel: LedgerViewModel) {
    var page by rememberSaveable { mutableStateOf(MyPage.ROOT) }
    BackHandler(enabled = page != MyPage.ROOT) { page = MyPage.ROOT }
    LaunchedEffect(state.ledger.profile?.avatarKey) { viewModel.refreshAvatar() }
    when (page) {
        MyPage.ROOT -> MyRoot(
            state,
            onProfile = { page = MyPage.PROFILE },
            onInbox = { page = MyPage.INBOX; viewModel.refreshMailbox() },
            onSettings = { page = MyPage.SETTINGS },
            onMembership = { page = MyPage.MEMBERSHIP }
        )
        MyPage.PROFILE -> ProfileScreen(state, onBack = { page = MyPage.ROOT }, onSave = viewModel::updateProfile, onAvatar = viewModel::uploadAvatar, onBind = viewModel::socialLogin)
        MyPage.INBOX -> Column(Modifier.fillMaxSize()) {
            BackHeader("消息与建议", onBack = { page = MyPage.ROOT })
            InboxScreen(state.officialMessages, state.isBusy, viewModel::submitFeedback)
        }
        MyPage.SETTINGS -> SettingsScreen(state, biometricAvailable, viewModel, onBack = { page = MyPage.ROOT })
        MyPage.MEMBERSHIP -> MembershipScreen(state, onBack = { page = MyPage.ROOT }, onPay = viewModel::startMembershipPurchase)
    }
}

@Composable
private fun MyRoot(state: UiState, onProfile: () -> Unit, onInbox: () -> Unit, onSettings: () -> Unit, onMembership: () -> Unit) {
    val palette = LocalPlushPalette.current
    val profile = state.ledger.profile
    val badge = membershipLabel(profile?.role, profile?.membershipTier)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("绒绒记账", fontSize = 28.sp, fontWeight = FontWeight.Black, color = palette.ink)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "设置", tint = palette.ink) }
            }
        }
        item {
            PlushCard(Modifier.fillMaxWidth().clickable(onClick = onProfile), padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(state.avatarUrl, 78.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile?.displayName ?: state.session?.displayName ?: "绒绒用户", fontWeight = FontWeight.Black, fontSize = 24.sp, color = palette.ink)
                        Text("享受每一次记录的好习惯～", color = palette.muted, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(badge, color = badgeColor(profile?.role, profile?.membershipTier), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Image(painterResource(R.drawable.ic_launcher), contentDescription = null, modifier = Modifier.size(88.dp), contentScale = ContentScale.Fit)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileMetric("本月已记录", "${state.ledger.transactions.count { java.time.YearMonth.from(it.localDateForProfile()) == java.time.YearMonth.now() }} 笔", palette.moss, Modifier.weight(1f))
                    ProfileMetric("本月支出", com.plushledger.data.Money.formatCny(state.ledger.summary.expenseMinor), palette.rose, Modifier.weight(1f))
                }
            }
        }
        item {
            PlushCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                Box(Modifier.fillMaxWidth().clickable(onClick = onInbox).padding(8.dp)) {
                    MenuRow(Icons.Default.Inbox, "消息与建议", "官方通知和写给开发者", palette.moss)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Box(Modifier.fillMaxWidth().clickable(onClick = onMembership).padding(8.dp)) {
                    MenuRow(Icons.Default.Star, "会员权益", "永久会员 0.01 元", palette.rose)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Box(Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(8.dp)) {
                    MenuRow(Icons.Default.Settings, "设置", "隐私、安全、同步与账号", palette.blue)
                }
            }
        }
        item {
            PlushCard {
                Text(if (state.session?.accessToken != null) "云端账号" else "本地账号", fontWeight = FontWeight.Bold, color = palette.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (state.session?.accessToken != null) "数据本地优先保存，并在联网时同步到云端。" else "数据只保存在当前设备，卸载后无法恢复。",
                    color = palette.muted
                )
            }
        }
    }
}

@Composable
private fun ProfileScreen(state: UiState, onBack: () -> Unit, onSave: (String) -> Unit, onAvatar: (android.net.Uri) -> Unit, onBind: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    val profile = state.ledger.profile
    var nickname by rememberSaveable(profile?.displayName) { mutableStateOf(profile?.displayName ?: state.session?.displayName.orEmpty()) }
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
                PlushButton("保存资料", Icons.Default.Save, Modifier.fillMaxWidth()) { onSave(nickname) }
            }
        }
        item {
            PlushCard {
                InfoRow(Icons.Default.Badge, "身份", membershipLabel(profile?.role, profile?.membershipTier))
                InfoRow(Icons.Default.Phone, "手机号", profile?.phone ?: "未绑定")
                InfoRow(Icons.Default.Email, "邮箱", profile?.email ?: state.session?.email ?: "本地账号")
            }
        }
        item {
            PlushCard {
                BindRow("微信", profile?.wechatBound == true) { onBind("微信绑定") }
                BindRow("QQ", profile?.qqBound == true) { onBind("QQ绑定") }
            }
        }
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
        PlushBadge(icon, color, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = palette.ink)
            Text(subtitle, color = palette.muted, fontSize = 12.sp)
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
    val palette = LocalPlushPalette.current
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFFFFC85C)), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
        } else {
            AsyncImage(model = url, contentDescription = "用户头像", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.10f)).padding(12.dp)) {
        Text(label, color = palette.muted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
