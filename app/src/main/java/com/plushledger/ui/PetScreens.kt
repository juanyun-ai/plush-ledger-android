package com.plushledger.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plushledger.R
import com.plushledger.data.LedgerState
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.delay

private data class PetFeature(
    val key: String,
    val title: String,
    val subtitle: String,
    val asset: String
)

private val petFeatures = listOf(
    PetFeature("outfit", "今日穿搭", "给绒绒换一身温柔", "pet_refs/吉祥物扩展UI/今日穿搭.jpg"),
    PetFeature("skin", "主题皮肤", "挑一份喜欢的配色", "pet_refs/吉祥物扩展UI/主题皮肤.jpg"),
    PetFeature("room", "小窝布置", "把小窝布置得更舒服", "pet_refs/吉祥物扩展UI/小窝布置.jpg"),
    PetFeature("diary", "绒绒日记", "记录今天的心情", "pet_refs/吉祥物扩展UI/绒绒日记.jpg"),
    PetFeature("story", "成长故事", "每一笔真实记录都会点亮", "pet_refs/吉祥物扩展UI/成长故事.jpg"),
    PetFeature("noise", "白噪音陪伴", "温柔的声音，陪你放松", "pet_refs/吉祥物扩展UI/白噪音陪伴.jpg"),
    PetFeature("frame", "头像框", "用真实记录解锁收藏", "pet_refs/吉祥物扩展UI/头像框.jpg"),
    PetFeature("emoji", "表情包", "把绒绒分享给朋友", "pet_refs/吉祥物扩展UI/表情包.jpg"),
    PetFeature("title", "专属称号", "为你的记录留下一枚称号", "pet_refs/吉祥物扩展UI/专属称号.jpg")
)

private data class PetMetrics(
    val companionValue: Int,
    val level: Int,
    val rangeStart: Int,
    val rangeEnd: Int,
    val progress: Float,
    val cumulativeYuan: Long,
    val recordDays: Int,
    val recordCount: Int
)

@Composable
fun PetRongRongScreen(ledger: LedgerState, onBack: () -> Unit) {
    val context = LocalContext.current
    val userId = ledger.profile?.id ?: "local-pet"
    val store = remember(userId) { PetStore(context.applicationContext, userId) }
    var preferences by remember(userId) { mutableStateOf(store.load()) }
    var featureKey by rememberSaveable { mutableStateOf<String?>(null) }
    val metrics = remember(ledger.transactions) { ledger.petMetrics() }

    fun updatePreferences(value: PetPreferences) {
        preferences = value
        store.save(value)
    }

    BackHandler(enabled = featureKey != null) { featureKey = null }
    val feature = petFeatures.firstOrNull { it.key == featureKey }
    if (feature == null) {
        PetHomeScreen(
            metrics = metrics,
            preferences = preferences,
            onBack = onBack,
            onOpenFeature = { featureKey = it },
            onPreferencesChanged = ::updatePreferences
        )
    } else {
        PetFeatureScreen(
            feature = feature,
            metrics = metrics,
            preferences = preferences,
            onPreferencesChanged = ::updatePreferences,
            onBack = { featureKey = null }
        )
    }
}

private fun LedgerState.petMetrics(): PetMetrics {
    val dates = transactions.map {
        java.time.Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }.distinct().size
    val totalYuan = transactions.sumOf { abs(it.amountMinor) } / 100L
    // Only real, already stored bookkeeping data contributes to companion value.
    val value = (transactions.size * 12 + dates * 10 + (totalYuan / 50L).toInt()).coerceAtMost(9_999)
    val level = (value / 100 + 1).coerceIn(1, 100)
    val rangeStart = (level - 1) * 100
    val rangeEnd = level * 100
    return PetMetrics(
        companionValue = value,
        level = level,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        progress = ((value - rangeStart).toFloat() / 100f).coerceIn(0f, 1f),
        cumulativeYuan = totalYuan,
        recordDays = dates,
        recordCount = transactions.size
    )
}

