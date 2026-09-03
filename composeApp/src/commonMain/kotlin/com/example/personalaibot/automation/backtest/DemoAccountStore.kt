package com.example.personalaibot.automation.backtest

import com.example.personalaibot.db.JarvisDatabaseHolder
import kotlinx.datetime.Clock

/** Persistent Demo Account registry. The simulator itself remains independent of MT5. */
object DemoAccountStore {
    suspend fun ensureDefault(): DemoAccountConfig {
        val existing = load("demo-default")
        if (existing != null) return existing
        val config = DemoAccountConfig(id = "demo-default", name = "Default Demo")
        save(config)
        return config
    }

    suspend fun load(id: String): DemoAccountConfig? {
        val db = JarvisDatabaseHolder.database ?: return null
        return db.jarvisDatabaseQueries.selectDemoAccount(id).executeAsOneOrNull()?.toConfig()
    }

    suspend fun list(): List<DemoAccountConfig> {
        val db = JarvisDatabaseHolder.database ?: return emptyList()
        return db.jarvisDatabaseQueries.selectDemoAccounts().executeAsList().map { it.toConfig() }
    }

    suspend fun save(config: DemoAccountConfig) {
        val db = JarvisDatabaseHolder.database ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        db.jarvisDatabaseQueries.upsertDemoAccount(
            id = config.id,
            name = config.name,
            currency = config.currency,
            initial_balance = config.initialBalance,
            balance = config.initialBalance,
            leverage = config.leverage,
            max_daily_loss_pct = config.maxDailyLossPct,
            max_drawdown_pct = config.maxDrawdownPct,
            spread_mode = config.costProfile.spreadMode.name,
            spread_price = config.costProfile.spreadPrice,
            commission_per_lot_round_turn = config.costProfile.commissionPerLotRoundTurn,
            commission_pct_per_side = config.costProfile.commissionPctPerSide,
            rebate_per_lot_round_turn = config.costProfile.rebatePerLotRoundTurn,
            swap_long_per_lot_per_day = config.costProfile.swapLongPerLotPerDay,
            swap_short_per_lot_per_day = config.costProfile.swapShortPerLotPerDay,
            contract_size = config.costProfile.contractSize,
            tick_size = config.costProfile.tickSize,
            tick_value_per_lot = config.costProfile.tickValuePerLot,
            created_at = now,
            updated_at = now,
        )
    }

    suspend fun reset(id: String) {
        val config = load(id) ?: return
        save(config)
        val db = JarvisDatabaseHolder.database ?: return
        db.jarvisDatabaseQueries.deleteDemoPositions(id)
        db.jarvisDatabaseQueries.deleteDemoDeals(id)
    }
}

private fun com.example.personalaibot.db.DemoAccount.toConfig() = DemoAccountConfig(
    id = id,
    name = name,
    initialBalance = initial_balance,
    currency = currency,
    leverage = leverage,
    maxDailyLossPct = max_daily_loss_pct,
    maxDrawdownPct = max_drawdown_pct,
    costProfile = DemoCostProfile(
        spreadMode = DemoCostProfile.SpreadMode.valueOf(spread_mode),
        spreadPrice = spread_price,
        commissionPerLotRoundTurn = commission_per_lot_round_turn,
        commissionPctPerSide = commission_pct_per_side,
        rebatePerLotRoundTurn = rebate_per_lot_round_turn,
        swapLongPerLotPerDay = swap_long_per_lot_per_day,
        swapShortPerLotPerDay = swap_short_per_lot_per_day,
        contractSize = contract_size,
        tickSize = tick_size,
        tickValuePerLot = tick_value_per_lot,
    ),
)
