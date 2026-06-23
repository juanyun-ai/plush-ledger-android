package com.plushledger.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.plushledger.BuildConfig
import com.plushledger.R
import com.plushledger.data.LedgerState
import com.plushledger.data.Money
import com.plushledger.data.OfficialMessage
import com.plushledger.sync.AppVersionInfo
import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private const val SUPPORT_EMAIL = "support@xiaoxing.online"

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
    var expandedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle("官方消息", Icons.Default.Notifications) }
        items(messages, key = { it.id }) { message ->
            val expanded = message.id in expandedIds
            PlushCard(
                Modifier
                    .fillMaxWidth()
                    .then(if (expanded) Modifier else Modifier.height(126.dp))
                    .clickable {
                        expandedIds = if (expanded) expandedIds - message.id else expandedIds + message.id
                    },
                padding = 12.dp
            ) {
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
                Spacer(Modifier.height(8.dp))
                Text(
                    message.body,
                    color = palette.muted,
                    lineHeight = 18.sp,
                    fontSize = 12.sp,
                    maxLines = if (expanded) 20 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                message.updateInfo?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }?.let { update ->
                    Spacer(Modifier.height(6.dp))
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

private enum class MyPage { ROOT, PROFILE, INBOX, SETTINGS, MEMBERSHIP, BUDGET, CATEGORY, ABOUT, DIARY }

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
            onAbout = { page = MyPage.ABOUT },
            onDiary = { page = MyPage.DIARY },
            onDarkMode = viewModel::setDarkMode
        )
        MyPage.PROFILE -> ProfileScreen(
            state = state,
            onBack = { page = MyPage.ROOT },
            onSave = viewModel::updateProfile,
            onAvatar = viewModel::uploadAvatar,
            onBind = viewModel::socialLogin,
            onSendIdentityCode = viewModel::requestIdentityChange,
            onVerifyIdentity = viewModel::verifyIdentityChange,
            onChangePassword = viewModel::changePassword,
            onDeleteAccount = viewModel::deleteAccountPermanently
        )
        MyPage.INBOX -> Column(Modifier.fillMaxSize()) {
            BackHeader("消息与建议", onBack = { page = MyPage.ROOT })
            InboxScreen(state.officialMessages, state.isBusy, viewModel::submitFeedback, onDownloadUpdate)
        }
        MyPage.SETTINGS -> SettingsScreen(state, biometricAvailable, viewModel, onBack = { page = MyPage.ROOT })
        MyPage.MEMBERSHIP -> MembershipScreen(state, onBack = { page = MyPage.ROOT })
        MyPage.BUDGET -> BudgetManagementScreen(state.ledger, onBack = { page = MyPage.ROOT }, onBudget = viewModel::setBudget)
        MyPage.CATEGORY -> CategoryManagementScreen(
            ledger = state.ledger,
            onBack = { page = MyPage.ROOT },
            onAdd = viewModel::addCategory,
            onDelete = viewModel::deleteCategory,
            onReorder = viewModel::moveCategory
        )
        MyPage.ABOUT -> AboutScreen(onBack = { page = MyPage.ROOT })
        MyPage.DIARY -> DiaryScreen(
            userId = state.session?.userId ?: state.ledger.profile?.id ?: "local-diary",
            quotes = rememberQuoteCollection(),
            onBack = { page = MyPage.ROOT }
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
    onAbout: () -> Unit,
    onDiary: () -> Unit,
    onDarkMode: (Boolean) -> Unit
) {
    val palette = LocalPlushPalette.current
    val profile = state.ledger.profile
    val badge = membershipLabel(profile?.role, profile?.membershipTier)
    val monthCount = state.ledger.transactions.count { java.time.YearMonth.from(it.localDateForProfile()) == java.time.YearMonth.now() }
    val streakDays = state.ledger.transactions.map { it.localDateForProfile() }.distinct().size.coerceAtMost(99)
    val context = LocalContext.current
    val dailyQuote = rememberDailyQuote()
    val quotes = rememberQuoteCollection()
    var quoteIndex by remember { mutableIntStateOf(Math.floorMod(System.nanoTime().toInt(), quotes.size)) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(buildCsv(state.ledger)) }
                    ?: error("无法打开导出位置")
            }.onSuccess {
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.brand_wordmark),
                    contentDescription = "绒绒记账",
                    modifier = Modifier.width(96.dp).height(30.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    dailyQuote,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    color = palette.muted,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "设置", tint = palette.ink) }
            }
        }
        item {
            ProfileWarmPanel(Modifier.fillMaxWidth().clickable(onClick = onProfile), padding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White, shadowElevation = 5.dp) {
                        Box(Modifier.padding(4.dp)) { Avatar(state.avatarUrl, 70.dp) }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile?.displayName ?: state.session?.displayName ?: "绒绒用户",
                            fontWeight = FontWeight.Black,
                        fontSize = 25.sp,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("享受每一次记录的好习惯～", color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFEDC8), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFDFA2))) {
                            Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                MascotArt(24.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    badge,
                                    color = badgeColor(profile?.role, profile?.membershipTier),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    MascotArt(88.dp)
                }
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.9f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProfileMetric(Icons.Default.CalendarMonth, "连续记账", "$streakDays 天", palette.rose, Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(58.dp).background(palette.border))
                        ProfileMetric(Icons.Default.EditNote, "本月已记录", "$monthCount 笔", palette.moss, Modifier.weight(1f))
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
                Box(Modifier.fillMaxWidth().clickable { showExportDialog = true }.padding(6.dp)) {
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
                Box(Modifier.fillMaxWidth().clickable(onClick = onAbout).padding(6.dp)) {
                    MenuRow(Icons.Default.AccountCircle, "关于我们", "", palette.blue)
                }
            }
        }
        item {
            DiarySummaryCard(
                userId = state.session?.userId ?: profile?.id ?: "local-diary",
                quote = quotes[quoteIndex],
                onChangeQuote = { quoteIndex = (quoteIndex + 1) % quotes.size },
                onClick = onDiary
            )
        }
    }
    if (showExportDialog) {
        ExportDataDialog(
            fileName = "rongrong-ledger-${LocalDate.now()}.csv",
            onDismiss = { showExportDialog = false },
            onConfirm = {
                showExportDialog = false
                exportLauncher.launch("rongrong-ledger-${LocalDate.now()}.csv")
            }
        )
    }
}

