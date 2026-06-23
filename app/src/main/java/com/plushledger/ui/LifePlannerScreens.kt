package com.plushledger.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plushledger.data.Money
import com.plushledger.data.ProfileEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun LifePlannerPreview(userId: String, profile: ProfileEntity?, onOpenCalendar: () -> Unit, onOpenWishes: () -> Unit) {
    val context = LocalContext.current
    val store = remember(userId) { LifePlannerStore(context.applicationContext, userId) }
    var events by remember(userId) { mutableStateOf(store.events()) }
    var wishes by remember(userId) { mutableStateOf(store.wishes()) }
    val settings = remember(userId) { store.birthdaySettings() }
    val allEvents = remember(events, profile?.birthDate, settings.showInLifeCalendar) {
        events.withBirthday(profile, settings.showInLifeCalendar)
    }
    val upcoming = allEvents.upcoming().take(2)
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.weight(1f).height(220.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpenCalendar),
            shape = RoundedCornerShape(24.dp), color = Color(0xFFFFFCF7), border = BorderStroke(1.dp, palette.border), shadowElevation = 5.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = palette.rose)
                    Spacer(Modifier.width(8.dp))
                    Text("生活日历", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                Spacer(Modifier.height(11.dp))
                MiniPlannerCalendar(allEvents)
                Spacer(Modifier.height(6.dp))
                Text(if (allEvents.isEmpty()) "点击添加一个重要日子" else "${allEvents.size} 个重要日子", color = palette.muted, fontSize = 11.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpenCalendar),
                shape = RoundedCornerShape(24.dp), color = Color(0xFFFFFAF3), border = BorderStroke(1.dp, palette.border), shadowElevation = 5.dp
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = palette.coral)
                        Spacer(Modifier.width(8.dp))
                        Text("小倒计时", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (upcoming.isEmpty()) Text("添加生日、旅行或见面日", color = palette.muted, fontSize = 11.sp)
                    else upcoming.forEach { event -> CountdownLine(event) }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpenWishes),
                shape = RoundedCornerShape(20.dp), color = Color(0xFFFFF6E7), border = BorderStroke(1.dp, palette.border), shadowElevation = 3.dp
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Redeem, null, tint = palette.coral)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("礼物与心愿", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text(if (wishes.isEmpty()) "为重要日子准备惊喜" else "${wishes.size} 个心愿正在靠近", color = palette.muted, fontSize = 10.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = palette.muted)
                }
            }
        }
    }
}

@Composable
fun LifeCalendarScreen(userId: String, profile: ProfileEntity?, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(userId) { LifePlannerStore(context.applicationContext, userId) }
    var events by remember(userId) { mutableStateOf(store.events()) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var editing by remember { mutableStateOf<LifeEvent?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val settings = remember(userId) { store.birthdaySettings() }
    val allEvents = events.withBirthday(profile, settings.showInLifeCalendar)
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PlannerHeader("生活日历", "每一个重要的日子，都值得被温柔记录", onBack) }
        item { PlannerCalendarCard(month, allEvents, onPrevious = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) }) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("重要日子", color = LocalPlushPalette.current.ink, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton(onClick = { adding = true }) { Text("添加日子", color = LocalPlushPalette.current.rose, fontWeight = FontWeight.Bold) }
            }
        }
        val shown = allEvents.filter { YearMonth.from(it.localDate()) == month }.sortedBy { it.localDate() }
        if (shown.isEmpty()) item { PlannerEmpty("${month.format(DateTimeFormatter.ofPattern("M月", Locale.CHINA))}还没有安排，加一个想记住的日子吧。") }
        items(shown, key = { it.id }) { event -> PlannerEventCard(event, onClick = { if (!event.id.startsWith("birthday_")) editing = event }) }
    }
    if (adding || editing != null) LifeEventDialog(
        initial = editing,
        onDismiss = { adding = false; editing = null },
        onSave = { event -> events = store.saveEvent(event); adding = false; editing = null },
        onDelete = editing?.takeIf { !it.id.startsWith("birthday_") }?.let { { events = store.deleteEvent(it.id); editing = null } }
    )
}

