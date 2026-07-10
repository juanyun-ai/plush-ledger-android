package com.plushledger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.plushledger.R
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DiaryStatusGroup(val title: String, val items: List<String>)

private val diaryStatuses = listOf(
    DiaryStatusGroup("心情想法", listOf("美滋滋", "裂开", "求锦鲤", "等天晴", "疲惫", "发呆", "冲", "emo", "胡思乱想", "元气满满", "bot")),
    DiaryStatusGroup("工作学习", listOf("搬砖", "沉迷学习", "忙", "摸鱼", "出差", "飞奔回家", "勿扰模式")),
    DiaryStatusGroup("活动", listOf("浪", "打卡", "运动", "喝咖啡", "喝奶茶", "干饭", "带娃", "拯救世界", "自拍")),
    DiaryStatusGroup("休息", listOf("闭关", "宅", "睡觉", "吸猫", "遛狗", "玩游戏", "听歌"))
)

@Composable
fun DiaryScreen(userId: String, quotes: List<String>, locationHint: String = "", onChanged: () -> Unit = {}, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPlushPalette.current
    val scope = rememberCoroutineScope()
    val store = remember(userId) { DiaryStore(context.applicationContext, userId) }
    var entries by remember(userId) { mutableStateOf(store.load()) }
    val today = LocalDate.now()
    val todayDraft = remember(userId) { store.draft(today.toString()) }
    var editingDate by rememberSaveable(userId) { mutableStateOf(today.toString()) }
    var text by rememberSaveable(userId) { mutableStateOf(todayDraft?.text.orEmpty()) }
    var mood by rememberSaveable(userId) { mutableStateOf(todayDraft?.mood ?: "开心") }
    var status by rememberSaveable(userId) { mutableStateOf(todayDraft?.status.orEmpty()) }
    var showStatusPicker by rememberSaveable { mutableStateOf(false) }
    var sharePreview by remember { mutableStateOf<Bitmap?>(null) }
    var showSharePicker by rememberSaveable { mutableStateOf(false) }
    var shareSearch by rememberSaveable { mutableStateOf("") }
    var loadingShareKey by rememberSaveable { mutableStateOf<String?>(null) }
    var savedNotice by rememberSaveable { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var selectedDiaryIds by remember(userId) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedDate = remember(editingDate) { runCatching { LocalDate.parse(editingDate) }.getOrDefault(today) }
    val displayStatus = status.ifBlank { mood }
    val shareQuote = remember(displayStatus, quotes) { matchingQuote(displayStatus, quotes) }
    val selectedEntries = entries.filter { it.id in selectedDiaryIds }
    val canMerge = selectedEntries.size >= 2 && selectedEntries.map { it.date }.distinct().size == 1

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
                    showSharePicker = true
                }) { Icon(Icons.Default.Share, "分享日记", tint = palette.pink) }
                MascotArt(46.dp, R.drawable.mascot_action_sleep)
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
                Box(Modifier.fillMaxWidth().height(224.dp)) {
                    Image(
                        painter = painterResource(R.drawable.diary_hero_reference),
                        contentDescription = "绒绒日记",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 44.dp, top = 55.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFDE7B8).copy(alpha = 0.98f)
                    ) {
                        Text(
                            selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            color = palette.ink,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
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
                            if (status.isBlank()) "设置状态" else status,
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
                        entries = store.addEntry(editingDate, text, displayStatus, status)
                        text = ""
                        selectedDiaryIds = emptySet()
                        store.clearDraft(editingDate)
                        onChanged()
                        savedNotice = true
                    }
                }
                if (savedNotice) {
                    Spacer(Modifier.height(8.dp))
                    Text("已新增一篇绒绒日记", modifier = Modifier.fillMaxWidth(), color = palette.moss, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("近期日记", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
                if (selectedDiaryIds.isNotEmpty()) {
                    Text("已选 ${selectedDiaryIds.size} 篇", color = palette.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { selectedDiaryIds = emptySet() }) { Text("取消", color = palette.muted) }
                    PlushButton("合并", Icons.Default.Save, color = palette.pink, enabled = canMerge) {
                        entries = store.mergeEntries(selectedDiaryIds)
                        selectedDiaryIds = emptySet()
                        onChanged()
                        Toast.makeText(context, "同一天日记已合并", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item { Text("第一篇日记，会从今天开始。", color = palette.muted, fontSize = 13.sp) }
        } else {
            items(entries.take(20), key = { it.id }) { entry ->
                SwipeDeleteDiaryHistoryCard(
                    entry = entry,
                    selected = entry.id in selectedDiaryIds,
                    onSelectionChange = {
                        selectedDiaryIds = if (entry.id in selectedDiaryIds) selectedDiaryIds - entry.id else selectedDiaryIds + entry.id
                    },
                    onClick = { editingEntry = store.entryDraft(entry.id) ?: entry },
                    onDelete = {
                        entries = store.deleteEntry(entry)
                        selectedDiaryIds = selectedDiaryIds - entry.id
                        onChanged()
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
                store.saveEntryDraft(draft)
                Toast.makeText(context, "这篇日记草稿已暂存", Toast.LENGTH_SHORT).show()
                editingEntry = null
            },
            onSave = { saved ->
                entries = store.saveEntry(saved)
                onChanged()
                editingEntry = null
            }
        )
    }

    if (showSharePicker) {
        val options = remember(locationHint, shareSearch) { DiaryShareCard.options(locationHint, shareSearch) }
        Dialog(onDismissRequest = { showSharePicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFFFFFCF7),
                border = BorderStroke(1.dp, Color(0xFFFFD7A3)),
                shadowElevation = 18.dp
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择分享卡片", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    OutlinedTextField(
                        value = shareSearch,
                        onValueChange = { shareSearch = it.take(24) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("搜索省份、城市或首字母，例如 sd / hz") },
                        shape = RoundedCornerShape(18.dp)
                    )
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(options, key = { it.key }) { option ->
                            val loading = loadingShareKey == option.key
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable(enabled = loadingShareKey == null) {
                                        loadingShareKey = option.key
                                        scope.launch {
                                            runCatching {
                                                DiaryShareCard.create(context, option)
                                            }.onSuccess { bitmap ->
                                                sharePreview = bitmap
                                                showSharePicker = false
                                            }.onFailure {
                                                Toast.makeText(context, "分享卡片加载失败，已保留当前页面", Toast.LENGTH_SHORT).show()
                                            }
                                            loadingShareKey = null
                                        }
                                    },
                                shape = RoundedCornerShape(18.dp),
                                color = when {
                                    loading -> palette.moss.copy(alpha = 0.12f)
                                    option.preferred -> palette.pink.copy(alpha = 0.12f)
                                    else -> Color.White
                                },
                                border = BorderStroke(1.dp, if (loading || option.preferred) palette.pink else palette.border)
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(option.label, color = palette.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(if (loading) "正在缓存" else option.kind, color = palette.muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Text("会优先按城市匹配，其次省份；首次使用会下载并缓存卡片，之后可直接分享。", color = palette.muted, fontSize = 12.sp)
                }
            }
        }
    }

    sharePreview?.let { bitmap ->
        Dialog(onDismissRequest = { sharePreview = null }) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFFFFFCF7), border = BorderStroke(1.5.dp, Color(0xFFFFDAB4)), shadowElevation = 18.dp) {
                Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap.asImageBitmap(),
                        "日记分享卡片",
                        Modifier.fillMaxWidth().heightIn(max = 560.dp).aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                        contentScale = ContentScale.Fit
                    )
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
private fun SwipeDeleteDiaryHistoryCard(entry: DiaryEntry, selected: Boolean, onSelectionChange: () -> Unit, onClick: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalPlushPalette.current
    val reveal = with(LocalDensity.current) { 86.dp.toPx() }
    var offsetX by remember(entry.id) { mutableStateOf(0f) }
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
                .pointerInput(entry.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = { offsetX = if (offsetX < -reveal / 2f) -reveal else 0f },
                        onDragCancel = { offsetX = 0f }
                    ) { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(-reveal, 0f)
                    }
                }
        ) {
            DiaryHistoryCard(entry, selected, onSelectionChange, onClick)
        }
    }
}

@Composable
private fun DiaryHistoryCard(entry: DiaryEntry, selected: Boolean, onSelectionChange: () -> Unit, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = palette.surface, border = BorderStroke(1.dp, palette.border), shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onSelectionChange),
                shape = CircleShape,
                color = if (selected) palette.pink else Color.Transparent,
                border = BorderStroke(2.dp, if (selected) palette.pink else palette.pink.copy(alpha = 0.42f))
            ) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = "已选择", tint = Color.White, modifier = Modifier.padding(5.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            MascotArt(48.dp, R.drawable.mascot_action_sleep)
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
    var text by rememberSaveable(initial.id) { mutableStateOf(initial.text) }
    var mood by rememberSaveable(initial.id) { mutableStateOf(initial.mood) }
    var status by rememberSaveable(initial.id) { mutableStateOf(initial.status) }
    var showStatus by rememberSaveable(initial.id) { mutableStateOf(false) }
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
                    MascotArt(58.dp, R.drawable.mascot_action_record)
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
                        Text(if (status.isBlank()) "设置状态" else status, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                        painterResource(R.drawable.mascot_action_record),
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
                    Image(painterResource(R.drawable.mascot_action_sleep), null, modifier = Modifier.size(76.dp), contentScale = ContentScale.Fit)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onSelect("") },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected.isBlank()) palette.pink.copy(alpha = 0.12f) else Color.White,
                    border = BorderStroke(1.dp, if (selected.isBlank()) palette.pink else palette.border)
                ) {
                    Text(
                        "设置状态",
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

private data class ShareCardOption(
    val key: String,
    val label: String,
    val kind: String,
    val search: String,
    val fileName: String,
    val preferred: Boolean = false
)

private object DiaryShareCard {
    private const val REMOTE_BASE_URL = "https://raw.githubusercontent.com/juanyun-ai/plush-ledger-android/main/docs/share-cards/"

    suspend fun create(context: Context, option: ShareCardOption): Bitmap = withContext(Dispatchers.IO) {
        val art = loadShareCard(context, option.fileName) ?: BitmapFactory.decodeResource(context.resources, R.drawable.share_province_default)
        art.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun loadShareCard(context: Context, fileName: String): Bitmap? {
        val cacheDir = File(context.cacheDir, "share-cards").apply { mkdirs() }
        val target = File(cacheDir, fileName)
        if (!target.exists() || target.length() < 1024L) {
            val temp = File(cacheDir, "$fileName.tmp")
            runCatching {
                URL(REMOTE_BASE_URL + fileName).openConnection().apply {
                    connectTimeout = 8_000
                    readTimeout = 20_000
                }.getInputStream().use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                }
                if (temp.length() >= 1024L) {
                    temp.renameTo(target)
                } else {
                    temp.delete()
                }
            }.onFailure {
                temp.delete()
            }
        }
        return target.takeIf { it.exists() && it.length() >= 1024L }?.let {
            it.setLastModified(System.currentTimeMillis())
            BitmapFactory.decodeFile(it.absolutePath)
        }
    }

    fun options(locationHint: String, query: String): List<ShareCardOption> {
        val preferred = preferredOption(locationHint)
        val normalized = query.trim().lowercase(Locale.ROOT)
        val list = shareOptions.map { it.copy(preferred = it.key == preferred.key) }
            .sortedWith(compareByDescending<ShareCardOption> { it.preferred }.thenBy { if (it.kind == "城市") 0 else if (it.kind == "省级") 1 else 2 }.thenBy { it.label })
        if (normalized.isBlank()) return list
        return list.filter { option ->
            option.label.contains(query.trim(), ignoreCase = true) || option.search.lowercase(Locale.ROOT).contains(normalized)
        }
    }

    private fun preferredOption(locationHint: String): ShareCardOption {
        val hint = locationHint.trim()
        if (hint.isBlank()) return shareOptions.first()
        return shareOptions.firstOrNull { hint.contains(it.label) }
            ?: shareOptions.firstOrNull { it.label == "港澳" && (hint.contains("香港") || hint.contains("澳门")) }
            ?: shareOptions.first()
    }

    private val shareOptions = listOf(
        ShareCardOption("province_beijing", "北京", "省级", "beijing bj", "share_card_province_beijing.png"),
        ShareCardOption("province_tianjin", "天津", "省级", "tianjin tj", "share_card_province_tianjin.png"),
        ShareCardOption("province_shanghai", "上海", "省级", "shanghai sh", "share_card_province_shanghai.png"),
        ShareCardOption("province_chongqing", "重庆", "省级", "chongqing cq", "share_card_province_chongqing.png"),
        ShareCardOption("province_hebei", "河北", "省级", "hebei hb", "share_card_province_hebei.png"),
        ShareCardOption("province_shanxi", "山西", "省级", "shanxi sx", "share_card_province_shanxi.png"),
        ShareCardOption("province_liaoning", "辽宁", "省级", "liaoning ln", "share_card_province_liaoning.png"),
        ShareCardOption("province_jilin", "吉林", "省级", "jilin jl", "share_card_province_jilin.png"),
        ShareCardOption("province_heilongjiang", "黑龙江", "省级", "heilongjiang hlj", "share_card_province_heilongjiang.png"),
        ShareCardOption("province_jiangsu", "江苏", "省级", "jiangsu js nanjing suzhou", "share_card_province_jiangsu.png"),
        ShareCardOption("province_zhejiang", "浙江", "省级", "zhejiang zj hangzhou ningbo", "share_card_province_zhejiang.png"),
        ShareCardOption("province_anhui", "安徽", "省级", "anhui ah", "share_card_province_anhui.png"),
        ShareCardOption("province_fujian", "福建", "省级", "fujian fj xiamen fuzhou", "share_card_province_fujian.png"),
        ShareCardOption("province_jiangxi", "江西", "省级", "jiangxi jx", "share_card_province_jiangxi.png"),
        ShareCardOption("province_shandong", "山东", "省级", "shandong sd jinan qingdao", "share_card_province_shandong.png"),
        ShareCardOption("province_henan", "河南", "省级", "henan hn zhengzhou luoyang", "share_card_province_henan.png"),
        ShareCardOption("province_hubei", "湖北", "省级", "hubei hb wuhan", "share_card_province_hubei.png"),
        ShareCardOption("province_hunan", "湖南", "省级", "hunan hn changsha", "share_card_province_hunan.png"),
        ShareCardOption("province_guangdong", "广东", "省级", "guangdong gd guangzhou shenzhen", "share_card_province_guangdong.png"),
        ShareCardOption("province_hainan", "海南", "省级", "hainan hi", "share_card_province_hainan.png"),
        ShareCardOption("province_sichuan", "四川", "省级", "sichuan sc", "share_card_province_sichuan.png"),
        ShareCardOption("province_guizhou", "贵州", "省级", "guizhou gz", "share_card_province_guizhou.png"),
        ShareCardOption("province_yunnan", "云南", "省级", "yunnan yn", "share_card_province_yunnan.png"),
        ShareCardOption("province_shaanxi", "陕西", "省级", "shaanxi sx xian", "share_card_province_shaanxi.png"),
        ShareCardOption("province_gansu", "甘肃", "省级", "gansu gs lanzhou", "share_card_province_gansu.png"),
        ShareCardOption("province_qinghai", "青海", "省级", "qinghai qh", "share_card_province_qinghai.png"),
        ShareCardOption("province_taiwan", "台湾", "省级", "taiwan tw", "share_card_province_taiwan.png"),
        ShareCardOption("province_neimenggu", "内蒙古", "省级", "neimenggu nmg", "share_card_province_neimenggu.png"),
        ShareCardOption("province_guangxi", "广西", "省级", "guangxi gx", "share_card_province_guangxi.png"),
        ShareCardOption("province_xizang", "西藏", "省级", "xizang xz tibet", "share_card_province_xizang.png"),
        ShareCardOption("province_ningxia", "宁夏", "省级", "ningxia nx", "share_card_province_ningxia.png"),
        ShareCardOption("province_xinjiang", "新疆", "省级", "xinjiang xj", "share_card_province_xinjiang.png"),
        ShareCardOption("province_gangao", "港澳", "省级", "gangao ga hongkong xianggang hk macao aomen mo", "share_card_province_gangao.png")
    )

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
}