@Composable
private fun DiarySummaryCard(userId: String, quote: String, onChangeQuote: () -> Unit, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val entry = remember(userId) { DiaryStore(context.applicationContext, userId).load().firstOrNull { it.date == today.toString() } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFFF1F5),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF6DCE5)),
        shadowElevation = 7.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = palette.pink, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("绒绒日记", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("记录今天的心情", color = palette.muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.92f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(today.format(DateTimeFormatter.ofPattern("M月d日")), color = palette.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Spacer(Modifier.width(12.dp))
                                TinyPill(entry?.mood ?: "开心", palette.pink)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(entry?.text ?: "写下今天想留住的一句话…", color = if (entry == null) palette.muted else palette.ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.diary_card_mascot),
                        contentDescription = "绒绒写日记",
                        modifier = Modifier.width(124.dp).height(94.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
                        shape = RoundedCornerShape(18.dp),
                        color = palette.pink
                    ) { Text("去写日记", Modifier.padding(horizontal = 18.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.56f), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF8DCE5))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatQuote, contentDescription = null, tint = palette.pink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text("今日语录", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text(quote, color = palette.ink, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = onChangeQuote) { Text("换一句", color = palette.pink, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    val context = LocalContext.current
    var showMailPrompt by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BackHeader("关于我们", onBack) }
        item {
            ProfileWarmPanel(padding = 20.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MascotArt(116.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painterResource(R.drawable.brand_wordmark),
                            contentDescription = "绒绒记账",
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.8f), border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
                            Text("Version ${BuildConfig.VERSION_NAME}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = palette.rose, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            PlushCard(padding = 16.dp) {
                SectionTitle("品牌简介", Icons.Default.AccountCircle)
                Spacer(Modifier.height(10.dp))
                Text("绒绒记账是一款本地优先、可云同步的温柔记账应用。每一笔收入和支出，都被安全保存，也被整理成容易看懂的生活线索。", color = palette.muted, lineHeight = 20.sp, fontSize = 13.sp)
            }
        }
        item {
            PlushCard(padding = 16.dp) {
                SectionTitle("我们的初心", Icons.Default.Star)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutValue(Icons.Default.EditNote, "轻松记录", palette.rose, Modifier.weight(1f))
                    AboutValue(Icons.Default.Security, "安心守护", palette.moss, Modifier.weight(1f))
                    AboutValue(Icons.Default.Badge, "生活有序", palette.blue, Modifier.weight(1f))
                }
            }
        }
        item {
            PlushCard(padding = 16.dp) {
                SectionTitle("联系我们", Icons.Default.Email)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().clickable { showMailPrompt = true }) {
                    MenuRow(Icons.Default.Email, SUPPORT_EMAIL, "用户反馈和合作联系", palette.rose)
                }
            }
        }
        item {
            ProfileWarmPanel(padding = 16.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("愿每一次记录，都能帮你更温柔地靠近生活", color = palette.ink, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("谢谢你一直以来的陪伴～", color = palette.muted, fontSize = 12.sp)
                    }
                    MascotArt(74.dp)
                }
            }
        }
    }
    if (showMailPrompt) {
        ContactSupportDialog(
            email = SUPPORT_EMAIL,
            onDismiss = { showMailPrompt = false },
            onOpenEmail = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, "绒绒记账用户反馈")
                }
                runCatching { context.startActivity(intent) }
                    .onFailure { Toast.makeText(context, "没有找到可用的邮箱 App", Toast.LENGTH_SHORT).show() }
                showMailPrompt = false
            }
        )
    }
}

