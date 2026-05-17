package com.example.personalaibot.tools.trading

import com.example.personalaibot.db.JarvisDatabaseHolder
import com.example.personalaibot.db.Mt5TradeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ─── Mt5LocalCache ──────────────────────────────────────────────────────────
 *
 * Standalone cache layer wrapping SQLDelight tables (Mt5AccountSnapshot,
 * Mt5SymbolCatalog, Mt5TradeRecord, TradeSyncSnapshot, AppSetting).
 *
 * Goal: app can render the *most-recent* MT5 view immediately on launch even
 * if the bridge / core-server is offline.  Every successful fetch is mirrored
 * here, every screen reads from here on first paint.  When the network comes
 * back, the cache is updated transparently.
 *
 * Broker awareness: symbol catalogue and watchlist memory are keyed by
 * `<server>|<login>` so different brokers don't share each other's symbol
 * naming (e.g. XAUUSD vs GOLD vs XAUUSDm).
 *
 * 2026-04-30 — skyliner.jojo@gmail.com
 */
object Mt5LocalCache {

    private const val P_BROKER = "mt5_cache.broker."          // broker_id, last_seen
    private const val P_WATCH  = "mt5_cache.watchlist."        // watchlist per broker
    private const val P_SYNC   = "mt5_cache.sync."             // last_sync per section

    // ── Persistence (mirrors of JarvisViewModel.persistMt5Snapshot) ─────────

    /** Save the full snapshot.  Empty sections are skipped to preserve older data. */
    suspend fun saveSnapshot(
        snapshot: Mt5TerminalSnapshot,
        changedSections: Set<String> = setOf("account", "symbols", "positions", "orders", "history"),
    ) {
        withContext(Dispatchers.IO) {
            val db = JarvisDatabaseHolder.database ?: return@withContext
            val q = db.jarvisDatabaseQueries
            val syncedAt = snapshot.syncedAt

            if (changedSections.contains("account")) {
                snapshot.account?.let { acc ->
                    q.insertMt5AccountSnapshot(
                        login = acc.login,
                        account_name = acc.accountName,
                        server = acc.server,
                        currency = acc.currency,
                        leverage = acc.leverage.toLong(),
                        balance = acc.balance,
                        equity = acc.equity,
                        margin = acc.margin,
                        free_margin = acc.freeMargin,
                        payload_json = acc.payloadJson,
                        updated_at = acc.updatedAt,
                    )
                    q.insertTradeSyncSnapshot("account", acc.payloadJson, syncedAt)
                    rememberBroker(brokerIdFor(acc), syncedAt)
                    putSyncMs("account", syncedAt)
                }
            }

            if (changedSections.contains("symbols") && snapshot.symbols.isNotEmpty()) {
                snapshot.symbols.forEach { s ->
                    q.upsertMt5Symbol(
                        symbol = s.symbol,
                        description = s.description,
                        digits = s.digits.toLong(),
                        point = s.point,
                        trade_mode = s.tradeMode,
                        bid = s.bid,
                        ask = s.ask,
                        spread = s.spread,
                        payload_json = s.payloadJson,
                        updated_at = s.updatedAt,
                    )
                }
                q.insertTradeSyncSnapshot(
                    source = "symbols",
                    payload_json = snapshot.symbols.joinToString(prefix = "[", postfix = "]") { it.payloadJson },
                    synced_at = syncedAt,
                )
                putSyncMs("symbols", syncedAt)
            }

            suspend fun persistTrades(source: String, rows: List<Mt5TradeItem>) {
                if (rows.isEmpty()) return
                rows.forEach { r ->
                    q.upsertMt5TradeRecord(
                        record_type = r.recordType,
                        ticket = r.ticket,
                        position_ticket = r.positionTicket,
                        symbol = r.symbol,
                        side = r.side,
                        volume = r.volume,
                        price_open = r.priceOpen,
                        price_current = r.priceCurrent,
                        profit = r.profit,
                        swap = r.swap,
                        commission = r.commission,
                        sl = r.sl,
                        tp = r.tp,
                        state = r.state,
                        comment = r.comment,
                        event_time = r.eventTime,
                        payload_json = r.payloadJson,
                        synced_at = r.syncedAt,
                    )
                }
                q.insertTradeSyncSnapshot(
                    source = source,
                    payload_json = rows.joinToString(prefix = "[", postfix = "]") { it.payloadJson },
                    synced_at = syncedAt,
                )
                putSyncMs(source, syncedAt)
            }

            if (changedSections.contains("positions")) persistTrades("positions", snapshot.positions)
            if (changedSections.contains("orders"))    persistTrades("orders", snapshot.orders)
            if (changedSections.contains("history"))   persistTrades("history", snapshot.deals)
        }
    }

