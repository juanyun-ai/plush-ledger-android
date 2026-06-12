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
    val border: Color
)

private val LightPalette = PlushPalette(
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFFFF4E4),
    ink = Color(0xFF4A392F),
    muted = Color(0xFF9C8D84),
    rose = Color(0xFFFF9F2D),
    moss = Color(0xFF69C69E),
    blue = Color(0xFF82AEE8),
    border = Color(0xFFF2DDC4)
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
    border = Color(0xFF514347)
)

val LocalPlushPalette = compositionLocalOf { LightPalette }

@Composable
fun PlushLedgerTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    val palette = if (darkMode) DarkPalette else LightPalette
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
        modifier = modifier.shadow(10.dp, RoundedCornerShape(8.dp), spotColor = palette.rose.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(8.dp),
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
        modifier = modifier.height(52.dp).shadow(8.dp, RoundedCornerShape(12.dp), spotColor = color.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(12.dp),
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
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) color.copy(alpha = 0.15f) else palette.surface,
        border = BorderStroke(1.dp, if (selected) color else palette.border),
        shadowElevation = if (selected) 6.dp else 1.dp
    ) {
        Text(
            text = text,
            color = if (selected) color else palette.ink,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmText, color = if (confirmEnabled) palette.rose else palette.muted)
            }
        },
        containerColor = palette.surface
    )
}
