package com.skyliner2008.jarvis.ai

/**
 * Keyword matchers ที่ LiveToolBridge ใช้ตัดสินว่า tool call จาก Live model ตรงกับคำขอจริงของผู้ใช้หรือไม่
 * แยกเป็น object เดี่ยวเพื่อ unit test ได้ (review 2026-09-16)
 */
object LiveIntentMatchers {

    /**
     * Live transcription แทรกช่องว่างระหว่างคำไทย ("เท่า ไหร่", "ราคา ทองคำ") — เทียบคีย์เวิร์ดตรงๆ จึงพลาด
     * (เคสจริง 2026-09-16: "ราคา ทองคำ เท่า ไหร่ ตอน นี้" ไม่เข้า USER_QUERY profile → model เรียก SMC analysis ซ้ำ รอ 14 วิ)
     */
    fun normalize(text: String): String = text.lowercase().filterNot { it.isWhitespace() }

    /** เทียบคีย์เวิร์ดทั้งแบบมีช่องว่างและแบบตัดช่องว่าง */
    fun matchesAny(prompt: String, terms: List<String>): Boolean {
        if (prompt.isBlank()) return false
        val lower = prompt.lowercase()
        val compact = normalize(prompt)
        return terms.any { term ->
            val t = term.lowercase()
            lower.contains(t) || compact.contains(t.replace(" ", ""))
        }
    }

    private fun has(prompt: String, vararg terms: String): Boolean = matchesAny(prompt, terms.toList())

    /** tool ที่ Live model มักเรียกผิดแทน intent อื่น (redirect guards ทำงานเฉพาะกลุ่มนี้) */
    val MISROUTED_TRADING_TOOLS = setOf(
        "trading_fear_greed", "trading_sentiment", "trading_market_snapshot", "trading_price", "trading_indicators"
    )


    fun tradingProfileFor(prompt: String): String = when {
        has(prompt, "smc", "smart money", "order block", "fvg") -> "SMC"
        has(prompt, "rsi", "ema", "sma", "atr", "แนวรับ", "แนวต้าน", "support", "resistance", "เท่าไร", "เท่าไหร่", "กี่บาท", "ราคาเท่า") -> "USER_QUERY"
        has(prompt, "วิเคราะห์", "analysis", "overview", "ภาพรวม", "5 มิติ", "5มิติ", "confluence") -> "AI"
        else -> "NONE"
    }

    fun isTradingAnalysisTool(name: String): Boolean = name in setOf(
        "trading_deep_analysis_suite",
        "trading_technical_analysis",
        "trading_smc_analysis"
    )

    private val DEFAULT_TIMEFRAMES = setOf("15m", "m15", "1h", "h1", "4h", "h4")
    private val HIGHER_TIMEFRAMES = setOf("1d", "d1", "d", "1w", "w1", "w")

    /** ผู้ใช้ระบุ timeframe ใหญ่ (D1/W1) เองในคำพูดหรือไม่ */
    fun userRequestsHigherTimeframe(prompt: String): Boolean {
        val p = prompt.lowercase()
        val words = Regex("""[a-z0-9]+""").findAll(p).map { it.value }.toSet()
        return listOf("d1", "1d", "w1", "1w", "daily", "weekly").any { it in words } ||
            matchesAny(prompt, listOf("รายวัน", "รายสัปดาห์", "กราฟวัน", "ไทม์เฟรมวัน", "เดย์"))
    }

    /**
     * AI Profile จำกัด TF เริ่มต้นที่ M15/H1/H4 — แต่ถ้าผู้ใช้ขอ D1/W1 เองต้องอนุญาต
     * (เดิมบล็อก D1 ทุกกรณี ทั้งที่ข้อความ guard บอกว่า "เว้นแต่ผู้ใช้ระบุเอง")
     */
    fun allowedTradingTimeframe(args: Map<String, String>, prompt: String = ""): Boolean {
        val tf = requestedTimeframe(args)
        if (tf in DEFAULT_TIMEFRAMES) return true
        if (tf in HIGHER_TIMEFRAMES) return userRequestsHigherTimeframe(prompt)
        // TF เล็ก (M1/M5/M30) — อนุญาตเมื่อผู้ใช้พูดถึงเอง (เดิมบล็อกแม้ผู้ใช้ขอ "M5")
        return userMentionsMinuteTimeframe(prompt, tf)
    }

