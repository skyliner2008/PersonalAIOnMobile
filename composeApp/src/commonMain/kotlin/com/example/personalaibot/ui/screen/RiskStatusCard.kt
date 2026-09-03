package com.example.personalaibot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalaibot.automation.backtest.TradingAccount
import com.example.personalaibot.ui.theme.JarvisTheme

@Composable
fun RiskStatusCard(
    account: TradingAccount,
    riskPct: Double? = null,
    maxDailyLossPct: Double = 5.0,
    maxDrawdownPct: Double = 10.0,
    killSwitchActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dailyLoss = (-account.todayPnL / account.balance * 100.0).coerceAtLeast(0.0)
    val drawdown = ((account.balance - account.equity) / account.balance * 100.0).coerceAtLeast(0.0)
    val allowed = !killSwitchActive && dailyLoss < maxDailyLossPct && drawdown < maxDrawdownPct
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = JarvisTheme.Card)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("RISK ENGINE", fontSize = 12.sp)
                Text(if (allowed) "¡ñ PASS" else "¡ñ BLOCKED", fontSize = 12.sp)
            }
            Text("Balance ${'$'}%.2f   Equity ${'$'}%.2f".format(account.balance, account.equity), fontSize = 13.sp)
            Text("Free Margin ${'$'}%.2f   Positions %d".format(account.freeMargin, account.openPositions), fontSize = 12.sp)
            Text("Risk %s   Daily Loss %.2f%%   DD %.2f%%".format(riskPct?.let { "%.2f%%".format(it) } ?: "¡ª", dailyLoss, drawdown), fontSize = 12.sp)
            Text(if (killSwitchActive) "KILL SWITCH ACTIVE" else "Exposure %.2f%%".format(account.currentExposurePct), fontSize = 11.sp)
        }
    }
}
