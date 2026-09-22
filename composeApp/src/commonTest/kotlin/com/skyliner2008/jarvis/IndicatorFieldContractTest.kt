package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.AlertFieldCatalog
import com.skyliner2008.jarvis.automation.AutomationCondition
import com.skyliner2008.jarvis.automation.AutomationEvaluator
import com.skyliner2008.jarvis.automation.ConditionOperator
import com.skyliner2008.jarvis.automation.IndicatorAlertProvider
import com.skyliner2008.jarvis.tools.trading.Candle
import com.skyliner2008.jarvis.tools.trading.SmcApiService
import com.skyliner2008.jarvis.tools.trading.TaIndicators
import io.ktor.client.HttpClient
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P2/P3: สัญญาระหว่าง "ค่าที่ประกาศให้ AI" กับ "ค่าที่ระบบส่งออกจริง"
 *
 * บัคเดิม: tool definition โฆษณา adx14 / resistance1 / donchian_upper / obv / mfi14 / ichimoku_*
 * แต่ provider ส่งออกเป็น adx / r1 / donchian20_high และไม่มี obv/mfi/ichimoku เลย
 * AutomationEvaluator หา field ไม่เจอแล้ว `return false` เงียบๆ
 * → ผู้ใช้ตั้ง alert เห็นว่า active แต่ไม่มีวันยิง และไม่มีใครบอก
 */
class IndicatorFieldContractTest {

    private val provider = IndicatorAlertProvider(SmcApiService(HttpClient()))

    /** ชุดแท่งสังเคราะห์ที่ยาวพอให้อินดิเคเตอร์ทุกตัวคำนวณได้จริง */
    private fun syntheticCandles(count: Int = TaIndicators.Warmup.FULL_SET): List<Candle> {
        val tfMs = TaIndicators.timeframeMillis("1h")
        val start = 1_700_000_000_000L
        return (0 until count).map { i ->
            val base = 2000.0 + i * 0.4 + sin(i / 7.0) * 25.0
            Candle(
                open = base - 1.5,
                high = base + 4.0,
                low = base - 4.0,
                close = base,
                volume = 1000.0 + (i % 17) * 50.0,
                timestamp = start + i * tfMs,
                isClosed = true
            )
        }
    }

    private fun computeAll(): Map<String, String> =
        provider.computeIndicators("XAUUSD", "1h", "TV:OANDA", syntheticCandles())

    // ─── ทุก field ที่ประกาศต้องส่งออกได้จริง ────────────────────────────────

    @Test
    fun everyAdvertisedAlertFieldIsActuallyProduced() {
        val produced = computeAll()
        val advertised = AlertFieldCatalog.INDICATORS.fields.map { it.field }

        val missing = advertised.filter { it !in produced }
        assertTrue(
            missing.isEmpty(),
            "field ที่ประกาศให้ AI แต่ระบบไม่ส่งออก (alert จะไม่มีวันยิง): $missing"
        )
    }

    /** ทุก field ที่ provider ส่งออกต้องอยู่ในทะเบียน SUPPORTED_FIELDS */
    @Test
    fun producedFieldsAreAllDeclaredInRegistry() {
        val produced = computeAll().keys
        val undeclared = produced.filter { it !in IndicatorAlertProvider.SUPPORTED_FIELDS }
        assertTrue(undeclared.isEmpty(), "field ที่ส่งออกแต่ไม่ได้ประกาศไว้: $undeclared")
    }

    /** ทะเบียนต้องครอบคลุมทุก field ที่ catalogue ให้ผู้ใช้เลือกได้ */
    @Test
    fun catalogueFieldsAreSubsetOfRegistry() {
        val advertised = AlertFieldCatalog.INDICATORS.fields.map { it.field }
        val notInRegistry = advertised.filter { it !in IndicatorAlertProvider.SUPPORTED_FIELDS }
        assertTrue(notInRegistry.isEmpty(), "catalogue มี field ที่ไม่อยู่ในทะเบียน: $notInRegistry")
    }

    // ─── อินดิเคเตอร์ใหม่ต้องคำนวณได้จริง ไม่ใช่แค่ประกาศชื่อ ────────────────

    @Test
    fun newlyAddedIndicatorsProduceFiniteValues() {
        val produced = computeAll()
        val newlyAdded = listOf(
            "adx14", "di_plus", "di_minus", "atr_pct",
            "obv", "obv_slope20", "mfi14", "roc", "williams_r",
            "volume_sma20", "volume_ratio20",
            "ichimoku_tenkan", "ichimoku_kijun", "ichimoku_cloud_top", "ichimoku_cloud_bottom",
            "donchian_upper", "donchian_mid", "donchian_lower",
            "resistance1", "resistance2", "support1", "support2", "pivot",
            "ema50_200_spread", "ema20_50_spread",
            "fib_382", "fib_500", "fib_618", "swing_high", "swing_low"
        )
        newlyAdded.forEach { key ->
            val raw = produced[key]
            assertTrue(raw != null, "ไม่มีค่า $key")
            assertTrue(raw.toDoubleOrNull()?.isFinite() == true, "$key = '$raw' ไม่ใช่ตัวเลขที่ใช้ได้")
        }
    }

