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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
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
        shape = RoundedCornerShape(size * 0.28f),
        color = palette.surfaceAlt,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.72f)),
        shadowElevation = 2.dp
    ) {
        Image(
            painter = painterResource(categoryArtRes(name)),
            contentDescription = name,
            modifier = Modifier.fillMaxSize().padding(size * 0.035f),
            contentScale = ContentScale.Fit
        )
    }
}

private fun categoryArtRes(name: String?): Int = when (name?.trim()) {
    "餐饮", "美食", "饮食", "吃喝", "餐费" -> R.drawable.root_food
    "早餐" -> R.drawable.sub_breakfast
    "正餐" -> R.drawable.sub_meal
    "外卖" -> R.drawable.sub_delivery
    "奶茶咖啡", "奶茶", "咖啡" -> R.drawable.category_milktea
    "零食" -> R.drawable.sub_snacks
    "聚餐" -> R.drawable.sub_gathering
    "交通" -> R.drawable.root_transport
    "通勤" -> R.drawable.sub_commute
    "公交地铁" -> R.drawable.sub_metro
    "打车" -> R.drawable.sub_taxi
    "火车高铁" -> R.drawable.sub_rail
    "单车月卡" -> R.drawable.sub_bike_pass
    "购物" -> R.drawable.root_shopping
    "日用百货" -> R.drawable.sub_basket
    "服饰鞋包" -> R.drawable.sub_clothes
    "数码配件" -> R.drawable.sub_digital
    "美妆个护" -> R.drawable.sub_beauty
    "日常", "日常消费", "日用消费", "生活费" -> R.drawable.category_daily_consume
    "生活用品" -> R.drawable.sub_household
    "快递物流" -> R.drawable.sub_parcel
    "话费网络" -> R.drawable.sub_phone
    "水电房租", "住房", "居住" -> R.drawable.sub_utilities
    "娱乐" -> R.drawable.root_entertainment
    "游戏" -> R.drawable.sub_game
    "影视会员" -> R.drawable.sub_media
    "旅游出行" -> R.drawable.sub_travel
    "兴趣爱好" -> R.drawable.sub_hobby
    "人情社交", "人情", "人情往来", "社交" -> R.drawable.category_social
    "人情红包" -> R.drawable.sub_redpacket
    "请客送礼" -> R.drawable.sub_gift
    "恋爱约会" -> R.drawable.sub_date
    "社交活动" -> R.drawable.sub_social_activity
    "宠物" -> R.drawable.root_pet
    "宠物食品" -> R.drawable.sub_pet_food
    "宠物用品" -> R.drawable.sub_pet_supplies
    "宠物医疗" -> R.drawable.sub_pet_health
    "学习工作", "学习", "学习成长", "教育" -> R.drawable.category_study
    "书籍资料" -> R.drawable.sub_books
    "课程考试" -> R.drawable.sub_course
    "文具打印" -> R.drawable.sub_stationery
    "软件工具" -> R.drawable.sub_software
    "AI软件订阅" -> R.drawable.sub_ai_subscription
    "医疗健康", "医疗", "健康", "健康医疗" -> R.drawable.category_medical
    "药品" -> R.drawable.sub_medicine
    "就诊体检" -> R.drawable.sub_checkup
    "运动健身" -> R.drawable.sub_fitness
    "工资", "薪资", "薪水", "工资收入" -> R.drawable.category_salary
    "房屋", "转租", "租金收入", "房租收入" -> R.drawable.art_home
    "兼职", "副业", "稿费" -> R.drawable.category_parttime
    "理财", "投资", "投资收益", "利息收益" -> R.drawable.category_investment
    "礼金", "礼金收入", "红包礼金", "礼物" -> R.drawable.category_gift_income
    "其他" -> R.drawable.root_other
    "临时支出" -> R.drawable.sub_temporary
    "杂项备用" -> R.drawable.sub_miscellaneous
    "未分类", "无法归类" -> R.drawable.sub_unknown
    else -> R.drawable.sub_unknown
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
