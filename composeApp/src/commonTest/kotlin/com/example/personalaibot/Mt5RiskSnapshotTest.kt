package com.example.personalaibot

import com.example.personalaibot.automation.backtest.RiskEngine
import com.example.personalaibot.tools.trading.Mt5AccountInfo
import com.example.personalaibot.tools.trading.Mt5TerminalSnapshot
import com.example.personalaibot.tools.trading.Mt5TradeItem
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Mt5RiskSnapshotTest {
    @Test
    fun builds_risk_account_from_live_mt5_snapshot() {
        val now = Clock.System.now().toEpochMilliseconds()
        val snapshot = Mt5TerminalSnapshot(
            account = Mt5AccountInfo(
                balance = 10_000.0,
                equity = 9_800.0,
                margin = 1_960.0,
                freeMargin = 7_840.0,
            ),
            symbols = emptyList(),
            positions = listOf(
                Mt5TradeItem(
                    recordType = "POSITION",
                    ticket = "1",
                    symbol = "XAUUSD",
                    side = "BUY",
                    volume = 0.10,
                    priceCurrent = 2_500.0,
                )
            ),
            orders = emptyList(),
            deals = listOf(
                Mt5TradeItem(
                    recordType = "DEAL",
                    ticket = "2",
                    symbol = "XAUUSD",
                    entry = 1,
                    profit = -100.0,
                    eventTime = now,
                )
            ),
            syncedAt = now,
        )

        val account = RiskEngine.accountFromMt5Snapshot(snapshot, now, "XAUUSD")
        assertEquals(9_800.0, account.equity)
        assertEquals(10_000.0, account.balance)
        assertEquals(7_840.0, account.freeMargin)
        assertEquals(-100.0, account.todayPnL)
        assertEquals(1, account.openPositions)
        assertEquals(20.0, account.currentExposurePct)
        assertTrue(account.correlatedExposurePct > 0.0)
    }

    @Test
    fun snapshot_account_can_feed_risk_engine_without_server_call() {
        val snapshot = Mt5TerminalSnapshot(
            account = Mt5AccountInfo(balance = 10_000.0, equity = 10_000.0, freeMargin = 9_500.0),
            symbols = emptyList(),
            positions = emptyList(),
            orders = emptyList(),
            deals = emptyList(),
            syncedAt = Clock.System.now().toEpochMilliseconds(),
        )
        val account = RiskEngine.accountFromMt5Snapshot(snapshot, snapshot.syncedAt, "EURUSD")
        val result = RiskEngine.evaluate(
            account = account,
            proposal = RiskEngine.Proposal(
                strategyGate = com.example.personalaibot.automation.backtest.RiskGate.Decision.PASS,
                entry = 100.0,
                stopLoss = 98.0,
                valuePerPriceUnit = 10.0,
            ),
        )
        assertEquals(RiskEngine.Decision.PASS, result.decision)
    }
}