    // ── Loading (used by ViewModel + screens on first paint) ────────────────

    /**
     * Load the freshest snapshot from local DB, regardless of whether the
     * server is reachable.  Returns `null` only when the DB is completely
     * empty (first install).  Callers should check `syncedAt` and surface
     * a "Last synced" hint if it's older than expected.
     */
    suspend fun loadSnapshot(
        positionsLimit: Long = 200,
        ordersLimit: Long = 200,
        dealsLimit: Long = 400,
    ): Mt5TerminalSnapshot? = withContext(Dispatchers.IO) {
        val db = JarvisDatabaseHolder.database ?: return@withContext null
        val q = db.jarvisDatabaseQueries

        val accountRow = q.getLatestMt5Account().executeAsOneOrNull()
        val symbolRows = q.getMt5Symbols().executeAsList()
        val positionRows = q.getRecentMt5TradeRecordsByType("POSITION", positionsLimit).executeAsList()
        val orderRows = q.getRecentMt5TradeRecordsByType("ORDER", ordersLimit).executeAsList()
        val dealRows = q.getRecentMt5TradeRecordsByType("DEAL", dealsLimit).executeAsList()

        if (accountRow == null && symbolRows.isEmpty() && positionRows.isEmpty()
            && orderRows.isEmpty() && dealRows.isEmpty()
        ) {
            return@withContext null
        }

        val account = accountRow?.let {
            Mt5AccountInfo(
                login = it.login.orEmpty(),
                accountName = it.account_name.orEmpty(),
                server = it.server.orEmpty(),
                currency = it.currency ?: "USD",
                leverage = (it.leverage ?: 0L).toInt(),
                balance = it.balance ?: 0.0,
                equity = it.equity ?: 0.0,
                margin = it.margin ?: 0.0,
                freeMargin = it.free_margin ?: 0.0,
                payloadJson = it.payload_json.orEmpty(),
                updatedAt = it.updated_at,
            )
        }
        val symbols = symbolRows.map { row ->
            Mt5SymbolInfo(
                symbol = row.symbol,
                description = row.description.orEmpty(),
                digits = (row.digits ?: 0L).toInt(),
                point = row.point ?: 0.0,
                tradeMode = row.trade_mode.orEmpty(),
                bid = row.bid ?: 0.0,
                ask = row.ask ?: 0.0,
                spread = row.spread ?: 0.0,
                payloadJson = row.payload_json.orEmpty(),
                updatedAt = row.updated_at,
            )
        }
        val syncedAt = listOf(
            accountRow?.updated_at ?: 0L,
            symbolRows.maxOfOrNull { it.updated_at } ?: 0L,
            positionRows.maxOfOrNull { it.synced_at } ?: 0L,
            orderRows.maxOfOrNull { it.synced_at } ?: 0L,
            dealRows.maxOfOrNull { it.synced_at } ?: 0L,
        ).max()

        Mt5TerminalSnapshot(
            account = account,
            symbols = symbols,
            positions = mapTrades(positionRows),
            orders = mapTrades(orderRows),
            deals = mapTrades(dealRows),
            syncedAt = syncedAt,
        )
    }

    private fun mapTrades(rows: List<Mt5TradeRecord>): List<Mt5TradeItem> = rows.map {
        Mt5TradeItem(
            recordType = it.record_type,
            ticket = it.ticket,
            positionTicket = it.position_ticket.orEmpty(),
            symbol = it.symbol,
            side = it.side.orEmpty(),
            volume = it.volume ?: 0.0,
            priceOpen = it.price_open ?: 0.0,
            priceCurrent = it.price_current ?: 0.0,
            profit = it.profit ?: 0.0,
            swap = it.swap ?: 0.0,
            commission = it.commission ?: 0.0,
            sl = it.sl ?: 0.0,
            tp = it.tp ?: 0.0,
            state = it.state.orEmpty(),
            comment = it.comment.orEmpty(),
            eventTime = it.event_time ?: 0L,
            payloadJson = it.payload_json.orEmpty(),
            syncedAt = it.synced_at,
        )
    }

    // ── Per-section sync timestamps ─────────────────────────────────────────