@Composable
fun GiftWishScreen(userId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(userId) { LifePlannerStore(context.applicationContext, userId) }
    var wishes by remember(userId) { mutableStateOf(store.wishes()) }
    var editing by remember { mutableStateOf<WishPlan?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PlannerHeader("礼物与心愿", "用心准备每一份温暖，让重要的日子更有意义", onBack) }
        item {
            Surface(shape = RoundedCornerShape(26.dp), color = Color(0xFFFFF7EC), border = BorderStroke(1.dp, LocalPlushPalette.current.border), shadowElevation = 5.dp) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    MascotArt(88.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("为生活留一点期待", color = LocalPlushPalette.current.ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
                        Text("礼物准备、存钱计划和旅行心愿都可以录进来。", color = LocalPlushPalette.current.muted, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("心愿与特别日子", modifier = Modifier.weight(1f), color = LocalPlushPalette.current.ink, fontWeight = FontWeight.Black, fontSize = 21.sp); TextButton(onClick = { adding = true }) { Text("添加心愿", color = LocalPlushPalette.current.rose, fontWeight = FontWeight.Bold) } } }
        if (wishes.isEmpty()) item { PlannerEmpty("还没有心愿。从想送出的一份礼物，或想实现的一次旅行开始。") }
        items(wishes, key = { it.id }) { wish -> WishCard(wish) { editing = wish } }
    }
    if (adding || editing != null) WishDialog(editing, onDismiss = { adding = false; editing = null }, onSave = { wish -> wishes = store.saveWish(wish); adding = false; editing = null }, onDelete = editing?.let { { wishes = store.deleteWish(it.id); editing = null } })
}

@Composable private fun PlannerHeader(title: String, subtitle: String, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = palette.ink) }
        Column(Modifier.weight(1f)) { Text(title, color = palette.ink, fontWeight = FontWeight.Black, fontSize = 28.sp); Text(subtitle, color = palette.muted, fontSize = 12.sp, maxLines = 2) }
        MascotArt(64.dp)
    }
}

@Composable private fun PlannerCalendarCard(month: YearMonth, events: List<LifeEvent>, onPrevious: () -> Unit, onNext: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFFFFFCF7), border = BorderStroke(1.dp, palette.border), shadowElevation = 6.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, "上个月", tint = palette.ink) }; Text(month.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)), modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Black, fontSize = 21.sp); IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "下个月", tint = palette.ink) } }
            PlannerCalendarGrid(month, events)
        }
    }
}

