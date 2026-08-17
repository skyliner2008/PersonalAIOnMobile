package com.example.personalaibot.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.automation.backtest.BacktestResult
import com.example.personalaibot.automation.backtest.BacktestResultStore
import com.example.personalaibot.automation.backtest.BacktestTrade
import com.example.personalaibot.ui.theme.JarvisTheme
import kotlinx.datetime.toLocalDateTime

/**
 * BacktestScreen — หน้าจอผล backtest แบบกราฟ (อ่านจาก BacktestResultStore)
 *  - เลือก run ล่าสุดจาก chips ด้านบน
 *  - แท็บ "ภาพรวม" + แท็บตามกลยุทธ์ เรียงคะแนนอัตโนมัติ (avgR มากสุดอยู่แท็บแรก)
 *  - equity curve, pie chart ชนะ/แพ้/ค้าง, metric cards, ไม้ล่าสุด
 */
@Composable
fun BacktestScreen(runs: List<BacktestResultStore.Run>) {
    if (runs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📈", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text("ยังไม่มีผล Backtest", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "สั่งในแชท เช่น \"backtest ทองคำ all\" หรือ \"backtest smc ทองคำ 1h\"\nแล้วผลจะมาแสดงที่นี่อัตโนมัติ",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
        return
    }

    var runIdx by remember { mutableIntStateOf(0) }
    val safeRunIdx = runIdx.coerceIn(0, runs.size - 1)
    val run = runs[safeRunIdx]
    val r = run.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisTheme.Dark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ── เลือก run ──
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            runs.take(8).forEachIndexed { i, item ->
                val sel = i == safeRunIdx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) JarvisTheme.Cyan.copy(alpha = 0.18f) else JarvisTheme.Card)
                        .border(1.dp, if (sel) JarvisTheme.Cyan else Color(0xFF303050), RoundedCornerShape(20.dp))
                        .clickable { runIdx = i }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${item.result.symbol} ${item.result.interval}",
                        color = if (sel) JarvisTheme.Cyan else Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "strategy=${run.strategyArg} ・ ${r.bars} แท่ง ・ ${fmtTs(r.fromTs)} → ${fmtTs(r.toTs)} UTC ・ ต้นทุน${if (r.config.includeCosts) "เปิด" else "ปิด"}",
            color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp
        )
        Spacer(Modifier.height(10.dp))

        // ── แท็บ: ภาพรวม + กลยุทธ์ (เรียงตาม avgR — ดีสุดอยู่แท็บแรก) ──
        val stratTabs = r.perStrategy.sortedByDescending { it.avgR }
        var tabIdx by remember { mutableIntStateOf(0) }
        val safeTab = tabIdx.coerceIn(0, stratTabs.size) // 0 = ภาพรวม
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BtTab("ภาพรวม", sel = safeTab == 0, score = null) { tabIdx = 0 }
            stratTabs.forEachIndexed { i, s ->
                BtTab(s.name, sel = safeTab == i + 1, score = s.avgR) { tabIdx = i + 1 }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (safeTab == 0) {
            OverviewTab(r)
        } else {
            val s = stratTabs[safeTab - 1]
            StrategyTab(r, s.kind, s.name)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─── Tabs ─────────────────────────────────────────────────────────────

@Composable
private fun BtTab(label: String, sel: Boolean, score: Double?, onClick: () -> Unit) {
    val good = (score ?: 0.0) > 0
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (sel) JarvisTheme.Purple.copy(alpha = 0.25f) else JarvisTheme.Surface)
            .border(1.dp, if (sel) JarvisTheme.Purple else Color(0xFF303050), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = if (sel) Color.White else Color.White.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1)
            if (score != null) {
                Text(
                    "${if (good) "▲" else "▼"} ${"%+.2f".format(score)}R",
                    color = if (good) JarvisTheme.Green else JarvisTheme.Red,
                    fontSize = 9.sp
                )
            }
        }
    }
}