    /** timeframe ที่โมเดลส่งมา — ได้หลายชื่อ: "XAUUSD@m15", timeframe=..., interval=... (เคสจริงจาก log) */
    private fun requestedTimeframe(args: Map<String, String>): String =
        (args["symbol"] ?: "").substringAfter("@", "").lowercase()
            .ifBlank { args["timeframe"]?.lowercase().orEmpty() }
            .ifBlank { args["interval"]?.lowercase().orEmpty() }
            .ifBlank { args["tf"]?.lowercase().orEmpty() }
            .ifBlank { "1h" }

    /** ผู้ใช้พูดถึง TF นาทีนี้เอง เช่น "M5", "5m", "5 นาที" */
    private fun userMentionsMinuteTimeframe(prompt: String, tf: String): Boolean {
        val minutes = Regex("""\d+""").find(tf)?.value ?: return false
        val p = prompt.lowercase()
        val words = Regex("""[a-z0-9]+""").findAll(p).map { it.value }.toSet()
        return "m$minutes" in words || "${minutes}m" in words ||
            p.contains("$minutes นาที") || p.contains("${minutes}นาที")
    }

    /**
     * แทน timeframe ที่ผู้ใช้ไม่ได้ขอด้วย 15m แล้วให้ tool ทำงานต่อ
     * (เดิมบล็อกทั้งคำขอด้วยข้อความเรื่อง D1 — โมเดลเลือก 5m เองแล้วตอบผู้ใช้ว่า "ดึงข้อมูลไม่สำเร็จ", logcat 2026-09-24 00:26)
     */
    fun withDefaultTimeframe(args: Map<String, String>): Map<String, String> {
        val out = args.toMutableMap()
        args["symbol"]?.takeIf { it.contains("@") }?.let { out["symbol"] = it.substringBefore("@") }
        val keys = listOf("interval", "timeframe", "tf").filter { it in args }
        if (keys.isEmpty()) out["interval"] = "15m" else keys.forEach { out[it] = "15m" }
        return out
    }

    fun profileToolAllowed(prompt: String, toolName: String, args: Map<String, String>): Boolean {
        val profile = tradingProfileFor(prompt)
        if (profile == "NONE" || !isTradingAnalysisTool(toolName)) return true
        if (!allowedTradingTimeframe(args, prompt)) return false
        return when (profile) {
            "SMC" -> toolName == "trading_smc_analysis"
            // "RSI / แนวรับ / เท่าไหร่" needs real indicator values: one technical analysis, no deep suite
            "USER_QUERY" -> toolName == "trading_technical_analysis"
            else -> toolName == "trading_deep_analysis_suite"
        }
    }