@Composable private fun PlannerCalendarGrid(month: YearMonth, events: List<LifeEvent>) {
    val palette = LocalPlushPalette.current
    val eventDates = events.groupBy { it.localDate() }
    Row(Modifier.fillMaxWidth()) { listOf("日", "一", "二", "三", "四", "五", "六").forEach { Text(it, modifier = Modifier.weight(1f), color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
    val leading = month.atDay(1).dayOfWeek.value % 7
    val days = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    days.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            week.forEach { date ->
                Box(Modifier.weight(1f).height(45.dp), contentAlignment = Alignment.Center) {
                    date?.let {
                        val marked = eventDates[it].orEmpty().isNotEmpty()
                        Surface(shape = CircleShape, color = if (it == LocalDate.now()) palette.rose.copy(alpha = 0.18f) else Color.Transparent) {
                            Column(Modifier.padding(horizontal = 5.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(it.dayOfMonth.toString(), color = palette.ink, fontWeight = if (marked) FontWeight.Black else FontWeight.Medium, fontSize = 14.sp)
                                Text(it.lunarLabel(), color = palette.muted, fontSize = 7.sp, maxLines = 1)
                                if (marked) Box(Modifier.size(5.dp).clip(CircleShape).background(eventDates[it]?.firstOrNull()?.plannerColor() ?: palette.rose))
                            }
                        }
                    }
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable private fun MiniPlannerCalendar(events: List<LifeEvent>) {
    val month = YearMonth.now(); val palette = LocalPlushPalette.current; val marked = events.map { it.localDate() }.toSet()
    Row(Modifier.fillMaxWidth()) { listOf("日", "一", "二", "三", "四", "五", "六").forEach { Text(it, modifier = Modifier.weight(1f), color = palette.muted, fontSize = 8.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
    val cells = List(month.atDay(1).dayOfWeek.value % 7) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    cells.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth()) { week.forEach { day -> Text(day?.dayOfMonth?.toString().orEmpty(), modifier = Modifier.weight(1f), color = if (day in marked) palette.rose else palette.ink, fontWeight = if (day in marked) FontWeight.Black else FontWeight.Normal, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }; repeat(7 - week.size) { Spacer(Modifier.weight(1f)) } } }
}

@Composable private fun CountdownLine(event: LifeEvent) { val palette = LocalPlushPalette.current; val days = ChronoUnit.DAYS.between(LocalDate.now(), event.localDate()).coerceAtLeast(0); Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Icon(event.plannerIcon(), null, tint = event.plannerColor(), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(event.title, modifier = Modifier.weight(1f), color = palette.ink, fontSize = 11.sp, maxLines = 1); Text("${days}天", color = event.plannerColor(), fontWeight = FontWeight.Black, fontSize = 18.sp) } }

@Composable private fun PlannerEventCard(event: LifeEvent, onClick: () -> Unit) { val palette = LocalPlushPalette.current; Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = palette.surface, border = BorderStroke(1.dp, palette.border), shadowElevation = 3.dp) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(15.dp), color = event.plannerColor().copy(alpha = 0.14f)) { Icon(event.plannerIcon(), null, tint = event.plannerColor(), modifier = Modifier.padding(11.dp).size(28.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(event.title, color = palette.ink, fontWeight = FontWeight.Black, fontSize = 17.sp); Text(event.localDate().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)) + "  ·  " + event.lunarHint(), color = palette.muted, fontSize = 12.sp) }; Text("${ChronoUnit.DAYS.between(LocalDate.now(), event.localDate()).coerceAtLeast(0)}天", color = event.plannerColor(), fontWeight = FontWeight.Black, fontSize = 18.sp) } } }

@Composable private fun WishCard(wish: WishPlan, onClick: () -> Unit) { val palette = LocalPlushPalette.current; val progress = if (wish.targetMinor == 0L) 0f else (wish.savedMinor.toFloat() / wish.targetMinor).coerceIn(0f, 1f); Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), color = palette.surface, border = BorderStroke(1.dp, palette.border), shadowElevation = 4.dp) { Column(Modifier.padding(15.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(14.dp), color = palette.moss.copy(alpha = 0.14f)) { Icon(Icons.Default.Savings, null, tint = palette.moss, modifier = Modifier.padding(11.dp).size(28.dp)) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(wish.title, color = palette.ink, fontWeight = FontWeight.Black, fontSize = 17.sp); Text(wish.note.ifBlank { "给生活留一个期待" }, color = palette.muted, fontSize = 12.sp, maxLines = 1) }; Icon(Icons.Default.ChevronRight, null, tint = palette.muted) }; Spacer(Modifier.height(12.dp)); androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)), color = palette.moss, trackColor = palette.moss.copy(alpha = 0.13f)); Spacer(Modifier.height(7.dp)); Text("已存 ${Money.formatCny(wish.savedMinor)}  /  预算 ${Money.formatCny(wish.targetMinor)}", color = palette.moss, fontWeight = FontWeight.Bold, fontSize = 12.sp) } } }

@Composable private fun PlannerEmpty(message: String) { Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFFAF3), border = BorderStroke(1.dp, LocalPlushPalette.current.border)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { MascotArt(58.dp); Spacer(Modifier.width(12.dp)); Text(message, color = LocalPlushPalette.current.muted, fontSize = 13.sp) } } }

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun LifeEventDialog(initial: LifeEvent?, onDismiss: () -> Unit, onSave: (LifeEvent) -> Unit, onDelete: (() -> Unit)?) { val context = LocalContext.current; var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }; var type by remember(initial?.id) { mutableStateOf(initial?.type ?: "birthday") }; var date by remember(initial?.id) { mutableStateOf(initial?.localDate() ?: LocalDate.now()) }; var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "添加重要日子" else "编辑重要日子", fontWeight = FontWeight.Black) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(title, { title = it.take(24) }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("birthday" to "生日", "gift" to "礼物", "travel" to "旅行", "meet" to "见面", "other" to "其他").forEach { (key, label) -> SoftChip(label, type == key, LocalPlushPalette.current.rose) { type = key } } }; Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF8EC), border = BorderStroke(1.dp, LocalPlushPalette.current.border)) { Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)), Modifier.padding(14.dp), color = LocalPlushPalette.current.ink, fontWeight = FontWeight.Bold) }; OutlinedTextField(note, { note = it.take(80) }, label = { Text("备注（选填）") }, modifier = Modifier.fillMaxWidth(), singleLine = true) } }, dismissButton = { if (onDelete != null) TextButton(onClick = onDelete) { Text("删除", color = LocalPlushPalette.current.coral) } else TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onSave(LifeEvent(id = initial?.id ?: java.util.UUID.randomUUID().toString(), title = title.trim(), date = date.toString(), type = type, note = note.trim())) }) { Text("保存", color = LocalPlushPalette.current.rose, fontWeight = FontWeight.Black) } }) }

