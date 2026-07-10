package com.plushledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plushledger.R

data class PlushPalette(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val muted: Color,
    val rose: Color,
    val moss: Color,
    val blue: Color,
    val pink: Color,
    val lilac: Color,
    val coral: Color,
    val border: Color
)

private val WarmPalette = PlushPalette(
    background = Color(0xFFFFFCF8),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFFFF5E8),
    ink = Color(0xFF4D3B32),
    muted = Color(0xFFA4978E),
    rose = Color(0xFFFFA126),
    moss = Color(0xFF66C79A),
    blue = Color(0xFF79A9E8),
    pink = Color(0xFFF58BAE),
    lilac = Color(0xFFA28ADC),
    coral = Color(0xFFFF887A),
    border = Color(0xFFF2E2D2)
)

private val PinkPalette = PlushPalette(
    background = Color(0xFFFFFBFC),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFFFEEF4),
    ink = Color(0xFF4E3840),
    muted = Color(0xFFA88D96),
    rose = Color(0xFFFF8DAE),
    moss = Color(0xFF78C8A3),
    blue = Color(0xFF8CB8EF),
    pink = Color(0xFFFF9EC0),
    lilac = Color(0xFFBCA0ED),
    coral = Color(0xFFFFA182),
    border = Color(0xFFF3DCE5)
)

private val MonoPalette = PlushPalette(
    background = Color(0xFFFBFAF8),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF2F0ED),
    ink = Color(0xFF34312F),
    muted = Color(0xFF938B85),
    rose = Color(0xFF4C4742),
    moss = Color(0xFF7FA08F),
    blue = Color(0xFF7B93AA),
    pink = Color(0xFFB8899B),
    lilac = Color(0xFF9690B2),
    coral = Color(0xFFB28A74),
    border = Color(0xFFE1DBD4)
)

private val GreenPalette = PlushPalette(
    background = Color(0xFFFBFEFA),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEEF8EC),
    ink = Color(0xFF33463B),
    muted = Color(0xFF879B8D),
    rose = Color(0xFF79C98D),
    moss = Color(0xFF66C79A),
    blue = Color(0xFF83BFE7),
    pink = Color(0xFFF59AB4),
    lilac = Color(0xFFA79AE6),
    coral = Color(0xFFFFA06F),
    border = Color(0xFFDCEBDB)
)

private val IceBluePalette = PlushPalette(
    background = Color(0xFFFAFDFF),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEFF7FF),
    ink = Color(0xFF34404F),
    muted = Color(0xFF8998AA),
    rose = Color(0xFF7BB6F3),
    moss = Color(0xFF70C9B0),
    blue = Color(0xFF70A8F2),
    pink = Color(0xFFFF96B8),
    lilac = Color(0xFFA99EF1),
    coral = Color(0xFFFFA071),
    border = Color(0xFFDCE9F5)
)

private val PurplePalette = PinkPalette.copy(
    background = Color(0xFFFDFBFF),
    surfaceAlt = Color(0xFFF4EEFF),
    ink = Color(0xFF473D55),
    muted = Color(0xFF978BA5),
    rose = Color(0xFFA58AE8),
    border = Color(0xFFE7DCF4)
)

private val OrangePalette = WarmPalette.copy(
    background = Color(0xFFFFFCF9),
    surfaceAlt = Color(0xFFFFF0E7),
    rose = Color(0xFFFF9560),
    coral = Color(0xFFFF806B),
    border = Color(0xFFF5DFD2)
)

private val BrownPalette = MonoPalette.copy(
    background = Color(0xFFFFFCF9),
    surfaceAlt = Color(0xFFF8EFE7),
    ink = Color(0xFF49372E),
    muted = Color(0xFF96857A),
    rose = Color(0xFF9B6B4B),
    border = Color(0xFFE8D8CB)
)

