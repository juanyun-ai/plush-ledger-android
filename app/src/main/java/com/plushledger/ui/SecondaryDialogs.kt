package com.plushledger.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                    MascotArt(86.dp)
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
        Text("仅支持你从微信或支付宝主动导出的 CSV 文件。应用不会读取你的支付账号、密码或云端账单。", color = palette.muted, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceOption("微信账单", provider == "微信", palette.moss, R.drawable.art_wechat, Modifier.weight(1f)) { onProvider("微信") }
            SourceOption("支付宝账单", provider == "支付宝", palette.blue, null, Modifier.weight(1f)) { onProvider("支付宝") }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.rose) }
            TextButton(onClick = onChoose) { Text("选择 CSV", color = palette.moss, fontWeight = FontWeight.Black) }
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
        Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (image != null) Image(painterResource(image), null, Modifier.size(42.dp), contentScale = ContentScale.Fit)
            else Surface(shape = CircleShape, color = palette.blue.copy(alpha = 0.13f)) { Text("支", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = palette.blue, fontWeight = FontWeight.Black, fontSize = 24.sp) }
            Spacer(Modifier.height(7.dp))
            Text(label, color = if (selected) color else palette.ink, fontWeight = FontWeight.Black)
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
    val options = listOf("人民币  ¥", "美元  $", "欧元  €", "日元  ¥", "英镑  £", "新加坡币  S$")
    PlushModalFrame("货币单位", onDismiss, showClose = true) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onChoose(option) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (current == option) Color(0xFFFFF1DD) else Color.White,
                    border = BorderStroke(1.dp, if (current == option) palette.rose else palette.border)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (current == option) Icons.Default.CheckCircle else Icons.Default.CurrencyExchange, null, tint = if (current == option) palette.rose else palette.muted)
                        Spacer(Modifier.width(12.dp))
                        Text(option, modifier = Modifier.weight(1f), color = palette.ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

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

private data class ThemeOption(val key: String, val label: String, val image: Int, val color: Color)

@Composable
fun ThemePickerDialog(current: String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val palette = LocalPlushPalette.current
    val options = listOf(
        ThemeOption("warm", "奶油黄（经典款）", R.drawable.theme_warm, Color(0xFFFFA126)),
        ThemeOption("pink", "樱花粉", R.drawable.theme_pink, Color(0xFFFF8DAE)),
        ThemeOption("green", "薄荷绿", R.drawable.theme_green, Color(0xFF79C98D)),
        ThemeOption("blue", "天空蓝", R.drawable.theme_blue, Color(0xFF7BB6F3)),
        ThemeOption("purple", "薰衣草紫", R.drawable.theme_purple, Color(0xFFA58AE8)),
        ThemeOption("orange", "蜜桃橙", R.drawable.theme_orange, Color(0xFFFF9560)),
        ThemeOption("brown", "可可棕", R.drawable.theme_brown, Color(0xFF9B6B4B)),
        ThemeOption("mono", "牛奶白", R.drawable.theme_white, Color(0xFFE7D7C7))
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.91f).heightIn(max = 760.dp), shape = RoundedCornerShape(34.dp), color = Color(0xFFFFFCF7), border = BorderStroke(2.dp, Color(0xFFFFD8A0)), shadowElevation = 22.dp) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("绒绒 · 主题", color = palette.ink, fontWeight = FontWeight.Black, fontSize = 27.sp)
                        Text("不同心情，不同颜色", color = palette.muted, fontSize = 13.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Close, "关闭", tint = palette.muted) }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(options.chunked(2)) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { option ->
                                Surface(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { onChoose(option.key) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (current == option.key) option.color.copy(alpha = 0.13f) else Color.White,
                                    border = BorderStroke(1.5.dp, if (current == option.key) option.color else palette.border)
                                ) {
                                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Image(painterResource(option.image), option.label, Modifier.fillMaxWidth().height(104.dp), contentScale = ContentScale.Crop)
                                        Spacer(Modifier.height(4.dp))
                                        Text(option.label, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