// ─── Overview ─────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(r: BacktestResult) {
    SectionTitle("Equity Curve")
    ChartCard {
        EquityChart(
            curve = r.equityCurve,
            baseline = r.config.initialBalance,
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
    }
    Spacer(Modifier.height(12.dp))

    SectionTitle("ตัวชี้วัดหลัก")
    MetricGrid(
        listOf(
            Metric("กำไรสุทธิ", "%+,.0f$".format(r.finalBalance - r.config.initialBalance), "%+.1f%%".format(r.totalReturnPct), r.totalReturnPct > 0),
            Metric("Win-rate", "%.1f%%".format(r.winRate * 100), "${r.wins}W/${r.losses}L/${r.timeouts}T", r.winRate >= 0.5),
            Metric("Profit Factor", "%.2f".format(r.profitFactor), if (r.profitFactor >= 1.5) "ดีมาก" else if (r.profitFactor >= 1.0) "กำไร" else "ขาดทุน", r.profitFactor >= 1.0),
            Metric("Expectancy", "%+.2fR".format(r.expectancyR), "ต่อไม้", r.expectancyR > 0),
            Metric("Max Drawdown", "−%.1f%%".format(r.maxDrawdownPct), if (r.maxDrawdownPct < 10) "ต่ำ" else if (r.maxDrawdownPct < 25) "กลาง" else "สูง", r.maxDrawdownPct < 15),
            Metric("Sharpe", "%.2f".format(r.sharpe), "คร่าวๆ (√252)", r.sharpe > 0)
        )
    )
    Spacer(Modifier.height(12.dp))

    SectionTitle("สัดส่วนผลไม้ (${r.totalTrades} ไม้)")
    ChartCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            PieChart(
                slices = listOf(
                    r.wins.toFloat() to JarvisTheme.Green,
                    r.losses.toFloat() to JarvisTheme.Red,
                    r.timeouts.toFloat() to JarvisTheme.Amber
                ),
                modifier = Modifier.size(110.dp)
            )
            Spacer(Modifier.width(18.dp))
            Column {
                PieLegend(JarvisTheme.Green, "ชนะ (TP)", r.wins, r.totalTrades)
                PieLegend(JarvisTheme.Red, "แพ้ (SL)", r.losses, r.totalTrades)
                PieLegend(JarvisTheme.Amber, "ค้าง (Timeout)", r.timeouts, r.totalTrades)
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    if (r.trades.isNotEmpty()) {
        SectionTitle("ไม้ล่าสุด")
        r.trades.takeLast(8).reversed().forEach { TradeRow(it) }
    }
}

// ─── Strategy tab ─────────────────────────────────────────────────────

@Composable
private fun StrategyTab(r: BacktestResult, kind: String, name: String) {
    val s = r.perStrategy.firstOrNull { it.kind == kind } ?: return
    val myTrades = r.trades.filter { it.kind == kind }

    // equity curve เฉพาะกลยุทธ์ — สร้างจาก cumulative R ของไม้ตัวเอง (ไม่มี balance แยก)
    SectionTitle("$name — สะสม R")
    ChartCard {
        val cum = myTrades.runningFold(0.0) { acc, t -> acc + t.pnlR }.drop(0)
        EquityChart(
            curve = if (cum.size >= 2) cum else listOf(0.0, 0.0),
            baseline = 0.0,
            unitLabel = "R",
            modifier = Modifier.fillMaxWidth().height(140.dp)
        )
    }
    Spacer(Modifier.height(12.dp))

    SectionTitle("ตัวชี้วัด")
    MetricGrid(
        listOf(
            Metric("สัญญาณ", "${s.signals}", "ข้าม ${s.skipped} (มีไม้ค้าง)", null),
            Metric("ไม้ที่เข้าจริง", "${s.taken}", "${s.wins}W/${s.losses}L/${s.timeouts}T", null),
            Metric("Win-rate", "%.1f%%".format(s.winRate * 100), "", s.winRate >= 0.5),
            Metric("avg R", "%+.2fR".format(s.avgR), "รวม ${"%+.1f".format(s.totalR)}R", s.avgR > 0),
            Metric("Profit Factor", "%.2f".format(s.profitFactor), "", s.profitFactor >= 1.0)
        )
    )
    Spacer(Modifier.height(12.dp))

    SectionTitle("สัดส่วนผลไม้")
    ChartCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            PieChart(
                slices = listOf(
                    s.wins.toFloat() to JarvisTheme.Green,
                    s.losses.toFloat() to JarvisTheme.Red,
                    s.timeouts.toFloat() to JarvisTheme.Amber
                ),
                modifier = Modifier.size(100.dp)
            )
            Spacer(Modifier.width(18.dp))
            Column {
                PieLegend(JarvisTheme.Green, "ชนะ (TP)", s.wins, s.taken)
                PieLegend(JarvisTheme.Red, "แพ้ (SL)", s.losses, s.taken)
                PieLegend(JarvisTheme.Amber, "ค้าง", s.timeouts, s.taken)
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    if (myTrades.isNotEmpty()) {
        SectionTitle("ไม้ล่าสุดของกลยุทธ์นี้")
        myTrades.takeLast(8).reversed().forEach { TradeRow(it) }
    }
}

private fun <T> List<T>.runningFold(initial: Double, op: (Double, T) -> Double): List<Double> {
    var acc = initial
    return listOf(initial) + map { acc = op(acc, it); acc }
}

// ─── Components ───────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = JarvisTheme.Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
}

/** การ์ดมีมิติ: เงา + gradient ขอบบนสว่าง (แนว 3D subtle) */
@Composable
private fun ChartCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF22223A), JarvisTheme.Card))
            )
            .border(1.dp, Color(0xFF3A3A5C), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) { content() }
}

