package com.example.personalaibot.automation.backtest

/**
 * EntryParams — params "จุดเข้า" (entry) ของแต่ละกลยุทธ์ที่จูนได้ แยกจาก SL/TP (TpSlParams)
 *
 * edge ของกลยุทธ์อยู่ที่จุดเข้า — เดิมค่าพวกนี้ hard-code ใน SignalMarkerProvider/StrategySignalProvider
 * (ROC 20, EMA 50/200, RSI 30/70, Donchian 20, W52 0.98/0.90, EMA 14/60, UT key=2.0 ATR6)
 * ตอนนี้จูนได้ผ่าน trading_backtest_optimize scope=entry/both → เซฟลงตาราง EntryTuning
 * → live alert (SignalAlertProvider) และ strategy signal (StrategySignalProvider) ใช้ค่าที่จูนแล้วอัตโนมัติ
 *
 * data class เดียวเก็บทุก field — แต่ละ kind ใช้เฉพาะ field ของตัวเอง (field อื่นคง default)
 * default ทุก field = ค่าคงที่เดิมเป๊ะ → call site เก่าที่ไม่ส่ง entryParams พฤติกรรมไม่เปลี่ยน
 * 3BR เป็น candle pattern ล้วน ไม่มี params ให้จูน
 */
data class EntryParams(
    // MOM — Time-Series Momentum: ROC lookback
    val momLookback: Int = 20,
    // TR — Trend Following: EMA fast/slow cross
    val trFast: Int = 50,
    val trSlow: Int = 200,
    // REV — Short-Term Reversal: RSI period + thresholds + Bollinger
    val revRsiPeriod: Int = 14,
    val revRsiLow: Double = 30.0,
    val revRsiHigh: Double = 70.0,
    val revBbPeriod: Int = 20,
    val revBbMult: Double = 2.0,
    // DC — Donchian Breakout: channel period
    val dcPeriod: Int = 20,
    // 52H — 52-Weeks High proximity thresholds
    val w52ProxBuy: Double = 0.98,
    val w52ProxSell: Double = 0.90,
    // E — EMA 14/60 cross
    val eFast: Int = 14,
    val eSlow: Int = 60,
    // UT — UT Bot: key × ATR(period)
    val utKey: Double = 2.0,
    val utAtrPeriod: Int = 6
) {
    companion object {
        /** kind ที่มี entry params จูนได้ (3BR เป็น pattern ล้วน / SMC ใช้โครงสร้างตลาด — ไม่มีอะไรจูนตรงนี้) */
        val TUNABLE_KINDS = setOf("MOM", "TR", "REV", "DC", "52H", "E", "UT")

        fun defaultsFor(kind: String): EntryParams = EntryParams()

        /**
         * grid ขนาดเล็กต่อ kind (4-12 combos) — จูนจุดเข้าโดย SL/TP คงค่าปัจจุบัน
         * ทุก combo ยัง point-in-time เหมือนเดิม (ไม่มี lookahead)
         */
        fun gridFor(kind: String): List<EntryParams> = when (kind) {
            "MOM" -> listOf(10, 15, 20, 30, 40).map { EntryParams(momLookback = it) }
            "TR" -> listOf(20 to 100, 20 to 200, 50 to 100, 50 to 150, 50 to 200)
                .map { (f, s) -> EntryParams(trFast = f, trSlow = s) }
            "REV" -> buildList {
                for (lo in listOf(25.0, 30.0, 35.0)) for (hi in listOf(65.0, 70.0, 75.0)) {
                    if (lo < hi) add(EntryParams(revRsiLow = lo, revRsiHigh = hi))
                }
            }
            "DC" -> listOf(10, 15, 20, 30, 55).map { EntryParams(dcPeriod = it) }
            "52H" -> buildList {
                for (b in listOf(0.95, 0.98, 0.99)) for (s in listOf(0.85, 0.90, 0.95)) {
                    if (b > s) add(EntryParams(w52ProxBuy = b, w52ProxSell = s))
                }
            }
            "E" -> listOf(8 to 21, 9 to 30, 14 to 60, 20 to 50)
                .map { (f, s) -> EntryParams(eFast = f, eSlow = s) }
            "UT" -> buildList {
                for (k in listOf(1.5, 2.0, 2.5, 3.0)) for (p in listOf(4, 6, 10)) {
                    add(EntryParams(utKey = k, utAtrPeriod = p))
                }
            }
            else -> emptyList()
        }

        /** serialize เฉพาะ field ที่ kind นั้นใช้ → เก็บลง EntryTuning.params_json (รูปแบบ "k=v;k=v") */
        fun serialize(kind: String, p: EntryParams): String = when (kind) {
            "MOM" -> "momLookback=${p.momLookback}"
            "TR" -> "trFast=${p.trFast};trSlow=${p.trSlow}"
            "REV" -> "revRsiLow=${p.revRsiLow};revRsiHigh=${p.revRsiHigh}"
            "DC" -> "dcPeriod=${p.dcPeriod}"
            "52H" -> "w52ProxBuy=${p.w52ProxBuy};w52ProxSell=${p.w52ProxSell}"
            "E" -> "eFast=${p.eFast};eSlow=${p.eSlow}"
            "UT" -> "utKey=${p.utKey};utAtrPeriod=${p.utAtrPeriod}"
            else -> ""
        }

        /** parse กลับจาก params_json — คืน null ถ้า format ไม่ถูก/field ไม่ครบ (caller fallback เป็น default) */
        fun deserialize(kind: String, json: String): EntryParams? {
            if (json.isBlank()) return null
            val map = json.split(';').mapNotNull {
                val kv = it.split('=')
                if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
            }.toMap()
            return runCatching {
                when (kind) {
                    "MOM" -> EntryParams(momLookback = map.getValue("momLookback").toInt())
                    "TR" -> EntryParams(trFast = map.getValue("trFast").toInt(), trSlow = map.getValue("trSlow").toInt())
                    "REV" -> EntryParams(revRsiLow = map.getValue("revRsiLow").toDouble(), revRsiHigh = map.getValue("revRsiHigh").toDouble())
                    "DC" -> EntryParams(dcPeriod = map.getValue("dcPeriod").toInt())
                    "52H" -> EntryParams(w52ProxBuy = map.getValue("w52ProxBuy").toDouble(), w52ProxSell = map.getValue("w52ProxSell").toDouble())
                    "E" -> EntryParams(eFast = map.getValue("eFast").toInt(), eSlow = map.getValue("eSlow").toInt())
                    "UT" -> EntryParams(utKey = map.getValue("utKey").toDouble(), utAtrPeriod = map.getValue("utAtrPeriod").toInt())
                    else -> null
                }
            }.getOrNull()
        }

        /** ข้อความย่อสำหรับแสดงในรายงาน เช่น "dcPeriod=30" */
        fun describe(kind: String, p: EntryParams): String = serialize(kind, p).replace(';', ' ')
    }
}
