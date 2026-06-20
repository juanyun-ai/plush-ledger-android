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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plushledger.R

@Composable
fun ReferenceHeader(
    title: String,
    subtitle: String,
    month: String? = null,
    branded: Boolean = false,
    mascot: Boolean = false,
    onMonth: (() -> Unit)? = null,
    trailingAction: (() -> Unit)? = null
) {
    val palette = LocalPlushPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (branded || mascot) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_transparent),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            if (branded) {
                Image(
                    painter = painterResource(R.drawable.brand_wordmark),
                    contentDescription = "绒绒记账",
                    modifier = Modifier.fillMaxWidth(0.68f).height(30.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = palette.ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(7.dp))
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.coral.copy(alpha = 0.72f), modifier = Modifier.size(15.dp))
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = palette.rose.copy(alpha = 0.35f), modifier = Modifier.size(12.dp))
                }
            }
            Text(subtitle, color = palette.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (month != null) {
            MonthPill(month, onMonth)
        }
        if (trailingAction != null) {
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = trailingAction) {
                Icon(Icons.Default.Search, contentDescription = "搜索", tint = palette.ink)
            }
        }
    }
}

@Composable
fun MonthPill(month: String, onClick: (() -> Unit)?) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(15.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = palette.rose, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(month, color = palette.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = palette.muted, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
fun ReferenceSegment(
    items: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = palette.surfaceAlt,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { (key, label) ->
                val active = key == selected
                Box(
                    modifier = Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (active) palette.rose else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelected(key) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (active) androidx.compose.ui.graphics.Color.White else palette.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MascotArt(size: Dp = 88.dp) {
    val palette = LocalPlushPalette.current
    Box(Modifier.size(size)) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_transparent),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = palette.rose.copy(alpha = 0.62f),
            modifier = Modifier.size(size * 0.18f).align(Alignment.TopStart)
        )
        Icon(
            Icons.Default.Favorite,
            contentDescription = null,
            tint = palette.coral.copy(alpha = 0.68f),
            modifier = Modifier.size(size * 0.18f).align(Alignment.TopEnd)
        )
    }
}

@Composable
fun CategoryArt(name: String?, size: Dp = 48.dp) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = categoryArtColor(name).copy(alpha = 0.16f),
        border = BorderStroke(1.dp, categoryArtColor(name).copy(alpha = 0.22f)),
        shadowElevation = 3.dp
    ) {
        Icon(
            imageVector = categoryArtIcon(name),
            contentDescription = name,
            tint = categoryArtColor(name),
            modifier = Modifier.padding(size * 0.22f)
        )
    }
}

private fun categoryArtIcon(name: String?): ImageVector = when (name) {
    "餐饮" -> Icons.Default.Restaurant
    "早餐" -> Icons.Default.FreeBreakfast
    "正餐" -> Icons.Default.Restaurant
    "外卖" -> Icons.Default.DeliveryDining
    "奶茶咖啡", "奶茶", "咖啡" -> Icons.Default.LocalCafe
    "零食" -> Icons.Default.Cookie
    "聚餐" -> Icons.Default.Groups
    "交通", "通勤" -> Icons.Default.Commute
    "公交地铁" -> Icons.Default.DirectionsTransit
    "打车" -> Icons.Default.LocalTaxi
    "火车高铁" -> Icons.Default.Train
    "单车月卡" -> Icons.Default.DirectionsBike
    "购物", "日用百货" -> Icons.Default.ShoppingBasket
    "服饰鞋包" -> Icons.Default.Checkroom
    "数码配件" -> Icons.Default.Headphones
    "美妆个护" -> Icons.Default.Palette
    "日常", "生活用品", "住房", "居住" -> Icons.Default.Home
    "快递物流" -> Icons.Default.LocalShipping
    "话费网络" -> Icons.Default.PhoneAndroid
    "水电房租" -> Icons.Default.Bolt
    "娱乐", "影视会员" -> Icons.Default.Movie
    "游戏" -> Icons.Default.SportsEsports
    "旅游出行" -> Icons.Default.FlightTakeoff
    "兴趣爱好" -> Icons.Default.Palette
    "人情社交", "人情红包", "人情" -> Icons.Default.Redeem
    "请客送礼", "礼金" -> Icons.Default.CardGiftcard
    "恋爱约会", "社交活动" -> Icons.Default.Favorite
    "宠物", "宠物食品", "宠物用品" -> Icons.Default.Pets
    "宠物医疗" -> Icons.Default.Medication
    "学习工作", "书籍资料" -> Icons.Default.MenuBook
    "课程考试" -> Icons.Default.School
    "文具打印" -> Icons.Default.Print
    "软件工具" -> Icons.Default.Terminal
    "医疗健康", "就诊体检", "医疗" -> Icons.Default.LocalHospital
    "药品" -> Icons.Default.Medication
    "运动健身" -> Icons.Default.FitnessCenter
    "工资" -> Icons.Default.CardGiftcard
    "兼职", "理财" -> Icons.Default.Terminal
    "其他", "临时支出", "杂项备用", "未分类", "无法归类" -> Icons.Default.MoreHoriz
    else -> Icons.Default.HelpOutline
}

private fun categoryArtColor(name: String?): Color = when (name) {
    "餐饮", "早餐", "正餐", "外卖", "奶茶咖啡", "奶茶", "咖啡", "零食", "聚餐" -> Color(0xFFF39C43)
    "交通", "通勤", "公交地铁", "打车", "火车高铁", "单车月卡" -> Color(0xFF6498D5)
    "购物", "日用百货", "服饰鞋包", "数码配件", "美妆个护" -> Color(0xFFEA7FA7)
    "日常", "生活用品", "快递物流", "话费网络", "水电房租", "住房", "居住" -> Color(0xFF6FAE85)
    "娱乐", "游戏", "影视会员", "旅游出行", "兴趣爱好" -> Color(0xFF9B7BDA)
    "人情社交", "人情红包", "请客送礼", "恋爱约会", "社交活动", "人情" -> Color(0xFFDE8374)
    "宠物", "宠物食品", "宠物用品", "宠物医疗" -> Color(0xFFB98563)
    "学习工作", "书籍资料", "课程考试", "文具打印", "软件工具", "学习" -> Color(0xFF7198CD)
    "医疗健康", "药品", "就诊体检", "运动健身", "医疗" -> Color(0xFFDE7676)
    else -> Color(0xFF9E978E)
}

@Composable
fun AccountArt(name: String?, size: Dp = 48.dp) {
    val res = when {
        name?.contains("微信") == true -> R.drawable.art_wechat
        name?.contains("支付宝") == true -> R.drawable.art_alipay
        name?.contains("银行卡") == true || name?.contains("银行") == true -> R.drawable.art_bank
        else -> R.drawable.art_cash
    }
    Image(
        painter = painterResource(res),
        contentDescription = name,
        modifier = Modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}
