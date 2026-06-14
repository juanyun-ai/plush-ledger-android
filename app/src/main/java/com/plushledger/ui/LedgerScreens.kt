package com.plushledger.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import com.plushledger.data.AccountEntity
import com.plushledger.R
import com.plushledger.data.CategoryEntity
import com.plushledger.data.CategorySpend
import com.plushledger.data.LedgerState
import com.plushledger.data.Money
import com.plushledger.data.TransactionEntity
import java.time.Instant
import java.time.LocalDate
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
    onBills: () -> Unit
) {
    val palette = LocalPlushPalette.current
    var showCalendar by rememberSaveable { mutableStateOf(false) }
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
            PlushButton("记一笔", Icons.Default.EditNote, Modifier.fillMaxWidth(), onClick = onRecord)
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
}

@Composable
fun BillsScreen(
    ledger: LedgerState,
    selectedDate: LocalDate,
    onMonth: (Long) -> Unit,
    onDate: (LocalDate) -> Unit,
    onDelete: (String) -> Unit
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
            onBack = { selectedRecordId = null },
            onDelete = {
                onDelete(selectedRecord.id)
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
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalPlushPalette.current
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = palette.ink) }
                Text("账单详情", modifier = Modifier.weight(1f), color = palette.ink, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.MoreHoriz, contentDescription = "更多", tint = palette.ink) }
            }
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
    onAdd: (String, String, String?, String?, String?, String, LocalDate) -> Unit,
    onBudget: (String, String?) -> Unit,
    onAddAccount: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onAddCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDeleteTransaction: (String) -> Unit
) {
    val ledger = state.ledger
    val palette = LocalPlushPalette.current
    var type by rememberSaveable { mutableStateOf("expense") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    var toAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showAccounts by rememberSaveable { mutableStateOf(false) }
    var showBudget by rememberSaveable { mutableStateOf(false) }
    var showManage by rememberSaveable { mutableStateOf(false) }
    var showOtherCategory by rememberSaveable { mutableStateOf(false) }
    var budgetAmount by rememberSaveable { mutableStateOf("") }
    var budgetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var newAccount by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf("") }
    var otherCategoryName by rememberSaveable { mutableStateOf("") }
    var deleteAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var deleteCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var managePage by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = ledger.categories.filter { it.kind == type }
    val categoryMap = ledger.categories.associateBy { it.id }
    val filtered = ledger.transactions.filter {
        query.isNotBlank() && (
            it.note.contains(query, true) || categoryMap[it.categoryId]?.name?.contains(query, true) == true
        )
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
                    showOtherCategory = false
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.take(8).forEach { CategoryChoice(it, categoryId == it.id) { categoryId = it.id } }
                        OtherCategoryChoice(showOtherCategory) {
                            showOtherCategory = !showOtherCategory
                            categoryId = null
                        }
                    }
                    if (showOtherCategory) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            otherCategoryName,
                            { otherCategoryName = it.take(12) },
                            placeholder = { Text("自定义分类名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        PlushButton("添加到${if (type == "income") "收入" else "支出"}分类", Icons.Default.Add, Modifier.fillMaxWidth()) {
                            val clean = otherCategoryName.trim()
                            if (clean.isNotBlank()) {
                                onAddCategory(clean, type)
                                otherCategoryName = ""
                                showOtherCategory = false
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
                RecordInfoRow(Icons.Default.AccountBalanceWallet, "账户", ledger.accounts.firstOrNull { it.id == accountId }?.name ?: "默认账户（现金）") { showAccounts = !showAccounts }
                if (showAccounts) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ledger.accounts.forEach { SoftChip(it.name, accountId == it.id, palette.blue) { accountId = it.id } }
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
                RecordInfoRow(Icons.Default.EditNote, "备注", note.ifBlank { "可输入备注信息" }) { showCategories = !showCategories }
                if (showCategories) {
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
            onAdd(type, amount, categoryId, accountId, toAccountId, note, date)
            if (amount.isNotBlank()) {
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
                ledger.categories.filter { it.kind == "expense" }.forEach { category ->
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
                StatsDonutPanel(chartData, monthExpense, Modifier.weight(1f))
                StatsTrendPanel(weeklySpend, Modifier.weight(1f))
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
            .filter { it.type == "expense" && it.categoryId == spend.category.id }
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    Text(week.label.substringBefore('-'), color = palette.muted, fontSize = 9.sp, maxLines = 1)
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
        weeks += WeekSpend("${start.monthValue}/${start.dayOfMonth}-${stop.monthValue}/${stop.dayOfMonth}", amount)
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
