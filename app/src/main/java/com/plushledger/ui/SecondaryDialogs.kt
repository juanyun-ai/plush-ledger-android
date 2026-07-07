package com.plushledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.plushledger.R
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun PlushModalFrame(
    title: String,
    onDismiss: () -> Unit,
    showClose: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = LocalPlushPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 760.dp),
            shape = RoundedCornerShape(34.dp),
            color = Color(0xFFFFFCF7),
            border = BorderStroke(2.dp, Color(0xFFFFD8A0)),
            shadowElevation = 22.dp
        ) {
            Box {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MascotArt(86.dp, R.drawable.mascot_action_heart)
                    Text(title, color = palette.ink, fontWeight = FontWeight.Black, fontSize = 27.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    content()
                }
                if (showClose) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Icon(Icons.Default.Close, "关闭", tint = palette.muted)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = LocalPlushPalette.current
    PlushModalFrame(title, onDismiss) {
        Text(message, color = palette.ink, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(22.dp)) { Text("取消", color = palette.ink) }
            PlushButton(confirmText, Icons.Default.CheckCircle, Modifier.weight(1f), enabled = confirmEnabled, color = palette.rose, onClick = onConfirm)
        }
    }
}

@Composable
fun ExportDataDialog(fileName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val palette = LocalPlushPalette.current
    PlushModalFrame("确认导出数据", onDismiss) {
        Text("导出文件将包含账目时间、分类、账户、金额、备注等完整字段。请选择你信任的本机位置保存。", color = palette.muted, fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFF5E8), border = BorderStroke(1.dp, Color(0xFFFFD8A0))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.UploadFile, null, tint = palette.coral, modifier = Modifier.size(35.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("文件名", color = palette.muted, fontSize = 12.sp)
                    Text(fileName, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.Description, null, tint = palette.moss)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("取消") }
            PlushButton("确认导出", Icons.Default.UploadFile, Modifier.weight(1f), color = palette.rose, onClick = onConfirm)
        }
    }
}

@Composable
fun BillSourceDialog(provider: String, onProvider: (String) -> Unit, onDismiss: () -> Unit, onChoose: () -> Unit) {
    val palette = LocalPlushPalette.current
    PlushModalFrame("选择账单来源", onDismiss, showClose = true) {
        Text("支持绒绒 App / 小程序导出的 CSV、JSON 备份，也支持微信或支付宝主动导出的支付账单 CSV。文件只在本机解析。", color = palette.muted, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceOption("绒绒备份", provider == "绒绒备份", palette.rose, R.drawable.brand_logo, Modifier.weight(1f)) { onProvider("绒绒备份") }
            SourceOption("微信账单", provider == "微信", palette.moss, R.drawable.art_wechat, Modifier.weight(1f)) { onProvider("微信") }
            SourceOption("支付宝账单", provider == "支付宝", palette.blue, null, Modifier.weight(1f)) { onProvider("支付宝") }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.rose) }
            TextButton(onClick = onChoose) { Text("选择文件", color = palette.moss, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun SourceOption(label: String, selected: Boolean, color: Color, image: Int?, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) color.copy(alpha = 0.08f) else Color.White,
        border = BorderStroke(1.5.dp, if (selected) color else palette.border),
        shadowElevation = if (selected) 5.dp else 1.dp
    ) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (image != null) Image(painterResource(image), null, Modifier.size(38.dp), contentScale = ContentScale.Fit)
            else Surface(shape = CircleShape, color = palette.blue.copy(alpha = 0.13f)) { Text("支", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = palette.blue, fontWeight = FontWeight.Black, fontSize = 24.sp) }
            Spacer(Modifier.height(7.dp))
            Text(label, color = if (selected) color else palette.ink, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) Icon(Icons.Default.CheckCircle, "已选择", tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ReminderDialog(enabled: Boolean, onDismiss: () -> Unit, onChoose: (Boolean) -> Unit) {
    val palette = LocalPlushPalette.current
    PlushModalFrame("记账提醒", onDismiss) {
        Text("开启后，应用会保存每天 21:00 作为记账提醒时间。获得系统通知权限后会按此时间提醒。", color = palette.ink, fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onChoose(false) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("关闭提醒") }
            PlushButton(if (enabled) "保持开启" else "开启提醒", Icons.Default.Notifications, Modifier.weight(1f), color = palette.rose) { onChoose(true) }
        }
    }
}

@Composable
fun CurrencyDialog(current: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    val options = listOf(
        CurrencyOption("🇨🇳", "人民币  ¥"),
        CurrencyOption("🇺🇸", "美元  $"),
        CurrencyOption("🇪🇺", "欧元  €"),
        CurrencyOption("🇯🇵", "日元  ¥"),
        CurrencyOption("🇬🇧", "英镑  £"),
        CurrencyOption("🇸🇬", "新加坡币  S$")
    )
    PlushModalFrame("货币单位", onDismiss, showClose = true) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                val selected = current == option.label
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onChoose(option.label) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) Color(0xFFFFF1DD) else Color.White,
                    border = BorderStroke(1.dp, if (selected) palette.rose else palette.border)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(option.flag, fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(option.label, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold)
                        Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.CurrencyExchange, null, tint = if (selected) palette.rose else palette.muted)
                    }
                }
            }
        }
    }
}