@Composable
private fun PetHomeScreen(
    metrics: PetMetrics,
    preferences: PetPreferences,
    onBack: () -> Unit,
    onOpenFeature: (String) -> Unit,
    onPreferencesChanged: (PetPreferences) -> Unit
) {
    val palette = LocalPlushPalette.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    val interactedToday = preferences.lastInteractionDate == LocalDate.now().toString()

    fun interact(action: PetInteraction) {
        if (interactedToday) {
            feedback = "今天已经陪伴过绒绒啦，明天再来看看它"
        } else {
            onPreferencesChanged(preferences.copy(mood = action.mood, lastInteractionDate = LocalDate.now().toString()))
            feedback = action.reply
        }
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2_600)
            feedback = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = palette.ink) }
                Text("宠物专题", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                PetPill("绒绒小窝", palette.pink)
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = palette.surface, border = BorderStroke(1.dp, palette.border), shadowElevation = 3.dp) {
                    Icon(Icons.Default.Favorite, "喜欢绒绒", tint = palette.pink, modifier = Modifier.padding(10.dp).size(20.dp))
                }
            }
        }
        item {
            PetHero(metrics, preferences)
        }
        item {
            PetInteractionPanel(expanded, interactedToday, onToggle = { expanded = !expanded }, onInteract = ::interact)
        }
        feedback?.let { message -> item { PetFeedbackBubble(message) } }
        item {
            PetHomeFeatureGroup(
                title = "绒绒衣橱 & 小窝",
                cards = listOf(
                    PetHomeCard("outfit", 55, 955, 270, 165),
                    PetHomeCard("skin", 335, 955, 270, 165),
                    PetHomeCard("room", 615, 955, 270, 165)
                ),
                onOpenFeature = onOpenFeature
            )
        }
        item {
            PetHomeFeatureGroup(
                title = "绒绒日记 & 故事",
                cards = listOf(
                    PetHomeCard("diary", 55, 1205, 270, 165),
                    PetHomeCard("story", 335, 1205, 270, 165),
                    PetHomeCard("noise", 615, 1205, 270, 165)
                ),
                onOpenFeature = onOpenFeature
            )
        }
        item {
            PetHomeFeatureGroup(
                title = "陪伴奖励",
                cards = listOf(
                    PetHomeCard("frame", 55, 1450, 270, 145),
                    PetHomeCard("emoji", 335, 1450, 270, 145),
                    PetHomeCard("title", 615, 1450, 270, 145)
                ),
                onOpenFeature = onOpenFeature
            )
        }
        item {
            Text(
                "陪伴值来自已保存的 ${metrics.recordCount} 笔记录、${metrics.recordDays} 个记账日和累计 ¥${metrics.cumulativeYuan} 的真实账本数据。",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                color = palette.muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun PetHero(metrics: PetMetrics, preferences: PetPreferences) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFFFFF7ED),
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = 5.dp
    ) {
        Box(Modifier.fillMaxWidth().height(218.dp)) {
            Image(
                painter = painterResource(R.drawable.pet_hero),
                contentDescription = "绒绒",
                modifier = Modifier.fillMaxWidth(0.57f).height(218.dp).align(Alignment.CenterEnd),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
            Column(Modifier.fillMaxWidth(0.52f).padding(start = 18.dp, top = 20.dp)) {
                Text("你好呀，我是绒绒", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
                Text("今天也要一起好好记录哦～", color = palette.muted, fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    shadowElevation = 2.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("陪伴值", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.width(9.dp))
                            Text("Lv.${metrics.level}", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(7.dp))
                        LinearProgressIndicator(
                            progress = { metrics.progress },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)),
                            color = palette.pink,
                            trackColor = palette.pink.copy(alpha = 0.18f)
                        )
                        Text("${metrics.companionValue} / ${metrics.rangeEnd}", color = palette.muted, fontSize = 9.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("今日心情", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(Modifier.width(7.dp))
                            PetPill("♥ ${preferences.mood}", palette.pink)
                        }
                    }
                }
            }
        }
    }
}

private data class PetInteraction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val mood: String,
    val reply: String
)

private val petInteractions = listOf(
    PetInteraction("摸一摸", Icons.Default.TouchApp, "开心", "你摸了摸绒绒，它开心地眨了眨眼。"),
    PetInteraction("抱一抱", Icons.Default.Favorite, "幸福", "绒绒收到了一个暖暖的拥抱。"),
    PetInteraction("揉一揉", Icons.Default.EmojiEmotions, "放松", "绒绒舒舒服服地伸了个懒腰。"),
    PetInteraction("戳一戳", Icons.Default.ChatBubble, "惊喜", "绒绒歪着脑袋，好奇地看向你。")
)