private val DarkPalette = PlushPalette(
    background = Color(0xFF201C1D),
    surface = Color(0xFF2B2527),
    surfaceAlt = Color(0xFF27312C),
    ink = Color(0xFFF5EDEC),
    muted = Color(0xFFC7B9BC),
    rose = Color(0xFFFFA53D),
    moss = Color(0xFF78B59A),
    blue = Color(0xFF85A8D4),
    pink = Color(0xFFE88DA9),
    lilac = Color(0xFFA995D0),
    coral = Color(0xFFE9887D),
    border = Color(0xFF514347)
)

data class PlushThemeSpec(
    val key: String,
    val type: String,
    val region: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val logic: String,
    val keywords: String = ""
)

private fun colorRgb(rgb: Long): Color = Color(0xFF000000 or rgb)

private fun mixColor(start: Color, end: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}

private fun Color.soft(amount: Float): Color = mixColor(this, Color.White, amount)
private fun Color.deep(amount: Float): Color = mixColor(this, Color.Black, amount)

private fun PlushThemeSpec.toPalette(): PlushPalette {
    val readableAccent = primary.deep(0.08f)
    val softAccent = primary.soft(0.78f)
    return PlushPalette(
        background = secondary.soft(0.88f),
        surface = Color.White,
        surfaceAlt = softAccent,
        ink = primary.deep(0.68f),
        muted = mixColor(primary.deep(0.32f), Color(0xFF8F8179), 0.58f),
        rose = readableAccent,
        moss = mixColor(readableAccent, Color(0xFF69B891), 0.42f),
        blue = mixColor(primary, Color(0xFF80BFE5), 0.46f),
        pink = mixColor(primary, Color(0xFFFF9FB7), 0.48f),
        lilac = mixColor(primary, Color(0xFFA996E8), 0.48f),
        coral = mixColor(readableAccent, Color(0xFFFF8B72), 0.36f),
        border = readableAccent.soft(0.72f)
    )
}

