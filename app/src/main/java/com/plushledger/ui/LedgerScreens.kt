package com.plushledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plushledger.data.AccountEntity
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

@Composable
fun HomeScreen(ledger: LedgerState, onDelete: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { BalancePanel(ledger) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniMetric("收入", Money.formatCny(ledger.summary.incomeMinor), LocalPlushPalette.current.moss, Modifier.weight(1f))
                MiniMetric("支出", Money.formatCny(ledger.summary.expenseMinor), LocalPlushPalette.current.rose, Modifier.weight(1f))
            }
        }
        item { SectionTitle("最近账目", Icons.Default.AccountBalanceWallet) }
        items(ledger.transactions.take(12), key = { it.id }) { record ->
            TransactionRow(record, ledger.categories.associateBy { it.id }, onDelete)
        }
        if (ledger.transactions.isEmpty()) item { EmptyPanel("还没有账目") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordScreen(
    state: UiState,
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
    var budgetAmount by rememberSaveable { mutableStateOf("") }
    var budgetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var newAccount by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf("") }
    var deleteAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var deleteCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    val categories = ledger.categories.filter { it.kind == type }
    val categoryMap = ledger.categories.associateBy { it.id }
    val filtered = ledger.transactions.filter {
        query.isNotBlank() && (
            it.note.contains(query, true) || categoryMap[it.categoryId]?.name?.contains(query, true) == true
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(30) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("搜索账目") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotBlank()) {
            items(filtered, key = { "search-${it.id}" }) { TransactionRow(it, categoryMap, onDeleteTransaction) }
            if (filtered.isEmpty()) item { EmptyPanel("没有找到匹配账目") }
        }
        item {
            PlushCard {
                TabRow(selectedTabIndex = when (type) { "expense" -> 0; "income" -> 1; else -> 2 }) {
                    Tab(type == "expense", { type = "expense"; categoryId = null }, text = { Text("支出") })
                    Tab(type == "income", { type = "income"; categoryId = null }, text = { Text("收入") })
                    Tab(type == "transfer", { type = "transfer"; categoryId = null }, text = { Text("转账") })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
                    label = { Text("金额") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(note, { note = it.take(80) }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                FoldHeader("日期：${date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}", Icons.Default.CalendarMonth, showDate) { showDate = !showDate }
                if (showDate) PlushCalendar(date, YearMonth.from(date), onSelect = { date = it; showDate = false })
            }
        }
        if (type != "transfer") {
            item {
                FoldSection("分类", Icons.Default.Category, showCategories, { showCategories = !showCategories }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { SoftChip(it.name, categoryId == it.id, it.categoryColor()) { categoryId = it.id } }
                    }
                }
            }
        }
        item {
            FoldSection("账户", Icons.Default.AccountBalanceWallet, showAccounts, { showAccounts = !showAccounts }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ledger.accounts.forEach { SoftChip(it.name, accountId == it.id, palette.blue) { accountId = it.id } }
                }
                if (type == "transfer") {
                    Spacer(Modifier.height(10.dp))
                    Text("转入账户", color = palette.muted, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ledger.accounts.forEach { SoftChip(it.name, toAccountId == it.id, palette.moss) { toAccountId = it.id } }
                    }
                }
            }
        }
        item {
            PlushButton("记一笔", Icons.Default.Add, Modifier.fillMaxWidth()) {
                onAdd(type, amount, categoryId, accountId, toAccountId, note, date)
                if (amount.isNotBlank()) {
                    amount = ""
                    note = ""
                }
            }
        }
        item {
            FoldSection("本月预算", Icons.Default.CalendarMonth, showBudget, { showBudget = !showBudget }) {
                val progress = if (ledger.summary.budgetLimitMinor == 0L) 0f else
                    (ledger.summary.budgetUsedMinor.toFloat() / ledger.summary.budgetLimitMinor).coerceIn(0f, 1f)
                Text("${Money.formatCny(ledger.summary.budgetUsedMinor)} / ${Money.formatCny(ledger.summary.budgetLimitMinor)}", fontWeight = FontWeight.Bold, color = palette.ink)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)), color = palette.rose)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(budgetAmount, { budgetAmount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, label = { Text("预算金额") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftChip("总预算", budgetCategory == null, palette.rose) { budgetCategory = null }
                    ledger.categories.filter { it.kind == "expense" }.forEach {
                        SoftChip(it.name, budgetCategory == it.id, it.categoryColor()) { budgetCategory = it.id }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PlushButton("保存预算", Icons.Default.CalendarMonth, Modifier.fillMaxWidth()) { onBudget(budgetAmount, budgetCategory) }
            }
        }
        item {
            FoldSection("管理分类与账户", Icons.Default.Category, showManage, { showManage = !showManage }) {
                Text("账户", fontWeight = FontWeight.Bold, color = palette.ink)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newAccount, { newAccount = it.take(12) }, label = { Text("新账户") }, modifier = Modifier.weight(1f), singleLine = true)
                    IconButton(onClick = { onAddAccount(newAccount, "wallet"); newAccount = "" }) { Icon(Icons.Default.Add, contentDescription = "新增账户") }
                }
                ManageFlow(ledger.accounts.map { it.id to it.name }, palette.blue) { id -> deleteAccount = ledger.accounts.first { it.id == id } }
                Spacer(Modifier.height(12.dp))
                Text("分类", fontWeight = FontWeight.Bold, color = palette.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newCategory, { newCategory = it.take(12) }, label = { Text("新分类") }, modifier = Modifier.weight(1f), singleLine = true)
                    IconButton(onClick = { onAddCategory(newCategory, type.takeIf { it != "transfer" } ?: "expense"); newCategory = "" }) { Icon(Icons.Default.Add, contentDescription = "新增分类") }
                }
                ManageFlow(ledger.categories.map { it.id to it.name }, palette.rose) { id -> deleteCategory = ledger.categories.first { it.id == id } }
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
    var chartMode by rememberSaveable { mutableStateOf("donut") }
    val palette = LocalPlushPalette.current
    val categoryMap = ledger.categories.associateBy { it.id }
    val dayTransactions = ledger.transactions.filter { it.localDate() == selectedDate }
    val dayIncome = dayTransactions.filter { it.type == "income" }.sumOf { it.amountMinor }
    val dayExpense = dayTransactions.filter { it.type == "expense" }.sumOf { it.amountMinor }
    val daySpend = dayTransactions.filter { it.type == "expense" && it.categoryId != null }
        .groupBy { it.categoryId }
        .mapNotNull { (id, rows) -> id?.let(categoryMap::get)?.let { CategorySpend(it, rows.sumOf(TransactionEntity::amountMinor)) } }
        .sortedByDescending { it.amountMinor }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            PlushCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onMonth(-1) }) { Text("上月") }
                    TextButton(onClick = { showCalendar = !showCalendar }) {
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { onMonth(1) }) { Text("下月") }
                }
                if (showCalendar) PlushCalendar(selectedDate, YearMonth.from(selectedDate)) { onDate(it); showCalendar = false }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniMetric("收入", Money.formatCny(dayIncome), palette.moss, Modifier.weight(1f))
                MiniMetric("支出", Money.formatCny(dayExpense), palette.rose, Modifier.weight(1f))
            }
        }
        item {
            PlushCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftChip("环形图", chartMode == "donut", palette.rose) { chartMode = "donut" }
                    SoftChip("柱状图", chartMode == "bar", palette.blue) { chartMode = "bar" }
                }
                Spacer(Modifier.height(14.dp))
                if (daySpend.isEmpty()) Text("这一天还没有支出", color = palette.muted)
                else if (chartMode == "donut") DonutChart(daySpend) else BarChart(daySpend)
            }
        }
        item {
            SectionTitle("本月分类支出", Icons.Default.PieChart)
            Spacer(Modifier.height(8.dp))
            if (ledger.categorySpend.isEmpty()) EmptyPanel("这个月还没有支出")
            else PlushCard {
                val max = ledger.categorySpend.maxOf { it.amountMinor }.coerceAtLeast(1)
                ledger.categorySpend.take(8).forEach {
                    CategoryBar(it.category.name, it.amountMinor, it.amountMinor.toFloat() / max, it.category.categoryColor())
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
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
private fun DonutChart(spend: List<CategorySpend>) {
    val palette = LocalPlushPalette.current
    val total = spend.sumOf { it.amountMinor }.coerceAtLeast(1)
    Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(168.dp)) {
            val stroke = 26.dp.toPx()
            var start = -90f
            spend.take(6).forEach {
                val sweep = it.amountMinor.toFloat() / total * 360f
                drawArc(it.category.categoryColor(), start, sweep, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("支出", color = palette.muted, fontSize = 12.sp)
            Text(Money.formatCny(total), color = palette.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}

@Composable
private fun BarChart(spend: List<CategorySpend>) {
    val max = spend.maxOf { it.amountMinor }.coerceAtLeast(1)
    val bars = spend.take(6)
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val gap = 12.dp.toPx()
        val width = (size.width - gap * (bars.size + 1)) / bars.size.coerceAtLeast(1)
        bars.forEachIndexed { index, item ->
            val height = item.amountMinor.toFloat() / max * size.height * 0.8f
            drawRoundRect(item.category.categoryColor(), Offset(gap + index * (width + gap), size.height - height), Size(width, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        }
    }
}

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
private fun TransactionRow(record: TransactionEntity, categories: Map<String, CategoryEntity>, onDelete: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    val category = record.categoryId?.let(categories::get)
    val color = when (record.type) { "income" -> palette.moss; "transfer" -> palette.blue; else -> palette.rose }
    var confirm by remember { mutableStateOf(false) }
    PlushCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlushBadge(Icons.Default.AccountBalanceWallet, color, 38.dp)
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
