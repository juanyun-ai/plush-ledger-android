package com.plushledger.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plushledger.data.LedgerState
import java.time.LocalDate
import kotlinx.coroutines.delay

private data class PetFeature(
    val key: String,
    val title: String,
    val asset: String,
    val primaryAction: String
)

private val petFeatures = listOf(
    PetFeature("outfit", "今日穿搭", "pet_refs/吉祥物扩展UI/今日穿搭.jpg", "换上这套"),
    PetFeature("skin", "主题皮肤", "pet_refs/吉祥物扩展UI/主题皮肤.jpg", "应用皮肤"),
    PetFeature("room", "小窝布置", "pet_refs/吉祥物扩展UI/小窝布置.jpg", "保存布置"),
    PetFeature("diary", "绒绒日记", "pet_refs/吉祥物扩展UI/绒绒日记.jpg", "保存日记"),
    PetFeature("story", "成长故事", "pet_refs/吉祥物扩展UI/成长故事.jpg", "查看故事"),
    PetFeature("noise", "白噪音陪伴", "pet_refs/吉祥物扩展UI/白噪音陪伴.jpg", "开始陪伴"),
    PetFeature("frame", "头像框", "pet_refs/吉祥物扩展UI/头像框.jpg", "使用头像框"),
    PetFeature("emoji", "表情包", "pet_refs/吉祥物扩展UI/表情包.jpg", "保存表情"),
    PetFeature("title", "专属称号", "pet_refs/吉祥物扩展UI/专属称号.jpg", "使用称号")
)

@Composable
fun PetRongRongScreen(ledger: LedgerState, onBack: () -> Unit, onRecord: () -> Unit) {
    var feature by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = feature != null) { feature = null }
    val selected = petFeatures.firstOrNull { it.key == feature }
    if (selected != null) {
        PetFeatureReferenceScreen(selected) { feature = null }
    } else {
        PetHomeScreen(ledger = ledger, onBack = onBack, onFeature = { feature = it }, onRecord = onRecord)
    }
}

@Composable
private fun PetHomeScreen(
    ledger: LedgerState,
    onBack: () -> Unit,
    onFeature: (String) -> Unit,
    onRecord: () -> Unit
) {
    val palette = LocalPlushPalette.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pet_rongrong_v2", Context.MODE_PRIVATE) }
    var companionValue by rememberSaveable { mutableIntStateOf(prefs.getInt("companion", 280)) }
    var mood by rememberSaveable { mutableStateOf(prefs.getString("mood", "开心") ?: "开心") }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var interactedToday by rememberSaveable {
        mutableStateOf(prefs.getString("interaction_day", "") == LocalDate.now().toString())
    }
    val level = (companionValue / 100 + 2).coerceIn(1, 10)
    val levelTarget = level * 100
    val progress = (companionValue.toFloat() / levelTarget).coerceIn(0f, 1f)

    fun interact(label: String, newMood: String) {
        if (interactedToday) {
            feedback = "今天已经互动过啦，明天绒绒再等你"
            return
        }
        companionValue = (companionValue + 10).coerceAtMost(999)
        mood = newMood
        interactedToday = true
        prefs.edit()
            .putInt("companion", companionValue)
            .putString("mood", mood)
            .putString("interaction_day", LocalDate.now().toString())
            .apply()
        feedback = "$label，陪伴值 +10"
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2_400)
            feedback = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = palette.ink) }
                Text("宠物专题", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                PetPill("绒绒小窝", palette.pink)
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = palette.surface, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border), shadowElevation = 4.dp) {
                    Icon(Icons.Default.Favorite, contentDescription = "喜欢绒绒", tint = palette.pink, modifier = Modifier.padding(10.dp).size(20.dp))
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFF6EC),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.border),
                shadowElevation = 5.dp
            ) {
                Box(Modifier.fillMaxWidth().height(190.dp)) {
                    PetAssetCrop(
                        asset = "pet_refs/宠物专题.jpg",
                        sourceX = 430,
                        sourceY = 150,
                        sourceWidth = 500,
                        sourceHeight = 410,
                        modifier = Modifier.fillMaxSize().align(Alignment.CenterEnd)
                    )
                    Column(Modifier.fillMaxWidth(0.62f).padding(18.dp)) {
                        Text("你好呀，我是绒绒", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
                        Text("今天也要一起好好记录哦～", color = palette.muted, fontSize = 11.sp, maxLines = 1)
                        Spacer(Modifier.height(16.dp))
                        Surface(modifier = Modifier.fillMaxWidth(0.78f), shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.9f)) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("陪伴值", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Lv.$level", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(7.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)),
                                    color = palette.pink,
                                    trackColor = palette.pink.copy(alpha = 0.2f)
                                )
                                Text("$companionValue / $levelTarget", color = palette.muted, fontSize = 9.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("今日心情", color = palette.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    PetPill("♥ $mood", palette.pink)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = palette.surface, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border), shadowElevation = 4.dp) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("轻松互动", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (interactedToday) "今日已互动" else "点击互动展开 · 每天最多 1 次", color = palette.muted, fontSize = 10.sp, maxLines = 1)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        val actions = listOf(
                            Triple("摸一摸", 60, "开心"),
                            Triple("抱一抱", 220, "幸福"),
                            Triple("互动", 390, "元气"),
                            Triple("揉一揉", 575, "放松"),
                            Triple("戳一戳", 750, "惊喜")
                        )
                        actions.forEach { (label, sourceX, nextMood) ->
                            Surface(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { interact("绒绒回应了$label", nextMood) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (interactedToday) palette.surfaceAlt.copy(alpha = 0.5f) else palette.surfaceAlt,
                                border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
                            ) {
                                Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    PetAssetCrop("pet_refs/宠物专题.jpg", sourceX, 640, 135, 105, Modifier.fillMaxWidth().aspectRatio(1.2f))
                                }
                            }
                        }
                    }
                    feedback?.let {
                        Spacer(Modifier.height(10.dp))
                        PetFeedbackBubble(it)
                    }
                }
            }
        }
        item {
            PetImageSection("绒绒衣橱 & 小窝", listOf(
                PetCropCard("outfit", 55, 955, 270, 165),
                PetCropCard("skin", 335, 955, 270, 165),
                PetCropCard("room", 615, 955, 270, 165)
            ), onFeature)
        }
        item {
            PetImageSection("绒绒日记 & 故事", listOf(
                PetCropCard("diary", 55, 1205, 270, 165),
                PetCropCard("story", 335, 1205, 270, 165),
                PetCropCard("noise", 615, 1205, 270, 165)
            ), onFeature)
        }
        item {
            PetImageSection("陪伴奖励", listOf(
                PetCropCard("frame", 55, 1450, 270, 145),
                PetCropCard("emoji", 335, 1450, 270, 145),
                PetCropCard("title", 615, 1450, 270, 145)
            ), onFeature)
        }
    }
}

