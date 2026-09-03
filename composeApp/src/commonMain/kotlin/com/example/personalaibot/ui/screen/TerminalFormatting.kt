package com.example.personalaibot.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.tools.trading.Mt5TradeItem
import com.example.personalaibot.ui.theme.JarvisTheme

// ── Period selector enum (shared by Overview + History) ───────────────────────
internal enum class PeriodFilter(val label: String, val days: Int) {
    Today("Today", 1),
    Week("7D", 7),
    Month("30D", 30),
    All("All", Int.MAX_VALUE),
}

internal fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

internal fun List<Mt5TradeItem>.filterByPeriod(period: PeriodFilter): List<Mt5TradeItem> {
    if (period == PeriodFilter.All) return this
    val cutoffMs = nowMs() - period.days.toLong() * 86_400_000L
    return filter { it.eventTime >= cutoffMs }
}

internal data class PeriodStats(
    val total: Int,
    val wins: Int,
    val losses: Int,
    val totalProfit: Double,
    val avgProfit: Double,
    val bestTrade: Double,
    val worstTrade: Double,
    val winRate: Double,
)

internal fun computeStats(deals: List<Mt5TradeItem>): PeriodStats {
    if (deals.isEmpty()) return PeriodStats(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0)
    val profits = deals.map { it.netPnl }
    val wins = profits.count { it > 0 }
    val losses = profits.count { it < 0 }
    val total = profits.sumOf { it }
    return PeriodStats(
        total = deals.size,
        wins = wins,
        losses = losses,
        totalProfit = total,
        avgProfit = total / deals.size,
        bestTrade = profits.maxOrNull() ?: 0.0,
        worstTrade = profits.minOrNull() ?: 0.0,
        winRate = if (deals.isNotEmpty()) wins.toDouble() / deals.size * 100.0 else 0.0,
    )
}

internal enum class HistoryFilter(val label: String) {
    All("All"),
    Buy("Buys"),
    Sell("Sells"),
    Winners("Winners"),
    Losers("Losers")
}

// ─── Format helpers ──────────────────────────────────────────────────────────

internal fun formatMoney(amount: Double, currency: String = ""): String {
    val symbol = when (currency.uppercase()) {
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        else  -> if (currency.isNotBlank()) "$currency " else ""
    }
    return if (amount >= 0)
        "$symbol${"%,.2f".format(amount)}"
    else
        "-$symbol${"%,.2f".format(-amount)}"
}

internal fun formatSignedMoney(amount: Double, currency: String = ""): String {
    val abs = "%,.2f".format(kotlin.math.abs(amount))
    val symbol = when (currency.uppercase()) {
        "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; "JPY" -> "¥"
        else  -> if (currency.isNotBlank()) "$currency " else ""
    }
    return if (amount >= 0) "+$symbol$abs" else "-$symbol$abs"
}

internal fun formatRelativeTs(ms: Long): String {
    val delta = nowMs() - ms
    return when {
        delta < 0          -> "just now"
        delta < 60_000     -> "${delta / 1_000}s ago"
        delta < 3_600_000  -> "${delta / 60_000}m ago"
        delta < 86_400_000 -> "${delta / 3_600_000}h ago"
        else               -> "${delta / 86_400_000}d ago"
    }
}

internal fun formatVolume(volume: Double): String =
    if (volume == kotlin.math.floor(volume) && volume < 1000) "%.2f".format(volume)
    else "%.2f".format(volume)

internal fun formatPrice(price: Double?): String {
    if (price == null || price == 0.0) return "—"
    return when {
        price >= 1_000 -> "%,.2f".format(price)
        price >= 1     -> "%.4f".format(price)
        else           -> "%.5f".format(price)
    }
}

// ─── Theme Colors ────────────────────────────────────────────────────────────

internal val TerminalSurface = Color(0xFF0D1220)
internal val PanelSurface = Color(0xFF171C2A)
internal val PanelSurfaceHi = Color(0xFF1E2536)
internal val BuyGreen = Color(0xFF00E676)
internal val SellRed = Color(0xFFFF5252)
internal val PositiveGreen = Color(0xFFB9F6CA)
internal val NegativeRed = Color(0xFFFF8A80)
internal val OutlineSubtle = Color(0xFF2A3247)

// ─── Common UI Components ────────────────────────────────────────────────────

@Composable
internal fun StatChip(label: String, value: String, valueColor: Color = Color.White) {
    Surface(
        color = PanelSurfaceHi,
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
            Text(value, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun MiniLabel(modifier: Modifier = Modifier, label: String, value: String) {
    Column(modifier = modifier) {
        Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun EmptyState(text: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun JarvisTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = JarvisTheme.Cyan,
            unfocusedBorderColor = OutlineSubtle,
            focusedLabelColor = JarvisTheme.Cyan,
            unfocusedLabelColor = Color.White.copy(alpha = 0.55f),
            cursorColor = JarvisTheme.Cyan
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    )
}

@Composable
internal fun SoftButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.16f),
            contentColor = color
        )
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun StatMini(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PanelSurfaceHi)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
