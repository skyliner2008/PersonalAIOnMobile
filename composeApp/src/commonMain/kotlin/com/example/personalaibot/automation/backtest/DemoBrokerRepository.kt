package com.example.personalaibot.automation.backtest

import com.example.personalaibot.db.JarvisDatabaseHolder
import kotlinx.datetime.Clock

/** Persistence boundary for Demo Broker state. No MT5/network dependency. */
object DemoBrokerRepository {
    suspend fun savePosition(accountId: String, position: DemoTradingEngine.Position) {
        val db = JarvisDatabaseHolder.database ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        db.jarvisDatabaseQueries.upsertDemoPosition(
            id = position.id, account_id = accountId, symbol = position.symbol,
            side = if (position.buy) "BUY" else "SELL", volume = position.volume,
            entry_price = position.entry, current_price = position.currentPrice,
            stop_loss = position.stopLoss, take_profit = position.takeProfit,
            opened_at = position.openedAt, updated_at = now,
        )
    }

    suspend fun restorePositions(accountId: String): List<DemoTradingEngine.Position> {
        val db = JarvisDatabaseHolder.database ?: return emptyList()
        return db.jarvisDatabaseQueries.selectDemoPositions(accountId).executeAsList().map {
            DemoTradingEngine.Position(
                id = it.id, symbol = it.symbol, buy = it.side == "BUY", volume = it.volume,
                entry = it.entry_price, stopLoss = it.stop_loss, takeProfit = it.take_profit,
                currentPrice = it.current_price, openedAt = it.opened_at,
            )
        }
    }

    suspend fun loadBalance(accountId: String): Double? {
        val db = JarvisDatabaseHolder.database ?: return null
        return db.jarvisDatabaseQueries.selectDemoBalance(accountId).executeAsOneOrNull()
    }

    suspend fun deletePosition(positionId: String) { JarvisDatabaseHolder.database?.jarvisDatabaseQueries?.deleteDemoPosition(positionId) }

    suspend fun updateBalance(accountId: String, balance: Double) {
        val db = JarvisDatabaseHolder.database ?: return
        db.jarvisDatabaseQueries.updateDemoBalance(balance, Clock.System.now().toEpochMilliseconds(), accountId)
    }

    suspend fun recordDeal(accountId: String, positionId: String, position: DemoTradingEngine.Position, result: DemoTradingEngine.CloseResult) {
        val db = JarvisDatabaseHolder.database ?: return
        db.jarvisDatabaseQueries.insertDemoDeal(
            account_id = accountId, position_id = positionId, symbol = position.symbol,
            side = if (position.buy) "BUY" else "SELL", volume = position.volume,
            price = position.currentPrice, gross_pnl = result.grossPnl,
            spread_cost = result.spreadCost, commission = result.commission,
            rebate = result.rebate, swap = result.swap, net_pnl = result.netPnl,
            timestamp = Clock.System.now().toEpochMilliseconds(),
        )
    }
}