private data class PetCropCard(val key: String, val x: Int, val y: Int, val width: Int, val height: Int)

@Composable
private fun PetImageSection(title: String, cards: List<PetCropCard>, onFeature: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(shape = RoundedCornerShape(24.dp), color = palette.surface, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border), shadowElevation = 4.dp) {
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
                        modifier = Modifier.weight(1f).aspectRatio(card.width.toFloat() / card.height).clip(RoundedCornerShape(16.dp)).clickable { onFeature(card.key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PetFeatureReferenceScreen(feature: PetFeature, onBack: () -> Unit) {
    val palette = LocalPlushPalette.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pet_rongrong_features", Context.MODE_PRIVATE) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var diaryText by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onBack)
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2_200)
            feedback = null
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth()) {
                PetAssetCrop(
                    asset = feature.asset,
                    sourceX = 0,
                    sourceY = 58,
                    sourceWidth = 941,
                    sourceHeight = 1614,
                    modifier = Modifier.fillMaxWidth().aspectRatio(941f / 1614f).clickable {
                        prefs.edit().putString("selected_${feature.key}", LocalDate.now().toString()).apply()
                        feedback = if (feature.key == "noise") "白噪音陪伴已切换" else "${feature.primaryAction}已保存"
                    }
                )
                Box(
                    Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp).size(64.dp)
                        .align(Alignment.TopStart)
                        .semantics { contentDescription = "返回" }
                        .clickable(onClick = onBack)
                )
            }
        }
        if (feature.key == "diary") {
            item {
                Surface(Modifier.padding(horizontal = 18.dp), shape = RoundedCornerShape(22.dp), color = palette.surface, border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("记录今天的心情", color = palette.ink, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(diaryText, { diaryText = it.take(300) }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("写下一点温柔的小事") })
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable {
                                prefs.edit().putString("diary_${LocalDate.now()}", diaryText.trim()).apply()
                                feedback = "今天的绒绒日记已保存"
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = palette.pink
                        ) {
                            Text("保存日记", modifier = Modifier.padding(12.dp), color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
        feedback?.let { item { Box(Modifier.padding(horizontal = 18.dp)) { PetFeedbackBubble(it) } } }
    }
}

@Composable
private fun PetPill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = color.copy(alpha = 0.12f), border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.28f))) {
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
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.pink.copy(alpha = 0.38f)),
        shadowElevation = 6.dp
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            MascotArt(38.dp)
            Spacer(Modifier.width(8.dp))
            Text(message, modifier = Modifier.weight(1f), color = palette.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.pink, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun rememberAssetBitmap(path: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(path) {
        runCatching {
            context.assets.open(path).use(BitmapFactory::decodeStream).asImageBitmap()
        }.getOrNull()
    }
}

@Composable
private fun PetAssetCrop(
    asset: String,
    sourceX: Int,
    sourceY: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberAssetBitmap(asset)
    if (bitmap == null) {
        Box(modifier.background(LocalPlushPalette.current.surfaceAlt))
        return
    }
    Canvas(modifier) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(sourceX.coerceAtLeast(0), sourceY.coerceAtLeast(0)),
            srcSize = IntSize(
                sourceWidth.coerceAtMost(bitmap.width - sourceX.coerceAtLeast(0)),
                sourceHeight.coerceAtMost(bitmap.height - sourceY.coerceAtLeast(0))
            ),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt())
        )
    }
}