private data class CurrencyOption(val flag: String, val label: String)

@Composable
fun DownloadLineDialog(current: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    PlushModalFrame("更新下载线路", onDismiss) {
        Text("国内网络访问 GitHub 可能较慢。发布端优先使用 Supabase/国内对象存储地址，GitHub 仅作为备用归档。", color = palette.muted, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(15.dp))
        listOf(
            Triple("国内优先", Icons.Default.Business, palette.coral),
            Triple("GitHub 备用", Icons.Default.Cloud, palette.moss),
            Triple("自动选择", Icons.Default.Paid, palette.rose)
        ).forEach { (label, icon, color) ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(22.dp)).clickable { onChoose(label) },
                shape = RoundedCornerShape(22.dp),
                color = if (current == label) color.copy(alpha = 0.10f) else Color.White,
                border = BorderStroke(1.dp, if (current == label) color else palette.border),
                shadowElevation = 3.dp
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = color)
                    Spacer(Modifier.width(13.dp))
                    Text(label, modifier = Modifier.weight(1f), color = color, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    if (current == label) Icon(Icons.Default.Check, "当前线路", tint = color)
                }
            }
        }
    }
}

@Composable
fun LicenseDialog(onDismiss: () -> Unit) {
    val palette = LocalPlushPalette.current
    PlushModalFrame("开源许可", onDismiss) {
        Text("绒绒记账当前以 PolyForm Noncommercial License 发布，可学习、研究、测试和个人非商业使用。商业使用需另行取得授权。", color = palette.ink, fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(20.dp))
        PlushButton("知道了", Icons.Default.CheckCircle, Modifier.fillMaxWidth(0.7f), color = palette.rose, onClick = onDismiss)
    }
}

@Composable
fun ContactSupportDialog(email: String, onDismiss: () -> Unit, onOpenEmail: () -> Unit) {
    val palette = LocalPlushPalette.current
    PlushModalFrame("联系绒绒记账", onDismiss) {
        Text("将打开本机默认邮箱，收件人会自动填入\n$email", color = palette.ink, fontSize = 15.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("取消") }
            PlushButton("打开邮箱", Icons.Default.Email, Modifier.weight(1f), color = palette.rose, onClick = onOpenEmail)
        }
    }
}