private val legacyPlushThemeCatalog = listOf(
    PlushThemeSpec("cn_beijing", "国内", "北京", "宫墙绒红", colorRgb(0xC96F5F), colorRgb(0xF1C08A), "宫墙、胡同、秋日银杏", "beijing bj"),
    PlushThemeSpec("cn_tianjin", "国内", "天津", "海河雾蓝", colorRgb(0xA9C9D8), colorRgb(0xE7C68D), "海河、欧式建筑、码头感", "tianjin tj"),
    PlushThemeSpec("cn_shanghai", "国内", "上海", "浦江蓝灰", colorRgb(0x9FB9CC), colorRgb(0xF2B6A0), "海派、玻璃幕墙、黄浦江", "shanghai sh"),
    PlushThemeSpec("cn_chongqing", "国内", "重庆", "山城雾橙", colorRgb(0xE68B5D), colorRgb(0xB7C2B5), "火锅、山雾、夜景", "chongqing cq"),
    PlushThemeSpec("cn_hebei", "国内", "河北", "长城沙褐", colorRgb(0xC69A6D), colorRgb(0xEAD5B7), "长城、燕赵、山地土色", "hebei hb"),
    PlushThemeSpec("cn_shanxi", "国内", "山西", "晋商赭棕", colorRgb(0xB87952), colorRgb(0xE8C79D), "晋商大院、黄土、古建", "shanxi sx"),
    PlushThemeSpec("cn_liaoning", "国内", "辽宁", "港湾钢蓝", colorRgb(0x90AFC3), colorRgb(0xD8B08C), "港口、工业、海风", "liaoning ln"),
    PlushThemeSpec("cn_jilin", "国内", "吉林", "雾凇冰蓝", colorRgb(0xC7DDE6), colorRgb(0xF2E7D2), "雾凇、雪原、冷空气", "jilin jl"),
    PlushThemeSpec("cn_heilongjiang", "国内", "黑龙江", "雪松深蓝", colorRgb(0x8FAAC2), colorRgb(0xEAF0EF), "冰雪、森林、边境感", "heilongjiang hlj"),
    PlushThemeSpec("cn_jiangsu", "国内", "江苏", "江南水绿", colorRgb(0xBFD9C8), colorRgb(0xF0D6A4), "水乡、园林、温润", "jiangsu js"),
    PlushThemeSpec("cn_zhejiang", "国内", "浙江", "青瓷浅绿", colorRgb(0xAFCFC1), colorRgb(0xE9D6B8), "青瓷、山水、茶田", "zhejiang zj"),
    PlushThemeSpec("cn_anhui", "国内", "安徽", "徽墨青灰", colorRgb(0x9DAFAA), colorRgb(0xE8D7BC), "徽派建筑、墨色、马头墙", "anhui ah"),
    PlushThemeSpec("cn_fujian", "国内", "福建", "乌龙茶青", colorRgb(0xA9C8A8), colorRgb(0xE8C58E), "茶山、海风、闽南红砖", "fujian fj"),
    PlushThemeSpec("cn_jiangxi", "国内", "江西", "瓷釉青白", colorRgb(0xB8D5D2), colorRgb(0xF1E5C8), "景德镇、瓷器、赣江", "jiangxi jx"),
    PlushThemeSpec("cn_shandong", "国内", "山东", "山海晴蓝", colorRgb(0xAFCDE3), colorRgb(0xEBC58B), "泰山、海岸、泉城", "shandong sd"),
    PlushThemeSpec("cn_henan", "国内", "河南", "中原麦黄", colorRgb(0xE6BD72), colorRgb(0xCFA07A), "麦田、黄河、中原土色", "henan hn"),
    PlushThemeSpec("cn_hubei", "国内", "湖北", "楚天湖蓝", colorRgb(0x9CC6D6), colorRgb(0xE6B7A3), "江汉平原、东湖、楚文化", "hubei hb"),
    PlushThemeSpec("cn_hunan", "国内", "湖南", "湘绣胭粉", colorRgb(0xD98A8F), colorRgb(0xE6C28B), "湘绣、辣椒、烟火气", "hunan hn"),
    PlushThemeSpec("cn_guangdong", "国内", "广东", "岭南荔红", colorRgb(0xE58B7C), colorRgb(0xBFD7B2), "荔枝、骑楼、岭南绿意", "guangdong gd"),
    PlushThemeSpec("cn_hainan", "国内", "海南", "椰林浅绿", colorRgb(0xA9D8BD), colorRgb(0xF4D58C), "椰林、海岛、阳光", "hainan hi"),
    PlushThemeSpec("cn_sichuan", "国内", "四川", "蜀锦暖红", colorRgb(0xD98269), colorRgb(0xF1C88E), "蜀锦、火锅、熊猫米白", "sichuan sc"),
    PlushThemeSpec("cn_guizhou", "国内", "贵州", "苗银青蓝", colorRgb(0x9DBFC8), colorRgb(0xD7C4A4), "苗银、山地、瀑布", "guizhou gz"),
    PlushThemeSpec("cn_yunnan", "国内", "云南", "云霞粉紫", colorRgb(0xD9A6C6), colorRgb(0xF0C98D), "云霞、花海、少数民族色彩", "yunnan yn"),
    PlushThemeSpec("cn_shaanxi", "国内", "陕西", "秦俑陶橙", colorRgb(0xC98259), colorRgb(0xE8C79C), "兵马俑、城墙、关中", "shaanxi sx xian"),
    PlushThemeSpec("cn_gansu", "国内", "甘肃", "敦煌沙金", colorRgb(0xD8A65F), colorRgb(0xC9896B), "敦煌、戈壁、壁画", "gansu gs"),
    PlushThemeSpec("cn_qinghai", "国内", "青海", "青海湖蓝", colorRgb(0x8FC6D8), colorRgb(0xE6D7B8), "青海湖、天空、草甸", "qinghai qh"),
    PlushThemeSpec("cn_taiwan", "国内", "台湾", "阿里山青", colorRgb(0xA7C9A8), colorRgb(0xF0B6A3), "山林、海岸、樱花感", "taiwan tw"),
    PlushThemeSpec("cn_neimenggu", "国内", "内蒙古", "草原奶绿", colorRgb(0xB8D5A4), colorRgb(0xE8C17D), "草原、奶茶、风吹草低", "neimenggu nmg"),
    PlushThemeSpec("cn_guangxi", "国内", "广西", "桂花暖黄", colorRgb(0xF0C46F), colorRgb(0xAFC9A0), "桂花、山水、喀斯特", "guangxi gx"),
    PlushThemeSpec("cn_xizang", "国内", "西藏", "圣湖天蓝", colorRgb(0x8FC7E8), colorRgb(0xF0D996), "圣湖、雪山、阳光", "xizang xz tibet"),
    PlushThemeSpec("cn_ningxia", "国内", "宁夏", "枸杞绒红", colorRgb(0xD87963), colorRgb(0xE8C982), "枸杞、黄河、沙坡头", "ningxia nx"),
    PlushThemeSpec("cn_xinjiang", "国内", "新疆", "葡萄暖紫", colorRgb(0xB99ACB), colorRgb(0xE6C07A), "葡萄、天山、异域纹样", "xinjiang xj"),
    PlushThemeSpec("cn_hongkong", "国内", "香港", "维港莓红", colorRgb(0xD96D72), colorRgb(0xAFC7D8), "维港、霓虹、都市感", "xianggang hongkong hk"),
    PlushThemeSpec("cn_macao", "国内", "澳门", "莲花奶粉", colorRgb(0xE6A8B7), colorRgb(0xF1D6A0), "莲花、葡式建筑、暖光", "aomen macao mo"),
    PlushThemeSpec("global_usa", "国外", "美国", "星条莓蓝", colorRgb(0x8EA9C9), colorRgb(0xD96F6F), "星条旗蓝红，但降饱和，避免太硬", "usa america us"),
    PlushThemeSpec("global_japan", "国外", "日本", "樱白朱粉", colorRgb(0xE9A7A5), colorRgb(0xFFF2E6), "樱花、日之丸、和纸感", "japan jp"),
    PlushThemeSpec("global_singapore", "国外", "新加坡", "鱼尾狮珊红", colorRgb(0xD96D68), colorRgb(0xF5D6B5), "国旗红、热带城市暖光", "singapore sg"),
    PlushThemeSpec("global_korea", "国外", "韩国", "青瓦雾蓝", colorRgb(0x9DB9D1), colorRgb(0xE7A6A6), "青瓦台、太极红蓝、韩系清冷感", "korea kr"),
    PlushThemeSpec("global_uk", "国外", "英国", "伦敦雾蓝", colorRgb(0x8FA4BD), colorRgb(0xC86B6B), "雾都蓝灰、英伦红", "uk britain london gb"),
    PlushThemeSpec("global_france", "国外", "法国", "塞纳奶蓝", colorRgb(0xA8C5DD), colorRgb(0xE8D7B7), "塞纳河、法式奶油、浅蓝白调", "france paris fr"),
    PlushThemeSpec("global_australia", "国外", "澳大利亚", "桉树暖绿", colorRgb(0xAFC8A3), colorRgb(0xE8B86F), "桉树、阳光、袋鼠土金", "australia au"),
    PlushThemeSpec("global_russia", "国外", "俄罗斯", "冰原绒蓝", colorRgb(0xAFC7DD), colorRgb(0xD88989), "冰雪蓝、俄罗斯红，降低冷硬感", "russia ru"),
    PlushThemeSpec("global_germany", "国外", "德国", "黑森林青灰", colorRgb(0x7F9A8C), colorRgb(0xD9B06F), "黑森林、啤酒金、工业灰绿", "germany de"),
    PlushThemeSpec("global_italy", "国外", "意大利", "托斯卡纳橄榄", colorRgb(0xA8B887), colorRgb(0xD98968), "橄榄绿、陶土橙、意式暖阳", "italy it"),
    PlushThemeSpec("global_iceland", "国外", "冰岛", "极光冰青", colorRgb(0x9FD0D3), colorRgb(0xC6B7D9), "极光、冰川、冷调紫青", "iceland is"),
    PlushThemeSpec("global_norway", "国外", "挪威", "峡湾雾蓝", colorRgb(0x8FB3C7), colorRgb(0xE6CFA8), "峡湾、雪山、北欧木色", "norway no"),
    PlushThemeSpec("global_switzerland", "国外", "瑞士", "雪山奶红", colorRgb(0xD77A78), colorRgb(0xF2EFE5), "瑞士红、雪山白、干净克制", "switzerland ch"),
    PlushThemeSpec("global_finland", "国外", "芬兰", "森湖浅蓝", colorRgb(0xA7CFE2), colorRgb(0xB8CDA8), "千湖之国、森林、北欧浅色", "finland fi"),
    PlushThemeSpec("global_greece", "国外", "希腊", "爱琴海蓝", colorRgb(0x7FB8D6), colorRgb(0xF6F0DC), "爱琴海、白房子、海岛感", "greece gr"),
    PlushThemeSpec("global_rome", "国外", "罗马", "古城陶橙", colorRgb(0xC9825F), colorRgb(0xE6C79C), "古罗马城墙、石柱、陶土色", "rome roma"),
    PlushThemeSpec("global_brazil", "国外", "巴西", "雨林青绿", colorRgb(0x8FCB8F), colorRgb(0xF1C85B), "热带雨林、桑巴黄绿，但做奶油化", "brazil br"),
    PlushThemeSpec("global_israel", "国外", "以色列", "死海浅蓝", colorRgb(0x9FC8DA), colorRgb(0xF0E7D2), "死海、地中海、沙地米色", "israel il"),
    PlushThemeSpec("global_india", "国外", "印度", "恒河姜橙", colorRgb(0xD9915F), colorRgb(0xE8C86F), "香料、纱丽、恒河日光", "india in"),
    PlushThemeSpec("global_thailand", "国外", "泰国", "暹罗莲粉", colorRgb(0xE6A4B5), colorRgb(0xD6B36F), "莲花、寺庙金、热带粉", "thailand th"),
    PlushThemeSpec("global_malaysia", "国外", "马来西亚", "南洋椰绿", colorRgb(0xA8CFA6), colorRgb(0xE7B86F), "椰林、南洋、阳光暖黄", "malaysia my"),
    PlushThemeSpec("global_philippines", "国外", "菲律宾", "海岛晴蓝", colorRgb(0x8EC6DF), colorRgb(0xF2C96B), "海岛、阳光、浅海蓝", "philippines ph"),
    PlushThemeSpec("global_mongolia", "国外", "蒙古", "草原苍绿", colorRgb(0xA9BF8F), colorRgb(0xC98A67), "草原、戈壁、游牧皮革色", "mongolia mn"),
    PlushThemeSpec("global_egypt", "国外", "埃及", "尼罗沙金", colorRgb(0xD8A75F), colorRgb(0x8FB6C7), "金字塔、沙漠、尼罗河蓝", "egypt eg")
)