@Composable
private fun AboutValue(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PlushBadge(icon, color, 42.dp)
        Spacer(Modifier.height(8.dp))
        Text(label, color = LocalPlushPalette.current.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
    onVerifyIdentity: (String, String, String) -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onDeleteAccount: () -> Unit
) {
    val palette = LocalPlushPalette.current
    val profile = state.ledger.profile
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("plush_profile_actions", Context.MODE_PRIVATE) }
    val userKey = state.session?.userId ?: "guest"
    val initialName = profile?.displayName ?: state.session?.displayName.orEmpty()
    val initialAge = profile?.age?.toString().orEmpty()
    val initialBirthDate = profile?.birthDate.orEmpty()
    val initialGender = profile?.gender ?: "prefer_not"
    val initialSignature = remember(userKey) { prefs.getString("signature_$userKey", "认真生活，温柔记账") ?: "认真生活，温柔记账" }
    var nickname by rememberSaveable(profile?.displayName) { mutableStateOf(initialName) }
    var age by rememberSaveable(profile?.age) { mutableStateOf(initialAge) }
    var birthDate by rememberSaveable(profile?.birthDate) { mutableStateOf(initialBirthDate) }
    var gender by rememberSaveable(profile?.gender) { mutableStateOf(initialGender) }
    var signature by rememberSaveable(userKey) { mutableStateOf(initialSignature) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var identityChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var showNicknameEditor by rememberSaveable { mutableStateOf(false) }
    var showAgeEditor by rememberSaveable { mutableStateOf(false) }
    var showGenderEditor by rememberSaveable { mutableStateOf(false) }
    var showSignatureEditor by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showVerify by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var privacyOn by rememberSaveable(userKey) { mutableStateOf(prefs.getBoolean("privacy_$userKey", false)) }
    var verified by rememberSaveable(userKey) { mutableStateOf(prefs.getBoolean("verified_$userKey", false)) }
    var verifiedName by rememberSaveable(userKey) { mutableStateOf(prefs.getString("verified_name_$userKey", "") ?: "") }
    var deleteSeconds by remember { mutableIntStateOf(15) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onAvatar) }
    val dirty = nickname != initialName || age != initialAge || birthDate != initialBirthDate ||
        gender != initialGender || signature != initialSignature
    val genderLabel = when (gender) {
        "female" -> "女"
        "male" -> "男"
        "other" -> "其他"
        else -> "不公开"
    }
    val ageLabel = age.ifBlank { "--" } + "岁"
    val birthdayLabel = birthDate.toBirthdayLabel()
    val remoteMode = state.session?.accessToken != null

    LaunchedEffect(showDelete) {
        if (!showDelete) return@LaunchedEffect
        deleteSeconds = 15
        while (deleteSeconds > 0) {
            delay(1_000)
            deleteSeconds--
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ProfileTopBar(
                title = "用户信息",
                editing = editMode,
                onBack = onBack,
                onEdit = { editMode = !editMode }
            )
        }
        item {
            ProfileWarmPanel(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(state.avatarUrl, 100.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nickname.ifBlank { "绒绒用户" }, fontWeight = FontWeight.Black, fontSize = 26.sp, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("让每一次记录更贴近自己～", color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TinyPill(if (privacyOn) "**岁" else ageLabel, palette.rose)
                            TinyPill(genderLabel, palette.rose)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("生日  ${if (privacyOn) "**月**日" else birthdayLabel}", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(membershipLabel(profile?.role, profile?.membershipTier), color = badgeColor(profile?.role, profile?.membershipTier), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    MascotArt(86.dp)
                }
            }
        }
        item {
            PlushCard {
                ProfileSectionTitle("基本资料")
                ProfileListRow(Icons.Default.PhotoCamera, "头像", "", palette.rose, onClick = {
                    editMode = true
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Avatar(state.avatarUrl, 34.dp)
                }
                ProfileDivider()
                ProfileListRow(Icons.Default.Person, "昵称", nickname.ifBlank { "绒绒用户" }, palette.rose, onClick = {
                    editMode = true
                    showNicknameEditor = true
                })
                ProfileDivider()
                ProfileListRow(Icons.Default.Badge, "年龄", if (privacyOn) "**岁" else ageLabel, palette.moss, onClick = {
                    editMode = true
                    showAgeEditor = true
                })
                ProfileDivider()
                ProfileListRow(Icons.Default.AccountCircle, "性别", genderLabel, palette.lilac, onClick = {
                    editMode = true
                    showGenderEditor = true
                })
                ProfileDivider()
                ProfileListRow(Icons.Default.Badge, "生日", if (privacyOn) "**月**日" else birthdayLabel, palette.coral, onClick = {
                    editMode = true
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
                })
                ProfileDivider()
                ProfileListRow(Icons.Default.ChatBubble, "个性签名", signature, palette.coral, onClick = {
                    editMode = true
                    showSignatureEditor = true
                })
            }
        }
        item {
            PlushCard {
                ProfileSectionTitle("账号绑定")
                ProfileProviderRow(R.drawable.logo_wechat, "微信", if (profile?.wechatBound == true) "已绑定" else "未绑定", palette.moss, onClick = { onBind("微信绑定") })
                ProfileDivider()
                ProfileProviderRow(R.drawable.logo_qq_official, "QQ", if (profile?.qqBound == true) "已绑定" else "未绑定", palette.blue, onClick = { onBind("QQ绑定") })
                ProfileDivider()
                ProfileListRow(Icons.Default.Email, "邮箱", (profile?.email ?: state.session?.email ?: "本地账号").maskIf(privacyOn), palette.coral, enabled = remoteMode, onClick = {
                    identityChannel = "email"
                })
                ProfileDivider()
                ProfileListRow(Icons.Default.Phone, "手机号", (profile?.phone ?: state.session?.phone ?: "未绑定").maskIf(privacyOn), palette.blue, enabled = remoteMode, onClick = {
                    identityChannel = "phone"
                })
            }
        }
        item {
            PlushCard {
                ProfileSectionTitle("其他功能")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ProfileShortcut(Icons.Default.Lock, "资料隐私", palette.lilac, Modifier.weight(1f)) { showPrivacy = true }
                    Box(Modifier.width(1.dp).height(74.dp).background(palette.border))
                    ProfileShortcut(Icons.Default.Security, "修改密码", palette.rose, Modifier.weight(1f)) { showPassword = true }
                    Box(Modifier.width(1.dp).height(74.dp).background(palette.border))
                    ProfileShortcut(Icons.Default.Shield, "实名认证", palette.moss, Modifier.weight(1f)) { showVerify = true }
                    Box(Modifier.width(1.dp).height(74.dp).background(palette.border))
                    ProfileShortcut(Icons.Default.DeleteForever, "注销账号", palette.coral, Modifier.weight(1f)) { showDelete = true }
                }
            }
        }
        item {
            ProfileWarmPanel(padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("完善个人信息，体验会更完整哦～", color = palette.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MascotArt(72.dp)
                }
            }
        }
        if (editMode) {
            item {
                PlushButton("保存修改", Icons.Default.Save, Modifier.fillMaxWidth(), enabled = dirty) {
                    prefs.edit().putString("signature_$userKey", signature).apply()
                    onSave(nickname, age, birthDate.ifBlank { null }, gender)
                    editMode = false
                }
            }
        }
    }
    if (showNicknameEditor) {
        ProfileTextEditDialog(
            title = "修改昵称",
            value = nickname,
            label = "昵称",
            maxLength = 24,
            onDismiss = { showNicknameEditor = false },
            onConfirm = { nickname = it.ifBlank { nickname }; showNicknameEditor = false }
        )
    }
    if (showAgeEditor) {
        ProfileTextEditDialog(
            title = "修改年龄",
            value = age,
            label = "年龄",
            maxLength = 3,
            keyboardType = KeyboardType.Number,
            filter = { it.filter(Char::isDigit) },
            onDismiss = { showAgeEditor = false },
            onConfirm = { age = it; showAgeEditor = false }
        )
    }
    if (showGenderEditor) {
        AlertDialog(
            onDismissRequest = { showGenderEditor = false },
            title = { Text("选择性别", fontWeight = FontWeight.Bold) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("female" to "女", "male" to "男", "other" to "其他", "prefer_not" to "不公开").forEach { (key, label) ->
                        SoftChip(label, gender == key, palette.rose) {
                            gender = key
                            showGenderEditor = false
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
    if (showSignatureEditor) {
        ProfileTextEditDialog(
            title = "修改个性签名",
            value = signature,
            label = "个性签名",
            maxLength = 28,
            onDismiss = { showSignatureEditor = false },
            onConfirm = { signature = it.ifBlank { "认真生活，温柔记账" }; showSignatureEditor = false }
        )
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("资料隐私", fontWeight = FontWeight.Bold) },
            text = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("隐藏邮箱、手机号、年龄和生日", modifier = Modifier.weight(1f), color = palette.ink)
                    Switch(checked = privacyOn, onCheckedChange = {
                        privacyOn = it
                        prefs.edit().putBoolean("privacy_$userKey", it).apply()
                    })
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("完成") } }
        )
    }
    if (showPassword) {
        PasswordChangeDialog(
            busy = state.isBusy,
            onDismiss = { showPassword = false },
            onConfirm = { current, next, confirm ->
                onChangePassword(current, next, confirm)
                showPassword = false
            }
        )
    }
    if (showVerify) {
        IdentityVerifyDialog(
            verified = verified,
            verifiedName = verifiedName,
            onDismiss = { showVerify = false },
            onConfirm = { name ->
                verified = true
                verifiedName = name
                prefs.edit()
                    .putBoolean("verified_$userKey", true)
                    .putString("verified_name_$userKey", name)
                    .apply()
                showVerify = false
                Toast.makeText(context, "实名认证状态已保存", Toast.LENGTH_SHORT).show()
            }
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "注销账号",
            message = "注销后账号、云端账目和本机数据都无法恢复。请确认你已经备份重要数据。",
            confirmText = if (deleteSeconds == 0) "确认永久注销" else "请等待 ${deleteSeconds}s",
            confirmEnabled = deleteSeconds == 0,
            onDismiss = { showDelete = false }
        ) {
            onDeleteAccount()
            showDelete = false
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
private fun ProfileSectionTitle(text: String) {
    val palette = LocalPlushPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(4.dp)).background(palette.rose))
        Spacer(Modifier.width(8.dp))
        Text(text, color = palette.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun TinyPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.14f)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun ProfileTopBar(title: String, editing: Boolean, onBack: () -> Unit, onEdit: () -> Unit) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = palette.ink) }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = palette.ink,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = onEdit) {
            Icon(if (editing) Icons.Default.Close else Icons.Default.EditNote, contentDescription = if (editing) "结束编辑" else "编辑", tint = palette.ink)
        }
    }
}

@Composable
private fun ProfileDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPlushPalette.current.border))
}

