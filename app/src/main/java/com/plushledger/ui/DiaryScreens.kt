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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.plushledger.BuildConfig
import com.plushledger.R
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val diaryMoods = listOf("开心", "小确幸", "元气", "安静", "治愈")

@Composable
fun DiaryScreen(userId: String, quotes: List<String>, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPlushPalette.current
    val store = remember(userId) { DiaryStore(context.applicationContext, userId) }
    var entries by remember(userId) { mutableStateOf(store.load()) }
    val today = remember { LocalDate.now() }
    val existing = entries.firstOrNull { it.date == today.toString() }
    var text by rememberSaveable(userId) { mutableStateOf(existing?.text.orEmpty()) }
    var mood by rememberSaveable(userId) { mutableStateOf(existing?.mood ?: "开心") }
    var sharePreview by remember { mutableStateOf<Bitmap?>(null) }
    var savedNotice by rememberSaveable { mutableStateOf(false) }
    val shareQuote = remember(mood, quotes) { matchingQuote(mood, quotes) }

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
                    val shareText = text.trim().ifBlank { existing?.text ?: "今天也值得被温柔记录。" }
                    sharePreview = DiaryShareCard.create(context, today, mood, shareText, shareQuote)
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
                        Text(today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)), color = palette.ink, fontSize = 15.sp)
                        Spacer(Modifier.height(24.dp))
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.88f)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("每一次记录，", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("都是和未来的温柔约定呀～", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("今日心情  ♥ $mood", color = palette.pink, fontSize = 12.sp)
                            }
                        }
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
                        Text(today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)), color = palette.muted, fontSize = 13.sp)
                    }
                    Text("填写状态", color = palette.muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(diaryMoods) { item ->
                        Surface(
                            modifier = Modifier.clickable { mood = item },
                            shape = RoundedCornerShape(18.dp),
                            color = if (mood == item) Color(0xFFFFE4EC) else Color.White,
                            border = BorderStroke(1.dp, if (mood == item) palette.pink else palette.border)
                        ) { Text(item, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), color = if (mood == item) palette.pink else palette.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
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
                PlushButton("保存日记", Icons.Default.Save, Modifier.fillMaxWidth(), enabled = text.trim().isNotBlank(), color = palette.pink) {
                    entries = store.saveToday(text, mood)
                    savedNotice = true
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
            items(entries.take(20), key = { it.date }) { entry -> DiaryHistoryCard(entry) }
        }
    }

    sharePreview?.let { bitmap ->
        Dialog(onDismissRequest = { sharePreview = null }) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFFFFFCF7), border = BorderStroke(1.5.dp, Color(0xFFFFDAB4)), shadowElevation = 18.dp) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap.asImageBitmap(), "日记分享卡片", Modifier.fillMaxWidth().height(430.dp), contentScale = ContentScale.Fit)
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
private fun DiaryHistoryCard(entry: DiaryEntry) {
    val palette = LocalPlushPalette.current
    Surface(shape = RoundedCornerShape(20.dp), color = palette.surface, border = BorderStroke(1.dp, palette.border), shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            MascotArt(48.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.date, color = palette.muted, fontSize = 11.sp)
                Text(entry.text, color = palette.ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFE9EF)) {
                Text(entry.mood, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = palette.pink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun matchingQuote(mood: String, quotes: List<String>): String {
    val preferred = when (mood) {
        "安静" -> "安静不是空白，是心在慢慢整理自己。"
        "治愈" -> "慢一点也没关系，温柔本身就是力量。"
        "元气" -> "认真生活的人，日子总会悄悄发光。"
        "小确幸" -> "平凡的一天，也值得被温柔记录。"
        else -> "今天的快乐，值得好好收藏。"
    }
    return quotes.firstOrNull { quote -> quote.contains(mood) } ?: preferred
}

private object DiaryShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1440

    fun create(context: Context, date: LocalDate, mood: String, diary: String, quote: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.rgb(255, 250, 242))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val brown = AndroidColor.rgb(82, 61, 48)
        val peach = AndroidColor.rgb(255, 132, 103)
        val border = AndroidColor.rgb(248, 218, 180)

        paint.color = AndroidColor.WHITE
        canvas.drawRoundRect(RectF(115f, 205f, 965f, 1190f), 62f, 62f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = border
        canvas.drawRoundRect(RectF(115f, 205f, 965f, 1190f), 62f, 62f, paint)
        paint.style = Paint.Style.FILL

        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.brand_logo_transparent)
        canvas.drawBitmap(logo, Rect(0, 0, logo.width, logo.height * 3 / 5), RectF(300f, 38f, 420f, 148f), paint)
        paint.color = brown
        paint.textSize = 58f
        paint.isFakeBoldText = true
        canvas.drawText("绒绒记账", 440f, 112f, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 26f
        paint.isFakeBoldText = false
        canvas.drawText("♥  温暖每一笔，记录每一天  ♥", WIDTH / 2f, 170f, paint)

        paint.textSize = 34f
        canvas.drawText(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)), WIDTH / 2f, 285f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 46f
        paint.isFakeBoldText = true
        drawWrappedText(canvas, quote, 215f, 405f, 680f, 68f, paint, 4)
        paint.isFakeBoldText = false
        paint.textSize = 29f
        paint.color = brown
        canvas.drawText("今日状态 ·", 215f, 690f, paint)
        paint.color = peach
        canvas.drawText(mood, 395f, 690f, paint)

        val mascot = BitmapFactory.decodeResource(context.resources, R.drawable.diary_card_mascot)
        canvas.drawBitmap(mascot, null, RectF(355f, 720f, 885f, 1112f), paint)

        paint.color = AndroidColor.rgb(130, 103, 83)
        paint.textSize = 25f
        drawWrappedText(canvas, diary, 200f, 1140f, 480f, 36f, paint, 2)

        val qr = qrBitmap(downloadUrl(), 220)
        paint.color = AndroidColor.WHITE
        canvas.drawRoundRect(RectF(720f, 1040f, 975f, 1340f), 32f, 32f, paint)
        canvas.drawBitmap(qr, null, RectF(738f, 1055f, 958f, 1275f), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = brown
        paint.textSize = 21f
        canvas.drawText("扫码下载绒绒记账", 848f, 1315f, paint)
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

    private fun downloadUrl(): String =
        "https://github.com/juanyun-ai/plush-ledger-android/releases/download/v${BuildConfig.VERSION_NAME}/rongrong-ledger-${BuildConfig.VERSION_NAME}-debug.apk"

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