private val classicPlushThemeCatalog = listOf(
    PlushThemeSpec("warm", "经典", "默认", "暖黄", WarmPalette.rose, WarmPalette.rose, "温暖、轻快、绒绒默认色", "default warm yellow"),
    PlushThemeSpec("pink", "经典", "绒粉", "粉色", PinkPalette.rose, PinkPalette.rose, "柔软、可爱、心情记录感", "pink cute soft"),
    PlushThemeSpec("mono", "经典", "黑白", "黑白", MonoPalette.rose, MonoPalette.rose, "克制、清爽、低干扰", "mono black white"),
    PlushThemeSpec("green", "经典", "淡绿", "淡绿", GreenPalette.rose, GreenPalette.rose, "清新、自然、轻盈", "green fresh"),
    PlushThemeSpec("blue", "经典", "冰蓝", "冰蓝", IceBluePalette.rose, IceBluePalette.rose, "清醒、干净、冷静", "blue ice"),
    PlushThemeSpec("purple", "经典", "紫色", "薰衣草紫", PurplePalette.rose, PurplePalette.rose, "温柔、梦幻、轻松", "purple lavender"),
    PlushThemeSpec("orange", "经典", "蜜桃", "蜜桃橙", OrangePalette.rose, OrangePalette.rose, "明亮、元气、暖甜", "orange peach"),
    PlushThemeSpec("brown", "经典", "可可", "可可棕", BrownPalette.rose, BrownPalette.rose, "安定、复古、耐看", "brown cocoa")
)

