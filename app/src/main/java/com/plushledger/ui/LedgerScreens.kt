package com.plushledger.ui

import android.app.TimePickerDialog
import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.plushledger.data.AccountEntity
import com.plushledger.data.AiLedgerAnalysis
import com.plushledger.R
import com.plushledger.data.CategoryEntity
import com.plushledger.data.CategorySpend
import com.plushledger.data.LedgerState
import com.plushledger.data.Money
import com.plushledger.data.TransactionEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    ledger: LedgerState,
    selectedDate: LocalDate,
    onMonth: (Long) -> Unit,
    onDate: (LocalDate) -> Unit,
    onDelete: (String) -> Unit,
    onRecord: () -> Unit,
    onBills: () -> Unit,
    aiSuggestions: List<AiLedgerAnalysis>,
    isAiAnalyzing: Boolean,
    onAnalyzeAi: (String) -> Unit,
    onSaveAi: (List<AiLedgerAnalysis>) -> Unit,
    onDismissAi: () -> Unit
) {
    val palette = LocalPlushPalette.current
    val context = LocalContext.current
    val plannerUserId = ledger.profile?.id ?: "local"
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var showAiDialog by rememberSaveable { mutableStateOf(false) }
    val aiDraftStore = remember(plannerUserId) { AiDraftStore(context.applicationContext, plannerUserId) }
    var aiText by rememberSaveable(plannerUserId) { mutableStateOf("") }
    var plannerPage by rememberSaveable { mutableStateOf<String?>(null) }
    when (plannerPage) {
        "calendar" -> {
            LifeCalendarScreen(plannerUserId, ledger.profile, onBack = { plannerPage = null })
            return
        }
        "wishes" -> {
            GiftWishScreen(plannerUserId, onBack = { plannerPage = null })
            return
        }
    }
    LaunchedEffect(aiSuggestions) {
        if (aiSuggestions.isNotEmpty()) {
            showAiDialog = false
        }
    }
    val month = YearMonth.from(selectedDate)
    val todayExpense = ledger.transactions.filter { it.type == "expense" && it.localDate() == selectedDate }.sumOf { it.amountMinor }
    val yesterdayExpense = ledger.transactions.filter { it.type == "expense" && it.localDate() == selectedDate.minusDays(1) }.sumOf { it.amountMinor }
    val dayDelta = kotlin.math.abs(todayExpense - yesterdayExpense)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BrandHeader(
                title = "绒绒记账",
                subtitle = "早上好，今天也要好好记录呀～",
                trailing = month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                onTrailingClick = { showCalendar = !showCalendar }
            )
        }
        if (showCalendar) {
            item {
                CalendarSelector(selectedDate, month, onMonth) {
                    onDate(it)
                    showCalendar = false
                }
            }
        }
        item {
            PlushCard(padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryNumber("本月支出", ledger.summary.expenseMinor, palette.rose, Modifier.weight(1f))
                    SummaryNumber("本月收入", ledger.summary.incomeMinor, palette.moss, Modifier.weight(1f))
                    SummaryNumber("本月结余", ledger.summary.incomeMinor - ledger.summary.expenseMinor, palette.rose, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceAround) {
                    Image(
                        painterResource(R.drawable.art_expense_wallet),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Image(
                        painterResource(R.drawable.art_income_wallet),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Image(
                        painterResource(R.drawable.ic_launcher_transparent),
                        contentDescription = null,
                        modifier = Modifier.size(68.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        item {
            PlushCard(padding = 10.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(palette.rose.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = palette.rose)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("今日支出", color = palette.ink, fontWeight = FontWeight.Bold)
                        Text(
                            Money.formatCny(todayExpense),
                            color = palette.ink,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = palette.surfaceAlt) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (todayExpense <= yesterdayExpense) "比昨天少花了" else "比昨天多花了",
                                color = palette.ink,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                            Text(
                                "${Money.formatCny(dayDelta)} ${if (todayExpense <= yesterdayExpense) "↓" else "↑"}",
                                color = if (todayExpense <= yesterdayExpense) palette.moss else palette.rose,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Image(painterResource(R.drawable.art_plant), contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlushButton("记一笔", Icons.Default.EditNote, Modifier.weight(1f), onClick = onRecord)
                PlushButton(
                    "AI 记账",
                    Icons.Default.AutoAwesome,
                    Modifier.weight(1f),
                    color = palette.moss,
                    onClick = {
                        aiText = aiDraftStore.load()
                        onDismissAi()
                        showAiDialog = true
                    }
                )
            }
        }
        item {
            LifePlannerPreview(
                userId = plannerUserId,
                profile = ledger.profile,
                onOpenCalendar = { plannerPage = "calendar" },
                onOpenWishes = { plannerPage = "wishes" }
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("最近记录", Icons.Default.ReceiptLong)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBills) { Text("全部账单") }
            }
        }
        if (ledger.transactions.isEmpty()) item { EmptyPanel("还没有账目，记下今天的第一笔吧") }
        else item {
            PlushCard(padding = 10.dp) {
                val recent = ledger.transactions.take(6)
                recent.forEachIndexed { index, record ->
                    CompactTransactionRow(record, ledger.categories.associateBy { it.id }, onDelete)
                    if (index != recent.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                }
            }
        }
    }

    if (showAiDialog) {
        AiEntryDialog(
            text = aiText,
            analyzing = isAiAnalyzing,
            onTextChange = { aiText = it.take(160) },
            onDismiss = {
                if (!isAiAnalyzing) {
                    showAiDialog = false
                }
            },
            onAnalyze = { onAnalyzeAi(aiText) },
            onSaveDraft = { aiDraftStore.save(aiText) }
        )
    }

    if (aiSuggestions.isNotEmpty()) {
        AiBatchConfirmationDialog(
            suggestions = aiSuggestions,
            categories = ledger.categories,
            accounts = ledger.accounts,
            onDismiss = onDismissAi,
            onConfirm = { confirmed ->
                aiDraftStore.clear()
                aiText = ""
                onSaveAi(confirmed)
            }
        )
    }
}

@Composable
private fun AiEntryDialog(
    text: String,
    analyzing: Boolean,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAnalyze: () -> Unit,
    onSaveDraft: () -> Unit
) {
    val palette = LocalPlushPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(28.dp),
            color = palette.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.border),
            shadowElevation = 18.dp
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI 智能记账", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFC85A), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "例如“6月27日，买了一杯瑞幸咖啡，微信支付”。",
                    color = palette.muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = palette.surface,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFDFC0)),
                    shadowElevation = 5.dp
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        enabled = !analyzing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = palette.ink, fontSize = 16.sp, lineHeight = 22.sp),
                        decorationBox = { inner ->
                            if (text.isBlank()) Text("输入账目内容", color = palette.muted.copy(alpha = 0.72f), fontSize = 16.sp)
                            inner()
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (analyzing) {
                    AiAnalyzingIndicator()
                } else {
                    Text("识别结果会先由你确认，未确认不会写入账本。", color = palette.muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    MascotArt(58.dp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSaveDraft, enabled = text.isNotBlank() && !analyzing) {
                        Text("暂存", color = palette.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    TextButton(onClick = onDismiss, enabled = !analyzing) {
                        Text("取消", color = palette.pink, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable(enabled = text.isNotBlank() && !analyzing, onClick = onAnalyze),
                        shape = RoundedCornerShape(24.dp),
                        color = if (text.isNotBlank() && !analyzing) palette.moss else palette.moss.copy(alpha = 0.42f),
                        shadowElevation = 7.dp
                    ) {
                        Text(
                            if (analyzing) "正在识别" else "开始识别",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAnalyzingIndicator() {
    val palette = LocalPlushPalette.current
    val transition = rememberInfiniteTransition(label = "ai-analyzing")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "pulse"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFFC85A),
            modifier = Modifier.size(22.dp).scale(pulse).alpha(pulse)
        )
        Spacer(Modifier.width(10.dp))
        Text("绒绒正在理解日期、分类和账户", color = palette.moss, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        repeat(3) { index ->
            Box(
                Modifier.padding(horizontal = 2.dp).size(5.dp)
                    .scale((pulse - index * 0.08f).coerceAtLeast(0.62f))
                    .clip(CircleShape)
                    .background(palette.moss.copy(alpha = 0.55f + index * 0.12f))
            )
        }
    }
}

@Composable
private fun AiConfirmationDialog(
    suggestion: AiLedgerAnalysis,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (AiLedgerAnalysis) -> Unit
) {
    val palette = LocalPlushPalette.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    var type by rememberSaveable(suggestion.sourceText) { mutableStateOf(suggestion.type) }
    var amountText by rememberSaveable(suggestion.sourceText) {
        mutableStateOf(BigDecimal(suggestion.amountMinor).movePointLeft(2).stripTrailingZeros().toPlainString())
    }
    var dateText by rememberSaveable(suggestion.sourceText) {
        mutableStateOf(
            Instant.ofEpochMilli(suggestion.occurredAt).atZone(ZoneId.systemDefault())
                .format(dateFormatter)
        )
    }
    var categoryId by rememberSaveable(suggestion.sourceText) { mutableStateOf(suggestion.categoryId) }
    var accountId by rememberSaveable(suggestion.sourceText) { mutableStateOf(suggestion.accountId) }
    var note by rememberSaveable(suggestion.sourceText) { mutableStateOf(suggestion.note.ifBlank { suggestion.sourceText }) }
    val typeCategories = categories.filter { it.kind == type }
    val selectedCategory = typeCategories.firstOrNull { it.id == categoryId }
    val selectedAccount = accounts.firstOrNull { it.id == accountId }
    val parsedAmount = Money.parseToMinor(amountText)
    val parsedDate = runCatching {
        LocalDateTime.parse(dateText.trim(), dateFormatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
    val canConfirm = parsedAmount != null && parsedAmount > 0 && parsedDate != null && selectedCategory != null && selectedAccount != null

    LaunchedEffect(type) {
        if (typeCategories.none { it.id == categoryId }) {
            categoryId = typeCategories.firstOrNull { it.parentId != null }?.id ?: typeCategories.firstOrNull()?.id
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(28.dp),
            color = palette.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD58A)),
            shadowElevation = 20.dp
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("确认这笔账", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(suggestion.sourceText, color = palette.muted, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    MascotArt(70.dp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AiTypeChoice("支出", type == "expense", Modifier.weight(1f)) { type = "expense" }
                    AiTypeChoice("收入", type == "income", Modifier.weight(1f)) { type = "income" }
                }
                Spacer(Modifier.height(8.dp))
                AiEditableTextRow("金额", amountText, { amountText = it.filter { char -> char.isDigit() || char == '.' }.take(12) }, "例如 15.50", KeyboardType.Decimal)
                AiEditableTextRow("日期", dateText, { dateText = it.take(16) }, "yyyy-MM-dd HH:mm")
                AiEditableChoiceRow("分类", selectedCategory?.name ?: "请选择", typeCategories, { it.name }) { categoryId = it.id }
                AiEditableChoiceRow("账户", selectedAccount?.name ?: "请选择", accounts, { it.name }) { accountId = it.id }
                AiEditableTextRow("备注", note, { note = it.take(80) }, "备注")
                if (!canConfirm) {
                    Text("请检查金额、日期、分类和账户。", color = palette.coral, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = palette.muted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (suggestion.cloudAssisted) "已由已配置的 AI 服务辅助识别。" else "本次使用离线规则识别，不会上传文字。",
                        color = palette.muted,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = palette.pink, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable(enabled = canConfirm) {
                            val category = selectedCategory ?: return@clickable
                            val account = selectedAccount ?: return@clickable
                            onConfirm(
                                suggestion.copy(
                                    type = type,
                                    amountMinor = parsedAmount ?: return@clickable,
                                    categoryId = category.id,
                                    categoryLabel = category.name,
                                    accountId = account.id,
                                    accountLabel = account.name,
                                    note = note.trim(),
                                    occurredAt = parsedDate ?: return@clickable
                                )
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = if (canConfirm) palette.moss else palette.moss.copy(alpha = 0.42f),
                        shadowElevation = 7.dp
                    ) {
                        Text(
                            "确认记账",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiBatchConfirmationDialog(
    suggestions: List<AiLedgerAnalysis>,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (List<AiLedgerAnalysis>) -> Unit
) {
    if (suggestions.size == 1) {
        AiConfirmationDialog(
            suggestion = suggestions.first(),
            categories = categories,
            accounts = accounts,
            onDismiss = onDismiss,
            onConfirm = { onConfirm(listOf(it)) }
        )
        return
    }
    val palette = LocalPlushPalette.current
    val formatter = remember { DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA) }
    var drafts by remember(suggestions) { mutableStateOf(suggestions) }
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(30.dp),
            color = palette.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD58A)),
            shadowElevation = 20.dp
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("确认 ${drafts.size} 笔账", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 25.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("点击任意一条，可以修改金额、日期、分类和账户。", color = palette.muted, fontSize = 12.sp)
                    }
                    MascotArt(62.dp)
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    drafts.forEachIndexed { index, suggestion ->
                        val date = Instant.ofEpochMilli(suggestion.occurredAt)
                            .atZone(ZoneId.systemDefault())
                            .format(formatter)
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { editingIndex = index },
                            shape = RoundedCornerShape(18.dp),
                            color = if (suggestion.type == "income") palette.moss.copy(alpha = 0.11f) else Color(0xFFFFF7EC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = if (suggestion.type == "income") palette.moss else palette.rose) {
                                    Text(
                                        "${index + 1}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${if (suggestion.type == "income") "+" else "-"}${Money.formatCny(suggestion.amountMinor)}  ·  ${suggestion.categoryLabel}",
                                        color = palette.ink,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "$date  ·  ${suggestion.accountLabel}  ·  ${suggestion.note.ifBlank { suggestion.sourceText }}",
                                        color = palette.muted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "编辑", tint = palette.muted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = palette.muted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("未确认前不会写入账本。确认后会一次保存 ${drafts.size} 笔。", color = palette.muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = palette.pink, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable { onConfirm(drafts) },
                        shape = RoundedCornerShape(24.dp),
                        color = palette.moss,
                        shadowElevation = 7.dp
                    ) {
                        Text(
                            "确认全部",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
    editingIndex?.let { index ->
        AiConfirmationDialog(
            suggestion = drafts[index],
            categories = categories,
            accounts = accounts,
            onDismiss = { editingIndex = null },
            onConfirm = { edited ->
                drafts = drafts.toMutableList().also { it[index] = edited }
                editingIndex = null
            }
        )
    }
}

@Composable
private fun AiTypeChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) palette.rose else palette.surfaceAlt,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) palette.rose else palette.border)
    ) {
        Text(label, modifier = Modifier.padding(vertical = 8.dp), color = if (selected) Color.White else palette.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AiEditableTextRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF3DE)) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = Color(0xFF9A7045),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder, fontSize = 12.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun <T> AiEditableChoiceRow(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    val palette = LocalPlushPalette.current
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF3DE)) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color(0xFF9A7045), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { expanded = true },
                shape = RoundedCornerShape(14.dp),
                color = palette.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    Icon(Icons.Default.ExpandMore, contentDescription = "选择$label", tint = palette.muted, modifier = Modifier.size(18.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), color = palette.ink) },
                        onClick = { onSelect(option); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun BillsScreen(
    ledger: LedgerState,
    selectedDate: LocalDate,
    onMonth: (Long) -> Unit,
    onDate: (LocalDate) -> Unit,
    onDelete: (String) -> Unit,
    onUpdate: (String, String, String?, String?, String, LocalDateTime) -> Unit
) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    val palette = LocalPlushPalette.current
    val month = YearMonth.from(selectedDate)
    val categoryMap = ledger.categories.associateBy { it.id }
    val monthRecords = ledger.transactions.filter { YearMonth.from(it.localDate()) == month }
    val records = monthRecords
        .filter { filter == "all" || it.type == filter }
        .filter {
            query.isBlank() ||
                it.note.contains(query, true) ||
                categoryMap[it.categoryId]?.name?.contains(query, true) == true
        }
        .sortedByDescending { it.occurredAt }
    val grouped = records.groupBy { it.localDate() }.toSortedMap(compareByDescending { it })
    val monthExpense = monthRecords.filter { it.type == "expense" }.sumOf { it.amountMinor }
    val monthIncome = monthRecords.filter { it.type == "income" }.sumOf { it.amountMinor }
    val selectedRecord = ledger.transactions.firstOrNull { it.id == selectedRecordId }

    if (selectedRecord != null) {
        BackHandler { selectedRecordId = null }
        BillDetailScreen(
            record = selectedRecord,
            category = categoryMap[selectedRecord.categoryId],
            account = ledger.accounts.firstOrNull { it.id == selectedRecord.accountId },
            categories = ledger.categories,
            accounts = ledger.accounts,
            onBack = { selectedRecordId = null },
            onDelete = {
                onDelete(selectedRecord.id)
                selectedRecordId = null
            },
            onUpdate = { amount, categoryId, accountId, note, occurredAt ->
                onUpdate(selectedRecord.id, amount, categoryId, accountId, note, occurredAt)
                selectedRecordId = null
            }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            BrandHeader(
                "账单",
                "每一笔，都有来处",
                month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                onTrailingClick = { showCalendar = !showCalendar },
                trailingAction = { showSearch = !showSearch }
            )
        }
        if (showSearch) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(30) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("搜索分类、备注") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item { ReferenceSegment(listOf("all" to "全部", "expense" to "支出", "income" to "收入"), filter, { filter = it }) }
        if (showCalendar) item { CalendarSelector(selectedDate, month, onMonth) { onDate(it); showCalendar = false } }
        item {
            WarmPanel(padding = 16.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("本月支出", color = palette.muted, fontSize = 12.sp)
                            Text(Money.formatCny(monthExpense), color = palette.ink, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("本月收入", color = palette.muted, fontSize = 12.sp)
                            Text(Money.formatCny(monthIncome), color = palette.moss, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    MascotArt(92.dp)
                }
            }
        }
        grouped.forEach { (date, dayRecords) ->
            item(key = "date-$date") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayTitle(date), color = palette.ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "支出 ${Money.formatCny(dayRecords.filter { it.type == "expense" }.sumOf { it.amountMinor })}  收入 ${Money.formatCny(dayRecords.filter { it.type == "income" }.sumOf { it.amountMinor })}",
                        color = palette.muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item(key = "bill-group-$date") {
                PlushCard(padding = 10.dp) {
                    dayRecords.forEachIndexed { index, record ->
                        CompactTransactionRow(record, categoryMap, onDelete) { selectedRecordId = record.id }
                        if (index != dayRecords.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                    }
                }
            }
        }
        if (records.isEmpty()) item { EmptyPanel("这个月还没有符合条件的账目") }
    }
}

@Composable
private fun BillDetailScreen(
    record: TransactionEntity,
    category: CategoryEntity?,
    account: AccountEntity?,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (String, String?, String?, String, LocalDateTime) -> Unit
) {
    val palette = LocalPlushPalette.current
    var confirmDelete by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = palette.ink) }
                Text("账单详情", modifier = Modifier.weight(1f), color = palette.ink, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Row {
                    IconButton(onClick = { showEditor = true }) { Icon(Icons.Default.EditNote, contentDescription = "编辑账目", tint = palette.rose) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.MoreHoriz, contentDescription = "更多", tint = palette.ink) }
                }
            }
        }
        item {
            PlushButton("编辑这笔账", Icons.Default.EditNote, Modifier.fillMaxWidth(), color = palette.rose) { showEditor = true }
        }
        item {
            PlushCard(padding = 22.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        CategoryArt(category?.name, 64.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(category?.name ?: "未分类", color = palette.muted, fontSize = 13.sp)
                        Text(
                            when (record.type) {
                                "income" -> Money.formatCny(record.amountMinor, true)
                                "expense" -> "-${Money.formatCny(record.amountMinor)}"
                                else -> Money.formatCny(record.amountMinor)
                            },
                            color = if (record.type == "income") palette.moss else palette.ink,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    MascotArt(112.dp)
                }
            }
        }
        item {
            PlushCard(padding = 18.dp) {
                DetailRow("分类", category?.name ?: "未分类")
                DetailRow("账户", account?.name ?: "默认账户")
                DetailRow("日期", record.localDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
                DetailRow("备注", record.note.ifBlank { "无备注" })
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MascotArt(86.dp)
                Spacer(Modifier.width(10.dp))
                Surface(shape = RoundedCornerShape(18.dp), color = palette.surface, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
                    Text("每一笔记录，都有它的小故事～", modifier = Modifier.padding(14.dp), color = palette.muted, fontSize = 12.sp)
                }
            }
        }
        item {
            androidx.compose.material3.OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = palette.coral)
                Spacer(Modifier.width(8.dp))
                Text("删除记录", color = palette.coral, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("删除账目", "是否要删除“${record.note.ifBlank { category?.name ?: "这笔账目" }}”？", onDismiss = { confirmDelete = false }) {
            onDelete()
            confirmDelete = false
        }
    }
    if (showEditor) {
        BillEditDialog(
            record = record,
            categories = categories,
            accounts = accounts,
            onDismiss = { showEditor = false },
            onSave = { amount, categoryId, accountId, note, occurredAt ->
                onUpdate(amount, categoryId, accountId, note, occurredAt)
                showEditor = false
            }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BillEditDialog(
    record: TransactionEntity,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String, LocalDateTime) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalPlushPalette.current
    var amount by remember(record.id) { mutableStateOf("%.2f".format(Locale.US, record.amountMinor / 100.0)) }
    var categoryId by remember(record.id) { mutableStateOf(record.categoryId) }
    var accountId by remember(record.id) { mutableStateOf(record.accountId) }
    var note by remember(record.id) { mutableStateOf(record.note) }
    var date by remember(record.id) { mutableStateOf(record.localDate()) }
    var time by remember(record.id) { mutableStateOf(Instant.ofEpochMilli(record.occurredAt).atZone(ZoneId.systemDefault()).toLocalTime()) }
    val allowedCategories = categories.filter { it.kind == record.type && it.parentId != null }.ifEmpty { categories.filter { it.kind == record.type } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑这笔账", color = palette.ink, fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item { OutlinedTextField(amount, { amount = it.filter { char -> char.isDigit() || char == '.' }.take(12) }, label = { Text("金额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true) }
                if (record.type != "transfer") item {
                    Text("分类", color = palette.muted, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        allowedCategories.forEach { item -> SoftChip(item.name, categoryId == item.id, palette.rose) { categoryId = item.id } }
                    }
                }
                item {
                    Text("账户", color = palette.muted, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        accounts.forEach { item -> SoftChip(item.name, accountId == item.id, palette.blue) { accountId = item.id } }
                    }
                }
                item {
                    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, shape = RoundedCornerShape(16.dp), color = palette.surfaceAlt) { Text("日期  ${date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))}", Modifier.padding(13.dp), color = palette.ink, fontWeight = FontWeight.Bold) }
                }
                item {
                    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { TimePickerDialog(context, { _, hour, minute -> time = LocalTime.of(hour, minute) }, time.hour, time.minute, true).show() }, shape = RoundedCornerShape(16.dp), color = palette.surfaceAlt) { Text("时间  ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}", Modifier.padding(13.dp), color = palette.ink, fontWeight = FontWeight.Bold) }
                }
                item { OutlinedTextField(note, { note = it.take(80) }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = { onSave(amount, categoryId, accountId, note, LocalDateTime.of(date, time)) }) { Text("保存修改", color = palette.rose, fontWeight = FontWeight.Black) } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = palette.muted, fontSize = 13.sp)
        Text(value, color = palette.ink, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BrandHeader(
    title: String,
    subtitle: String,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailingAction: (() -> Unit)? = null
) {
    ReferenceHeader(
        title = title,
        subtitle = subtitle,
        month = trailing,
        branded = title == "绒绒记账",
        mascot = title == "账单",
        onMonth = onTrailingClick,
        trailingAction = trailingAction
    )
}

@Composable
private fun WarmPanel(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
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

private fun dayTitle(date: LocalDate): String {
    val today = LocalDate.now()
    val prefix = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> ""
    }
    val formatted = date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    return if (prefix.isBlank()) formatted else "$prefix  $formatted"
}

@Composable
private fun SummaryNumber(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    val amount = Money.formatCny(value)
    val amountSize = when {
        amount.length >= 12 -> 12.sp
        amount.length >= 10 -> 14.sp
        else -> 18.sp
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, color = palette.muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(amount, color = if (label.contains("结余")) palette.rose else palette.ink, fontWeight = FontWeight.Black, fontSize = amountSize, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun BudgetNumber(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, color = palette.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            Money.formatCny(value),
            color = palette.ink,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordScreen(
    state: UiState,
    onBack: () -> Unit,
    onAdd: (String, String, String?, String?, String?, String, LocalDateTime) -> Boolean,
    onDefaultAccount: (String) -> Unit,
    onBudget: (String, String?) -> Unit,
    onAddAccount: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onAddCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDeleteTransaction: (String) -> Unit
) {
    val ledger = state.ledger
    val palette = LocalPlushPalette.current
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf("expense") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedParentId by rememberSaveable { mutableStateOf<String?>(null) }
    var accountId by rememberSaveable(state.defaultAccountId) { mutableStateOf(state.defaultAccountId) }
    var toAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var timeText by rememberSaveable { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var showAccounts by rememberSaveable { mutableStateOf(false) }
    var showBudget by rememberSaveable { mutableStateOf(false) }
    var showManage by rememberSaveable { mutableStateOf(false) }
    var budgetAmount by rememberSaveable { mutableStateOf("") }
    var budgetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var newAccount by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf("") }
    var deleteAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var deleteCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var managePage by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = ledger.categories.filter { it.kind == type }
    val categoryRoots = categories.filter { it.parentId == null }
    val selectedParent = categoryRoots.firstOrNull { it.id == selectedParentId }
    val childCategories = selectedParent?.let { parent -> categories.filter { it.parentId == parent.id } }.orEmpty()
    val categoryMap = ledger.categories.associateBy { it.id }
    val filtered = ledger.transactions.filter {
        query.isNotBlank() && (
            it.note.contains(query, true) || categoryMap[it.categoryId]?.name?.contains(query, true) == true
        )
    }

    if (showCategoryPicker && type == "expense") {
        val activeRootId = selectedParentId ?: categoryRoots.firstOrNull()?.id
        BackHandler { showCategoryPicker = false }
        SubcategorySelectionScreen(
            categories = categories,
            selectedRootId = activeRootId,
            selectedCategoryId = categoryId,
            onBack = { showCategoryPicker = false },
            onRootSelected = { rootId ->
                selectedParentId = rootId
                categoryId = categories.filter { it.parentId == rootId }.minByOrNull { it.sortOrder }?.id
            },
            onCategorySelected = { categoryId = it },
            onConfirm = { if (categoryId != null) showCategoryPicker = false }
        )
        return
    }

    if (managePage != null) {
        BackHandler { managePage = null }
        when (managePage) {
            "category" -> CategoryManagementScreen(
                ledger = ledger,
                onBack = { managePage = null },
                onAdd = onAddCategory,
                onDelete = { id -> deleteCategory = ledger.categories.firstOrNull { it.id == id } }
            )
            "account" -> AccountManagementScreen(
                ledger = ledger,
                onBack = { managePage = null },
                onAdd = onAddAccount,
                onDelete = { id -> deleteAccount = ledger.accounts.firstOrNull { it.id == id } }
            )
            else -> BudgetManagementScreen(ledger, { managePage = null }, onBudget)
        }
        deleteAccount?.let { account ->
            ConfirmDialog("删除账户", "是否要删除“${account.name}”？历史账目会保留。", onDismiss = { deleteAccount = null }) {
                onDeleteAccount(account.id)
                deleteAccount = null
            }
        }
        deleteCategory?.let { category ->
            ConfirmDialog("删除分类", "是否要删除“${category.name}”？历史账目会保留。", onDismiss = { deleteCategory = null }) {
                onDeleteCategory(category.id)
                deleteCategory = null
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 122.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = palette.ink) }
                Text("记一笔", modifier = Modifier.weight(1f), color = palette.ink, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { showManage = !showManage }) { Icon(Icons.Default.Search, contentDescription = "搜索", tint = palette.ink) }
            }
        }
        if (showManage) item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(30) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("搜索账目") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotBlank()) {
            items(filtered, key = { "search-${it.id}" }) { TransactionRow(it, categoryMap, onDeleteTransaction) }
            if (filtered.isEmpty()) item { EmptyPanel("没有找到匹配账目") }
        }
        item {
            ReferenceSegment(
                items = listOf("expense" to "↓  支出", "income" to "↑  收入"),
                selected = type,
                onSelected = {
                    type = it
                    categoryId = null
                    selectedParentId = null
                }
            )
        }
        item {
            PlushCard(padding = 12.dp) {
                BasicTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, color = palette.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    decorationBox = { inner ->
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text("¥ ", fontSize = 24.sp, fontWeight = FontWeight.Black, color = palette.ink)
                            if (amount.isBlank()) Text("0.00", fontSize = 34.sp, fontWeight = FontWeight.Black, color = palette.muted.copy(alpha = 0.45f)) else inner()
                        }
                    }
                )
            }
        }
        if (type != "transfer") {
            item {
                PlushCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                    if (type == "expense") {
                        val selectedCategory = categories.firstOrNull { it.id == categoryId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    val rootId = selectedParentId ?: categoryRoots.firstOrNull()?.id
                                    selectedParentId = rootId
                                    if (categoryId == null) {
                                        categoryId = categories.filter { it.parentId == rootId }.minByOrNull { it.sortOrder }?.id
                                    }
                                    showCategoryPicker = true
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryArt(selectedCategory?.name ?: selectedParent?.name ?: "餐饮", 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("分类", color = palette.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    selectedCategory?.let { child ->
                                        val root = categoryRoots.firstOrNull { it.id == child.parentId }
                                        listOfNotNull(root?.name, child.name).joinToString(" · ")
                                    } ?: "选择主分类与子分类",
                                    color = palette.ink,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "选择分类", tint = palette.muted)
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categoryRoots.forEach { category ->
                                CategoryChoice(category, categoryId == category.id) { categoryId = category.id }
                            }
                        }
                    }
                }
            }
        }
        item {
            PlushCard(padding = 16.dp) {
                RecordInfoRow(Icons.Default.CalendarMonth, "日期", date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))) { showDate = !showDate }
                if (showDate) {
                    Spacer(Modifier.height(8.dp))
                    PlushCalendar(date, YearMonth.from(date), onSelect = { date = it; showDate = false })
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                RecordInfoRow(Icons.Default.Schedule, "时间", timeText) {
                    val current = runCatching {
                        LocalTime.parse(timeText, DateTimeFormatter.ofPattern("HH:mm"))
                    }.getOrDefault(LocalTime.now())
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> timeText = String.format(Locale.US, "%02d:%02d", hour, minute) },
                        current.hour,
                        current.minute,
                        true
                    ).show()
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                val defaultAccount = ledger.accounts.firstOrNull { it.id == accountId }
                    ?: ledger.accounts.firstOrNull { it.id == state.defaultAccountId }
                    ?: ledger.accounts.firstOrNull { it.name == "现金" }
                RecordInfoRow(Icons.Default.AccountBalanceWallet, "账户", defaultAccount?.name ?: "默认账户（现金）") { showAccounts = !showAccounts }
                if (showAccounts) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ledger.accounts.forEach {
                            SoftChip(it.name, accountId == it.id, palette.blue) {
                                accountId = it.id
                                onDefaultAccount(it.id)
                            }
                        }
                    }
                }
                if (type == "transfer") {
                    Spacer(Modifier.height(10.dp))
                    Text("转入账户", color = palette.muted, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ledger.accounts.forEach { SoftChip(it.name, toAccountId == it.id, palette.moss) { toAccountId = it.id } }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                RecordInfoRow(Icons.Default.EditNote, "备注", note.ifBlank { "可输入备注信息" }) { showNote = !showNote }
                if (showNote) {
                    OutlinedTextField(note, { note = it.take(80) }, placeholder = { Text("备注") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MascotArt(92.dp)
                Surface(shape = RoundedCornerShape(18.dp), color = palette.surface, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
                    Text("每一笔记录，\n都是通往美好生活的一步～", modifier = Modifier.padding(14.dp), color = palette.muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = { managePage = "budget" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("预算") }
                androidx.compose.material3.OutlinedButton(onClick = { managePage = "category" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("分类") }
                androidx.compose.material3.OutlinedButton(onClick = { managePage = "account" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("账户") }
            }
        }
        }
        PlushButton(
            "保存记录",
            Icons.Default.EditNote,
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 18.dp).fillMaxWidth()
        ) {
            val time = runCatching {
                LocalTime.parse(timeText, DateTimeFormatter.ofPattern("HH:mm"))
            }.getOrDefault(LocalTime.now())
            val accepted = onAdd(type, amount, categoryId, accountId, toAccountId, note, LocalDateTime.of(date, time))
            if (accepted) {
                amount = ""
                note = ""
            }
        }
    }

    deleteAccount?.let { account ->
        ConfirmDialog("删除账户", "是否要删除“${account.name}”？历史账目会保留。", onDismiss = { deleteAccount = null }) {
            onDeleteAccount(account.id)
            deleteAccount = null
        }
    }
    deleteCategory?.let { category ->
        ConfirmDialog("删除分类", "是否要删除“${category.name}”？历史账目会保留。", onDismiss = { deleteCategory = null }) {
            onDeleteCategory(category.id)
            deleteCategory = null
        }
    }
}

@Composable
private fun SubcategorySelectionScreen(
    categories: List<CategoryEntity>,
    selectedRootId: String?,
    selectedCategoryId: String?,
    onBack: () -> Unit,
    onRootSelected: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val palette = LocalPlushPalette.current
    val roots = categories.filter { it.parentId == null }.sortedBy { it.sortOrder }
    val activeRoot = roots.firstOrNull { it.id == selectedRootId } ?: roots.firstOrNull()
    val children = categories.filter { it.parentId == activeRoot?.id }.sortedBy { it.sortOrder }
    val rows = children.chunked(2)

    Column(Modifier.fillMaxSize().background(Color(0xFFFFFCF8))) {
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color(0xFF4D3B32), modifier = Modifier.size(28.dp))
            }
            Text(
                "子分类选择",
                modifier = Modifier.weight(1f),
                color = Color(0xFF4D3B32),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.size(48.dp))
        }

        Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 12.dp, bottom = 10.dp)) {
            Surface(
                modifier = Modifier.width(98.dp).fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFFBF6),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF5E6D6)),
                shadowElevation = 3.dp
            ) {
                Column(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    roots.forEach { root ->
                        RootCategoryItem(
                            category = root,
                            selected = root.id == activeRoot?.id,
                            onClick = { onRootSelected(root.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(activeRoot?.name.orEmpty(), color = Color(0xFF4D3B32), fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text("  ·  子分类", color = Color(0xFFA4958B), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subcategoryPageSubtitle(activeRoot?.name),
                            color = Color(0xFFA9988D),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(rows, key = { row -> row.first().id }) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            row.forEach { child ->
                                SubcategoryCard(
                                    category = child,
                                    selected = child.id == selectedCategoryId,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onCategorySelected(child.id) }
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    item { SubcategoryQuote(activeRoot?.name) }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    onClick = onConfirm,
                    enabled = selectedCategoryId != null,
                    shape = RoundedCornerShape(30.dp),
                    color = if (selectedCategoryId != null) Color(0xFFFF9D22) else Color(0xFFFFD29B),
                    border = androidx.compose.foundation.BorderStroke(4.dp, Color.White.copy(alpha = 0.78f)),
                    shadowElevation = 7.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("确认选择", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun RootCategoryItem(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val contentColor = if (selected) Color(0xFFFF8A18) else Color(0xFF5B4A41)
    Surface(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        onClick = onClick,
        color = if (selected) Color.White else Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = if (selected) 5.dp else 0.dp
    ) {
        Box {
            if (selected) {
                Box(
                    Modifier.align(Alignment.CenterStart).width(3.dp).height(34.dp)
                        .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        .background(Color(0xFFFF9B25))
                )
            }
            Row(
                Modifier.fillMaxSize().padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val art = rootCategoryArtRes(category.name)
                if (art != null) {
                    Image(
                        painter = painterResource(art),
                        contentDescription = category.name,
                        modifier = Modifier.size(31.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CategoryArt(category.name, 31.dp)
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    category.name,
                    color = contentColor,
                    fontSize = if (category.name.length >= 4) 11.sp else 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SubcategoryCard(
    category: CategoryEntity,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(108.dp),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFCF8),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) Color(0xFFFFA12B) else Color(0xFFF4E2D1)
        ),
        shadowElevation = if (selected) 6.dp else 4.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(start = 6.dp, top = 7.dp, end = 6.dp, bottom = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val art = subcategoryArtRes(category.name)
                if (art != null) {
                    Image(
                        painter = painterResource(art),
                        contentDescription = category.name,
                        modifier = Modifier.fillMaxWidth(0.88f).height(64.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CategoryArt(category.name, 68.dp)
                }
                Text(
                    category.name,
                    color = Color(0xFF4D3B32),
                    fontSize = if (category.name.length >= 5) 13.sp else 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                if (category.name == "单车月卡") {
                    Text("哈啰 / 美团月卡", color = Color(0xFFA9988D), fontSize = 8.sp, maxLines = 1)
                }
            }
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(25.dp),
                    shape = CircleShape,
                    color = Color(0xFFFF9D22),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "已选择", tint = Color.White, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SubcategoryQuote(rootName: String?) {
    val (title, body) = subcategoryQuote(rootName)
    Surface(
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFF8F0),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF4E2D1))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(subcategoryQuoteArtRes(rootName)),
                contentDescription = null,
                modifier = Modifier.width(88.dp).fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(5.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color(0xFF59453B), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(body, color = Color(0xFFA9988D), fontSize = 9.sp, maxLines = 2, lineHeight = 13.sp)
            }
        }
    }
}

private fun subcategoryPageSubtitle(name: String?): String = when (name) {
    "餐饮" -> "好好吃饭，认真生活"
    "交通" -> "出行有序，记账清楚"
    "购物" -> "精致生活，从记录开始"
    "日常" -> "小日常，也值得认真记录"
    "娱乐" -> "放松一下，快乐生活"
    "人情社交" -> "礼尚往来，人情记录不乱"
    "宠物" -> "爱宠的每一笔，都值得记录"
    "学习工作" -> "每一份成长，都值得被记录"
    "医疗健康" -> "照顾自己，也要记下来"
    else -> "零碎开销，也能清楚归类"
}

private fun subcategoryQuote(name: String?): Pair<String, String> = when (name) {
    "餐饮" -> "美食能治愈一切～" to "记下一餐的美好时光吧！"
    "交通" -> "绿色出行每一步" to "智慧又环保，记账更轻松～"
    "购物" -> "精致生活的小确幸" to "每一笔记录，都是对自己的宠爱～"
    "日常" -> "财务生活的小确幸" to "每一笔记账，都陪你更安心～"
    "娱乐" -> "快乐也是生活的调味剂！" to "认真记录，享受每一刻～"
    "人情社交" -> "每一次心意的传递" to "都是关系的温暖联系～"
    "宠物" -> "用心记录，点滴关爱" to "陪伴它的每一天～"
    "学习工作" -> "学习让生活更美好～" to "点滴投入，未来可期！"
    "医疗健康" -> "健康是最好的投资" to "好好爱自己，记得关心～"
    else -> "每一笔，都有意义" to "把生活的点滴，都记录在这里吧～"
}

private fun rootCategoryArtRes(name: String?): Int? = when (name) {
    "餐饮" -> R.drawable.root_food
    "交通" -> R.drawable.root_transport
    "购物" -> R.drawable.root_shopping
    "日常" -> R.drawable.category_daily_consume
    "娱乐" -> R.drawable.root_entertainment
    "人情社交" -> R.drawable.category_social
    "宠物" -> R.drawable.root_pet
    "学习工作" -> R.drawable.category_study
    "医疗健康" -> R.drawable.category_medical
    "其他" -> R.drawable.root_other
    else -> null
}

private fun subcategoryArtRes(name: String?): Int? = when (name) {
    "早餐" -> R.drawable.sub_breakfast
    "正餐" -> R.drawable.sub_meal
    "外卖" -> R.drawable.sub_delivery
    "奶茶咖啡", "奶茶" -> R.drawable.category_milktea
    "零食" -> R.drawable.sub_snacks
    "聚餐" -> R.drawable.sub_gathering
    "通勤" -> R.drawable.sub_commute
    "公交地铁" -> R.drawable.sub_metro
    "打车" -> R.drawable.sub_taxi
    "火车高铁" -> R.drawable.sub_rail
    "单车月卡" -> R.drawable.sub_bike_pass
    "日用百货" -> R.drawable.sub_basket
    "服饰鞋包" -> R.drawable.sub_clothes
    "数码配件" -> R.drawable.sub_digital
    "美妆个护" -> R.drawable.sub_beauty
    "生活用品" -> R.drawable.sub_household
    "快递物流" -> R.drawable.sub_parcel
    "话费网络" -> R.drawable.sub_phone
    "水电房租" -> R.drawable.sub_utilities
    "游戏" -> R.drawable.sub_game
    "影视会员" -> R.drawable.sub_media
    "旅游出行" -> R.drawable.sub_travel
    "兴趣爱好" -> R.drawable.sub_hobby
    "人情红包" -> R.drawable.sub_redpacket
    "请客送礼" -> R.drawable.sub_gift
    "恋爱约会" -> R.drawable.sub_date
    "社交活动" -> R.drawable.sub_social_activity
    "宠物食品" -> R.drawable.sub_pet_food
    "宠物用品" -> R.drawable.sub_pet_supplies
    "宠物医疗" -> R.drawable.sub_pet_health
    "书籍资料" -> R.drawable.sub_books
    "课程考试" -> R.drawable.sub_course
    "文具打印" -> R.drawable.sub_stationery
    "软件工具" -> R.drawable.sub_software
    "药品" -> R.drawable.sub_medicine
    "就诊体检" -> R.drawable.sub_checkup
    "运动健身" -> R.drawable.sub_fitness
    "临时支出" -> R.drawable.sub_temporary
    "杂项备用" -> R.drawable.sub_miscellaneous
    "日常消费" -> R.drawable.category_daily_consume
    "人情" -> R.drawable.category_social
    "学习" -> R.drawable.category_study
    "医疗" -> R.drawable.category_medical
    "未分类" -> R.drawable.sub_unknown
    else -> null
}

private fun subcategoryQuoteArtRes(name: String?): Int = when (name) {
    "餐饮" -> R.drawable.quote_food
    "交通" -> R.drawable.quote_transport
    "购物" -> R.drawable.quote_shopping
    "日常" -> R.drawable.quote_daily
    "娱乐" -> R.drawable.quote_entertainment
    "人情社交" -> R.drawable.quote_social
    "宠物" -> R.drawable.quote_pet
    "学习工作" -> R.drawable.quote_study
    "医疗健康" -> R.drawable.quote_health
    else -> R.drawable.quote_other
}

@Composable
private fun RecordInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(palette.surfaceAlt), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = palette.rose, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold)
        Text(value, color = palette.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.muted, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryManagementScreen(
    ledger: LedgerState,
    onBack: () -> Unit,
    onAdd: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (String, Int) -> Unit = { _, _ -> }
) {
    val palette = LocalPlushPalette.current
    var kind by rememberSaveable { mutableStateOf("expense") }
    var newName by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<CategoryEntity?>(null) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    val categories = ledger.categories.filter { it.kind == kind }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ManagementHeader("分类管理", onBack) }
        item { ReferenceSegment(listOf("expense" to "支出分类", "income" to "收入分类"), kind, { kind = it; editMode = false }) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (editMode) "编辑中：拖动分类可调整顺序，点右上角叉叉删除。"
                    else "长按任意分类进入编辑状态，再拖动排序或点叉叉删除。",
                    color = palette.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                if (editMode) TextButton(onClick = { editMode = false }) { Text("完成") }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.forEach { category ->
                    var dragOffset by remember(category.id) { mutableStateOf(0f) }
                    Box(
                        modifier = Modifier
                            .width(164.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { editMode = true }
                            )
                            .pointerInput(category.id, editMode) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        editMode = true
                                        dragOffset = 0f
                                    },
                                    onDragEnd = { dragOffset = 0f },
                                    onDragCancel = { dragOffset = 0f },
                                    onDrag = { _, dragAmount ->
                                        dragOffset += dragAmount.y
                                        when {
                                            dragOffset > 44f -> {
                                                onReorder(category.id, 1)
                                                dragOffset = 0f
                                            }
                                            dragOffset < -44f -> {
                                                onReorder(category.id, -1)
                                                dragOffset = 0f
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        PlushCard(Modifier.fillMaxWidth(), padding = 10.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryArt(category.name, 46.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(category.name, color = palette.ink, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(if (category.kind == "income") "收入" else "支出", color = palette.muted, fontSize = 11.sp)
                                }
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Menu, contentDescription = "拖动${category.name}", tint = palette.muted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (editMode) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).size(26.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = palette.coral,
                                shadowElevation = 4.dp
                            ) {
                                IconButton(onClick = { pendingDelete = category }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "删除${category.name}", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MascotArt(82.dp)
                Spacer(Modifier.width(10.dp))
                Text("分类清楚一点，生活也会更有条理～", color = palette.muted, fontSize = 12.sp)
            }
        }
        item {
            OutlinedTextField(newName, { newName = it.take(12) }, placeholder = { Text("新分类名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            PlushButton("添加新分类", Icons.Default.Add, Modifier.fillMaxWidth()) {
                onAdd(newName, kind)
                newName = ""
            }
        }
    }
    pendingDelete?.let { category ->
        ConfirmDialog("删除分类", "是否要删除“${category.name}”？历史账目会保留。", onDismiss = { pendingDelete = null }) {
            onDelete(category.id)
            pendingDelete = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountManagementScreen(
    ledger: LedgerState,
    onBack: () -> Unit,
    onAdd: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    val palette = LocalPlushPalette.current
    var newName by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ManagementHeader("账户管理", onBack) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ledger.accounts.take(4).forEach { account ->
                    PlushCard(Modifier.width(164.dp), padding = 12.dp) {
                        AccountArt(account.name, 58.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(account.name, color = palette.ink, fontWeight = FontWeight.Bold)
                        Text(Money.formatCny(account.initialBalanceMinor), color = palette.muted, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            PlushCard(padding = 18.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("总资产", color = palette.muted, fontSize = 12.sp)
                        Text(Money.formatCny(ledger.summary.balanceMinor), color = palette.ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                    MascotArt(92.dp)
                }
            }
        }
        item {
            PlushCard(padding = 10.dp) {
                ledger.accounts.forEachIndexed { index, account ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccountArt(account.name, 46.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(account.name, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onDelete(account.id) }) { Icon(Icons.Default.Delete, contentDescription = "删除${account.name}", tint = palette.muted) }
                    }
                    if (index != ledger.accounts.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                }
            }
        }
        item {
            OutlinedTextField(newName, { newName = it.take(12) }, placeholder = { Text("新账户名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            PlushButton("添加新账户", Icons.Default.Add, Modifier.fillMaxWidth()) {
                onAdd(newName, "wallet")
                newName = ""
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetManagementScreen(ledger: LedgerState, onBack: () -> Unit, onBudget: (String, String?) -> Unit) {
    val palette = LocalPlushPalette.current
    var amount by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val progress = if (ledger.summary.budgetLimitMinor == 0L) 0f else (ledger.summary.budgetUsedMinor.toFloat() / ledger.summary.budgetLimitMinor).coerceIn(0f, 1f)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ManagementHeader("预算管理", onBack) }
        item {
            WarmPanel(padding = 16.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MonthPill(YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy年M月"))) {}
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BudgetNumber("本月预算", ledger.summary.budgetLimitMinor, palette.rose, Modifier.weight(1f))
                            BudgetNumber("已花费", ledger.summary.budgetUsedMinor, palette.moss, Modifier.weight(1f))
                            BudgetNumber("剩余预算", (ledger.summary.budgetLimitMinor - ledger.summary.budgetUsedMinor).coerceAtLeast(0), palette.rose, Modifier.weight(1f))
                        }
                    }
                    MascotArt(70.dp)
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp)), color = palette.rose, trackColor = palette.surfaceAlt)
                Spacer(Modifier.height(8.dp))
                Text("本月已花费 ${(progress * 100).toInt()}%", color = palette.rose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("各分类预算", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Text("管理分类", color = palette.muted, fontSize = 12.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ledger.categorySpend.take(5).forEach { spend ->
                    WarmPanel(padding = 12.dp) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CategoryArt(spend.category.name, 42.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(spend.category.name, color = palette.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(Money.formatCny(spend.amountMinor), color = palette.rose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(7.dp))
                                LinearProgressIndicator(
                                    progress = { (spend.amountMinor.toFloat() / ledger.summary.budgetLimitMinor.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)),
                                    color = spend.category.categoryColor(),
                                    trackColor = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, label = { Text("预算金额") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftChip("总预算", categoryId == null, palette.rose) { categoryId = null }
                ledger.categories.filter { it.kind == "expense" && it.parentId == null }.forEach { category ->
                    SoftChip(category.name, categoryId == category.id, category.categoryColor()) { categoryId = category.id }
                }
            }
            Spacer(Modifier.height(12.dp))
            PlushButton("保存预算", Icons.Default.CalendarMonth, Modifier.fillMaxWidth()) { onBudget(amount, categoryId) }
        }
    }
}

@Composable
private fun ManagementHeader(title: String, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = palette.ink) }
        Text(title, modifier = Modifier.weight(1f), color = palette.ink, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.size(48.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManageFlow(items: List<Pair<String, String>>, color: Color, onDelete: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, name) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)).padding(start = 10.dp)
            ) {
                Text(name, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { onDelete(id) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除$name", modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun FoldHeader(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, expanded: Boolean, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = palette.rose)
        Spacer(Modifier.width(8.dp))
        Text(text, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.SemiBold)
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
    }
}

@Composable
private fun FoldSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    PlushCard {
        FoldHeader(title, icon, expanded, onToggle)
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Column(content = content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(ledger: LedgerState, selectedDate: LocalDate, onMonth: (Long) -> Unit, onDate: (LocalDate) -> Unit) {
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var detailSpend by remember { mutableStateOf<CategorySpend?>(null) }
    val palette = LocalPlushPalette.current
    val month = YearMonth.from(selectedDate)
    val monthRecords = ledger.transactions.filter { YearMonth.from(it.localDate()) == month }
    val monthExpense = monthRecords.filter { it.type == "expense" }.sumOf { it.amountMinor }
    val monthIncome = monthRecords.filter { it.type == "income" }.sumOf { it.amountMinor }
    val monthBalance = monthIncome - monthExpense
    val chartData = ledger.categorySpend
    val weeklySpend = month.weeklyExpense(monthRecords)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BrandHeader(
                "统计",
                "用数据了解自己，让每一笔都有意义～",
                month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                onTrailingClick = { showCalendar = !showCalendar }
            )
        }
        if (showCalendar) item {
            CalendarSelector(selectedDate, month, onMonth) { onDate(it); showCalendar = false }
        }
        item {
            StatsOverviewCard(monthExpense, monthIncome, monthBalance)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsDonutPanel(chartData, monthExpense, Modifier.weight(1f).height(316.dp))
                StatsTrendPanel(weeklySpend, Modifier.weight(1f).height(316.dp))
            }
        }
        item {
            if (ledger.categorySpend.isEmpty()) WarmPanel(Modifier.fillMaxWidth(), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MascotArt(72.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("这个月还没有支出，记一笔后就能看到分类排行～", color = palette.muted, fontSize = 12.sp)
                }
            } else WarmPanel(padding = 14.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("分类", color = palette.muted, fontSize = 14.sp, modifier = Modifier.weight(1.45f))
                    Text("金额", color = palette.muted, fontSize = 14.sp, modifier = Modifier.weight(0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text("占比", color = palette.muted, fontSize = 14.sp, modifier = Modifier.weight(1.1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Spacer(Modifier.width(22.dp))
                }
                Spacer(Modifier.height(8.dp))
                val max = ledger.categorySpend.maxOf { it.amountMinor }.coerceAtLeast(1)
                ledger.categorySpend.take(8).forEachIndexed { index, spend ->
                    StatsCategoryRow(
                        spend = spend,
                        rank = index + 1,
                        max = max,
                        total = monthExpense,
                        color = statsColor(index),
                        onClick = { detailSpend = spend }
                    )
                    if (index != ledger.categorySpend.take(8).lastIndex) {
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                        Spacer(Modifier.height(5.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MascotArt(78.dp)
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = palette.surfaceAlt, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
                    Text(
                        if (monthBalance >= 0) "棒棒哒！本月结余很不错呢～继续保持，向小目标前进吧！" else "这个月支出偏高，看看分类排行就能找到节奏～",
                        color = palette.ink,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)
                    )
                }
            }
        }
    }
    detailSpend?.let { spend ->
        val categories = ledger.categories.associateBy { it.id }
        val records = monthRecords
            .filter { it.type == "expense" && it.categoryId in spend.memberCategoryIds }
            .sortedByDescending { it.occurredAt }
        AlertDialog(
            onDismissRequest = { detailSpend = null },
            title = { Text("${spend.category.name}明细", fontWeight = FontWeight.Bold, color = palette.ink) },
            text = {
                Column(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本月共 ${records.size} 笔，合计 ${Money.formatCny(spend.amountMinor)}", color = palette.muted, fontSize = 12.sp)
                    records.take(10).forEach { record ->
                        StatsTransactionRow(record, categories)
                    }
                    if (records.size > 10) {
                        Text("已展示最近 10 笔，完整明细可在账单页筛选查看。", color = palette.muted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detailSpend = null }) { Text("知道了") } }
        )
    }
}

@Composable
private fun PlushCalendar(selectedDate: LocalDate, month: YearMonth, onSelect: (LocalDate) -> Unit) {
    val palette = LocalPlushPalette.current
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { Text(it, modifier = Modifier.weight(1f), color = palette.muted, fontSize = 12.sp) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val selected = day == selectedDate
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (selected) palette.rose else palette.surfaceAlt)
                            .clickable(enabled = day != null) { day?.let(onSelect) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(day?.dayOfMonth?.toString().orEmpty(), color = if (selected) Color.White else palette.ink, fontSize = 12.sp)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun CalendarSelector(
    selectedDate: LocalDate,
    month: YearMonth,
    onMonth: (Long) -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val palette = LocalPlushPalette.current
    PlushCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(-1) }) { Icon(Icons.Default.ChevronLeft, contentDescription = "上个月") }
            Text(
                month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                modifier = Modifier.weight(1f),
                color = palette.ink,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { onMonth(1) }) { Icon(Icons.Default.ChevronRight, contentDescription = "下个月") }
        }
        Spacer(Modifier.height(8.dp))
        PlushCalendar(selectedDate, month, onSelect)
    }
}

@Composable
private fun StatsOverviewCard(expense: Long, income: Long, balance: Long) {
    val palette = LocalPlushPalette.current
    WarmPanel(padding = 18.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                StatsMetric("本月支出", expense, palette.coral, Modifier.weight(1f))
                StatsMetric("本月收入", income, palette.moss, Modifier.weight(1f))
                StatsMetric("结余", balance, palette.rose, Modifier.weight(1f))
            }
            Spacer(Modifier.width(4.dp))
            MascotArt(60.dp)
        }
    }
}

@Composable
private fun StatsMetric(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    val text = Money.formatCny(value)
    val size = when {
        text.length >= 12 -> 10.sp
        text.length >= 10 -> 11.sp
        text.length >= 9 -> 13.sp
        else -> 15.sp
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, color = palette.muted, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.height(7.dp))
        Text(text, color = if (label == "结余") palette.rose else palette.ink, fontWeight = FontWeight.Black, fontSize = size, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun StatsDonutPanel(spend: List<CategorySpend>, totalExpense: Long, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    WarmPanel(modifier, padding = 12.dp) {
        ProfileSectionLine("支出构成")
        DonutChart(spend, compact = true)
        spend.take(6).forEachIndexed { index, item ->
            val percent = item.amountMinor * 1000 / totalExpense.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(statsColor(index)))
                Spacer(Modifier.width(7.dp))
                Text(item.category.name, color = palette.ink, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${percent / 10}.${percent % 10}%", color = palette.muted, fontSize = 12.sp)
            }
        }
        if (spend.isEmpty()) {
            Text("记一笔支出后会自动生成构成图。", color = palette.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatsTrendPanel(spend: List<WeekSpend>, modifier: Modifier = Modifier) {
    WarmPanel(modifier, padding = 12.dp) {
        ProfileSectionLine("周支出趋势")
        MonthWeekTrendChart(spend)
    }
}

@Composable
private fun ProfileSectionLine(title: String) {
    val palette = LocalPlushPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(21.dp).clip(RoundedCornerShape(4.dp)).background(palette.rose))
        Spacer(Modifier.width(8.dp))
        Text(title, color = palette.ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

@Composable
private fun DonutChart(spend: List<CategorySpend>, compact: Boolean = false) {
    val palette = LocalPlushPalette.current
    val rawTotal = spend.sumOf { it.amountMinor }
    val total = rawTotal.coerceAtLeast(1)
    val top = spend.firstOrNull()
    val topPercent = (top?.amountMinor ?: 0L) * 1000 / total
    Box(Modifier.fillMaxWidth().height(if (compact) 150.dp else 210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(if (compact) 116.dp else 168.dp)) {
            val stroke = (if (compact) 18.dp else 26.dp).toPx()
            drawArc(
                palette.surfaceAlt,
                -90f,
                360f,
                false,
                Offset(stroke / 2, stroke / 2),
                Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            var start = -90f
            spend.take(6).forEachIndexed { index, item ->
                val sweep = item.amountMinor.toFloat() / total * 360f
                drawArc(statsColor(index), start, sweep, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(top?.category?.name ?: "支出构成", color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(Money.formatCny(top?.amountMinor ?: rawTotal), color = palette.ink, fontWeight = FontWeight.Black, fontSize = if (compact) 13.sp else 20.sp, maxLines = 1)
            Text("${topPercent / 10}.${topPercent % 10}%", color = palette.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BarChart(spend: List<CategorySpend>, compact: Boolean = false) {
    val palette = LocalPlushPalette.current
    val max = spend.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1) ?: 1
    val bars = spend.take(6)
    Canvas(Modifier.fillMaxWidth().height(if (compact) 150.dp else 200.dp)) {
        val gap = (if (compact) 6.dp else 12.dp).toPx()
        val count = if (bars.isEmpty()) 5 else bars.size
        val width = (size.width - gap * (count + 1)) / count.coerceAtLeast(1)
        repeat(count) { index ->
            val item = bars.getOrNull(index)
            val height = if (item == null) size.height * (0.22f + index * 0.08f) else item.amountMinor.toFloat() / max * size.height * 0.8f
            drawRoundRect(item?.category?.categoryColor() ?: palette.surfaceAlt, Offset(gap + index * (width + gap), size.height - height), Size(width, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        }
    }
}

@Composable
private fun MonthWeekTrendChart(spend: List<WeekSpend>) {
    val palette = LocalPlushPalette.current
    val max = spend.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1) ?: 1
    Box(Modifier.fillMaxWidth().height(214.dp).padding(top = 10.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            repeat(4) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border.copy(alpha = 0.72f)))
            }
        }
        Row(
            Modifier.fillMaxSize().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            spend.forEachIndexed { index, week ->
                val height = if (max <= 0) 22.dp else (34 + (week.amountMinor.toFloat() / max * 104)).dp
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(Money.formatCny(week.amountMinor).replace("¥", ""), color = palette.ink, fontSize = 10.sp, maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(height)
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(Brush.verticalGradient(listOf(statsColor(index).copy(alpha = 0.58f), statsColor(index))))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        week.label,
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.muted,
                        fontSize = 7.sp,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCategoryRow(spend: CategorySpend, rank: Int, max: Long, total: Long, color: Color, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    val percent = spend.amountMinor * 1000 / total.coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.85f)) {
            Text(rank.toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        CategoryArt(spend.category.name, 44.dp)
        Spacer(Modifier.width(10.dp))
        Text(spend.category.name, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Money.formatCny(spend.amountMinor), color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.8f), textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(9.dp)).background(palette.surfaceAlt)) {
                Box(
                    Modifier
                        .fillMaxWidth((spend.amountMinor.toFloat() / max).coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .background(color)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("${percent / 10}.${percent % 10}%", color = palette.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = "查看详情", tint = palette.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatsTransactionRow(record: TransactionEntity, categories: Map<String, CategoryEntity>) {
    val palette = LocalPlushPalette.current
    val category = record.categoryId?.let(categories::get)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryArt(category?.name, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(category?.name ?: "未分类", color = palette.ink, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                "${record.localDate().format(DateTimeFormatter.ofPattern("M月d日"))}  ${record.note.ifBlank { "无备注" }}",
                color = palette.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("-${Money.formatCny(record.amountMinor)}", color = palette.rose, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private data class WeekSpend(val label: String, val amountMinor: Long)

private fun YearMonth.weeklyExpense(records: List<TransactionEntity>): List<WeekSpend> {
    val end = atEndOfMonth()
    val weeks = mutableListOf<WeekSpend>()
    var start = atDay(1)
    while (!start.isAfter(end)) {
        val stopCandidate = start.plusDays(6)
        val stop = if (stopCandidate.isAfter(end)) end else stopCandidate
        val amount = records
            .filter { it.type == "expense" }
            .filter {
                val date = it.localDate()
                !date.isBefore(start) && !date.isAfter(stop)
            }
            .sumOf { it.amountMinor }
        weeks += WeekSpend("${start.dayOfMonth}~${stop.dayOfMonth}", amount)
        start = stop.plusDays(1)
    }
    return weeks
}

private fun statsColor(index: Int): Color = listOf(
    Color(0xFFFFB24A),
    Color(0xFF7BC79D),
    Color(0xFFF28CAB),
    Color(0xFFA98BE8),
    Color(0xFF8CB9F2),
    Color(0xFFCFCBC4)
)[index % 6]

@Composable
private fun BalancePanel(ledger: LedgerState) {
    val palette = LocalPlushPalette.current
    PlushCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Text("总资产", color = palette.muted)
        Spacer(Modifier.height(6.dp))
        Text(Money.formatCny(ledger.summary.balanceMinor), fontSize = 34.sp, fontWeight = FontWeight.Black, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ledger.accounts.take(3).forEach { SoftChip(it.name, false, palette.blue) {} }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    PlushCard(modifier, padding = 14.dp) {
        PlushBadge(if (label == "收入") Icons.Default.PieChart else Icons.Default.BarChart, color, 36.dp)
        Spacer(Modifier.height(8.dp))
        Text(label, color = palette.muted, fontSize = 12.sp)
        Text(value, color = palette.ink, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactTransactionRow(
    record: TransactionEntity,
    categories: Map<String, CategoryEntity>,
    onDelete: (String) -> Unit,
    onOpen: (() -> Unit)? = null
) {
    val palette = LocalPlushPalette.current
    val category = record.categoryId?.let(categories::get)
    val color = when (record.type) { "income" -> palette.moss; "transfer" -> palette.blue; else -> palette.rose }
    var confirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onOpen != null) { onOpen?.invoke() }
            .padding(horizontal = 2.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(category, color, 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(category?.name ?: if (record.type == "transfer") "转账" else "未分类", fontWeight = FontWeight.Bold, color = palette.ink)
            Text(
                record.note.ifBlank { record.localDate().format(DateTimeFormatter.ofPattern("M月d日")) },
                color = palette.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            when (record.type) {
                "income" -> Money.formatCny(record.amountMinor, true)
                "expense" -> "-${Money.formatCny(record.amountMinor)}"
                else -> Money.formatCny(record.amountMinor)
            },
            color = if (record.type == "income") palette.moss else palette.ink,
            fontWeight = FontWeight.Black
        )
        IconButton(onClick = { confirm = true }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "删除账目", tint = palette.muted, modifier = Modifier.size(17.dp))
        }
    }
    if (confirm) {
        val label = record.note.ifBlank { category?.name ?: Money.formatCny(record.amountMinor) }
        ConfirmDialog("删除账目", "是否要删除“$label”？", onDismiss = { confirm = false }) {
            onDelete(record.id)
            confirm = false
        }
    }
}

@Composable
private fun TransactionRow(record: TransactionEntity, categories: Map<String, CategoryEntity>, onDelete: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    val category = record.categoryId?.let(categories::get)
    val color = when (record.type) { "income" -> palette.moss; "transfer" -> palette.blue; else -> palette.rose }
    var confirm by remember { mutableStateOf(false) }
    PlushCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category, color, 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(category?.name ?: if (record.type == "transfer") "转账" else "未分类", fontWeight = FontWeight.Bold, color = palette.ink)
                Text("${record.localDate().format(DateTimeFormatter.ofPattern("MM-dd"))}  ${record.note}", color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when (record.type) { "income" -> Money.formatCny(record.amountMinor, true); "expense" -> "-${Money.formatCny(record.amountMinor)}"; else -> Money.formatCny(record.amountMinor) },
                color = color,
                fontWeight = FontWeight.Black
            )
            IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, contentDescription = "删除账目", modifier = Modifier.size(18.dp), tint = palette.muted) }
        }
    }
    if (confirm) {
        val label = record.note.ifBlank { category?.name ?: Money.formatCny(record.amountMinor) }
        ConfirmDialog("删除账目", "是否要删除“$label”？", onDismiss = { confirm = false }) {
            onDelete(record.id)
            confirm = false
        }
    }
}

@Composable
private fun CategoryChoice(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.width(68.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) palette.rose else Color.Transparent)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CategoryBadge(category, category.categoryColor(), 40.dp)
            Spacer(Modifier.height(4.dp))
            Text(category.name, color = palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun OtherCategoryChoice(selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.width(68.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(2.dp, if (selected) palette.rose else Color.Transparent)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(palette.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = palette.rose, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text("其他", color = palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun CategoryBadge(category: CategoryEntity?, fallback: Color, size: androidx.compose.ui.unit.Dp) {
    CategoryArt(category?.name, size)
}

private fun categoryIcon(name: String?): androidx.compose.ui.graphics.vector.ImageVector = when (name) {
    "餐饮" -> Icons.Default.Fastfood
    "交通" -> Icons.Default.DirectionsBus
    "购物" -> Icons.Default.ShoppingBag
    "住房" -> Icons.Default.Home
    "娱乐" -> Icons.Default.Movie
    "医疗" -> Icons.Default.LocalHospital
    "学习" -> Icons.Default.MenuBook
    "工资", "兼职" -> Icons.Default.Work
    "礼金", "人情" -> Icons.Default.Redeem
    "理财" -> Icons.Default.Payments
    else -> Icons.Default.MoreHoriz
}

@Composable
private fun CategoryBar(label: String, amount: Long, progress: Float, color: Color) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = palette.ink, fontWeight = FontWeight.SemiBold)
        Text(Money.formatCny(amount), color = palette.muted)
    }
    Spacer(Modifier.height(5.dp))
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)).background(palette.surfaceAlt)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

@Composable
private fun EmptyPanel(text: String) {
    val palette = LocalPlushPalette.current
    PlushCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) { Text(text, color = palette.muted) }
    }
}

private fun CategoryEntity.categoryColor(): Color =
    runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color(0xFFC86F7E))

private fun TransactionEntity.localDate(): LocalDate =
    Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
