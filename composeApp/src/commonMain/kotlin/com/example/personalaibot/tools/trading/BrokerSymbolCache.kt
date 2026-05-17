package com.example.personalaibot.tools.trading

import com.example.personalaibot.data.SymbolCategory
import com.example.personalaibot.data.SymbolCategoryClassifier
import com.example.personalaibot.db.JarvisDatabaseHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * BrokerSymbolCache
 * ─────────────────
 * In-memory TTL cache of the broker's full symbol catalogue, with per-broker
 * favorites persistence (SQLDelight AppSetting table).
 *
 * ● loadAll() reads from Mt5LocalCache.loadSymbols() (SQLDelight) on first call,
 *   then serves from RAM until TTL expires or invalidate() is called.
 * ● Favorites are stored as comma-separated keys in AppSetting under
 *   "broker_symbol_favs.<brokerId>" so they survive app restarts and
 *   don't bleed across broker accounts.
 *
 * Usage pattern (from ViewModel / SymbolPickerDialog):
 *   val symbols = BrokerSymbolCache.loadAll(brokerId)
 *   val grouped = BrokerSymbolCache.grouped(brokerId)
 *   BrokerSymbolCache.toggleFavorite(brokerId, "XAUUSD")
 *   val favs = BrokerSymbolCache.favorites(brokerId)
 *
 * 2026-05-01 — skyliner.jojo@gmail.com
 */
object BrokerSymbolCache {

    // ── Constants ─────────────────────────────────────────────────────────────
    private const val TTL_MS = 5 * 60_000L          // 5 min in-memory TTL
    private const val PREF_FAV = "broker_symbol_favs."

    // ── In-memory store ───────────────────────────────────────────────────────
    private data class CacheEntry(
        val symbols: List<Mt5SymbolInfo>,
        val loadedAt: Long,
    )

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()   // brokerId → entry

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Load the full symbol list for [brokerId].
     * Returns the cached list if it's fresh (< TTL_MS), otherwise reloads from
     * SQLDelight (which was populated by the last successful server sync).
     *
     * If the DB is empty, returns an empty list — callers should trigger a
     * server sync and call [invalidate] when it completes.
     */
    suspend fun loadAll(brokerId: String, nowMs: Long = nowMs()): List<Mt5SymbolInfo> {
        return mutex.withLock {
            val entry = cache[brokerId]
            if (entry != null && nowMs - entry.loadedAt < TTL_MS) {
                entry.symbols
            } else {
                val fresh = Mt5LocalCache.loadSymbols()
                cache[brokerId] = CacheEntry(fresh, nowMs)
                fresh
            }
        }
    }

    /**
     * Return symbols grouped by [SymbolCategory].
     * Favorites always appear at the top regardless of category.
     */
    suspend fun grouped(brokerId: String): Map<SymbolCategory, List<String>> {
        val all = loadAll(brokerId).map { it.symbol }
        return SymbolCategoryClassifier.groupSymbols(all)
    }

    /**
     * Return only [SymbolCategory.METALS] + [SymbolCategory.CRYPTO] symbols
     * that also appear in the user's favorites, plus all favorites regardless
     * of category.  Used as the default "quick-pick" row in the Trade tab.
     */
    suspend fun favoritesFirst(brokerId: String): List<String> {
        val favs = favorites(brokerId).toSet()
        val all = loadAll(brokerId).map { it.symbol }
        val nonFavHighValue = all.filter { sym ->
            !favs.contains(sym) &&
                SymbolCategoryClassifier.classify(sym).let {
                    it == SymbolCategory.METALS || it == SymbolCategory.CRYPTO
                }
        }.take(10)
        return (favs.toList() + nonFavHighValue).distinct()
    }

    /**
     * Invalidate the cached symbol list for [brokerId] (e.g. after a fresh
     * server sync so the picker shows updated spread / bid prices).
     */
    suspend fun invalidate(brokerId: String) {
        mutex.withLock {
            cache.remove(brokerId)
        }
    }

    /** Invalidate ALL broker caches (used after DB wipe / logout). */
    suspend fun invalidateAll() {
        mutex.withLock {
            cache.clear()
        }
    }

    // ── Favorites persistence ─────────────────────────────────────────────────

    /**
     * Load the user's favorite symbols for [brokerId] from SQLDelight.
     * Returns an empty list on first install.
     */
    suspend fun favorites(brokerId: String): List<String> = withContext(Dispatchers.IO) {
        val db = JarvisDatabaseHolder.database ?: return@withContext emptyList()
        val raw = db.jarvisDatabaseQueries
            .getSetting(PREF_FAV + brokerId)
            .executeAsOneOrNull()
            ?: return@withContext emptyList()
        raw.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    }

    /**
     * Persist the favorites list (replaces the whole list).
     */
    suspend fun saveFavorites(brokerId: String, symbols: List<String>) {
        withContext(Dispatchers.IO) {
            val db = JarvisDatabaseHolder.database ?: return@withContext
            val value = symbols.map { it.trim().uppercase() }
                .distinct()
                .filter { it.isNotEmpty() }
                .joinToString(",")
            db.jarvisDatabaseQueries.insertSetting(PREF_FAV + brokerId, value)
        }
    }

    /**
     * Add or remove [symbol] from favorites (toggle).
     * @return `true` if symbol is now a favorite, `false` if it was removed.
     */
    suspend fun toggleFavorite(brokerId: String, symbol: String): Boolean {
        val current = favorites(brokerId).toMutableList()
        val sym = symbol.trim().uppercase()
        return if (current.contains(sym)) {
            current.remove(sym)
            saveFavorites(brokerId, current)
            false
        } else {
            current.add(sym)
            saveFavorites(brokerId, current)
            true
        }
    }

    /** True if [symbol] is in the favorites list for [brokerId]. */
    suspend fun isFavorite(brokerId: String, symbol: String): Boolean {
        return favorites(brokerId).contains(symbol.trim().uppercase())
    }

    // ── Symbol search ─────────────────────────────────────────────────────────

    /**
     * Filter symbol names by a free-text query (case-insensitive prefix or
     * substring match against symbol code and description).
     */
    suspend fun search(brokerId: String, query: String): List<Mt5SymbolInfo> {
        if (query.isBlank()) return loadAll(brokerId)
        val q = query.trim().uppercase()
        return loadAll(brokerId).filter { info ->
            info.symbol.uppercase().contains(q) ||
                info.description.uppercase().contains(q)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