@Composable
private fun PetInteractionPanel(
    expanded: Boolean,
    interactedToday: Boolean,
    onToggle: () -> Unit,
    onInteract: (PetInteraction) -> Unit
) {
    val palette = LocalPlushPalette.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("轻松互动", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
                Spacer(Modifier.width(10.dp))
                Text(if (interactedToday) "今日已互动" else "点互动展开 · 每天最多 1 次", color = palette.muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (expanded) {
                    PetInteractionAction(petInteractions[0], Modifier.weight(1f), onInteract)
                    PetInteractionAction(petInteractions[1], Modifier.weight(1f), onInteract)
                } else {
                    Spacer(Modifier.weight(2f))
                }
                Box(Modifier.width(24.dp).height(2.dp).background(palette.pink.copy(alpha = 0.35f)))
                Surface(
                    modifier = Modifier.size(82.dp).clip(CircleShape).clickable(onClick = onToggle),
                    shape = CircleShape,
                    color = palette.pink,
                    border = BorderStroke(4.dp, Color.White),
                    shadowElevation = 10.dp
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (expanded) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "互动", tint = Color.White, modifier = Modifier.size(25.dp))
                        Text(if (expanded) "收起" else "互动", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
                Box(Modifier.width(24.dp).height(2.dp).background(palette.pink.copy(alpha = 0.35f)))
                if (expanded) {
                    PetInteractionAction(petInteractions[2], Modifier.weight(1f), onInteract)
                    PetInteractionAction(petInteractions[3], Modifier.weight(1f), onInteract)
                } else {
                    Spacer(Modifier.weight(2f))
                }
            }
        }
    }
}

@Composable
private fun PetInteractionAction(action: PetInteraction, modifier: Modifier, onInteract: (PetInteraction) -> Unit) {
    val palette = LocalPlushPalette.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable { onInteract(action) }.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = palette.surfaceAlt, border = BorderStroke(1.dp, palette.border)) {
            Icon(action.icon, contentDescription = action.label, tint = palette.pink, modifier = Modifier.padding(8.dp).size(23.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(action.label, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
    }
}

private data class PetHomeCard(val key: String, val x: Int, val y: Int, val width: Int, val height: Int)

@Composable
private fun PetHomeFeatureGroup(title: String, cards: List<PetHomeCard>, onOpenFeature: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(shape = RoundedCornerShape(24.dp), color = palette.surface, border = BorderStroke(1.dp, palette.border), shadowElevation = 3.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = palette.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                cards.forEach { card ->
                    PetAssetCrop(
                        asset = "pet_refs/宠物专题.jpg",
                        sourceX = card.x,
                        sourceY = card.y,
                        sourceWidth = card.width,
                        sourceHeight = card.height,
                        modifier = Modifier.weight(1f).height(if (card.height > 150) 104.dp else 90.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpenFeature(card.key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PetFeatureScreen(
    feature: PetFeature,
    metrics: PetMetrics,
    preferences: PetPreferences,
    onPreferencesChanged: (PetPreferences) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    when (feature.key) {
        "outfit" -> OutfitFeature(feature, preferences, onPreferencesChanged, onBack)
        "skin" -> SkinFeature(feature, preferences, onPreferencesChanged, onBack)
        "room" -> RoomFeature(feature, preferences, onPreferencesChanged, onBack)
        "diary" -> DiaryFeature(feature, preferences, onPreferencesChanged, onBack)
        "story" -> StoryFeature(feature, metrics, onBack)
        "noise" -> NoiseFeature(feature, onBack)
        "frame" -> FrameFeature(feature, metrics, preferences, onPreferencesChanged, onBack)
        "emoji" -> EmojiFeature(feature, onBack)
        "title" -> TitleFeature(feature, metrics, preferences, onPreferencesChanged, onBack)
    }
}

@Composable
private fun PetFeatureScaffold(feature: PetFeature, onBack: () -> Unit, content: @Composable () -> Unit) {
    val palette = LocalPlushPalette.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = palette.ink) }
                Column(Modifier.weight(1f)) {
                    Text(feature.title, color = palette.ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Text(feature.subtitle, color = palette.muted, fontSize = 12.sp)
                }
                MascotArt(46.dp)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = palette.surfaceAlt, border = BorderStroke(1.dp, palette.border)) {
                PetAssetCrop(feature.asset, 0, 95, 941, 440, Modifier.fillMaxWidth().height(185.dp).clip(RoundedCornerShape(24.dp)))
            }
        }
        item { content() }
    }
}

@Composable
private fun OutfitFeature(feature: PetFeature, preferences: PetPreferences, onUpdate: (PetPreferences) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPlushPalette.current
    val choices = listOf("暖绒围巾", "星星帽子", "心心外套")
    PetFeatureScaffold(feature, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("选择今天的穿搭", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(choices) { choice ->
                    PetChoiceCard(choice, choice == preferences.outfit, Icons.Default.Style) { onUpdate(preferences.copy(outfit = choice)) }
                }
            }
            Text("已换上：${preferences.outfit}", color = palette.muted, fontSize = 13.sp)
            PlushButton("分享穿搭", Icons.Default.Share, Modifier.fillMaxWidth(), color = palette.pink) {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "绒绒今天换上了${preferences.outfit}，一起好好记录每一天。")
                }, "分享绒绒穿搭"))
            }
        }
    }
}