@Composable
private fun ProfileListRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val palette = LocalPlushPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlushBadge(icon, color.copy(alpha = if (enabled) 0.88f else 0.42f), 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(0.9f), color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (trailing != null) {
            trailing()
        } else {
            Text(
                value,
                modifier = Modifier.weight(1.2f),
                color = if (enabled) palette.muted else palette.muted.copy(alpha = 0.5f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.muted.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

@Composable
private fun ProfileProviderRow(
    logoRes: Int,
    label: String,
    value: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = LocalPlushPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
            Image(painterResource(logoRes), contentDescription = label, modifier = Modifier.size(34.dp).padding(6.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(0.9f), color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            value,
            modifier = Modifier.weight(1.2f),
            color = if (enabled) palette.muted else palette.muted.copy(alpha = 0.5f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.muted.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

@Composable
private fun ProfileTextEditDialog(
    title: String,
    value: String,
    label: String,
    maxLength: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
    filter: (String) -> String = { it },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var draft by rememberSaveable(title) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                draft,
                { draft = filter(it).take(maxLength) },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = { onConfirm(draft.trim()) }) { Text("确定") } }
    )
}

@Composable
private fun PasswordChangeDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(current, { current = it.take(64) }, label = { Text("当前密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(next, { next = it.take(64) }, label = { Text("新密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(confirm, { confirm = it.take(64) }, label = { Text("确认新密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("新密码需要 8-64 位，并同时包含字母和数字。", color = LocalPlushPalette.current.muted, fontSize = 12.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            TextButton(onClick = { onConfirm(current, next, confirm) }, enabled = !busy) { Text("保存密码") }
        }
    )
}

@Composable
private fun IdentityVerifyDialog(
    verified: Boolean,
    verifiedName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var realName by rememberSaveable { mutableStateOf(verifiedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("实名认证", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (verified) "当前已保存认证状态。正式上线前建议接入合规实名服务商。"
                    else "先保存应用内认证状态；正式实名认证需要后续接入合规服务商。",
                    color = LocalPlushPalette.current.muted,
                    fontSize = 12.sp
                )
                OutlinedTextField(realName, { realName = it.take(12) }, label = { Text("姓名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = { onConfirm(realName.trim()) }, enabled = realName.isNotBlank()) { Text("保存") } }
    )
}

@Composable
private fun ProfileEditRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    trailing: @Composable RowScope.() -> Unit
) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        PlushBadge(icon, color, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.ink, fontWeight = FontWeight.Bold)
            Text(subtitle, color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.muted)
    }
}

@Composable
private fun ProfileShortcut(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlushBadge(icon, color, 42.dp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = palette.ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.muted, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun SettingsScreen(state: UiState, biometricAvailable: Boolean, viewModel: LedgerViewModel, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("plush_user_settings", Context.MODE_PRIVATE) }
    val profile = state.ledger.profile
    var pin by rememberSaveable { mutableStateOf("") }
    var showSignOut by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showReminder by rememberSaveable { mutableStateOf(false) }
    var showCurrency by rememberSaveable { mutableStateOf(false) }
    var showDownloadLine by rememberSaveable { mutableStateOf(false) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showBillImportSource by rememberSaveable { mutableStateOf(false) }
    var billImportProvider by rememberSaveable { mutableStateOf("微信") }
    var showCache by rememberSaveable { mutableStateOf(false) }
    var showLicense by rememberSaveable { mutableStateOf(false) }
    var reminderEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("ledger_reminder", true)) }
    var currency by rememberSaveable { mutableStateOf(prefs.getString("currency_unit", "人民币  ¥") ?: "人民币  ¥") }
    var downloadLine by rememberSaveable { mutableStateOf(prefs.getString("download_line", "国内优先") ?: "国内优先") }
    var deleteSeconds by remember { mutableIntStateOf(15) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(buildCsv(state.ledger)) }
                    ?: error("无法打开导出位置")
            }.onSuccess {
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val billImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.previewExternalBill(it, billImportProvider) }
    }

    LaunchedEffect(showDelete) {
        if (!showDelete) return@LaunchedEffect
        deleteSeconds = 15
        while (deleteSeconds > 0) {
            delay(1_000)
            deleteSeconds--
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { BackHeader("设置", onBack) }
        item {
            ProfileWarmPanel(padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(state.avatarUrl, 76.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile?.displayName ?: state.session?.displayName ?: "绒绒用户", color = palette.ink, fontSize = 23.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("让每一次记录更顺手～", color = palette.muted, fontSize = 12.sp)
                    }
                    MascotArt(82.dp)
                }
            }
        }
        item {
            PlushCard {
                ProfileSectionTitle("通用设置")
                SettingsValueRow(Icons.Default.Palette, "主题", plushThemeName(state.themeTone), palette.rose) { showTheme = true }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                ToggleRow(Icons.Default.DarkMode, "深色模式", state.darkMode, viewModel::setDarkMode)
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                SettingsValueRow(Icons.Default.Notifications, "记账提醒", if (reminderEnabled) "每天 21:00" else "已关闭", palette.coral) { showReminder = true }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                SettingsValueRow(Icons.Default.Paid, "货币单位", currency, palette.moss) { showCurrency = true }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                SettingsValueRow(Icons.Default.SystemUpdate, "更新下载线路", downloadLine, palette.rose) { showDownloadLine = true }
            }
        }
        item {
            PlushCard {
                ProfileSectionTitle("数据与安全")
                ActionRow(Icons.Default.Download, "数据导出", "确认后选择本机保存位置", palette.moss) { showExportDialog = true }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                ActionRow(Icons.Default.CreditCard, "账单智能导入", "导入微信或支付宝导出的 CSV", palette.blue) { showBillImportSource = true }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                ToggleRow(Icons.Default.Security, "隐私防截图", state.secureScreen, viewModel::setSecureScreen)
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                ActionRow(Icons.Default.CloudSync, "云端备份", state.syncLabel.ifBlank { "未开启" }, palette.blue, viewModel::syncNow)
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Text("打开验证", fontWeight = FontWeight.Bold, color = palette.ink, modifier = Modifier.padding(top = 10.dp))
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
                ProfileSectionTitle("更多")
                ActionRow(
                    Icons.Default.SystemUpdate,
                    "检查更新",
                    if (state.isCheckingUpdate) "正在检查" else "当前版本 ${BuildConfig.VERSION_NAME}",
                    palette.moss
                ) { viewModel.checkForUpdates() }
                Spacer(Modifier.height(8.dp))
                ActionRow(Icons.Default.DeleteForever, "清理缓存", "清除临时图片和下载残留", palette.coral) { showCache = true }
                Spacer(Modifier.height(8.dp))
                ActionRow(Icons.Default.AccountCircle, "开源许可", "仅允许非商业用途", palette.blue) { showLicense = true }
            }
        }
        item {
            ProfileWarmPanel(padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("小绒绒提示：", color = palette.ink, fontWeight = FontWeight.Bold)
                        Text("设置好提醒，记账会更轻松哦～", color = palette.muted, fontSize = 12.sp)
                    }
                    MascotArt(74.dp)
                }
            }
        }
        item {
            OutlinedButton(onClick = { showSignOut = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Default.Logout, contentDescription = null, tint = palette.rose)
                Spacer(Modifier.width(8.dp))
                Text("退出登录", color = palette.rose, fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = palette.coral)
                Spacer(Modifier.width(8.dp))
                Text("注销账号", color = palette.coral, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showSignOut) {
        SettingsConfirmDialog("退出登录", "确定要退出当前账号吗？本机账目不会删除。", confirmText = "退出", onDismiss = { showSignOut = false }) {
            viewModel.signOut()
            showSignOut = false
        }
    }
    if (showDelete) {
        SettingsConfirmDialog(
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
    if (showExportDialog) {
        ExportDataDialog(
            fileName = "rongrong-ledger-${LocalDate.now()}.csv",
            onDismiss = { showExportDialog = false },
            onConfirm = {
                showExportDialog = false
                exportLauncher.launch("rongrong-ledger-${LocalDate.now()}.csv")
            }
        )
    }
    if (showBillImportSource) {
        BillSourceDialog(
            provider = billImportProvider,
            onProvider = { billImportProvider = it },
            onDismiss = { showBillImportSource = false },
            onChoose = {
                showBillImportSource = false
                billImportLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
            }
        )
    }
    state.billImportPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissExternalBillImport,
            title = { Text("确认导入${preview.provider}账单", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("识别到 ${preview.entries.size} 笔成功收支${if (preview.skippedRows > 0) "，已跳过 ${preview.skippedRows} 笔退款、转账或无效记录" else ""}。", color = palette.ink, fontSize = 13.sp)
                    ProfileWarmPanel(padding = 12.dp) {
                        preview.entries.take(3).forEach { entry ->
                            Text(
                                "${if (entry.type == "income") "收入" else "支出"} ${Money.formatCny(entry.amountMinor)}  ${entry.note.ifBlank { "未填写备注" }}",
                                color = palette.muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text("账单文件不会上传；仅在确认后保存账目，登录云端账号时账目会按你的同步设置备份。", color = palette.muted, fontSize = 12.sp)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissExternalBillImport) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = viewModel::confirmExternalBillImport, enabled = !state.isBusy) {
                    Text(if (state.isBusy) "正在导入" else "确认导入", color = palette.moss)
                }
            },
            containerColor = palette.surface
        )
    }
    if (showTheme) {
        ThemePickerDialog(
            current = state.themeTone,
            onDismiss = { showTheme = false },
            onChoose = {
                viewModel.setThemeTone(it)
                showTheme = false
            }
        )
    }
    if (showReminder) {
        ReminderDialog(reminderEnabled, onDismiss = { showReminder = false }) { enabled ->
            reminderEnabled = enabled
            prefs.edit().putBoolean("ledger_reminder", enabled).apply()
            showReminder = false
            Toast.makeText(context, if (enabled) "记账提醒已保存" else "记账提醒已关闭", Toast.LENGTH_SHORT).show()
        }
    }
    if (showCurrency) {
        CurrencyDialog(currency, onDismiss = { showCurrency = false }) { option ->
            currency = option
            prefs.edit().putString("currency_unit", option).apply()
            showCurrency = false
        }
    }
    if (showDownloadLine) {
        DownloadLineDialog(downloadLine, onDismiss = { showDownloadLine = false }) { option ->
            downloadLine = option
            prefs.edit().putString("download_line", option).apply()
            showDownloadLine = false
        }
    }
    if (showCache) {
        SettingsConfirmDialog("清理缓存", "是否清除临时缓存？账本数据不会被删除。", confirmText = "确认删除", onDismiss = { showCache = false }) {
            val success = runCatching { context.cacheDir.deleteRecursively() }.getOrDefault(false)
            Toast.makeText(context, if (success) "缓存已清理" else "缓存清理完成", Toast.LENGTH_SHORT).show()
            showCache = false
        }
    }
    if (showLicense) {
        LicenseDialog { showLicense = false }
    }
}

@Composable
private fun ThemeChoiceDialog(currentTone: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    val options = listOf(
        Triple("warm", "暖黄", Color(0xFFFFA126)),
        Triple("pink", "绒粉", Color(0xFFFF8DAE)),
        Triple("mono", "黑白", Color(0xFF4C4742)),
        Triple("green", "淡绿", Color(0xFF79C98D)),
        Triple("blue", "冰蓝", Color(0xFF7BB6F3))
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题", fontWeight = FontWeight.Bold, color = palette.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (key, label, color) ->
                    OutlinedButton(onClick = { onChoose(key) }, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(10.dp))
                        Text(label, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold)
                        Text(if (currentTone == key) "当前" else "切换", color = if (currentTone == key) color else palette.muted)
                    }
                }
                Text("所有主题都保持低饱和、柔和毛绒质感，只改变主色氛围。", color = palette.muted, fontSize = 12.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {}
    )
}

@Composable
private fun MembershipScreen(state: UiState, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    val profile = state.ledger.profile
    val hasRights = profile?.role == "admin" || profile?.membershipTier == "permanent"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("会员", onBack) }
        item {
            PlushCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlushBadge(if (profile?.role == "admin") Icons.Default.Shield else Icons.Default.Star, palette.rose, 54.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(membershipLabel(profile?.role, profile?.membershipTier), fontWeight = FontWeight.Black, fontSize = 22.sp, color = palette.ink)
                        Text(if (hasRights) "权益永久有效" else "公测期间全部功能免费", color = palette.muted)
                    }
                }
                if (!hasRights) {
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = palette.moss.copy(alpha = 0.12f)) {
                        Text(
                            "当前版本不收取会员费用，所有记账、统计、云同步和数据管理功能均可免费使用。",
                            modifier = Modifier.padding(14.dp),
                            color = palette.ink,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 21.sp
                        )
                    }
                }
            }
        }
        item {
            PlushCard {
                InfoRow(Icons.Default.Star, "公测权益", "全部免费")
                InfoRow(Icons.Default.CloudSync, "云端同步", "免费开放")
                InfoRow(Icons.Default.CreditCard, "账单智能导入", "后续开放")
            }
        }
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
private fun SettingsValueRow(icon: ImageVector, title: String, value: String, color: Color, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PlushBadge(icon, color, 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = palette.ink)
        Text(value, color = palette.muted, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
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
    var countryCode by rememberSaveable(channel) { mutableStateOf("+86") }
    val isEmail = channel == "email"
    val target = if (isEmail) value.trim() else "$countryCode${value.filter(Char::isDigit)}"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEmail) "换绑邮箱" else "换绑手机号", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isEmail) "请输入新邮箱收到的验证码后完成换绑。若邮件仍显示确认链接，需要先把 Supabase 邮件模板改为验证码。"
                    else "选择区号后输入日常手机号即可，默认中国大陆 +86。",
                    color = LocalPlushPalette.current.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                if (isEmail) {
                    OutlinedTextField(
                        value,
                        { value = it.trim().take(80) },
                        label = { Text("新邮箱") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProfileCountryCodeButton(countryCode, Modifier.width(92.dp)) { countryCode = it }
                        OutlinedTextField(
                            value,
                            { value = it.filter(Char::isDigit).take(15) },
                            label = { Text("新手机号") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                }
                OutlinedButton(
                    onClick = { onSend(target) },
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
            TextButton(onClick = { onVerify(target, code) }, enabled = !busy && value.isNotBlank() && code.length >= 4) {
                Text("确认换绑")
            }
        }
    )
}

@Composable
private fun ProfileCountryCodeButton(value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val palette = LocalPlushPalette.current
    OutlinedButton(onClick = { showPicker = true }, modifier = modifier.height(56.dp), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
        Text(value, color = palette.rose, fontWeight = FontWeight.Black)
    }
    if (showPicker) {
        Dialog(onDismissRequest = { showPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.86f).heightIn(max = 650.dp),
                shape = RoundedCornerShape(28.dp),
                color = palette.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.border),
                shadowElevation = 18.dp
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = palette.moss, modifier = Modifier.size(12.dp))
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.rose, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("选择区号", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Spacer(Modifier.width(9.dp))
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.rose, modifier = Modifier.size(15.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = palette.moss, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(profilePhoneCountryOptions) { item ->
                            val selected = value == item.code
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable {
                                    onChange(item.code)
                                    showPicker = false
                                },
                                shape = RoundedCornerShape(22.dp),
                                color = if (selected) Color(0xFFFFF6E7) else palette.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) palette.rose else palette.border)
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.flag, fontSize = 24.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(item.name, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold)
                                    Text(item.code, color = palette.rose, fontWeight = FontWeight.Black)
                                    if (selected) Text("  ✓", color = palette.rose, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        TextButton(onClick = { showPicker = false }) { Text("取消", color = palette.pink, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.weight(1f))
                        MascotArt(72.dp)
                    }
                }
            }
        }
    }
}

private data class ProfilePhoneCountry(val flag: String, val name: String, val code: String)

private val profilePhoneCountryOptions = listOf(
    ProfilePhoneCountry("🇨🇳", "中国大陆", "+86"),
    ProfilePhoneCountry("🇭🇰", "中国香港", "+852"),
    ProfilePhoneCountry("🇲🇴", "中国澳门", "+853"),
    ProfilePhoneCountry("🇹🇼", "中国台湾", "+886"),
    ProfilePhoneCountry("🇺🇸🇨🇦", "美国/加拿大", "+1"),
    ProfilePhoneCountry("🇬🇧", "英国", "+44"),
    ProfilePhoneCountry("🇯🇵", "日本", "+81"),
    ProfilePhoneCountry("🇰🇷", "韩国", "+82"),
    ProfilePhoneCountry("🇸🇬", "新加坡", "+65"),
    ProfilePhoneCountry("🇦🇺", "澳大利亚", "+61"),
    ProfilePhoneCountry("🇳🇿", "新西兰", "+64"),
    ProfilePhoneCountry("🇩🇪", "德国", "+49")
)

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
                placeholder = painterResource(R.drawable.ic_launcher_transparent),
                error = painterResource(R.drawable.ic_launcher_transparent),
                fallback = painterResource(R.drawable.ic_launcher_transparent)
            )
        }
    }
}

@Composable
private fun ProfileMetric(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    Row(modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp).size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = palette.ink, fontSize = 12.sp)
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun rememberDailyQuote(): String {
    val context = LocalContext.current
    var day by remember { mutableStateOf(LocalDate.now()) }
    val quotes = rememberQuoteCollection()
    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now()
            val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, nextDay).toMillis().coerceAtLeast(1_000L) + 500L)
            day = LocalDate.now()
        }
    }
    return quotes[Math.floorMod(day.toEpochDay().toInt(), quotes.size)]
}