    suspend fun lastSyncMs(section: String): Long = withContext(Dispatchers.IO) {
        val db = JarvisDatabaseHolder.database ?: return@withContext 0L
        db.jarvisDatabaseQueries.getSetting(P_SYNC + section).executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    suspend fun lastSyncMsAny(): Long = withContext(Dispatchers.IO) {
        val sections = listOf("account", "symbols", "positions", "orders", "history")
        sections.map { lastSyncMs(it) }.max()
    }

    /**
     * Returns true when ANY section's last sync is older than `thresholdMs`,
     * or no data has been synced yet (first install).  UI uses this to flip
     * the "Server offline / Last synced N min ago" banner.
     */
    suspend fun isStale(thresholdMs: Long = 30_000L, nowMs: Long = nowMs()): Boolean {
        val last = lastSyncMsAny()
        if (last == 0L) return true
        return (nowMs - last) > thresholdMs
    }

    private suspend fun putSyncMs(section: String, ms: Long) {
        val db = JarvisDatabaseHolder.database ?: return
        db.jarvisDatabaseQueries.insertSetting(P_SYNC + section, ms.toString())
    }

    // ── Broker awareness ────────────────────────────────────────────────────

    /** Stable broker id used as a namespace for symbols and watchlists. */
    fun brokerIdFor(account: Mt5AccountInfo?): String {
        if (account == null) return "default"
        val s = account.server.trim().lowercase()
        val l = account.login.trim()
        return when {
            s.isNotEmpty() && l.isNotEmpty() -> "$s|$l"
            s.isNotEmpty() -> s
            l.isNotEmpty() -> l
            else -> "default"
        }
    }

    suspend fun activeBrokerId(): String = withContext(Dispatchers.IO) {
        val db = JarvisDatabaseHolder.database ?: return@withContext "default"
        val acc = db.jarvisDatabaseQueries.getLatestMt5Account().executeAsOneOrNull()
            ?: return@withContext "default"
        brokerIdFor(
            Mt5AccountInfo(
                login = acc.login.orEmpty(),
                server = acc.server.orEmpty(),
            )
        )
    }

    private suspend fun rememberBroker(brokerId: String, ms: Long) {
        val db = JarvisDatabaseHolder.database ?: return
        db.jarvisDatabaseQueries.insertSetting(P_BROKER + brokerId, ms.toString())
    }

    // ── Watchlist memory (per-broker) ───────────────────────────────────────

    /**
     * Save the user's watchlist for the current broker so it isn't lost when
     * connecting to a different broker (different symbol naming convention).
     */
    suspend fun saveWatchlist(brokerId: String, symbols: List<String>) {
        withContext(Dispatchers.IO) {
            val db = JarvisDatabaseHolder.database ?: return@withContext
            db.jarvisDatabaseQueries.insertSetting(
                P_WATCH + brokerId,
                symbols.joinToString(",") { it.trim().uppercase() }.trim(',')
            )
        }
    }

    suspend fun loadWatchlist(brokerId: String): List<String> = withContext(Dispatchers.IO) {
        val db = JarvisDatabaseHolder.database ?: return@withContext emptyList()
        val raw = db.jarvisDatabaseQueries.getSetting(P_WATCH + brokerId).executeAsOneOrNull()
            ?: return@withContext emptyList()
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ── Symbol catalogue helpers ────────────────────────────────────────────

    suspend fun loadSymbols(): List<Mt5SymbolInfo> = withContext(Dispatchers.IO) {
        val db = JarvisDatabaseHolder.database ?: return@withContext emptyList()
        db.jarvisDatabaseQueries.getMt5Symbols().executeAsList().map { row ->
            Mt5SymbolInfo(
                symbol = row.symbol,
                description = row.description.orEmpty(),
                digits = (row.digits ?: 0L).toInt(),
                point = row.point ?: 0.0,
                tradeMode = row.trade_mode.orEmpty(),
                bid = row.bid ?: 0.0,
                ask = row.ask ?: 0.0,
                spread = row.spread ?: 0.0,
                payloadJson = row.payload_json.orEmpty(),
                updatedAt = row.updated_at,
            )
        }
    }

    /**
     * Detect the broker's "Gold" symbol from a list — names vary widely:
     * XAUUSD, GOLD, XAU/USD, XAUUSDm, XAUUSD.r, XAUUSD-cent, etc.  Used by
     * AutoTrading first-time seeding to pre-fill the watchlist with one Gold
     * pair that *actually exists* on this broker.  Returns null if no match.
     */
    fun detectGoldSymbol(available: List<String>): String? {
        if (available.isEmpty()) return null
        val ranked = available
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { sym ->
                val u = sym.uppercase()
                val score = when {
                    u == "XAUUSD" -> 100
                    u == "XAU/USD" -> 95
                    u == "GOLD" -> 90
                    u.startsWith("XAUUSD") -> 80                  // XAUUSDm, XAUUSD.r, XAUUSD-cent
                    u.startsWith("XAU") && u.contains("USD") -> 70
                    u.startsWith("GOLD") -> 60
                    u.contains("XAU") -> 50
                    else -> 0
                }
                sym to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .toList()
        return ranked.firstOrNull()?.first
    }
}

private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
