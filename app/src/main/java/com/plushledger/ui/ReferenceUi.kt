package com.plushledger.ui

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
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
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
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
                Text(title, color = palette.ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
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
    Image(
        painter = painterResource(R.drawable.ic_launcher),
        contentDescription = null,
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun CategoryArt(name: String?, size: Dp = 48.dp) {
    val res = categoryArtResource(name)
    Image(
        painter = painterResource(res),
        contentDescription = name,
        modifier = Modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}

@DrawableRes
fun categoryArtResource(name: String?): Int = when (name) {
    "餐饮", "饮食" -> R.drawable.art_food
    "交通" -> R.drawable.art_transport
    "购物" -> R.drawable.art_shopping
    "娱乐" -> R.drawable.art_entertainment
    "住房", "居住" -> R.drawable.art_home
    "学习" -> R.drawable.art_study
    "医疗" -> R.drawable.art_medical
    "工资" -> R.drawable.art_salary
    "兼职", "理财" -> R.drawable.art_parttime
    "生活费", "礼金", "人情" -> R.drawable.art_living
    else -> R.drawable.art_food
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
