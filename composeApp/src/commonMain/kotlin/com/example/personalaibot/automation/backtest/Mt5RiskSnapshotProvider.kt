package com.example.personalaibot.automation.backtest

import com.example.personalaibot.tools.trading.Mt5TerminalService

/** MT5 implementation of the unified account provider. No cache fallback is used here. */
class Mt5LiveAccountProvider(
    private val terminalService: Mt5TerminalService,
    private val bridgeBaseUrlProvider: () -> String,
    private val authTokenProvider: () -> String = { "" },
    private val historyLimit: Int = 400,
) : TradingAccountProvider {
    override suspend fun getAccount(): TradingAccount? {
        val base = bridgeBaseUrlProvider().trim()
        if (base.isBlank()) return null
        val snapshot = runCatching {
            terminalService.fetchSnapshot(
                baseUrl = base,
                authToken = authTokenProvider().trim(),
                historyLimit = historyLimit,
            )
        }.getOrNull() ?: return null
        val account = RiskEngine.accountFromMt5Snapshot(snapshot, snapshot.syncedAt)
        return TradingAccount(
            id = "mt5-live",
            name = "MT5 Live",
            environment = TradingAccount.Environment.MT5_LIVE,
            equity = account.equity,
            balance = account.balance,
            freeMargin = account.freeMargin,
            todayPnL = account.todayPnL,
            openPositions = account.openPositions,
            currentExposurePct = account.currentExposurePct,
            correlatedExposurePct = account.correlatedExposurePct,
        )
    }
}
