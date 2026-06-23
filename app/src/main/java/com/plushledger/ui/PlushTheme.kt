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

val LocalPlushPalette = compositionLocalOf { WarmPalette }

fun plushThemeName(tone: String): String = when (tone) {
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
            else -> WarmPalette
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
                MascotArt(72.dp)
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