    fun isSignalAlertRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val alertTerms = listOf("แจ้งเตือน", "signal alert", "signal", "สัญญาณ")
        val tradeTerms = listOf("ทอง", "gold", "xau", "buy", "sell", "ซื้อ", "ขาย")
        return matchesAny(prompt, alertTerms) && matchesAny(prompt, tradeTerms)
    }

    /** คำขอเรื่องตลาด/เทรดจริง — guard ที่เปลี่ยน trading tool เป็นอย่างอื่นต้องไม่ทำงาน */
    fun isTradingQuestion(prompt: String): Boolean =
        TradingIntentUtility.isTradingPrompt(prompt) || TradingIntentUtility.isMt5Prompt(prompt) ||
            TradingIntentUtility.isSmcPrompt(prompt) ||
            matchesAny(
                prompt,
                listOf("ราคา", "หุ้น", "ตลาด", "กราฟ", "เทรด", "xau", "btc", "บิทคอยน์", "ดัชนี", "set50", "nasdaq", "ดาวโจนส์")
            )

    fun isAvatarEmotionRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        // ("หน้า" / "แบบ" / "mood" alone matched ordinary sentences like "แบบไหนดี" or "หน้าจอ")
        val avatarTerms = listOf(
            "หน้าที่", "แบบที่", "moodset",
            "เดโม", "เดโม่", "demo",
            "แสดงอารมณ์", "โชว์อารมณ์", "ทดสอบอารมณ์", "อารมณ์ทั้งหมด",
            "ซะแดงเดโมอารมณ์", "แสดงเดโม่อารมณ์", "ซะแดงอารมณ์",
            "ทำหน้า", "สีหน้า", "avatar", "อวาตาร์", "ขยิบตา", "ยิ้มหน่อย", "หน้าตา",
            "ทุกหน้า", "ทุกแบบ", "หน้าทั้งหมด", "all pages", "all moods", "play all", "show all"
        )
        return matchesAny(prompt, avatarTerms) ||
            com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.parsePageNumber(prompt) != null ||
            com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog.isPlayAllCommand(prompt)
    }

    fun isSceneRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val sceneTerms = listOf(
            "ฉาก", "กินข้าว", "ให้อาหาร", "กินพิซซ่า", "กินเบอร์เกอร์", "กินเค้ก", "กินไอติม", "กินป๊อปคอร์น", "หิวข้าว",
            "ดื่มน้ำ", "ขอดื่ม", "กินกาแฟ", "ดื่มกาแฟ", "กินชา", "ดื่มชา", "กินโค้ก", "กินชานม", "หิวน้ำ",
            "อาบน้ำ", "ถูสบู่", "แปรงฟัน", "สระผม",
            "เล่นเกม", "จอยเกม", "ทำงาน", "อ่านหนังสือ",
            // (no "ทองคำ" / "เหรียญทอง" / "คนรวย": those are gold-price questions far more often than scenes)
            "ใส่แว่น", "แว่นตา", "thug life", "แว่นดำ", "มงกุฎ", "ราชา", "เจ้าหญิง",
            "ไฟลุก", "วิ่งหนีไฟ", "ไฟไหม้", "โดนช็อต", "ฟ้าผ่า", "ไฟดูด", "วิญญาณหลุด", "เหนื่อยมาก", "หมดแรง", "ตายแป๊บ",
            "ยิงจรวด", "มิสไซล์", "ถล่มจอ", "ซุปเปอร์เลิฟ", "คลั่งรัก", "หัวใจเต็มจอ", "อกหัก", "ร้องไห้หนักมาก", "ปาร์ตี้", "ฉลอง"
        )
        return matchesAny(prompt, sceneTerms)
    }

    fun isAlwaysLiveRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "โหมดควบคุม", "โหมดขับขี่", "โหมดรถยนต์", "โหมดสัตว์เลี้ยง",
            "เปิดโหมดควบคุม", "เปิดโหมดขับขี่", "เปิดโหมดรถยนต์", "เปิดโหมดสัตว์เลี้ยง",
            "เข้าโหมดควบคุม", "เข้าโหมดขับขี่", "เข้าโหมดรถยนต์", "เข้าโหมดสัตว์เลี้ยง",
            "โหมด always", "always live", "drive mode", "car mode", "pet mode"
        )
        return matchesAny(prompt, terms)
    }

    fun isMediaRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "เปิดเพลง", "เล่นเพลง", "หยุดเพลง", "ข้ามเพลง", "เพลงถัดไป", "เพลงก่อนหน้า",
            "เพลงอะไร", "พักเพลง", "สลับเพลง", "play music", "stop music", "next song", "previous song"
        )
        return matchesAny(prompt, terms)
    }

    /**
     * เดิมมีคำว่า "ไปที่" เดี่ยวๆ ซึ่งตรงกับ "ราคาทองจะไปที่ 2400 ไหม" → เปิด Google Maps ผิด
     * ตอนนี้ต้องมีคำกริยาการเดินทางชัดเจน
     */
    fun isNavigationRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "นำทางไป", "นำทาง", "เปิดแผนที่ไป", "เปิด google maps ไป", "พาไปที่", "ขับไปที่", "ขอเส้นทางไป",
            "navigate to", "directions to"
        )
        return matchesAny(prompt, terms)
    }

    fun extractNavigationDestination(prompt: String): String =
        prompt.lowercase()
            .replace("นำทางไป", "")
            .replace("เปิดแผนที่ไป", "")
            .replace("เปิด google maps ไป", "")
            .replace("ขอเส้นทางไป", "")
            .replace("พาไปที่", "")
            .replace("ขับไปที่", "")
            .replace("นำทาง", "")
            .replace("navigate to", "")
            .replace("directions to", "")
            .trim()

    fun isNotificationRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "อ่านแจ้งเตือน", "อ่านข้อความ", "มีแจ้งเตือนอะไร", "เช็คแจ้งเตือน", "มีไลน์เข้าไหม",
            "read notifications", "read notification", "check notifications"
        )
        return matchesAny(prompt, terms)
    }

    fun isLocationOrSpeedRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "ขับเร็วเท่าไหร่", "ความเร็วเท่าไหร่", "วิ่งเร็วเท่าไหร่", "ตอนนี้อยู่ที่ไหน",
            "พิกัดปัจจุบัน", "เช็คตำแหน่ง", "ตำแหน่งปัจจุบัน", "current speed", "where am i"
        )
        return matchesAny(prompt, terms)
    }

    fun isParkingRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "จอดรถอยู่ที่ไหน", "จอดรถไว้ตรงไหน", "รถจอดอยู่ที่ไหน", "รถจอดที่ไหน",
            "หาที่จอดรถ", "รถอยู่ไหน", "จำที่จอดรถ", "บันทึกที่จอดรถ", "บันทึกจุดจอด",
            "จอดรถตรงนี้", "where did i park", "where is my car", "save parking", "remember parking"
        )
        return matchesAny(prompt, terms)
    }

    fun isNightModeRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        val terms = listOf(
            "เปิดโหมดกลางคืน", "ปิดโหมดกลางคืน", "โหมดกลางคืน", "ลดแสงสะท้อน", "หรี่แสง",
            "night mode", "low glare"
        )
        return matchesAny(prompt, terms)
    }

    /**
     * ผู้ใช้สั่งให้ "พิมพ์/ค้นหา/ตอบข้อความ" จริงหรือไม่
     *
     * ใช้กัน device_type_text ที่โมเดลเรียกเอง — พบจริง 2026-09-20: ผู้ใช้พูดแค่ "เปิด YouTube"
     * แต่โมเดลพิมพ์คำค้นหาที่ค้างจากบทสนทนาก่อนหน้าแล้วกดส่งให้เอง
     */
    fun hasTypingIntent(prompt: String): Boolean {
        val terms = listOf(
            "พิมพ์", "ค้นหา", "เสิร์ช", "หาเพลง", "หาคลิป", "หาวิดีโอ", "หาข้อมูล", "เขียน",
            "ตอบ", "ส่งข้อความ", "ใส่ข้อความ", "กรอก", "แชท", "ทัก",
            "search", "type", "write", "reply", "enter", "fill", "message"
        )
        return matchesAny(prompt, terms)
    }

    /**
     * redirect guard ทุกตัว (always live / avatar / media / navigation / notification / location / parking / night)
     * ทำงานได้เฉพาะเมื่อ tool ที่ถูกเรียกอยู่ในกลุ่มที่ model มักเรียกผิด และคำขอไม่ใช่คำถามเทรด
     */
    fun canRedirectMisroutedTool(toolName: String, prompt: String): Boolean =
        toolName in MISROUTED_TRADING_TOOLS && !isTradingQuestion(prompt)
}