private data class Metric(val label: String, val value: String, val sub: String, val good: Boolean?)

@Composable
private fun MetricGrid(metrics: List<Metric>) {
    metrics.chunked(2).forEach { rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            rowItems.forEach { m ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(4.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF22223A), JarvisTheme.Card)))
                        .border(1.dp, Color(0xFF3A3A5C), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text(m.label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            m.value,
                            color = when (m.good) { true -> JarvisTheme.Green; false -> JarvisTheme.Red; null -> Color.White },
                            fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                        if (m.sub.isNotEmpty()) {
                            Text(m.sub, color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp)
                        }
                    }
                }
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Equity/กราฟเส้น + fill + เส้น baseline ประ */
@Composable
private fun EquityChart(curve: List<Double>, baseline: Double, unitLabel: String = "$", modifier: Modifier = Modifier) {
    if (curve.size < 2) { Text("ข้อมูลไม่พอวาดกราฟ", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp); return }
    val minV = curve.min(); val maxV = curve.max()
    val lastV = curve.last()
    val up = lastV >= baseline
    val lineColor = if (up) JarvisTheme.Green else JarvisTheme.Red

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("start ${fmtAxis(baseline, unitLabel)}", color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp)
            Text(
                "end ${fmtAxis(lastV, unitLabel)} (${if (up) "+" else ""}${fmtAxis(lastV - baseline, unitLabel)})",
                color = lineColor, fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(110.dp)) {
            val w = size.width; val h = size.height
            val range = (maxV - minV).takeIf { it > 0 } ?: 1.0
            val pad = range * 0.08
            val lo = minV - pad; val hi = maxV + pad
            fun x(i: Int) = i.toFloat() / (curve.size - 1) * w
            fun y(v: Double) = (h - ((v - lo) / (hi - lo)) * h).toFloat()

            // baseline
            val by = y(baseline.coerceIn(lo, hi))
            val dash = 12f
            var dx = 0f
            while (dx < w) { drawLine(Color.White.copy(alpha = 0.25f), Offset(dx, by), Offset(minOf(dx + dash, w), by), strokeWidth = 2f); dx += dash * 2 }

            // line + fill
            val line = Path()
            curve.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }
            val fill = Path()
            fill.addPath(line)
            fill.lineTo(w, h); fill.lineTo(0f, h); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.30f), Color.Transparent)))
            drawPath(line, lineColor, style = Stroke(width = 4f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("min ${fmtAxis(minV, unitLabel)}", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
            Text("max ${fmtAxis(maxV, unitLabel)}", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
        }
    }
}

/** Donut pie chart */
@Composable
private fun PieChart(slices: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.first.toDouble() }.toFloat()
    Canvas(modifier = modifier) {
        if (total <= 0f) {
            drawArc(Color(0xFF303050), 0f, 360f, useCenter = false, style = Stroke(width = 26f))
            return@Canvas
        }
        var start = -90f
        slices.forEach { (v, c) ->
            val sweep = v / total * 360f
            if (sweep > 0f) drawArc(c, start, sweep - 2f, useCenter = false, style = Stroke(width = 26f))
            start += sweep
        }
    }
}

@Composable
private fun PieLegend(color: Color, label: String, count: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            "$label — $count (${if (total > 0) "%.0f%%".format(count * 100.0 / total) else "-"})",
            color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp
        )
    }
}

@Composable
private fun TradeRow(t: BacktestTrade) {
    val (icon, outColor, outText) = when (t.exitReason) {
        "TP" -> Triple("✅", JarvisTheme.Green, "TP")
        "SL" -> Triple("❌", JarvisTheme.Red, "SL")
        else -> Triple("⏱", JarvisTheme.Amber, "ค้าง")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisTheme.Surface)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(if (t.side == "BUY") "🟢" else "🔴", fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${t.side} @ ${fmtPrice(t.entryPrice)} → ${fmtPrice(t.exitPrice)}", color = Color.White, fontSize = 11.sp)
            Text("${t.strategyName} ・ ${fmtTs(t.entryTime)}", color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$icon $outText", color = outColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("%+.2fR".format(t.pnlR), color = outColor, fontSize = 10.sp)
        }
    }
}

// ─── utils ────────────────────────────────────────────────────────────

private fun fmtTs(ms: Long): String {
    val l = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
        .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
    return "%02d/%02d/%02d %02d:%02d".format(l.dayOfMonth, l.monthNumber, l.year % 100, l.hour, l.minute)
}

private fun fmtPrice(v: Double): String = if (kotlin.math.abs(v) >= 100) "%.2f".format(v) else "%.4f".format(v)

private fun fmtAxis(v: Double, unit: String): String =
    if (unit == "R") "%.1fR".format(v) else "%,.0f$".format(v)