@Composable
private fun SkinFeature(feature: PetFeature, preferences: PetPreferences, onUpdate: (PetPreferences) -> Unit, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    val choices = listOf("暖阳奶油", "柔粉小屋", "薄荷晴空", "冰蓝云朵")
    PetFeatureScaffold(feature, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("宠物小窝皮肤", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
            choices.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { choice ->
                        PetChoiceCard(choice, choice == preferences.skin, Icons.Default.Palette, Modifier.weight(1f)) { onUpdate(preferences.copy(skin = choice)) }
                    }
                }
            }
            Text("已使用：${preferences.skin}。宠物小窝会记住这份选择。", color = palette.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RoomFeature(feature: PetFeature, preferences: PetPreferences, onUpdate: (PetPreferences) -> Unit, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    val choices = listOf("奶油小窝", "阅读角落", "星夜窗边")
    PetFeatureScaffold(feature, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("布置温馨小窝", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
            choices.forEach { choice ->
                PetChoiceCard(choice, choice == preferences.room, Icons.Default.Bed, Modifier.fillMaxWidth()) { onUpdate(preferences.copy(room = choice)) }
            }
            Text("当前小窝：${preferences.room}", color = palette.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DiaryFeature(feature: PetFeature, preferences: PetPreferences, onUpdate: (PetPreferences) -> Unit, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    var text by rememberSaveable { mutableStateOf("") }
    var mood by rememberSaveable { mutableStateOf("元气") }
    var saved by rememberSaveable { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    PetFeatureScaffold(feature, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(18.dp), color = palette.surfaceAlt, border = BorderStroke(1.dp, palette.border)) {
                Column(Modifier.padding(14.dp)) {
                    Text("记录今天的心情", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
                    Text(today.format(DateTimeFormatter.ofPattern("M月d日 EEEE")), color = palette.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it.take(300); saved = false }, modifier = Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text("写下今天想留住的一句话") })
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("元气", "开心", "安静", "治愈")) { item ->
                            SoftChip(item, mood == item, if (item == "开心") palette.pink else palette.rose) { mood = item }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    PlushButton("保存日记", Icons.Default.Save, Modifier.fillMaxWidth(), enabled = text.trim().isNotEmpty(), color = palette.pink) {
                        val date = LocalDate.now().toString()
                        val entry = PetDiaryEntry(date, text.trim(), mood)
                        onUpdate(preferences.copy(diaries = (listOf(entry) + preferences.diaries.filterNot { it.date == date }).take(60)))
                        saved = true
                        text = ""
                    }
                }
            }
            if (saved) PetFeedbackBubble("今天的绒绒日记已保存")
            Text("近期日记", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
            if (preferences.diaries.isEmpty()) {
                Text("第一篇日记，会从今天开始。", color = palette.muted, fontSize = 13.sp)
            } else {
                preferences.diaries.take(8).forEach { entry ->
                    DiaryEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun DiaryEntryCard(entry: PetDiaryEntry) {
    val palette = LocalPlushPalette.current
    Surface(shape = RoundedCornerShape(18.dp), color = palette.surface, border = BorderStroke(1.dp, palette.border)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MascotArt(44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.date, color = palette.muted, fontSize = 11.sp)
                Text(entry.text, color = palette.ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            PetPill(entry.mood, palette.pink)
        }
    }
}

@Composable
private fun StoryFeature(feature: PetFeature, metrics: PetMetrics, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    val milestones = listOf(1L, 100L, 500L, 1_000L, 5_000L, 10_000L, 30_000L, 50_000L, 100_000L, 500_000L, 1_000_000L, 5_000_000L, 10_000_000L, 50_000_000L, 100_000_000L)
    PetFeatureScaffold(feature, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("累计记录 ¥${metrics.cumulativeYuan}", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 21.sp)
            Text("成长故事会随着真实账本累计金额自动点亮，可左右滑动查看全部里程碑。", color = palette.muted, fontSize = 13.sp, lineHeight = 19.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 12.dp)) {
                items(milestones) { amount ->
                    val unlocked = metrics.cumulativeYuan >= amount
                    Surface(
                        modifier = Modifier.width(126.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (unlocked) palette.surfaceAlt else palette.surface,
                        border = BorderStroke(1.dp, if (unlocked) palette.pink.copy(alpha = 0.5f) else palette.border)
                    ) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (unlocked) Icons.Default.AutoStories else Icons.Default.Lock, null, tint = if (unlocked) palette.pink else palette.muted, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(formatMilestone(amount), color = palette.ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text(if (unlocked) "已点亮" else "继续记录", color = if (unlocked) palette.pink else palette.muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoiseFeature(feature: PetFeature, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    val context = LocalContext.current
    val tracks = listOf(NoiseTrack("春雨", "柔和雨声", R.raw.pet_rain), NoiseTrack("夜读", "安静粉红噪音", R.raw.pet_night), NoiseTrack("专注", "轻柔节奏", R.raw.pet_focus))
    var selected by rememberSaveable { mutableStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    fun stop() { player?.release(); player = null; playing = false }
    fun toggle() {
        if (playing) stop() else {
            player = MediaPlayer.create(context, tracks[selected].resource).apply { isLooping = true; start() }
            playing = true
        }
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    PetFeatureScaffold(feature, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("白噪音陪伴", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
            tracks.forEachIndexed { index, track ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable {
                        if (selected != index) stop()
                        selected = index
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected == index) palette.surfaceAlt else palette.surface,
                    border = BorderStroke(1.dp, if (selected == index) palette.moss else palette.border)
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, null, tint = palette.moss)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.name, color = palette.ink, fontWeight = FontWeight.Bold)
                            Text(track.description, color = palette.muted, fontSize = 12.sp)
                        }
                        if (selected == index) Icon(Icons.Default.CheckCircle, "已选择", tint = palette.moss)
                    }
                }
            }
            PlushButton(if (playing) "暂停陪伴" else "播放 ${tracks[selected].name}", if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, Modifier.fillMaxWidth(), color = palette.moss, onClick = ::toggle)
            Text("音频保存在应用内，播放时不上传任何账本或日记内容。", color = palette.muted, fontSize = 12.sp)
        }
    }
}

private data class NoiseTrack(val name: String, val description: String, val resource: Int)

@Composable
private fun FrameFeature(feature: PetFeature, metrics: PetMetrics, preferences: PetPreferences, onUpdate: (PetPreferences) -> Unit, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    val frames = listOf("默认头像框" to 0, "心心花环" to 100, "星星花环" to 500, "月光花环" to 1_000)
    PetFeatureScaffold(feature, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("头像框", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("累计账本金额 ¥${metrics.cumulativeYuan}", color = palette.muted, fontSize = 13.sp)
            frames.forEach { (name, threshold) ->
                val unlocked = metrics.cumulativeYuan >= threshold
                PetUnlockChoice(name, threshold, unlocked, name == preferences.frame, Icons.Default.Badge) {
                    if (unlocked) onUpdate(preferences.copy(frame = name))
                }
            }
        }
    }
}

@Composable
private fun EmojiFeature(feature: PetFeature, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPlushPalette.current
    val messages = listOf("绒绒给你一个拥抱", "今天也认真记账啦", "温柔地记录生活")
    PetFeatureScaffold(feature, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("绒绒表情包", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
            messages.forEach { text ->
                Surface(shape = RoundedCornerShape(18.dp), color = palette.surfaceAlt, border = BorderStroke(1.dp, palette.border)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MascotArt(48.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(text, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "$text - 来自绒绒记账")
                            }, "分享表情"))
                        }) { Icon(Icons.Default.Share, "分享", tint = palette.pink) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleFeature(feature: PetFeature, metrics: PetMetrics, preferences: PetPreferences, onUpdate: (PetPreferences) -> Unit, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    val titles = listOf("绒绒伙伴" to 0, "记账小能手" to 100, "绒绒守护者" to 500, "生活收藏家" to 1_000)
    PetFeatureScaffold(feature, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("专属称号", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("称号按真实记录累计金额解锁", color = palette.muted, fontSize = 13.sp)
            titles.forEach { (name, threshold) ->
                val unlocked = metrics.cumulativeYuan >= threshold
                PetUnlockChoice(name, threshold, unlocked, name == preferences.title, Icons.Default.EmojiEmotions) {
                    if (unlocked) onUpdate(preferences.copy(title = name))
                }
            }
        }
    }
}

@Composable
private fun PetChoiceCard(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) palette.surfaceAlt else palette.surface,
        border = BorderStroke(1.dp, if (selected) palette.rose else palette.border),
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) palette.rose else palette.muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            if (selected) Icon(Icons.Default.CheckCircle, "已选择", tint = palette.rose, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PetUnlockChoice(label: String, threshold: Int, unlocked: Boolean, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(enabled = unlocked, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) palette.surfaceAlt else palette.surface,
        border = BorderStroke(1.dp, if (selected) palette.rose else palette.border)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (unlocked) icon else Icons.Default.Lock, null, tint = if (unlocked) palette.rose else palette.muted)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = palette.ink, fontWeight = FontWeight.Bold)
                Text(if (unlocked) "已解锁" else "累计 ¥$threshold 解锁", color = palette.muted, fontSize = 11.sp)
            }
            if (selected) Icon(Icons.Default.CheckCircle, "已使用", tint = palette.rose)
        }
    }
}

private fun formatMilestone(value: Long): String = when {
    value >= 10_000_000L -> "${value / 10_000_000}亿"
    value >= 10_000L -> "${value / 10_000}万"
    else -> "¥$value"
}

@Composable
private fun PetPill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = color.copy(alpha = 0.12f), border = BorderStroke(1.dp, color.copy(alpha = 0.28f))) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PetFeedbackBubble(message: String) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = palette.surfaceAlt,
        border = BorderStroke(1.dp, palette.pink.copy(alpha = 0.4f)),
        shadowElevation = 5.dp
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            MascotArt(36.dp)
            Spacer(Modifier.width(8.dp))
            Text(message, modifier = Modifier.weight(1f), color = palette.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.Favorite, null, tint = palette.pink, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun rememberAssetBitmap(path: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(path) {
        runCatching {
            context.assets.open(path).use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() }
        }.getOrNull()
    }
}

@Composable
private fun PetAssetCrop(asset: String, sourceX: Int, sourceY: Int, sourceWidth: Int, sourceHeight: Int, modifier: Modifier = Modifier) {
    val bitmap = rememberAssetBitmap(asset)
    if (bitmap == null) {
        Box(modifier.background(LocalPlushPalette.current.surfaceAlt))
        return
    }
    Canvas(modifier) {
        val safeX = sourceX.coerceIn(0, bitmap.width - 1)
        val safeY = sourceY.coerceIn(0, bitmap.height - 1)
        val width = sourceWidth.coerceIn(1, bitmap.width - safeX)
        val height = sourceHeight.coerceIn(1, bitmap.height - safeY)
        drawImage(
            image = bitmap,
            srcOffset = androidx.compose.ui.unit.IntOffset(safeX, safeY),
            srcSize = androidx.compose.ui.unit.IntSize(width, height),
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
        )
    }
}