@Composable
fun ThemePickerDialog(current: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("经典") }
    val selectedSpec = plushThemeSpec(current) ?: plushThemeSpec("warm")
    val options = remember(query, typeFilter) {
        plushThemeCatalog.filter { spec ->
            spec.type == typeFilter && spec.matchesThemeQuery(query)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = 820.dp), shape = RoundedCornerShape(34.dp), color = Color(0xFFFFFCF7), border = BorderStroke(2.dp, Color(0xFFFFD8A0)), shadowElevation = 22.dp) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hi~", color = Color(0xFFFFA126), fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("绒绒 · 主题色卡", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 30.sp)
                        Text("♥  经典 / 国内 / 国外  ♥", color = Color(0xFF9A7865), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Close, "关闭", tint = palette.muted) }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(24) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = palette.muted) },
                    placeholder = { Text("搜索地区、色卡名或关键词", color = palette.muted) },
                    shape = RoundedCornerShape(22.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("经典", "国内", "国外").forEach { filter ->
                        ThemeFilterPill(filter, selected = typeFilter == filter, modifier = Modifier.weight(1f)) { typeFilter = filter }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = (selectedSpec?.secondary ?: palette.rose).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, (selectedSpec?.primary ?: palette.rose).copy(alpha = 0.32f))
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text("当前主题 · $typeFilter ${options.size} 款", color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(plushThemeName(current), color = palette.ink, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("下方长条只负责快速切换；主色和辅助色统一在这里查看。", color = palette.muted, fontSize = 12.sp)
                        selectedSpec?.let { spec ->
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeHexChip("主色", spec.primary)
                                ThemeHexChip("辅助", spec.secondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (options.isEmpty()) {
                        item {
                            Text("没有找到匹配的色卡，换个关键词试试，或切到其他分组。", modifier = Modifier.fillMaxWidth().padding(18.dp), color = palette.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                    items(options) { option ->
                        ThemeCatalogRow(option, current == option.key) { onChoose(option.key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCatalogRow(option: PlushThemeSpec, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) option.primary.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, if (selected) option.primary.copy(alpha = 0.42f) else palette.border)
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(option.primary))
            Spacer(Modifier.width(8.dp))
            Text(option.region, color = palette.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(option.name, color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(option.logic, color = palette.muted, fontSize = 10.sp, modifier = Modifier.weight(1.18f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.CheckCircle, "已选中", tint = option.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ThemeFilterPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier.height(40.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) palette.rose else Color.White,
        border = BorderStroke(1.dp, if (selected) palette.rose else palette.border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Color.White else palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ThemeCatalogCard(option: PlushThemeSpec, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier.height(236.dp).clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, if (selected) option.primary else option.secondary.copy(alpha = 0.68f)),
        shadowElevation = if (selected) 8.dp else 2.dp
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(option.secondary.copy(alpha = 0.34f), Color.White, option.primary.copy(alpha = 0.10f))))
                .padding(11.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.82f), border = BorderStroke(1.dp, option.secondary.copy(alpha = 0.56f))) {
                    Text(option.type, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = palette.ink, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Icon(Icons.Default.CheckCircle, "已选中", tint = option.primary, modifier = Modifier.size(19.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                ThemeMascotPreview(
                    color = option.primary,
                    surface = option.secondary.copy(alpha = 0.86f),
                    modifier = Modifier.size(102.dp)
                )
            }
            Text("${option.region} · ${option.name}", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(option.logic, color = palette.muted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeHexChip("主", option.primary, Modifier.weight(1f))
                ThemeHexChip("辅", option.secondary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeHexChip(label: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.76f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.46f))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(color))
            Text(
                "$label ${themeHex(color)}",
                color = palette.ink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun PlushThemeSpec.matchesThemeQuery(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return true
    val lower = trimmed.lowercase(Locale.ROOT)
    return listOf(type, region, name, logic, keywords, key).any { it.contains(trimmed, ignoreCase = true) || it.lowercase(Locale.ROOT).contains(lower) }
}

private fun themeHex(color: Color): String {
    val red = (color.red * 255).roundToInt().coerceIn(0, 255)
    val green = (color.green * 255).roundToInt().coerceIn(0, 255)
    val blue = (color.blue * 255).roundToInt().coerceIn(0, 255)
    return String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
}

@Composable
private fun ThemeMascotPreview(color: Color, surface: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bookLeft = w * 0.18f
        val bookTop = h * 0.07f
        val bookW = w * 0.58f
        val bookH = h * 0.73f
        drawOval(
            color = Color(0x22000000),
            topLeft = Offset(w * 0.2f, h * 0.74f),
            size = Size(w * 0.66f, h * 0.17f)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(surface.copy(alpha = 0.98f), color.copy(alpha = 0.9f))),
            topLeft = Offset(bookLeft, bookTop),
            size = Size(bookW, bookH),
            cornerRadius = CornerRadius(w * 0.13f, w * 0.13f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.42f),
            topLeft = Offset(bookLeft + w * 0.05f, bookTop + h * 0.04f),
            size = Size(bookW * 0.78f, bookH * 0.18f),
            cornerRadius = CornerRadius(w * 0.1f, w * 0.1f)
        )
        repeat(2) { index ->
            drawCircle(Color.White, radius = w * 0.045f, center = Offset(bookLeft + w * 0.03f, bookTop + h * (0.22f + index * 0.2f)))
            drawLine(Color(0xFFEADCCB), Offset(bookLeft - w * 0.05f, bookTop + h * (0.22f + index * 0.2f)), Offset(bookLeft + w * 0.06f, bookTop + h * (0.22f + index * 0.2f)), strokeWidth = w * 0.025f, cap = StrokeCap.Round)
        }
        drawCircle(Color(0xFF4D362B), radius = w * 0.025f, center = Offset(bookLeft + bookW * 0.38f, bookTop + bookH * 0.42f))
        drawCircle(Color(0xFF4D362B), radius = w * 0.025f, center = Offset(bookLeft + bookW * 0.64f, bookTop + bookH * 0.42f))
        drawLine(Color(0xFF4D362B), Offset(bookLeft + bookW * 0.48f, bookTop + bookH * 0.53f), Offset(bookLeft + bookW * 0.57f, bookTop + bookH * 0.53f), strokeWidth = w * 0.018f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFFF9FA6).copy(alpha = 0.65f), radius = w * 0.025f, center = Offset(bookLeft + bookW * 0.29f, bookTop + bookH * 0.51f))
        drawCircle(Color(0xFFFF9FA6).copy(alpha = 0.65f), radius = w * 0.025f, center = Offset(bookLeft + bookW * 0.72f, bookTop + bookH * 0.51f))
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFFFF5E8))),
            topLeft = Offset(bookLeft - w * 0.08f, bookTop + bookH * 0.49f),
            size = Size(w * 0.25f, h * 0.19f),
            cornerRadius = CornerRadius(w * 0.09f, w * 0.09f)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(bookLeft + bookW * 0.78f, bookTop + bookH * 0.32f),
            size = Size(w * 0.18f, h * 0.17f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
        drawCircle(Color.White.copy(alpha = 0.9f), radius = w * 0.036f, center = Offset(bookLeft + bookW * 0.91f, bookTop + bookH * 0.405f))
        drawCircle(color, radius = w * 0.14f, center = Offset(w * 0.73f, h * 0.72f))
        drawCircle(Color(0xFFFFD16A), radius = w * 0.105f, center = Offset(w * 0.68f, h * 0.72f))
        drawCircle(Color(0xFF79C6A2), radius = w * 0.078f, center = Offset(w * 0.79f, h * 0.66f))
        drawCircle(Color.White, radius = w * 0.105f, center = Offset(w * 0.66f, h * 0.73f))
        drawLine(color, Offset(w * 0.61f, h * 0.72f), Offset(w * 0.65f, h * 0.77f), strokeWidth = w * 0.025f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.65f, h * 0.77f), Offset(w * 0.73f, h * 0.67f), strokeWidth = w * 0.025f, cap = StrokeCap.Round)
    }
}
