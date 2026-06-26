package com.plushledger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.plushledger.R
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private data class DiaryStatusGroup(val title: String, val items: List<String>)

private val diaryStatuses = listOf(
    DiaryStatusGroup("心情想法", listOf("美滋滋", "裂开", "求锦鲤", "等天晴", "疲惫", "发呆", "冲", "emo", "胡思乱想", "元气满满", "bot")),
    DiaryStatusGroup("工作学习", listOf("搬砖", "沉迷学习", "忙", "摸鱼", "出差", "飞奔回家", "勿扰模式")),
    DiaryStatusGroup("活动", listOf("浪", "打卡", "运动", "喝咖啡", "喝奶茶", "干饭", "带娃", "拯救世界", "自拍")),
    DiaryStatusGroup("休息", listOf("闭关", "宅", "睡觉", "吸猫", "遛狗", "玩游戏", "听歌"))
)

@Composable
fun DiaryScreen(userId: String, quotes: List<String>, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPlushPalette.current
    val store = remember(userId) { DiaryStore(context.applicationContext, userId) }
    var entries by remember(userId) { mutableStateOf(store.load()) }
    val today = remember { LocalDate.now() }
    val todayEntry = entries.firstOrNull { it.date == today.toString() }
    val todayDraft = remember(userId) { store.draft(today.toString()) }
    var editingDate by rememberSaveable(userId) { mutableStateOf(today.toString()) }
    var text by rememberSaveable(userId) { mutableStateOf(todayDraft?.text ?: todayEntry?.text.orEmpty()) }
    var mood by rememberSaveable(userId) { mutableStateOf(todayDraft?.mood ?: todayEntry?.mood ?: "开心") }
    var status by rememberSaveable(userId) { mutableStateOf(todayDraft?.status ?: todayEntry?.status.orEmpty()) }
    var showStatusPicker by rememberSaveable { mutableStateOf(false) }
    var sharePreview by remember { mutableStateOf<Bitmap?>(null) }
    var savedNotice by rememberSaveable { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    val selectedDate = remember(editingDate) { runCatching { LocalDate.parse(editingDate) }.getOrDefault(today) }
    val displayStatus = status.ifBlank { mood }
    val shareQuote = remember(displayStatus, quotes) { matchingQuote(displayStatus, quotes) }

    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = palette.ink) }
                Column(Modifier.weight(1f)) {
                    Text("绒绒日记", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 27.sp)
                    Text("把今天的心情，轻轻写下来。", color = palette.muted, fontSize = 12.sp)
                }
                IconButton(onClick = {
                    val shareText = text.trim().ifBlank { entries.firstOrNull { it.date == editingDate }?.text ?: "今天也值得被温柔记录。" }
                    sharePreview = DiaryShareCard.create(context, selectedDate, displayStatus, shareText, shareQuote)
                }) { Icon(Icons.Default.Share, "分享日记", tint = palette.pink) }
                MascotArt(46.dp)
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFFFFF5E9),
                border = BorderStroke(1.dp, Color(0xFFFFDEC0)),
                shadowElevation = 7.dp
            ) {
                Row(Modifier.height(205.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(0.9f).padding(start = 22.dp, top = 18.dp, bottom = 18.dp)) {
                        Text("今日日记", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 26.sp)
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)), color = palette.ink, fontSize = 15.sp)
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "“把这刻写下来，\n以后回看也会觉得温暖。”",
                            color = palette.ink,
                            fontFamily = FontFamily.Cursive,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            lineHeight = 30.sp,
                            maxLines = 3
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.diary_hero_mascot),
                        contentDescription = "绒绒日记",
                        modifier = Modifier.weight(1.15f).fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFF8EE),
                border = BorderStroke(1.dp, Color(0xFFFFDEC0)),
                shadowElevation = 5.dp
            ) {
              Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("记录今天的心情", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)), color = palette.muted, fontSize = 13.sp)
                    }
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { showStatusPicker = true },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, palette.border)
                    ) {
                        Text(
                            if (status.isBlank()) "不设置状态" else status,
                            Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            color = if (status.isBlank()) palette.muted else palette.pink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(300); savedNotice = false },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    placeholder = { Text("写下今天想留住的一句话") },
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiaryActionButton("一键清空", Icons.Default.Delete, palette.coral, Modifier.weight(1f)) {
                        text = ""
                        store.clearDraft(editingDate)
                        savedNotice = false
                        Toast.makeText(context, "已清空当前日记输入", Toast.LENGTH_SHORT).show()
                    }
                    DiaryActionButton("暂存", Icons.Default.Favorite, Color(0xFF76A9E8), Modifier.weight(1f)) {
                        store.saveDraft(editingDate, text, displayStatus, status)
                        savedNotice = false
                        Toast.makeText(context, "日记草稿已暂存", Toast.LENGTH_SHORT).show()
                    }
                    DiaryActionButton("保存", Icons.Default.Save, palette.pink, Modifier.weight(1f), enabled = text.trim().isNotBlank()) {
                        entries = store.saveEntry(editingDate, text, displayStatus, status)
                        savedNotice = true
                    }
                }
                if (savedNotice) {
                    Spacer(Modifier.height(8.dp))
                    Text("今天的绒绒日记已保存", modifier = Modifier.fillMaxWidth(), color = palette.moss, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              }
            }
        }
        item { Text("近期日记", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 22.sp) }
        if (entries.isEmpty()) {
            item { Text("第一篇日记，会从今天开始。", color = palette.muted, fontSize = 13.sp) }
        } else {
            items(entries.take(20), key = { it.date }) { entry ->
                SwipeDeleteDiaryHistoryCard(
                    entry = entry,
                    onClick = { editingEntry = entry },
                    onDelete = {
                        entries = store.deleteEntry(entry.date)
                        Toast.makeText(context, "已删除这篇日记", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    if (showStatusPicker) {
        DiaryStatusDialog(
            selected = status,
            onDismiss = { showStatusPicker = false },
            onSelect = {
                status = it
                if (it.isNotBlank()) mood = it
                savedNotice = false
                showStatusPicker = false
            }
        )
    }

    editingEntry?.let { entry ->
        DiaryEditDialog(
            initial = entry,
            onDismiss = { editingEntry = null },
            onDraft = { draft ->
                store.saveDraft(draft.date, draft.text, draft.mood, draft.status)
                Toast.makeText(context, "这篇日记草稿已暂存", Toast.LENGTH_SHORT).show()
                editingEntry = null
            },
            onSave = { saved ->
                entries = store.saveEntry(saved.date, saved.text, saved.mood, saved.status)
                editingEntry = null
            }
        )
    }

    sharePreview?.let { bitmap ->
        Dialog(onDismissRequest = { sharePreview = null }) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFFFFFCF7), border = BorderStroke(1.5.dp, Color(0xFFFFDAB4)), shadowElevation = 18.dp) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap.asImageBitmap(), "日记分享卡片", Modifier.fillMaxWidth().height(560.dp), contentScale = ContentScale.Fit)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { sharePreview = null }, modifier = Modifier.weight(1f)) { Text("取消", color = palette.muted) }
                        PlushButton("分享图片", Icons.Default.Share, Modifier.weight(1f), color = palette.pink) {
                            DiaryShareCard.share(context, bitmap)
                            sharePreview = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (enabled) color else palette.border,
        shadowElevation = if (enabled) 5.dp else 0.dp
    ) {
        Row(
            Modifier.padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DiaryOutlineActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFFEFA),
        border = BorderStroke(1.dp, Color(0xFFFFD7A3))
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SwipeDeleteDiaryHistoryCard(entry: DiaryEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalPlushPalette.current
    val reveal = with(LocalDensity.current) { 86.dp.toPx() }
    var offsetX by remember(entry.date) { mutableStateOf(0f) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(74.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .width(78.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        offsetX = 0f
                        onDelete()
                    },
                shape = RoundedCornerShape(18.dp),
                color = palette.coral.copy(alpha = 0.96f),
                shadowElevation = 2.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Delete, contentDescription = "删除日记", tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("删除", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(entry.date) {
                    detectHorizontalDragGestures(
                        onDragEnd = { offsetX = if (offsetX < -reveal / 2f) -reveal else 0f },
                        onDragCancel = { offsetX = 0f }
                    ) { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(-reveal, 0f)
                    }
                }
        ) {
            DiaryHistoryCard(entry, onClick)
        }
    }
}

@Composable
private fun DiaryHistoryCard(entry: DiaryEntry, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = palette.surface, border = BorderStroke(1.dp, palette.border), shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            MascotArt(48.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.date, color = palette.muted, fontSize = 11.sp)
                Text(entry.text, color = palette.ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFE9EF)) {
                Text(entry.status.ifBlank { entry.mood }, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = palette.pink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DiaryEditDialog(
    initial: DiaryEntry,
    onDismiss: () -> Unit,
    onDraft: (DiaryEntry) -> Unit,
    onSave: (DiaryEntry) -> Unit
) {
    val palette = LocalPlushPalette.current
    var text by rememberSaveable(initial.date) { mutableStateOf(initial.text) }
    var mood by rememberSaveable(initial.date) { mutableStateOf(initial.mood) }
    var status by rememberSaveable(initial.date) { mutableStateOf(initial.status) }
    var showStatus by rememberSaveable(initial.date) { mutableStateOf(false) }
    val displayStatus = status.ifBlank { mood }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFFFFCF7),
            border = BorderStroke(1.5.dp, Color(0xFFFFD7A3)),
            shadowElevation = 22.dp
        ) {
            Box {
                Box(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 8.dp)) {
                    MascotArt(58.dp)
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp).size(38.dp).clip(CircleShape).clickable(onClick = onDismiss),
                    shape = CircleShape,
                    color = Color(0xFFFFF1E3),
                    border = BorderStroke(1.dp, Color(0xFFFFD7A3))
                ) {
                    Icon(Icons.Default.Close, "关闭", tint = palette.ink, modifier = Modifier.padding(8.dp))
                }
                Column(
                    Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                Text("编辑日记", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { showStatus = true },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFFFFEFA),
                    border = BorderStroke(1.dp, Color(0xFFFFD7A3))
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = palette.rose, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(initial.date, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text("|", color = Color(0xFFEED8C4), fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Text(statusIcon(status), fontSize = 18.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(if (status.isBlank()) "不设置状态" else status, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ChevronRight, null, tint = palette.muted)
                    }
                }
                Box(Modifier.fillMaxWidth().height(218.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(1000) },
                        modifier = Modifier.fillMaxSize(),
                        minLines = 8,
                        placeholder = { Text("写下今天的日记吧...") },
                        shape = RoundedCornerShape(22.dp)
                    )
                    Image(
                        painterResource(R.drawable.diary_card_mascot),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(118.dp).alpha(0.24f),
                        contentScale = ContentScale.Fit
                    )
                    Text("${text.length}/1000", modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 14.dp), color = palette.muted, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiaryOutlineActionButton("输入", Icons.Default.Edit, palette.rose, Modifier.weight(1f)) {}
                    DiaryOutlineActionButton("一键清空", Icons.Default.Delete, palette.coral, Modifier.weight(1f)) { text = "" }
                    DiaryOutlineActionButton("暂存", Icons.Default.Favorite, palette.rose, Modifier.weight(1f)) {
                        onDraft(initial.copy(text = text, mood = displayStatus, status = status))
                    }
                }
                Spacer(Modifier.height(6.dp))
                DiaryActionButton("保存", Icons.Default.Save, palette.pink, Modifier.fillMaxWidth(0.56f), enabled = text.trim().isNotBlank()) {
                        onSave(initial.copy(text = text, mood = displayStatus, status = status))
                    }
                }
            }
        }
    }
    if (showStatus) {
        DiaryStatusDialog(
            selected = status,
            onDismiss = { showStatus = false },
            onSelect = {
                status = it
                if (it.isNotBlank()) mood = it
                showStatus = false
            }
        )
    }
}

@Composable
private fun DiaryStatusDialog(selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFFFFF7F0),
            border = BorderStroke(1.dp, Color(0xFFFFD2BE)),
            shadowElevation = 18.dp
        ) {
            Column(
                Modifier
                    .padding(18.dp)
                    .height(560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, "关闭", tint = palette.ink) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("设个状态", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("只在你的日记里保存", color = palette.muted, fontSize = 11.sp)
                    }
                    Image(painterResource(R.drawable.diary_card_mascot), null, modifier = Modifier.size(76.dp), contentScale = ContentScale.Fit)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onSelect("") },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected.isBlank()) palette.pink.copy(alpha = 0.12f) else Color.White,
                    border = BorderStroke(1.dp, if (selected.isBlank()) palette.pink else palette.border)
                ) {
                    Text(
                        "不设置状态",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = if (selected.isBlank()) palette.pink else palette.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                diaryStatuses.forEach { group ->
                    Surface(shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.72f), border = BorderStroke(1.dp, Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(group.title, color = palette.muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            group.items.chunked(4).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { item ->
                                        Surface(
                                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onSelect(item) },
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (selected == item) palette.pink.copy(alpha = 0.16f) else Color.White,
                                            border = BorderStroke(1.dp, if (selected == item) palette.pink else palette.border)
                                        ) {
                                            Text(
                                                "${statusIcon(item)}  $item",
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                color = if (selected == item) palette.pink else palette.ink,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusIcon(status: String): String = when (status) {
    "美滋滋" -> "💕"
    "裂开" -> "💔"
    "求锦鲤" -> "🐟"
    "等天晴" -> "🌤️"
    "疲惫" -> "😔"
    "发呆" -> "😶"
    "冲" -> "🚀"
    "emo" -> "🌧️"
    "胡思乱想" -> "🌀"
    "元气满满" -> "☀️"
    "bot" -> "🤖"
    "搬砖" -> "🧱"
    "沉迷学习" -> "📖"
    "忙" -> "🕘"
    "摸鱼" -> "🐳"
    "出差" -> "✈️"
    "飞奔回家" -> "🏠"
    "勿扰模式" -> "🚫"
    "浪" -> "🌊"
    "打卡" -> "✅"
    "运动" -> "🏋️"
    "喝咖啡" -> "☕"
    "喝奶茶" -> "🧋"
    "干饭" -> "🍜"
    "带娃" -> "👶"
    "拯救世界" -> "🌍"
    "自拍" -> "📷"
    "闭关" -> "🚪"
    "宅" -> "🏡"
    "睡觉" -> "🌙"
    "吸猫" -> "🐱"
    "遛狗" -> "🐶"
    "玩游戏" -> "🎮"
    "听歌" -> "🎵"
    else -> "💗"
}

private fun matchingQuote(mood: String, quotes: List<String>): String {
    val preferred = when (mood) {
        "安静" -> "安静不是空白，是心在慢慢整理自己。"
        "治愈" -> "慢一点也没关系，温柔本身就是力量。"
        "元气" -> "认真生活的人，日子总会悄悄发光。"
        "小确幸" -> "平凡的一天，也值得被温柔记录。"
        "疲惫" -> "累的时候，也可以把自己轻轻放下。"
        "emo" -> "情绪不是麻烦，是心在提醒你需要被照顾。"
        "美滋滋" -> "小小的快乐，也值得被认真收藏。"
        "等天晴" -> "雨会停，天会亮，生活会慢慢变软。"
        else -> "今天的快乐，值得好好收藏。"
    }
    return quotes.firstOrNull { quote -> quote.contains(mood) } ?: preferred
}

private object DiaryShareCard {
    private const val WIDTH = 1254
    private const val HEIGHT = 1254

    fun create(context: Context, date: LocalDate, mood: String, diary: String, quote: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.rgb(255, 250, 244))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val brown = AndroidColor.rgb(82, 61, 48)
        val border = AndroidColor.rgb(248, 218, 180)

        drawSoftCircle(canvas, paint, 102f, 1030f, 74f, AndroidColor.rgb(255, 246, 232))
        drawSoftCircle(canvas, paint, 1150f, 245f, 64f, AndroidColor.rgb(255, 244, 226))
        drawHeart(canvas, paint, 940f, 205f, 20f, AndroidColor.rgb(255, 184, 166))
        drawStar(canvas, paint, 922f, 300f, 36f, AndroidColor.rgb(255, 198, 72))
        drawHeart(canvas, paint, 184f, 1052f, 22f, AndroidColor.rgb(255, 152, 118))

        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.brand_logo_transparent)
        canvas.drawBitmap(logo, Rect(0, 0, logo.width, logo.height * 3 / 5), RectF(396f, 42f, 504f, 150f), paint)
        paint.color = brown
        paint.textSize = 62f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("绒绒记账", 535f, 122f, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 27f
        paint.isFakeBoldText = false
        paint.color = AndroidColor.rgb(160, 128, 104)
        canvas.drawText("♥  温暖每一笔，记录每一天  ♥", WIDTH / 2f, 180f, paint)

        paint.color = AndroidColor.rgb(255, 253, 248)
        canvas.drawRoundRect(RectF(238f, 218f, 1016f, 1002f), 58f, 58f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = AndroidColor.rgb(246, 224, 197)
        canvas.drawRoundRect(RectF(238f, 218f, 1016f, 1002f), 58f, 58f, paint)
        paint.style = Paint.Style.FILL

        paint.color = AndroidColor.rgb(220, 174, 140)
        paint.textSize = 80f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("“", 282f, 330f, paint)
        paint.textSize = 32f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER
        paint.color = AndroidColor.rgb(130, 103, 83)
        canvas.drawText(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)), WIDTH / 2f, 318f, paint)
        paint.color = AndroidColor.rgb(228, 197, 166)
        paint.strokeWidth = 2f
        canvas.drawLine(548f, 348f, 604f, 348f, paint)
        canvas.drawCircle(627f, 348f, 4f, paint)
        canvas.drawLine(650f, 348f, 706f, 348f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 56f
        paint.color = brown
        paint.isFakeBoldText = true
        paint.typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
        drawWrappedText(canvas, quote.ifBlank { "平凡的一天，也值得被温柔记录。" }, 366f, 430f, 420f, 80f, paint, 3)
        paint.typeface = null
        paint.isFakeBoldText = false

        paint.color = AndroidColor.rgb(113, 185, 148)
        canvas.drawCircle(322f, 618f, 10f, paint)
        paint.textSize = 28f
        paint.textAlign = Paint.Align.LEFT
        paint.color = AndroidColor.rgb(130, 103, 83)
        canvas.drawText("今日状态 ·", 360f, 636f, paint)
        paint.color = AndroidColor.rgb(255, 112, 91)
        canvas.drawText(if (mood.isBlank()) "未设置" else mood, 532f, 636f, paint)
        paint.color = AndroidColor.rgb(226, 205, 184)
        canvas.drawLine(360f, 670f, 630f, 670f, paint)

        val mascot = BitmapFactory.decodeResource(context.resources, R.drawable.diary_card_mascot)
        canvas.drawBitmap(mascot, null, RectF(680f, 564f, 1062f, 965f), paint)

        paint.color = AndroidColor.rgb(255, 253, 248)
        canvas.drawRoundRect(RectF(280f, 786f, 748f, 896f), 24f, 24f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = AndroidColor.rgb(248, 224, 200)
        canvas.drawRoundRect(RectF(280f, 786f, 748f, 896f), 24f, 24f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.color = AndroidColor.rgb(130, 103, 83)
        paint.textSize = 25f
        drawWrappedText(canvas, diary.ifBlank { "今天也值得被温柔记录。" }, 330f, 835f, 370f, 35f, paint, 2)

        val qr = qrBitmap(downloadUrl(), 205)
        paint.color = AndroidColor.WHITE
        canvas.drawRoundRect(RectF(522f, 994f, 732f, 1204f), 28f, 28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = AndroidColor.rgb(246, 224, 197)
        canvas.drawRoundRect(RectF(522f, 994f, 732f, 1204f), 28f, 28f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(qr, null, RectF(540f, 1012f, 714f, 1186f), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = brown
        paint.textSize = 20f
        canvas.drawText("扫码下载绒绒记账", WIDTH / 2f, 1230f, paint)
        return bitmap
    }

    fun share(context: Context, bitmap: Bitmap) {
        val directory = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(directory, "rongrong-diary-${LocalDate.now()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "分享绒绒日记"))
    }

    private fun downloadUrl(): String = "https://privacy.xiaoxing.online/"

    private fun drawSoftCircle(canvas: Canvas, paint: Paint, x: Float, y: Float, radius: Float, color: Int) {
        paint.color = color
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun drawStar(canvas: Canvas, paint: Paint, x: Float, y: Float, size: Float, color: Int) {
        paint.color = color
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("★", x, y, paint)
    }

    private fun drawHeart(canvas: Canvas, paint: Paint, x: Float, y: Float, size: Float, color: Int) {
        paint.color = color
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("♥", x, y, paint)
    }

    private fun qrBitmap(content: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, lineHeight: Float, paint: Paint, maxLines: Int) {
        var line = ""
        var lineIndex = 0
        for (character in text) {
            val candidate = line + character
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, y + lineIndex * lineHeight, paint)
                lineIndex++
                if (lineIndex >= maxLines) return
                line = character.toString()
            } else line = candidate
        }
        if (line.isNotEmpty() && lineIndex < maxLines) canvas.drawText(line, x, y + lineIndex * lineHeight, paint)
    }
}
