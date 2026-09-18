package com.skyliner2008.jarvis.tools.trading

/**
 * Central in-memory OHLCV store (foundation layer)
 *
 * เป็นชั้น cache ระหว่าง DB กับผู้เรียก — ของจริงอยู่ใน TvCandle (SQLite)
 *
 * ข้อกำหนดที่แก้จากเวอร์ชันเดิม:
 *  - **มีเพดาน**: เดิม map โตไม่จำกัดทั้งจำนวน key และความยาวแต่ละซีรีส์ (memory leak ใน session ยาว)
 *  - **เลือก source ตามลำดับความน่าเชื่อถือ** ไม่ใช่ "ซีรีส์ไหนยาวสุด" ซึ่งทำให้ BINANCE_PAXG
 *    (คนละ instrument กับทองจริง) ชนะ TV ได้ถ้าบังเอิญแคชไว้เยอะกว่า
 */
object OhlcvCentralStore {

    /** จำนวนแท่งสูงสุดที่เก็บต่อหนึ่งซีรีส์ — ลึกพอสำหรับ warm-up EMA200 (800 แท่ง) */
    private const val MAX_BARS_PER_SERIES = 1_200

    /** จำนวนซีรีส์สูงสุดใน memory (symbol × interval × source) ก่อนเริ่ม evict ตัวที่ไม่ได้แตะนานสุด */
    private const val MAX_SERIES = 64

    /**
     * ลำดับความน่าเชื่อถือของ source เมื่อมีหลายตัวให้เลือกสำหรับ symbol/interval เดียวกัน
     * ตัวที่ prefix ตรงและอยู่ก่อนในลิสต์ชนะ; ตัวที่ไม่อยู่ในลิสต์ถือว่าท้ายสุด
     */
    private val SOURCE_PRIORITY = listOf(
        "TV:OANDA",
        "TV:FX_IDC",
        "TV:TVC",
        "TV:BINANCE",
        "TV:NASDAQ",
        "TV:NYSE",
        "TV:",        // TV อื่นๆ ที่ไม่ได้ระบุไว้
        "BINANCE"     // fallback คริปโตแท้
    )

    private data class Key(
        val symbol: String,
        val interval: String,
        val source: String
    )

    private val store = mutableMapOf<Key, List<Candle>>()

    /** เวลาที่แต่ละซีรีส์ถูกแตะล่าสุด — ใช้ตัดสินว่าจะ evict ตัวไหน */
    private val touchedAt = mutableMapOf<Key, Long>()
    private var tick = 0L

    private fun normalizeSymbol(symbol: String): String = symbol.uppercase()
    private fun normalizeInterval(interval: String): String = interval.lowercase()
    private fun normalizeSource(source: String): String = source.uppercase()

    private fun priorityOf(source: String): Int {
        val idx = SOURCE_PRIORITY.indexOfFirst { source.startsWith(it.uppercase()) }
        return if (idx >= 0) idx else SOURCE_PRIORITY.size
    }

    private fun touch(key: Key) {
        touchedAt[key] = ++tick
    }

    /** ตัดซีรีส์ที่ไม่ได้แตะนานสุดออกจนเหลือไม่เกิน [MAX_SERIES] */
    private fun evictIfNeeded() {
        while (store.size > MAX_SERIES) {
            val oldest = touchedAt.entries.minByOrNull { it.value }?.key ?: return
            store.remove(oldest)
            touchedAt.remove(oldest)
        }
    }

    @Synchronized
    fun put(symbol: String, interval: String, source: String, candles: List<Candle>) {
        if (candles.isEmpty()) return
        val key = Key(normalizeSymbol(symbol), normalizeInterval(interval), normalizeSource(source))
        // รวมกับของเดิม ตัดซ้ำตาม timestamp (ตัวใหม่ทับตัวเก่า) แล้วเรียงตามเวลา
        // reversed() ก่อน distinctBy เพื่อให้ "แท่งที่เข้ามาใหม่" ชนะ ไม่ใช่แท่งเก่าที่อาจยังไม่ปิด
        val merged = (store[key].orEmpty() + candles)
            .asReversed()
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
        store[key] = merged.takeLast(MAX_BARS_PER_SERIES)
        touch(key)
        evictIfNeeded()
    }

    @Synchronized
    fun get(symbol: String, interval: String, source: String, limit: Int): List<Candle> {
        val key = Key(normalizeSymbol(symbol), normalizeInterval(interval), normalizeSource(source))
        val series = store[key] ?: return emptyList()
        touch(key)
        return series.takeLast(limit)
    }

    /**
     * หาซีรีส์ที่ดีที่สุดของ symbol/interval นี้จาก source ใดก็ได้
     *
     * เรียงตาม **ลำดับความน่าเชื่อถือของ source ก่อน** แล้วจึงดูความยาว
     * (เดิมใช้ maxByOrNull { it.value.size } อย่างเดียว — ซีรีส์ยาวจาก source ที่ผิด instrument ชนะได้)
     */
    @Synchronized
    fun getAny(symbol: String, interval: String, limit: Int): Pair<String, List<Candle>>? {
        val sym = normalizeSymbol(symbol)
        val tf = normalizeInterval(interval)
        val best = store.entries
            .filter { it.key.symbol == sym && it.key.interval == tf && it.value.isNotEmpty() }
            .minWithOrNull(
                compareBy<Map.Entry<Key, List<Candle>>> { priorityOf(it.key.source) }
                    .thenByDescending { it.value.size }
            )
            ?: return null
        touch(best.key)
        return best.key.source to best.value.takeLast(limit)
    }

    /** ลบซีรีส์ของ symbol/interval ทุก source — เรียกหลังลบออกจาก DB (OhlcvMaintenance) */
    @Synchronized
    fun invalidate(symbol: String, interval: String) {
        val sym = normalizeSymbol(symbol)
        val tf = normalizeInterval(interval)
        store.keys.filter { it.symbol == sym && it.interval == tf }.forEach {
            store.remove(it)
            touchedAt.remove(it)
        }
    }

    /** ล้าง store ทั้งหมด — ใช้หลังกู้คืนฐานข้อมูล และในเทสต์ */
    @Synchronized
    fun clearAll() = clearForTest()

    /** ล้าง store — ใช้ในเทสต์เท่านั้น */
    @Synchronized
    fun clearForTest() {
        store.clear()
        touchedAt.clear()
        tick = 0L
    }
}
