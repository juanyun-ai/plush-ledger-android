package com.plushledger.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreditCard
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
import androidx.compose.material.icons.filled.QrCode2
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
import java.io.File
import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

private const val SUPPORT_EMAIL = "2998319435@qq.com"

private fun openSupportEmail(context: android.content.Context, content: String = "") {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, "绒绒记账用户建议")
        if (content.isNotBlank()) putExtra(Intent.EXTRA_TEXT, content)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "没有找到可用的邮箱 App", Toast.LENGTH_SHORT).show() }
}

@Composable
fun InboxScreen(
    messages: List<OfficialMessage>,
    busy: Boolean,
    isLocalMode: Boolean,
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
                if (isLocalMode) {
                    Text("本地模式的账目不上传云端；这里的建议会直接发送到开发者后台，不再依赖邮箱。", color = palette.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                }
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
            onSendIdentityCode = viewModel::requestIdentityChange,
            onVerifyIdentity = viewModel::verifyIdentityChange,
            onSendPhoneUpgradeCode = { viewModel.sendLoginOtp("phone", it) },
            onVerifyPhoneUpgrade = { phone, code -> viewModel.verifyLoginOtp("phone", phone, code) },
            onChangePassword = viewModel::changePassword,
            onDeleteAccount = viewModel::deleteAccountPermanently
        )
        MyPage.INBOX -> Column(Modifier.fillMaxSize()) {
            BackHeader("消息与建议", onBack = { page = MyPage.ROOT })
            InboxScreen(
                messages = state.officialMessages,
                busy = state.isBusy,
                isLocalMode = state.session?.accessToken == null,
                onFeedback = viewModel::submitFeedback,
                onDownloadUpdate = onDownloadUpdate
            )
        }
        MyPage.SETTINGS -> SettingsScreen(
            state,
            biometricAvailable,
            viewModel,
            onBack = { page = MyPage.ROOT },
            onBudget = { page = MyPage.BUDGET },
            onCategory = { page = MyPage.CATEGORY },
            onInbox = { page = MyPage.INBOX; viewModel.refreshMailbox() },
            onAbout = { page = MyPage.ABOUT }
        )
        MyPage.MEMBERSHIP -> MembershipScreen(state, onBack = { page = MyPage.ROOT })
        MyPage.BUDGET -> BudgetManagementScreen(state.ledger, onBack = { page = MyPage.ROOT }, onBudget = viewModel::setBudget)
        MyPage.CATEGORY -> CategoryManagementScreen(
            ledger = state.ledger,
            onBack = { page = MyPage.ROOT },
            onAdd = viewModel::addCategory,
            onDelete = viewModel::deleteCategory,
            onReorder = viewModel::moveCategory,
            onMoveTo = viewModel::moveCategoryTo
        )
        MyPage.ABOUT -> AboutScreen(
            onBack = { page = MyPage.ROOT },
            busy = state.isBusy,
            onFeedback = viewModel::submitFeedback
        )
        MyPage.DIARY -> DiaryScreen(
            userId = state.session?.userId ?: state.ledger.profile?.id ?: "local-diary",
            quotes = rememberQuoteCollection(),
            locationHint = listOfNotNull(state.ledger.profile?.province, state.ledger.profile?.city).joinToString(" "),
            onChanged = viewModel::syncNow,
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
    val ledgerDays = state.ledger.transactions.map { it.localDateForProfile() }.distinct().size
    val context = LocalContext.current
    val profilePrefs = remember { context.getSharedPreferences("plush_profile_actions", Context.MODE_PRIVATE) }
    val userKey = state.session?.userId ?: "guest"
    val signature = remember(userKey, profile?.updatedAt) {
        profilePrefs.getString("signature_$userKey", "认真生活，温柔记账") ?: "认真生活，温柔记账"
    }
    val birthdayLabel = profile?.birthDate?.takeIf { it.isNotBlank() }?.toBirthdayLabel() ?: "未设置生日"
    val regionLabel = listOfNotNull(profile?.province, profile?.city).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未设置地区" }
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
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "设置", tint = palette.ink) }
            }
        }
        item {
            ProfileWarmPanel(Modifier.fillMaxWidth().clickable(onClick = onProfile), padding = 10.dp) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 340.dp
                    val avatarSize = if (compact) 58.dp else 70.dp
                    val mascotSize = if (compact) 62.dp else 88.dp
                    val nameSize = if (compact) 21.sp else 25.sp
                    val gap = if (compact) 10.dp else 16.dp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color.White, shadowElevation = 5.dp) {
                            Box(Modifier.padding(4.dp)) { Avatar(state.avatarUrl, avatarSize) }
                        }
                        Spacer(Modifier.width(gap))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    profile?.displayName ?: state.session?.displayName ?: "绒绒用户",
                                    fontWeight = FontWeight.Black,
                                    fontSize = nameSize,
                                    color = palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                GenderMark(profile?.gender)
                            }
                            Text(signature.ifBlank { "认真生活，温柔记账" }, color = palette.muted, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
                            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFEDC8), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFDFA2))) {
                                Row(Modifier.padding(start = 4.dp, end = if (compact) 8.dp else 12.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    MascotArt(if (compact) 20.dp else 24.dp, R.drawable.mascot_action_like)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        badge,
                                        color = badgeColor(profile?.role, profile?.membershipTier),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (compact) 10.sp else 12.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("生日：$birthdayLabel", color = palette.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("地区：$regionLabel", color = palette.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        MascotArt(mascotSize, R.drawable.mascot_action_heart)
                    }
                }
            }
        }
        item {
            LedgerFootprintCard(state.ledger, ledgerDays, monthCount)
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
                        painter = painterResource(R.drawable.mascot_action_sleep),
                        contentDescription = "绒绒写日记",
                        modifier = Modifier.width(124.dp).height(94.dp),
                        contentScale = ContentScale.Fit
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
private fun AboutScreen(
    onBack: () -> Unit,
    busy: Boolean,
    onFeedback: (String) -> Unit
) {
    val palette = LocalPlushPalette.current
    val context = LocalContext.current
    val draftStore = remember(context) { context.getSharedPreferences("about_feedback_draft", Context.MODE_PRIVATE) }
    var savedDraft by rememberSaveable { mutableStateOf(draftStore.getString("content", "").orEmpty()) }
    var feedback by rememberSaveable { mutableStateOf(savedDraft) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BackHeader("关于我们", onBack) }
        item {
            ProfileWarmPanel(padding = 20.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MascotArt(116.dp, R.drawable.mascot_action_heart)
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
                SectionTitle("小程序版本", Icons.Default.QrCode2)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("也可以在微信里使用绒绒记账", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("适合临时记一笔、换设备快速打开；App 和小程序功能会按平台规则逐步对齐。", color = palette.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Image(
                        painter = painterResource(R.drawable.miniprogram_code),
                        contentDescription = "绒绒记账小程序码",
                        modifier = Modifier.size(118.dp).clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        item {
            PlushCard(padding = 16.dp) {
                SectionTitle("联系我们", Icons.Default.Email)
                Spacer(Modifier.height(10.dp))
                MenuRow(Icons.Default.Email, SUPPORT_EMAIL, "备用联系邮箱；请优先使用下方在线留言", palette.rose)
                Spacer(Modifier.height(12.dp))
                Text("在线留言", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text("这里会直接进入开发者后台，不依赖邮箱。请尽量写清问题页面、操作步骤或希望增加的功能。", color = palette.muted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it.take(500) },
                    label = { Text("留言内容") },
                    placeholder = { Text("例如：我在统计页点某个按钮后没有反应……") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 7
                )
                Spacer(Modifier.height(8.dp))
                Text("${feedback.length}/500，至少 5 个字", color = palette.muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { feedback = "" }, enabled = feedback.isNotBlank() && !busy) {
                        Text("清空", color = palette.coral, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            savedDraft = feedback
                            draftStore.edit().putString("content", feedback).apply()
                            Toast.makeText(context, "留言已暂存", Toast.LENGTH_SHORT).show()
                        },
                        enabled = feedback.isNotBlank() && !busy
                    ) {
                        Text("暂存", color = palette.muted, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            feedback = savedDraft
                            Toast.makeText(context, "已取消本次编辑", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !busy
                    ) {
                        Text("取消", color = palette.pink, fontWeight = FontWeight.Black)
                    }
                }
                PlushButton(
                    "发送",
                    Icons.Default.Send,
                    Modifier.fillMaxWidth(),
                    enabled = !busy && feedback.trim().length >= 5,
                    color = palette.rose
                ) {
                    val pending = feedback.trim()
                    onFeedback(pending)
                    savedDraft = ""
                    draftStore.edit().remove("content").apply()
                    feedback = ""
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
                    MascotArt(74.dp, R.drawable.mascot_action_like)
                }
            }
        }
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
    onSave: (String, String, String?, String?, String?, String?, String?) -> Unit,
    onAvatar: (android.net.Uri) -> Unit,
    onSendIdentityCode: (String, String) -> Unit,
    onVerifyIdentity: (String, String, String) -> Unit,
    onSendPhoneUpgradeCode: (String) -> Unit,
    onVerifyPhoneUpgrade: (String, String) -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onDeleteAccount: () -> Unit
) {
    val palette = LocalPlushPalette.current
    val profile = state.ledger.profile
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("plush_profile_actions", Context.MODE_PRIVATE) }
    val userKey = state.session?.userId ?: "guest"
    val plannerStore = remember(userKey) { LifePlannerStore(context.applicationContext, userKey) }
    val initialName = profile?.displayName ?: state.session?.displayName.orEmpty()
    val initialAge = profile?.age?.toString().orEmpty()
    val initialBirthDate = profile?.birthDate.orEmpty()
    val initialGender = profile?.gender ?: "prefer_not"
    val initialProvince = profile?.province.orEmpty()
    val initialCity = profile?.city.orEmpty()
    val initialAccountNo = profileAccountNo(profile, userKey)
    val initialSignature = remember(userKey) { prefs.getString("signature_$userKey", "认真生活，温柔记账") ?: "认真生活，温柔记账" }
    var nickname by rememberSaveable(profile?.displayName) { mutableStateOf(initialName) }
    var age by rememberSaveable(profile?.age) { mutableStateOf(initialAge) }
    var birthDate by rememberSaveable(profile?.birthDate) { mutableStateOf(initialBirthDate) }
    var gender by rememberSaveable(profile?.gender) { mutableStateOf(initialGender) }
    var province by rememberSaveable(profile?.province) { mutableStateOf(initialProvince) }
    var city by rememberSaveable(profile?.city) { mutableStateOf(initialCity) }
    var accountNo by rememberSaveable(profile?.accountNo) { mutableStateOf(initialAccountNo) }
    var signature by rememberSaveable(userKey) { mutableStateOf(initialSignature) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var identityChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var showNicknameEditor by rememberSaveable { mutableStateOf(false) }
    var showAgeEditor by rememberSaveable { mutableStateOf(false) }
    var showGenderEditor by rememberSaveable { mutableStateOf(false) }
    var showProvinceEditor by rememberSaveable { mutableStateOf(false) }
    var showCityEditor by rememberSaveable { mutableStateOf(false) }
    var showAccountNoEditor by rememberSaveable { mutableStateOf(false) }
    var showSignatureEditor by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showBirthdayPicker by rememberSaveable { mutableStateOf(false) }
    var privacyOn by rememberSaveable(userKey) { mutableStateOf(prefs.getBoolean("privacy_$userKey", false)) }
    var birthdaySettings by remember(userKey) { mutableStateOf(plannerStore.birthdaySettings()) }
    var deleteSeconds by remember { mutableIntStateOf(15) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onAvatar) }
    val dirty = nickname != initialName || age != initialAge || birthDate != initialBirthDate ||
        gender != initialGender || province != initialProvince || city != initialCity ||
        accountNo != initialAccountNo || signature != initialSignature
    val genderLabel = when (gender) {
        "female" -> "女"
        "male" -> "男"
        "other" -> "其他"
        else -> "不公开"
    }
    val ageLabel = age.ifBlank { "--" } + "岁"
    val birthdayLabel = birthDate.toBirthdayLabel()
    val constellation = birthDate.toConstellation()
    val locationLabel = listOf(province, city).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "未设置" }
    val createdLabel = profile?.createdAt?.toDateLabel() ?: "首次使用时记录"
    val usingDays = profile?.createdAt?.let { Duration.between(Instant.ofEpochMilli(it), Instant.now()).toDays().coerceAtLeast(0) + 1 } ?: 1
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                nickname.ifBlank { "绒绒用户" },
                                fontWeight = FontWeight.Black,
                                fontSize = 26.sp,
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            GenderMark(gender)
                        }
                        Text("让每一次记录更贴近自己～", color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TinyPill(if (privacyOn) "**岁" else ageLabel, palette.rose)
                            TinyPill(genderLabel, palette.rose)
                            TinyPill(if (privacyOn) "地区已隐藏" else locationLabel, palette.moss)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("生日  ${if (privacyOn) "**月**日" else birthdayLabel}", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("用户ID  ${accountNo.ifBlank { "--" }}", color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("地区  ${if (privacyOn) "已隐藏" else locationLabel}", color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    MascotArt(86.dp, R.drawable.mascot_action_heart)
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
                ProfileListRow(Icons.Default.Badge, "用户ID", accountNo.ifBlank { "未设置" }, palette.rose, onClick = {
                    editMode = true
                    showAccountNoEditor = true
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
                BirthdayProfileCard(
                    birthDate = birthDate,
                    privacyOn = privacyOn,
                    settings = birthdaySettings,
                    onModeChange = { mode ->
                        val updated = birthdaySettings.copy(calendarMode = mode)
                        birthdaySettings = updated
                        plannerStore.saveBirthdaySettings(updated)
                    },
                    onPickDate = {
                        editMode = true
                        showBirthdayPicker = true
                    },
                    onReminderChange = { enabled ->
                        val updated = birthdaySettings.copy(reminderEnabled = enabled)
                        birthdaySettings = updated
                        plannerStore.saveBirthdaySettings(updated)
                    },
                    onSyncChange = { enabled ->
                        val updated = birthdaySettings.copy(showInLifeCalendar = enabled)
                        birthdaySettings = updated
                        plannerStore.saveBirthdaySettings(updated)
                    }
                )
                ProfileDivider()
                ProfileListRow(Icons.Default.Home, "地区", if (privacyOn) "已隐藏" else locationLabel, palette.moss, onClick = {
                    editMode = true
                    showProvinceEditor = true
                })
                ProfileDivider()
                ProfileListRow(Icons.Default.Star, "星座", constellation, palette.lilac, enabled = false, onClick = {})
                ProfileDivider()
                ProfileListRow(Icons.Default.CalendarMonth, "注册时间 / 使用天数", "$createdLabel · 第 ${usingDays} 天", palette.coral, enabled = false, onClick = {})
                ProfileDivider()
                ProfileListRow(Icons.Default.ChatBubble, "个性签名", signature, palette.coral, onClick = {
                    editMode = true
                    showSignatureEditor = true
                })
            }
        }
        item {
            PlushCard {
                ProfileSectionTitle("账号信息")
                ProfileListRow(Icons.Default.Email, "邮箱", (profile?.email ?: state.session?.email ?: "本地账号").maskIf(privacyOn), palette.coral, enabled = remoteMode, onClick = {
                    identityChannel = "email"
                })
                if (BuildConfig.PHONE_AUTH_ENABLED) {
                    ProfileDivider()
                    ProfileListRow(
                        Icons.Default.Phone,
                        "手机号",
                        (profile?.phone ?: state.session?.phone ?: if (remoteMode) "未绑定" else "未绑定 · 可升级云账号").maskIf(privacyOn),
                        palette.blue,
                        enabled = true,
                        onClick = { identityChannel = "phone" }
                    )
                }
            }
        }
        item {
            PlushCard {
                ProfileSectionTitle("其他功能")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ProfileShortcut(Icons.Default.Lock, "资料隐私", palette.lilac, Modifier.weight(1f)) { showPrivacy = true }
                    if (remoteMode) {
                        Box(Modifier.width(1.dp).height(74.dp).background(palette.border))
                        ProfileShortcut(Icons.Default.Security, "修改密码", palette.rose, Modifier.weight(1f)) { showPassword = true }
                    }
                    Box(Modifier.width(1.dp).height(74.dp).background(palette.border))
                    ProfileShortcut(Icons.Default.DeleteForever, "注销账号", palette.coral, Modifier.weight(1f)) { showDelete = true }
                }
            }
        }
        item {
            ProfileWarmPanel(padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("完善个人信息，体验会更完整哦～", color = palette.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MascotArt(72.dp, R.drawable.mascot_action_like)
                }
            }
        }
        if (editMode) {
            item {
                PlushButton("保存修改", Icons.Default.Save, Modifier.fillMaxWidth(), enabled = dirty) {
                    prefs.edit().putString("signature_$userKey", signature).apply()
                    onSave(nickname, age, birthDate.ifBlank { null }, gender, province, city, accountNo)
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
    if (showProvinceEditor) {
        RegionEditDialog(
            province = province,
            city = city,
            onDismiss = { showProvinceEditor = false },
            onConfirm = { nextProvince, nextCity ->
                province = nextProvince
                city = nextCity
                showProvinceEditor = false
            }
        )
    }
    if (showAccountNoEditor) {
        ProfileTextEditDialog(
            title = "修改用户ID",
            value = accountNo,
            label = "6-8 位英文字母，一年最多改 2 次",
            maxLength = 8,
            filter = { it.filter { ch -> ch in 'A'..'Z' || ch in 'a'..'z' } },
            isValid = { it.length in 6..8 && it.all { ch -> ch in 'A'..'Z' || ch in 'a'..'z' } },
            errorText = "用户ID只能是 6-8 位英文字母，不能包含数字、空格或符号。",
            onDismiss = { showAccountNoEditor = false },
            onConfirm = {
                val nextAccountNo = it.trim()
                accountNo = nextAccountNo
                prefs.edit().putString("signature_$userKey", signature).apply()
                onSave(nickname, age, birthDate.ifBlank { null }, gender, province, city, nextAccountNo)
                editMode = false
                showAccountNoEditor = false
            }
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
    if (showBirthdayPicker) {
        BirthdayPickerDialog(
            initial = runCatching { LocalDate.parse(birthDate) }.getOrDefault(LocalDate.now().minusYears(20)),
            initialMode = birthdaySettings.calendarMode,
            onDismiss = { showBirthdayPicker = false },
            onConfirm = { solarDate, mode ->
                birthDate = solarDate.toString()
                age = Period.between(solarDate, LocalDate.now()).years.coerceAtLeast(0).toString()
                val updated = birthdaySettings.copy(calendarMode = mode)
                birthdaySettings = updated
                plannerStore.saveBirthdaySettings(updated)
                showBirthdayPicker = false
            }
        )
    }
    identityChannel?.let { channel ->
        IdentityChangeDialog(
            channel = channel,
            isAccountUpgrade = !remoteMode && channel == "phone",
            cooldown = state.otpCooldown,
            busy = state.isBusy,
            onDismiss = { identityChannel = null },
            onSend = {
                if (!remoteMode && channel == "phone") onSendPhoneUpgradeCode(it)
                else onSendIdentityCode(channel, it)
            },
            onVerify = { value, code ->
                if (!remoteMode && channel == "phone") onVerifyPhoneUpgrade(value, code)
                else onVerifyIdentity(channel, value, code)
            }
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
private fun BirthdayProfileCard(
    birthDate: String,
    privacyOn: Boolean,
    settings: BirthdaySettings,
    onModeChange: (String) -> Unit,
    onPickDate: () -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onSyncChange: (Boolean) -> Unit
) {
    val palette = LocalPlushPalette.current
    val date = runCatching { LocalDate.parse(birthDate) }.getOrNull()
    val solarLabel = date?.toString()?.toBirthdayLabel() ?: "未设置"
    val lunarLabel = date?.lunarLabel() ?: "未设置"
    Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFF8EC), border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = palette.rose.copy(alpha = 0.13f)) {
                    Icon(Icons.Default.Cake, null, tint = palette.rose, modifier = Modifier.padding(9.dp).size(22.dp))
                }
                Spacer(Modifier.width(11.dp))
                Text("生日", modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Surface(shape = RoundedCornerShape(22.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
                    Row(Modifier.padding(3.dp)) {
                        BirthdayModeButton("阳历", settings.calendarMode == "solar") { onModeChange("solar") }
                        BirthdayModeButton("农历", settings.calendarMode == "lunar") { onModeChange("lunar") }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onPickDate),
                shape = RoundedCornerShape(18.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = palette.coral, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (privacyOn) "**月**日" else if (settings.calendarMode == "lunar") "农历 $lunarLabel" else solarLabel,
                            color = palette.ink,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                        if (!privacyOn && date != null && settings.calendarMode == "lunar") {
                            Text("对应公历 $solarLabel", color = palette.muted, fontSize = 12.sp)
                        } else Text("点这里选择生日", color = palette.muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = palette.muted)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    ProfileListRow(Icons.Default.Notifications, "生日提醒", if (settings.reminderEnabled) "提前 ${settings.reminderDays} 天应用内提示" else "已关闭", palette.coral, onClick = { onReminderChange(!settings.reminderEnabled) })
    ProfileDivider()
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = palette.moss.copy(alpha = 0.13f)) {
            Icon(Icons.Default.CalendarMonth, null, tint = palette.moss, modifier = Modifier.padding(9.dp).size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("重要日子同步到生活日历", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("生日将在首页日历和纪念日中显示", color = palette.muted, fontSize = 11.sp)
        }
        Switch(checked = settings.showInLifeCalendar, onCheckedChange = onSyncChange)
    }
}

@Composable
private fun BirthdayModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = if (selected) palette.rose else Color.Transparent) {
        Text(label, Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = if (selected) Color.White else palette.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun BirthdayPickerDialog(
    initial: LocalDate,
    initialMode: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, String) -> Unit
) {
    val palette = LocalPlushPalette.current
    var mode by rememberSaveable { mutableStateOf(initialMode.takeIf { it == "lunar" } ?: "solar") }
    var yearText by rememberSaveable { mutableStateOf(initial.year.toString()) }
    var monthText by rememberSaveable {
        mutableStateOf(if (mode == "lunar") initial.toLunarDate().month.toString() else initial.monthValue.toString())
    }
    var dayText by rememberSaveable {
        mutableStateOf(if (mode == "lunar") initial.toLunarDate().day.toString() else initial.dayOfMonth.toString())
    }
    fun resolvedSolar(): LocalDate? {
        val year = yearText.toIntOrNull() ?: return null
        val month = monthText.toIntOrNull() ?: return null
        val day = dayText.toIntOrNull() ?: return null
        return if (mode == "solar") {
            runCatching { LocalDate.of(year, month, day) }.getOrNull()
        } else lunarToSolarDate(year, month, day)
    }
    fun switchMode(next: String) {
        val solar = resolvedSolar() ?: initial
        mode = next
        if (next == "solar") {
            yearText = solar.year.toString()
            monthText = solar.monthValue.toString()
            dayText = solar.dayOfMonth.toString()
        } else {
            val lunar = solar.toLunarDate()
            yearText = solar.year.toString()
            monthText = lunar.month.toString()
            dayText = lunar.day.toString()
        }
    }
    val solar = resolvedSolar()
    val counterpart = if (mode == "solar") {
        solar?.let { "对应农历 ${it.toLunarDate().display()}" } ?: "请检查阳历日期"
    } else {
        solar?.let { "对应公历 ${it.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}" } ?: "请检查农历日期"
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFFFFCF7),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.border),
            shadowElevation = 16.dp
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("选择生日", modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Black, fontSize = 23.sp)
                    MascotArt(50.dp, R.drawable.mascot_action_heart)
                }
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
                    Row(Modifier.padding(4.dp)) {
                        BirthdayModeButton("阳历", mode == "solar") { switchMode("solar") }
                        BirthdayModeButton("农历", mode == "lunar") { switchMode("lunar") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BirthdayNumberField("年", yearText, { yearText = it.filter(Char::isDigit).take(4) }, Modifier.weight(1.2f))
                    BirthdayNumberField("月", monthText, { monthText = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f))
                    BirthdayNumberField("日", dayText, { dayText = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f))
                }
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF4E4), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFDEB8))) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = palette.rose)
                        Spacer(Modifier.width(10.dp))
                        Text(counterpart, color = if (solar == null) palette.coral else palette.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消", color = palette.muted) }
                    TextButton(
                        enabled = solar != null,
                        onClick = { solar?.let { onConfirm(it, mode) } }
                    ) { Text("保存生日", color = palette.rose, fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
private fun BirthdayNumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp)
    )
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
private fun RegionEditDialog(
    province: String,
    city: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val initialProvince = province.takeIf { it in regionPickerCities.keys } ?: regionPickerCities.keys.first()
    var nextProvince by rememberSaveable { mutableStateOf(initialProvince) }
    val cityOptions = regionPickerCities[nextProvince].orEmpty()
    var nextCity by rememberSaveable(nextProvince) {
        mutableStateOf(city.takeIf { it in cityOptions }.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置地区", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(regionPickerCities.keys.toList()) { item ->
                            RegionPickerChip(item, item == nextProvince) {
                                nextProvince = item
                                nextCity = ""
                            }
                        }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            RegionPickerChip("不选城市", nextCity.isBlank()) { nextCity = "" }
                        }
                        items(cityOptions) { item ->
                            RegionPickerChip(item, item == nextCity) { nextCity = item }
                        }
                    }
                }
                Text("地区会保存到资料数据库，并用于分享卡片优先匹配城市、再匹配省份。", color = LocalPlushPalette.current.muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(nextProvince, nextCity) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RegionPickerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) palette.rose.copy(alpha = 0.14f) else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) palette.rose else palette.border)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = if (selected) palette.rose else palette.ink,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

private val regionPickerCities = linkedMapOf(
    "北京" to listOf("北京市"),
    "天津" to listOf("天津市"),
    "河北" to listOf("石家庄", "唐山", "秦皇岛", "邯郸", "邢台", "保定", "张家口", "承德", "沧州", "廊坊", "衡水"),
    "山西" to listOf("太原", "大同", "阳泉", "长治", "晋城", "朔州", "晋中", "运城", "忻州", "临汾", "吕梁"),
    "内蒙古" to listOf("呼和浩特", "包头", "乌海", "赤峰", "通辽", "鄂尔多斯", "呼伦贝尔", "巴彦淖尔", "乌兰察布", "兴安盟", "锡林郭勒盟", "阿拉善盟"),
    "辽宁" to listOf("沈阳", "大连", "鞍山", "抚顺", "本溪", "丹东", "锦州", "营口", "阜新", "辽阳", "盘锦", "铁岭", "朝阳", "葫芦岛"),
    "吉林" to listOf("长春", "吉林", "四平", "辽源", "通化", "白山", "松原", "白城", "延边朝鲜族自治州"),
    "黑龙江" to listOf("哈尔滨", "齐齐哈尔", "鸡西", "鹤岗", "双鸭山", "大庆", "伊春", "佳木斯", "七台河", "牡丹江", "黑河", "绥化", "大兴安岭地区"),
    "上海" to listOf("上海市"),
    "江苏" to listOf("南京", "无锡", "徐州", "常州", "苏州", "南通", "连云港", "淮安", "盐城", "扬州", "镇江", "泰州", "宿迁"),
    "浙江" to listOf("杭州", "宁波", "温州", "嘉兴", "湖州", "绍兴", "金华", "衢州", "舟山", "台州", "丽水"),
    "安徽" to listOf("合肥", "芜湖", "蚌埠", "淮南", "马鞍山", "淮北", "铜陵", "安庆", "黄山", "滁州", "阜阳", "宿州", "六安", "亳州", "池州", "宣城"),
    "福建" to listOf("福州", "厦门", "莆田", "三明", "泉州", "漳州", "南平", "龙岩", "宁德"),
    "江西" to listOf("南昌", "景德镇", "萍乡", "九江", "新余", "鹰潭", "赣州", "吉安", "宜春", "抚州", "上饶"),
    "山东" to listOf("济南", "青岛", "淄博", "枣庄", "东营", "烟台", "潍坊", "济宁", "泰安", "威海", "日照", "临沂", "德州", "聊城", "滨州", "菏泽"),
    "河南" to listOf("郑州", "开封", "洛阳", "平顶山", "安阳", "鹤壁", "新乡", "焦作", "濮阳", "许昌", "漯河", "三门峡", "南阳", "商丘", "信阳", "周口", "驻马店", "济源"),
    "湖北" to listOf("武汉", "黄石", "十堰", "宜昌", "襄阳", "鄂州", "荆门", "孝感", "荆州", "黄冈", "咸宁", "随州", "恩施土家族苗族自治州", "仙桃", "潜江", "天门", "神农架林区"),
    "湖南" to listOf("长沙", "株洲", "湘潭", "衡阳", "邵阳", "岳阳", "常德", "张家界", "益阳", "郴州", "永州", "怀化", "娄底", "湘西土家族苗族自治州"),
    "广东" to listOf("广州", "深圳", "珠海", "汕头", "佛山", "韶关", "湛江", "肇庆", "江门", "茂名", "惠州", "梅州", "汕尾", "河源", "阳江", "清远", "东莞", "中山", "潮州", "揭阳", "云浮"),
    "广西" to listOf("南宁", "柳州", "桂林", "梧州", "北海", "防城港", "钦州", "贵港", "玉林", "百色", "贺州", "河池", "来宾", "崇左"),
    "海南" to listOf("海口", "三亚", "三沙", "儋州", "五指山", "琼海", "文昌", "万宁", "东方", "定安", "屯昌", "澄迈", "临高", "白沙黎族自治县", "昌江黎族自治县", "乐东黎族自治县", "陵水黎族自治县", "保亭黎族苗族自治县", "琼中黎族苗族自治县"),
    "重庆" to listOf("重庆市"),
    "四川" to listOf("成都", "自贡", "攀枝花", "泸州", "德阳", "绵阳", "广元", "遂宁", "内江", "乐山", "南充", "眉山", "宜宾", "广安", "达州", "雅安", "巴中", "资阳", "阿坝藏族羌族自治州", "甘孜藏族自治州", "凉山彝族自治州"),
    "贵州" to listOf("贵阳", "六盘水", "遵义", "安顺", "毕节", "铜仁", "黔西南布依族苗族自治州", "黔东南苗族侗族自治州", "黔南布依族苗族自治州"),
    "云南" to listOf("昆明", "曲靖", "玉溪", "保山", "昭通", "丽江", "普洱", "临沧", "楚雄彝族自治州", "红河哈尼族彝族自治州", "文山壮族苗族自治州", "西双版纳傣族自治州", "大理白族自治州", "德宏傣族景颇族自治州", "怒江傈僳族自治州", "迪庆藏族自治州"),
    "西藏" to listOf("拉萨", "日喀则", "昌都", "林芝", "山南", "那曲", "阿里地区"),
    "陕西" to listOf("西安", "铜川", "宝鸡", "咸阳", "渭南", "延安", "汉中", "榆林", "安康", "商洛"),
    "甘肃" to listOf("兰州", "嘉峪关", "金昌", "白银", "天水", "武威", "张掖", "平凉", "酒泉", "庆阳", "定西", "陇南", "临夏回族自治州", "甘南藏族自治州"),
    "青海" to listOf("西宁", "海东", "海北藏族自治州", "黄南藏族自治州", "海南藏族自治州", "果洛藏族自治州", "玉树藏族自治州", "海西蒙古族藏族自治州"),
    "宁夏" to listOf("银川", "石嘴山", "吴忠", "固原", "中卫"),
    "新疆" to listOf("乌鲁木齐", "克拉玛依", "吐鲁番", "哈密", "昌吉回族自治州", "博尔塔拉蒙古自治州", "巴音郭楞蒙古自治州", "阿克苏地区", "克孜勒苏柯尔克孜自治州", "喀什地区", "和田地区", "伊犁哈萨克自治州", "塔城地区", "阿勒泰地区", "石河子", "阿拉尔", "图木舒克", "五家渠", "北屯", "铁门关", "双河", "可克达拉", "昆玉", "胡杨河", "新星", "白杨"),
    "台湾" to listOf("台北", "新北", "桃园", "台中", "台南", "高雄", "基隆", "新竹", "嘉义", "宜兰", "新竹县", "苗栗", "彰化", "南投", "云林", "嘉义县", "屏东", "台东", "花莲", "澎湖", "金门", "连江"),
    "香港" to listOf("香港岛", "九龙", "新界"),
    "澳门" to listOf("澳门半岛", "氹仔", "路环")
)

@Composable
private fun ProfileTextEditDialog(
    title: String,
    value: String,
    label: String,
    maxLength: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
    filter: (String) -> String = { it },
    isValid: (String) -> Boolean = { true },
    errorText: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var draft by rememberSaveable(title) { mutableStateOf(value) }
    val trimmedDraft = draft.trim()
    val valid = isValid(trimmedDraft)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    draft,
                    { draft = filter(it).take(maxLength) },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !valid && trimmedDraft.isNotEmpty(),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
                )
                if (!valid && trimmedDraft.isNotEmpty() && errorText.isNotBlank()) {
                    Text(errorText, color = LocalPlushPalette.current.coral, fontSize = 12.sp)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(trimmedDraft) }) { Text("确定") } }
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
private fun GenderMark(gender: String?) {
    val palette = LocalPlushPalette.current
    val spec = when (gender) {
        "male" -> "♂" to Color(0xFF3F8CF4)
        "female" -> "♀" to Color(0xFFFF7BAE)
        else -> return
    }
    Spacer(Modifier.width(6.dp))
    Surface(
        shape = CircleShape,
        color = spec.second.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, spec.second.copy(alpha = 0.28f))
    ) {
        Text(
            spec.first,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = spec.second,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private data class FootprintMetricItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val color: Color
)

internal enum class HeatmapPeriod(val label: String) {
    THREE_MONTHS("近3月"),
    SIX_MONTHS("近6月"),
    YEAR("全年")
}

internal fun heatmapVisibleStart(today: LocalDate, period: HeatmapPeriod): LocalDate = when (period) {
    HeatmapPeriod.THREE_MONTHS -> today.minusMonths(2).withDayOfMonth(1)
    HeatmapPeriod.SIX_MONTHS -> today.minusMonths(5).withDayOfMonth(1)
    HeatmapPeriod.YEAR -> today.withDayOfYear(1)
}

@Composable
private fun LedgerFootprintCard(ledger: LedgerState, ledgerDays: Int, monthCount: Int) {
    val palette = LocalPlushPalette.current
    val transactions = ledger.transactions
    val totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amountMinor }
    val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amountMinor }
    val streaks = remember(transactions) { ledgerStreaks(transactions.map { it.localDateForProfile() }) }
    var showHeatmap by rememberSaveable { mutableStateOf(false) }
    var heatmapPeriodName by rememberSaveable { mutableStateOf(HeatmapPeriod.THREE_MONTHS.name) }
    var selectedHeatmapDate by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHeatmapCount by rememberSaveable { mutableIntStateOf(0) }
    val heatmapPeriod = HeatmapPeriod.valueOf(heatmapPeriodName)
    val metrics = listOf(
        FootprintMetricItem(Icons.Default.EditNote, "累计记账", "${transactions.size} 笔", palette.rose),
        FootprintMetricItem(Icons.Default.Paid, "累计支出", compactCny(totalExpense), palette.coral),
        FootprintMetricItem(Icons.Default.Favorite, "累计收入", compactCny(totalIncome), palette.moss),
        FootprintMetricItem(Icons.Default.CalendarMonth, "本月记录", "$monthCount 笔", palette.blue),
        FootprintMetricItem(Icons.Default.TouchApp, "累计天数", "$ledgerDays 天", palette.rose),
        FootprintMetricItem(Icons.Default.EmojiEvents, "最长连续", "${streaks.longest} 天", Color(0xFFFFB24A))
    )
    ProfileWarmPanel(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("记账足迹", color = palette.ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("每一笔，都在慢慢变成生活的轨迹", color = palette.muted, fontSize = 12.sp)
            }
            MascotArt(58.dp, R.drawable.mascot_action_wave)
        }
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints {
            val columns = if (maxWidth < 390.dp) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.chunked(columns).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { item ->
                            FootprintMetric(item.icon, item.label, item.value, item.color, Modifier.weight(1f))
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.72f),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("记账热力图", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (showHeatmap) {
                        HeatmapPeriod.entries.forEach { period ->
                            HeatmapPeriodButton(
                                period = period,
                                selected = period == heatmapPeriod,
                                onClick = {
                                    heatmapPeriodName = period.name
                                    selectedHeatmapDate = null
                                }
                            )
                            if (period != HeatmapPeriod.YEAR) Spacer(Modifier.width(4.dp))
                        }
                    } else {
                        HeatmapToggle("展开") { showHeatmap = true }
                    }
                }
                if (showHeatmap) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selectedHeatmapDate?.let {
                                val date = LocalDate.parse(it)
                                "${date.format(DateTimeFormatter.ofPattern("M月d日"))} · $selectedHeatmapCount 笔"
                            } ?: "点格子查看日期和记录笔数",
                            color = palette.muted,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )
                        HeatmapToggle("收起") { showHeatmap = false }
                    }
                    Spacer(Modifier.height(12.dp))
                    ActivityHeatmap(
                        transactions = transactions,
                        period = heatmapPeriod,
                        selectedDate = selectedHeatmapDate?.let(LocalDate::parse),
                        onCellClick = { date, count ->
                            selectedHeatmapDate = date.toString()
                            selectedHeatmapCount = count
                        }
                    )
                    Spacer(Modifier.height(9.dp))
                    Text("左右滑动查看更多日期，数据随账目实时变化。", color = palette.muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun HeatmapPeriodButton(period: HeatmapPeriod, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) palette.rose.copy(alpha = 0.14f) else Color(0xFFF6F4F1),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, palette.rose.copy(alpha = 0.42f)) else null
    ) {
        Text(
            period.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = if (selected) palette.rose else palette.ink,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HeatmapToggle(label: String, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Text(
        label,
        color = palette.rose,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun FootprintMetric(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlushBadge(icon, color, 34.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(label, color = palette.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    value,
                    color = palette.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LedgerGentleSummary(
    transactions: List<com.plushledger.data.TransactionEntity>,
    ledgerDays: Int,
    monthCount: Int,
    streaks: LedgerStreaks
) {
    val palette = LocalPlushPalette.current
    val latest = transactions.maxByOrNull { it.occurredAt }
    val latestLabel = latest?.localDateForProfile()?.format(DateTimeFormatter.ofPattern("M月d日")) ?: "还没有"
    val summary = when {
        transactions.isEmpty() -> "第一笔账还没来，等你愿意开始的时候再写也可以。"
        monthCount == 0 -> "已经在 $ledgerDays 天里留下 ${transactions.size} 笔记录，这个月可以慢慢重新接上。"
        else -> "已经在 $ledgerDays 天里留下 ${transactions.size} 笔记录，本月新增 $monthCount 笔。"
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.86f),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最近小结", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TinyPill("实时", palette.rose)
            }
            Text(summary, color = palette.muted, fontSize = 12.sp, lineHeight = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GentleStatPill("最近记录", latestLabel, palette.blue, Modifier.weight(1f))
                GentleStatPill("当前连续", "${streaks.current} 天", palette.moss, Modifier.weight(1f))
                GentleStatPill("本月记录", "$monthCount 笔", palette.rose, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GentleStatPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.11f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = palette.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                value,
                color = palette.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun compactCny(amountMinor: Long): String {
    val yuan = amountMinor / 100.0
    return when {
        kotlin.math.abs(yuan) >= 10000 -> "¥" + String.format(java.util.Locale.CHINA, "%.2f万", yuan / 10000.0)
        amountMinor % 100L == 0L -> "¥" + String.format(java.util.Locale.CHINA, "%,.0f", yuan)
        else -> "¥" + String.format(java.util.Locale.CHINA, "%,.2f", yuan)
    }
}

private fun LocalDate.weekStart(): LocalDate =
    minusDays((dayOfWeek.value - 1).toLong())

@Composable
private fun ActivityHeatmap(
    transactions: List<com.plushledger.data.TransactionEntity>,
    period: HeatmapPeriod,
    selectedDate: LocalDate?,
    onCellClick: (LocalDate, Int) -> Unit
) {
    val palette = LocalPlushPalette.current
    val today = LocalDate.now()
    val visibleStart = heatmapVisibleStart(today, period)
    val gridStart = visibleStart.weekStart()
    val columns = ((ChronoUnit.DAYS.between(gridStart, today) + 1L + 6L) / 7L).toInt()
    val monthLabels = generateSequence(visibleStart.withDayOfMonth(1)) { it.plusMonths(1) }
        .takeWhile { !it.isAfter(today) }
        .map { monthStart ->
            val column = (ChronoUnit.DAYS.between(gridStart, monthStart) / 7L).toInt().coerceIn(0, columns - 1)
            column to monthStart.format(DateTimeFormatter.ofPattern("M月"))
        }
        .toMap()
    val counts = transactions
        .map { it.localDateForProfile() }
        .filter { !it.isBefore(visibleStart) && !it.isAfter(today) }
        .groupingBy { it }
        .eachCount()
    val maxCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val scrollState = rememberScrollState()
    LaunchedEffect(period, scrollState.maxValue) {
        if (scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
    }
    Row(verticalAlignment = Alignment.Top) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(label, color = palette.muted, fontSize = 10.sp, modifier = Modifier.width(18.dp).height(13.dp))
            }
            Spacer(Modifier.height(14.dp))
        }
        Column(
            modifier = Modifier.horizontalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(7) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(columns) { column ->
                    val date = gridStart.plusDays((column * 7 + row).toLong())
                    val count = counts[date] ?: 0
                    val inRange = !date.isBefore(visibleStart) && !date.isAfter(today)
                    val isMonthStart = inRange && date.dayOfMonth == 1
                    val isSelected = inRange && date == selectedDate
                    Surface(
                        modifier = Modifier
                            .size(13.dp)
                            .then(if (inRange) Modifier.clickable { onCellClick(date, count) } else Modifier),
                        shape = RoundedCornerShape(3.dp),
                        color = heatmapActivityColor(palette.rose, count, maxCount, inRange),
                        border = when {
                            isSelected -> androidx.compose.foundation.BorderStroke(1.4.dp, palette.rose)
                            isMonthStart -> androidx.compose.foundation.BorderStroke(0.7.dp, Color(0xFFB8BEC6))
                            else -> null
                        }
                    ) {}
                }
            }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(columns) { column ->
                    Text(
                        monthLabels[column].orEmpty(),
                        color = palette.muted,
                        fontSize = 9.sp,
                        modifier = Modifier.width(13.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}

private fun heatmapActivityColor(accent: Color, count: Int, maxCount: Int, inRange: Boolean): Color {
    if (!inRange) return Color(0xFFF5F6F8)
    if (count <= 0) return Color(0xFFEDEFF2)
    val intensity = (count.toFloat() / maxCount.coerceAtLeast(1)).coerceIn(0f, 1f)
    val light = blendProfileColor(accent, Color.White, 0.78f)
    val strong = blendProfileColor(accent, Color.Black, 0.12f)
    return blendProfileColor(light, strong, 0.22f + 0.78f * intensity)
}

private fun blendProfileColor(start: Color, end: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}

private data class LedgerStreaks(val current: Int, val longest: Int)

private fun ledgerStreaks(dates: List<LocalDate>): LedgerStreaks {
    val days = dates.distinct().sorted()
    if (days.isEmpty()) return LedgerStreaks(0, 0)
    var longest = 1
    var run = 1
    for (index in 1 until days.size) {
        run = if (ChronoUnit.DAYS.between(days[index - 1], days[index]) == 1L) run + 1 else 1
        if (run > longest) longest = run
    }
    val today = LocalDate.now()
    val latest = days.last()
    val current = if (ChronoUnit.DAYS.between(latest, today) <= 1L) {
        var cursor = latest
        var count = 0
        val set = days.toSet()
        while (cursor in set) {
            count++
            cursor = cursor.minusDays(1)
        }
        count
    } else {
        0
    }
    return LedgerStreaks(current, longest)
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
private fun SettingsScreen(
    state: UiState,
    biometricAvailable: Boolean,
    viewModel: LedgerViewModel,
    onBack: () -> Unit,
    onBudget: () -> Unit,
    onCategory: () -> Unit,
    onInbox: () -> Unit,
    onAbout: () -> Unit
) {
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
    var showFontScale by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showBillImportSource by rememberSaveable { mutableStateOf(false) }
    var billImportProvider by rememberSaveable { mutableStateOf("绒绒备份") }
    var showCache by rememberSaveable { mutableStateOf(false) }
    var showLicense by rememberSaveable { mutableStateOf(false) }
    var reminderEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("ledger_reminder", true)) }
    var currency by rememberSaveable { mutableStateOf(prefs.getString("currency_unit", "人民币  ¥") ?: "人民币  ¥") }
    var downloadLine by rememberSaveable { mutableStateOf(prefs.getString("download_line", "国内优先") ?: "国内优先") }
    var appFontScale by rememberSaveable { mutableStateOf(prefs.getFloat("app_font_scale", 1f)) }
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
                    MascotArt(82.dp, R.drawable.mascot_action_wave)
                }
            }
        }
        item {
            PlushCard {
                ProfileSectionTitle("常用入口")
                ActionRow(Icons.Default.Paid, "预算管理", "管理月度预算和提醒", palette.rose, onBudget)
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                ActionRow(Icons.Default.Badge, "分类管理", "调整支出、收入分类", palette.moss, onCategory)
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                ActionRow(Icons.Default.Notifications, "通知提醒", if (state.officialMessages.isEmpty()) "已开启" else "${state.officialMessages.size} 条消息", palette.coral, onInbox)
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                ActionRow(Icons.Default.AccountCircle, "关于我们", "联系与产品信息", palette.blue, onAbout)
            }
        }
        item {
            PlushCard {
                ProfileSectionTitle("通用设置")
                SettingsValueRow(Icons.Default.Palette, "主题", plushThemeName(state.themeTone), palette.rose) { showTheme = true }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                SettingsValueRow(Icons.Default.Style, "界面字号", appFontScaleLabel(appFontScale), palette.blue) { showFontScale = true }
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
                ActionRow(Icons.Default.CreditCard, "账单智能导入", "导入绒绒备份、微信或支付宝账单", palette.blue) { showBillImportSource = true }
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
                    MascotArt(74.dp, R.drawable.mascot_action_wave)
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
                billImportLauncher.launch(arrayOf("text/*", "application/json", "application/csv", "application/vnd.ms-excel"))
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
    if (showFontScale) {
        FontScaleDialog(
            current = appFontScale,
            onDismiss = { showFontScale = false },
            onChoose = { scale ->
                appFontScale = scale
                prefs.edit().putFloat("app_font_scale", scale).apply()
                showFontScale = false
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
            val success = runCatching { clearTemporaryFiles(context) }.getOrDefault(false)
            Toast.makeText(context, if (success) "缓存已清理" else "缓存清理完成", Toast.LENGTH_SHORT).show()
            showCache = false
        }
    }
    if (showLicense) {
        LicenseDialog { showLicense = false }
    }
}

private fun clearTemporaryFiles(context: Context): Boolean {
    var success = true
    success = runCatching {
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
    }.getOrDefault(false) && success
    val updateDirs = listOfNotNull(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        File(context.filesDir, "updates")
    )
    updateDirs.forEach { directory ->
        directory.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith("rongrong-ledger-") &&
                    file.extension.equals("apk", ignoreCase = true)
            }
            ?.forEach { file ->
                success = runCatching { file.delete() }.getOrDefault(false) && success
            }
    }
    return success
}

private fun appFontScaleLabel(scale: Float): String = when {
    scale < 0.82f -> "细"
    scale < 0.95f -> "偏细"
    else -> "标准"
}

@Composable
private fun FontScaleDialog(current: Float, onDismiss: () -> Unit, onChoose: (Float) -> Unit) {
    val palette = LocalPlushPalette.current
    val options = listOf(
        Triple(0.76f, "细", "适合显示偏大的手机，文字和行距都会明显收紧"),
        Triple(0.88f, "偏细", "比标准小一档，减少换行但仍保持舒适间距"),
        Triple(1.0f, "标准", "保持默认观感，不提供放大字号")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("界面字号", fontWeight = FontWeight.Black, color = palette.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (scale, label, desc) ->
                    val selected = kotlin.math.abs(current - scale) < 0.01f
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onChoose(scale) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) palette.blue.copy(alpha = 0.10f) else Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) palette.blue else palette.border)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlushBadge(Icons.Default.Style, palette.blue, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, color = palette.ink, fontWeight = FontWeight.Black)
                                Text(desc, color = palette.muted, fontSize = 11.sp)
                            }
                            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "已选择", tint = palette.blue)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成", color = palette.rose) } },
        containerColor = palette.surface
    )
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
    isAccountUpgrade: Boolean = false,
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
        title = {
            Text(
                when {
                    isEmail -> "换绑邮箱"
                    isAccountUpgrade -> "绑定手机号并开启云同步"
                    else -> "换绑手机号"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isEmail) "请输入新邮箱收到的验证码后完成换绑。若邮件仍显示确认链接，需要先把 Supabase 邮件模板改为验证码。"
                    else if (isAccountUpgrade) {
                        "验证成功后会创建或进入对应的手机号云账号，并把当前本地账本安全合并过去。原本地数据仍会保留。"
                    } else {
                        "选择区号后输入日常手机号即可，默认中国大陆 +86。"
                    },
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
                Text(if (isAccountUpgrade) "确认绑定" else "确认换绑")
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
                        MascotArt(72.dp, R.drawable.mascot_action_coin)
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
    val seed = day.toEpochDay().toInt() xor 0x5F3759DF
    return quotes[kotlin.random.Random(seed).nextInt(quotes.size)]
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

private fun String.toConstellation(): String {
    val date = runCatching { LocalDate.parse(this) }.getOrNull() ?: return "生日设置后自动生成"
    val md = date.monthValue * 100 + date.dayOfMonth
    return when {
        md in 321..419 -> "白羊座"
        md in 420..520 -> "金牛座"
        md in 521..621 -> "双子座"
        md in 622..722 -> "巨蟹座"
        md in 723..822 -> "狮子座"
        md in 823..922 -> "处女座"
        md in 923..1023 -> "天秤座"
        md in 1024..1122 -> "天蝎座"
        md in 1123..1221 -> "射手座"
        md >= 1222 || md <= 119 -> "摩羯座"
        md in 120..218 -> "水瓶座"
        else -> "双鱼座"
    }
}

private fun Long.toDateLabel(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日"))

private fun profileAccountNo(profile: com.plushledger.data.ProfileEntity?, userKey: String): String =
    profile?.accountNo?.takeIf { it.isNotBlank() }
        ?: defaultProfileAccountNo(userKey)

private fun defaultProfileAccountNo(userKey: String): String =
    buildString {
        append("RR")
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val source = userKey.filter(Char::isLetter).uppercase(java.util.Locale.ROOT)
        append(source.take(6))
        var seed = userKey.fold(17) { acc, ch -> acc * 31 + ch.code }.toUInt().toLong()
        while (length < 8) {
            append(letters[(seed % letters.length).toInt()])
            seed = seed / letters.length + 11
        }
    }.take(8)

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
    else -> "永久用户"
}

private fun badgeColor(role: String?, tier: String?): Color = when {
    role == "admin" -> Color(0xFFD29A32)
    tier == "permanent" -> Color(0xFFC86F7E)
    else -> Color(0xFF7B6C70)
}