val plushThemeCatalog = classicPlushThemeCatalog + legacyPlushThemeCatalog

fun plushThemeSpec(tone: String): PlushThemeSpec? = plushThemeCatalog.firstOrNull { it.key == tone }

val LocalPlushPalette = compositionLocalOf { WarmPalette }

fun plushThemeName(tone: String): String = plushThemeSpec(tone)?.let { "${it.region} · ${it.name}" } ?: when (tone) {
    "pink" -> "绒粉"
    "mono" -> "黑白"
    "green" -> "淡绿"
    "blue" -> "冰蓝"
    "purple" -> "薰衣草紫"
    "orange" -> "蜜桃橙"
    "brown" -> "可可棕"
    else -> "暖黄"
}

@Composable
fun PlushLedgerTheme(darkMode: Boolean, themeTone: String = "warm", content: @Composable () -> Unit) {
    val palette = if (darkMode) {
        DarkPalette
    } else {
        when (themeTone) {
            "pink" -> PinkPalette
            "mono" -> MonoPalette
            "green" -> GreenPalette
            "blue" -> IceBluePalette
            "purple" -> PurplePalette
            "orange" -> OrangePalette
            "brown" -> BrownPalette
            "warm" -> WarmPalette
            else -> plushThemeSpec(themeTone)?.toPalette() ?: WarmPalette
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPlushPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkMode) {
                androidx.compose.material3.darkColorScheme(
                    primary = palette.rose,
                    secondary = palette.moss,
                    tertiary = palette.blue,
                    background = palette.background,
                    surface = palette.surface,
                    onSurface = palette.ink,
                    onBackground = palette.ink
                )
            } else {
                androidx.compose.material3.lightColorScheme(
                    primary = palette.rose,
                    secondary = palette.moss,
                    tertiary = palette.blue,
                    background = palette.background,
                    surface = palette.surface,
                    onSurface = palette.ink,
                    onBackground = palette.ink
                )
            },
            typography = MaterialTheme.typography.copy(
                displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, letterSpacing = 0.sp),
                headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, letterSpacing = 0.sp),
                headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
                bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
                labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
            ),
            content = content
        )
    }
}