    /** Donchian ต้องเรียง lower <= mid <= upper และ mid คือกึ่งกลางจริง */
    @Test
    fun donchianLevelsAreConsistent() {
        val p = computeAll()
        val upper = p.getValue("donchian_upper").toDouble()
        val mid = p.getValue("donchian_mid").toDouble()
        val lower = p.getValue("donchian_lower").toDouble()
        assertTrue(lower <= mid && mid <= upper, "donchian เรียงผิด: $lower / $mid / $upper")
        assertEquals((upper + lower) / 2.0, mid, 1e-6)
    }

    /** Ichimoku cloud top ต้อง >= cloud bottom เสมอ */
    @Test
    fun ichimokuCloudBoundsAreOrdered() {
        val p = computeAll()
        val top = p.getValue("ichimoku_cloud_top").toDouble()
        val bottom = p.getValue("ichimoku_cloud_bottom").toDouble()
        assertTrue(top >= bottom, "cloud top ($top) ต้อง >= bottom ($bottom)")
    }

    /** MFI และ Williams %R ต้องอยู่ในสเกลที่ถูกต้อง */
    @Test
    fun boundedOscillatorsStayInRange() {
        val p = computeAll()
        val mfi = p.getValue("mfi14").toDouble()
        assertTrue(mfi in 0.0..100.0, "MFI นอกช่วง 0-100: $mfi")
        val wr = p.getValue("williams_r").toDouble()
        assertTrue(wr in -100.0..0.0, "Williams %R นอกช่วง -100..0: $wr")
    }

    // ─── fail-closed: แท่งไม่พอต้องไม่ส่งค่าออกมาเลย ────────────────────────

    /**
     * หัวใจของหลักการ: ระบบส่งเฉพาะค่าที่คำนวณได้จริง
     * EMA200 บนชุด 150 แท่งต้อง **หายไปจากผลลัพธ์** ไม่ใช่กลายเป็นราคาปิด
     */
    @Test
    fun insufficientBarsOmitFieldsInsteadOfFabricatingThem() {
        val short = syntheticCandles(150)
        val produced = provider.computeIndicators("XAUUSD", "1h", "TV:OANDA", short)

        assertFalse("ema200" in produced, "EMA200 ไม่ควรมีค่าเมื่อมีแค่ 150 แท่ง")
        assertFalse("sma200" in produced, "SMA200 ไม่ควรมีค่าเมื่อมีแค่ 150 แท่ง")
        assertEquals("N/A", produced["ema_cross_state"], "cross state ต้องเป็น N/A ไม่ใช่เดา BULLISH/BEARISH")

        // ตัวที่แท่งพอต้องยังคำนวณได้ตามปกติ
        assertTrue("rsi14" in produced)
        assertTrue("ema50" in produced)
        assertTrue("bars_used" in produced)
        assertEquals("150", produced["bars_used"])

        // ค่าที่มี ต้องไม่บังเอิญเท่าราคาปิด (สัญญาณว่ายัง fallback อยู่)
        val close = produced.getValue("close").toDouble()
        assertTrue(produced["ema50"]!!.toDouble() != close)
    }

    // ─── evaluator ต้องไม่เงียบกับ field ที่ไม่รู้จัก ───────────────────────

    /**
     * evaluate() คืน false เงียบๆ เมื่อหา field ไม่เจอ — ยอมรับได้ในชั้น runtime
     * แต่ **ต้องดักตั้งแต่ตอนสร้าง alert** ผ่าน AlertFieldCatalog.isFieldSupported
     * ซึ่ง JarvisOrchestrator เรียกอยู่แล้ว ที่นี่จึงล็อกว่าทะเบียนสะท้อนชื่อจริง
     */
    @Test
    fun unknownFieldsAreRejectedAtAlertCreationTime() {
        val data = computeAll()
        assertTrue(
            AutomationEvaluator().evaluate(
                data,
                AutomationCondition("rsi14", ConditionOperator.GTE, "0")
            )
        )

        // ชื่อเก่าที่ provider ไม่เคยส่งออก → ต้องถูกปฏิเสธ
        assertFalse(AlertFieldCatalog.isFieldSupported("trading_indicators", "adx"))
        assertFalse(AlertFieldCatalog.isFieldSupported("trading_indicators", "donchian20_high"))
        assertFalse(AlertFieldCatalog.isFieldSupported("trading_indicators", "r1"))
        // ชื่อจริงที่ provider ส่งออก → ต้องผ่าน
        assertTrue(AlertFieldCatalog.isFieldSupported("trading_indicators", "adx14"))
        assertTrue(AlertFieldCatalog.isFieldSupported("trading_indicators", "donchian_upper"))
        assertTrue(AlertFieldCatalog.isFieldSupported("trading_indicators", "resistance1"))
        assertTrue(AlertFieldCatalog.isFieldSupported("trading_indicators", "obv"))
        assertTrue(AlertFieldCatalog.isFieldSupported("trading_indicators", "mfi14"))
        assertTrue(AlertFieldCatalog.isFieldSupported("trading_indicators", "ichimoku_kijun"))

        // ทุก field ที่ประกาศต้องผ่าน isFieldSupported ด้วย (กัน catalogue เพี้ยนจากตัวเอง)
        AlertFieldCatalog.INDICATORS.fields.forEach {
            assertTrue(
                AlertFieldCatalog.isFieldSupported("trading_indicators", it.field),
                "catalogue ประกาศ ${it.field} แต่ isFieldSupported ปฏิเสธ"
            )
        }
    }
}
