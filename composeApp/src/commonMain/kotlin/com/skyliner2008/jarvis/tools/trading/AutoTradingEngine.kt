package com.skyliner2008.jarvis.tools.trading

/**
 * ─── DEPRECATED ─────────────────────────────────────────────────────────────
 *
 * `com.skyliner2008.jarvis.tools.trading.AutoTradingEngine` ถูกแทนที่ด้วย
 * `com.skyliner2008.jarvis.tools.trading.auto.AutoTradingEngine` ซึ่งเป็น
 * เวอร์ชัน modular (10 analyzer × 8 strategy × RiskManager × TradeJournalStore).
 *
 * ไม่ต้อง import จาก package นี้อีกต่อไป — ใช้ `...tools.trading.auto.*` แทน
 */
@Deprecated(
    message = "ใช้ com.skyliner2008.jarvis.tools.trading.auto.AutoTradingEngine แทน",
    replaceWith = ReplaceWith("com.skyliner2008.jarvis.tools.trading.auto.AutoTradingEngine")
)
internal class AutoTradingEngineDeprecated private constructor()
