package com.example.personalaibot.ai

/**
 * Utility for detecting trading-related intent in user prompts.
 * Shared between GeminiService and JarvisOrchestrator to ensure consistent tool selection.
 */
object TradingIntentUtility {

    fun isTradingPrompt(prompt: String, intentAddon: String = ""): Boolean {
        val text = "$prompt $intentAddon".lowercase()
        val keywords = listOf(
            "trading", "trade", "xau", "xauusd", "gold", "forex", "smc",
            "liquidity", "sweep", "order block", "orderblock", "bos", "choch",
            "premium", "discount", "fvg", "price", "gc=f", "oanda", "tv:", "ทอง", "หุ้น", "คริปโต"
        )
        return keywords.any { text.contains(it) }
    }

    fun isSmcPrompt(prompt: String, intentAddon: String = ""): Boolean {
        val text = "$prompt $intentAddon".lowercase()
        val keywords = listOf(
            "smc", "sweep", "liquidity", "order block", "orderblock",
            "bos", "choch", "fvg", "premium", "discount", "ict",
            "trading_smc", "market structure",
            "สภาพคล่อง", "ออเดอร์บล็อก", "โครงสร้าง", "สวีป", "สมาร์ทมันนี่",
            "โซนแนวรับแนวต้านสถาบัน", "institutional"
        )
        return keywords.any { text.contains(it) }
    }

    fun isMt5Prompt(prompt: String, intentAddon: String = ""): Boolean {
        val text = "$prompt $intentAddon".lowercase()
        // เฉพาะคำที่ชี้ชัดว่าเป็น MT5/broker account เท่านั้น
        // (ห้ามใส่คำกว้างอย่าง order/position/history/balance — ชนกับ SMC "order blocks" แล้วโหมด MT5-only ติดเอง)
        val keywords = listOf(
            "mt5", "metatrader", "broker", "analyze mt5", "analyse mt5",
            "บัญชีเทรด", "บัญชี mt5", "พอร์ต", "โบรก", "โบรกเกอร์",
            "equity", "margin", "คำสั่งซื้อขาย", "สถานะคำสั่ง",
            "เปิดออเดอร์", "ปิดออเดอร์", "ออเดอร์ค้าง", "ประวัติเทรด",
            "trade journal", "break even", "break-even"
        )
        return keywords.any { text.contains(it) }
    }

    fun isDeepAnalysisPrompt(prompt: String, intentAddon: String = ""): Boolean {
        val text = "$prompt $intentAddon".lowercase()
        val keywords = listOf(
            "วิเคราะห์เชิงลึก", "deep analysis", "confluence", "debate", "consensus",
            "วิเคราะห์ confluence", "วิเคราะห์รอบด้าน", "multi-agent", "วิเคราะห์ละเอียด"
        )
        return keywords.any { text.contains(it) }
    }
}