@Composable
fun FabricBackdrop() {
    val palette = LocalPlushPalette.current
    Canvas(Modifier.fillMaxSize().background(palette.background)) {}
}

@Composable
fun PlushCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalPlushPalette.current
    Card(
        modifier = modifier.shadow(8.dp, RoundedCornerShape(18.dp), spotColor = palette.ink.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun PlushButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = LocalPlushPalette.current.rose,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp).shadow(8.dp, RoundedCornerShape(22.dp), spotColor = color.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SoftChip(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val palette = LocalPlushPalette.current
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) palette.rose else palette.surface,
        border = BorderStroke(1.dp, if (selected) palette.rose else palette.border),
        shadowElevation = if (selected) 6.dp else 1.dp
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else palette.ink,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun PlushBadge(icon: ImageVector, color: Color, size: Dp = 48.dp) {
    Box(
        modifier = Modifier.size(size).shadow(8.dp, CircleShape, spotColor = color.copy(alpha = 0.28f)).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun SectionTitle(text: String, icon: ImageVector) {
    val palette = LocalPlushPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = palette.rose, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, color = palette.ink, fontSize = 18.sp)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确认删除",
    confirmEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = LocalPlushPalette.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFFFFFCF7),
            border = BorderStroke(1.5.dp, Color(0xFFFFD8A0)),
            shadowElevation = 18.dp
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MascotArt(72.dp, R.drawable.mascot_action_think)
                Text(title, fontWeight = FontWeight.Black, color = palette.ink, fontSize = 24.sp)
                Spacer(Modifier.height(10.dp))
                Text(message, color = palette.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 20.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消", color = palette.ink) }
                    PlushButton(confirmText, androidx.compose.material.icons.Icons.Default.CheckCircle, Modifier.weight(1f), enabled = confirmEnabled, color = palette.rose, onClick = onConfirm)
                }
            }
        }
    }
}