@Composable
private fun rememberQuoteCollection(): List<String> {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.assets.open("daily_quotes.txt").bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    Regex("^\\s*\\d+\\.\\s*(.+)$").find(line)?.groupValues?.get(1)?.trim()
                }.filter(String::isNotBlank).toList()
            }
        }.getOrDefault(listOf("今天也要温柔地掌控生活。"))
    }.ifEmpty { listOf("今天也要温柔地掌控生活。") }
}

@Composable
private fun ProfileWarmPanel(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = palette.surfaceAlt,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border),
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

private fun buildCsv(ledger: LedgerState): String {
    val categories = ledger.categories.associateBy { it.id }
    val accounts = ledger.accounts.associateBy { it.id }
    val localFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    return buildString {
        appendLine("\ufeffid,date,time,datetime,occurred_at,created_at,updated_at,type,type_label,category,category_parent,category_parent_id,category_path,category_level,category_kind,category_icon,category_color,account,account_kind,to_account,amount_minor,amount_cny,currency,note")
        ledger.transactions.sortedByDescending { it.occurredAt }.forEach { record ->
            val occurred = Instant.ofEpochMilli(record.occurredAt).atZone(ZoneId.systemDefault())
            val categoryEntity = categories[record.categoryId]
            val parentCategory = categoryEntity?.parentId?.let(categories::get)
            val accountEntity = accounts[record.accountId]
            val toAccountEntity = accounts[record.toAccountId]
            val id = record.id.csvCell()
            val date = occurred.toLocalDate().toString()
            val time = occurred.toLocalTime().format(timeFormatter)
            val datetime = occurred.format(localFormatter).csvCell()
            val type = record.type.csvCell()
            val typeLabel = when (record.type) {
                "income" -> "收入"
                "transfer" -> "转账"
                else -> "支出"
            }.csvCell()
            val category = categoryEntity?.name.orEmpty().csvCell()
            val categoryParent = parentCategory?.name.orEmpty().csvCell()
            val categoryParentId = parentCategory?.id.orEmpty().csvCell()
            val categoryPath = listOfNotNull(parentCategory?.name, categoryEntity?.name).joinToString("/").csvCell()
            val categoryLevel = if (parentCategory == null) "1" else "2"
            val categoryKind = categoryEntity?.kind.orEmpty().csvCell()
            val categoryIcon = categoryEntity?.icon.orEmpty().csvCell()
            val categoryColor = categoryEntity?.colorHex.orEmpty().csvCell()
            val account = accountEntity?.name.orEmpty().csvCell()
            val accountKind = accountEntity?.kind.orEmpty().csvCell()
            val toAccount = toAccountEntity?.name.orEmpty().csvCell()
            val amountCny = "%.2f".format(java.util.Locale.US, record.amountMinor / 100.0)
            val currency = record.currency.csvCell()
            val note = record.note.csvCell()
            appendLine("$id,$date,$time,$datetime,${record.occurredAt},${record.createdAt},${record.updatedAt},$type,$typeLabel,$category,$categoryParent,$categoryParentId,$categoryPath,$categoryLevel,$categoryKind,$categoryIcon,$categoryColor,$account,$accountKind,$toAccount,${record.amountMinor},$amountCny,$currency,$note")
        }
    }
}

private fun String.csvCell(): String = "\"${replace("\"", "\"\"")}\""

private fun com.plushledger.data.TransactionEntity.localDateForProfile(): java.time.LocalDate =
    java.time.Instant.ofEpochMilli(occurredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

private fun String.toBirthdayLabel(): String =
    runCatching {
        val date = LocalDate.parse(this)
        date.format(DateTimeFormatter.ofPattern("MM月dd日"))
    }.getOrDefault(ifBlank { "未设置" })

private fun String.maskIf(enabled: Boolean): String {
    if (!enabled || isBlank() || this == "未绑定" || this == "本地账号") return this
    return if (contains("@")) {
        val parts = split("@")
        "${parts.first().take(2)}***@${parts.getOrNull(1).orEmpty()}"
    } else {
        take(3) + "****" + takeLast(2)
    }
}

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