@Composable private fun WishDialog(initial: WishPlan?, onDismiss: () -> Unit, onSave: (WishPlan) -> Unit, onDelete: (() -> Unit)?) { var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }; var budget by remember(initial?.id) { mutableStateOf(if (initial == null || initial.targetMinor == 0L) "" else "%.2f".format(Locale.US, initial.targetMinor / 100.0)) }; var saved by remember(initial?.id) { mutableStateOf(if (initial == null || initial.savedMinor == 0L) "" else "%.2f".format(Locale.US, initial.savedMinor / 100.0)) }; var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "添加心愿" else "编辑心愿", fontWeight = FontWeight.Black) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(title, { title = it.take(24) }, label = { Text("心愿或礼物") }, modifier = Modifier.fillMaxWidth(), singleLine = true); OutlinedTextField(budget, { budget = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, label = { Text("预算金额") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true); OutlinedTextField(saved, { saved = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, label = { Text("已存金额") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true); OutlinedTextField(note, { note = it.take(80) }, label = { Text("备注（选填）") }, modifier = Modifier.fillMaxWidth(), singleLine = true) } }, dismissButton = { if (onDelete != null) TextButton(onClick = onDelete) { Text("删除", color = LocalPlushPalette.current.coral) } else TextButton(onClick = onDismiss) { Text("取消") } }, confirmButton = { TextButton(onClick = { val target = Money.parseToMinor(budget) ?: 0L; val savedAmount = Money.parseToMinor(saved) ?: 0L; if (title.isNotBlank() && target > 0) onSave(WishPlan(id = initial?.id ?: java.util.UUID.randomUUID().toString(), title = title.trim(), targetMinor = target, savedMinor = savedAmount.coerceAtMost(target), note = note.trim())) }) { Text("保存", color = LocalPlushPalette.current.rose, fontWeight = FontWeight.Black) } }) }

private fun List<LifeEvent>.withBirthday(profile: ProfileEntity?, enabled: Boolean): List<LifeEvent> { val birthday = profile?.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }; if (!enabled || birthday == null) return this; val today = LocalDate.now(); var next = birthday.withYear(today.year); if (next.isBefore(today)) next = next.plusYears(1); return (this.filterNot { it.id == "birthday_${profile.id}" } + LifeEvent(id = "birthday_${profile.id}", title = "我的生日", date = next.toString(), type = "birthday", note = "给自己留一份温柔祝福")).sortedBy { it.localDate() } }
private fun List<LifeEvent>.upcoming(): List<LifeEvent> = filter { !it.localDate().isBefore(LocalDate.now()) }.sortedBy { it.localDate() }
private fun LifeEvent.plannerColor(): Color = when (type) { "birthday" -> Color(0xFFFF718F); "gift" -> Color(0xFFFFA126); "travel" -> Color(0xFF69C79A); "meet" -> Color(0xFF83B7F1); else -> Color(0xFFA58AE8) }
private fun LifeEvent.plannerIcon() = when (type) { "birthday" -> Icons.Default.Celebration; "gift" -> Icons.Default.Redeem; "travel" -> Icons.Default.Flight; "meet" -> Icons.Default.Favorite; else -> Icons.Default.CalendarMonth }
private fun LifeEvent.lunarHint(): String = localDate().lunarLabel()
