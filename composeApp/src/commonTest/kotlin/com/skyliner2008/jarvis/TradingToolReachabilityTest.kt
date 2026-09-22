package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.ai.TradingToolPolicy
import com.skyliner2008.jarvis.tools.ToolRegistry
import com.skyliner2008.jarvis.tools.trading.TradingToolDefinitions
import com.skyliner2008.jarvis.tools.trading.TradingToolRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P4: tool ที่ประกาศให้ AI ต้อง "เรียกถึง" และ "ทำงานได้" จริง
 *
 * บัคเดิมที่เทสต์นี้ล็อกไว้:
 *  - trading_elliot_modern_analysis ประกาศ+route ครบ แต่ handler ใช้ชื่อ branch อื่น
 *    → คืน "Unknown market/technical tool" ทุกครั้ง
 *  - trading_crypto_overview / trading_economic_data / automation_manage_schedule
 *    ไม่อยู่ใน allowlist ทั้ง tvOnly และ mt5Only → policy กรองทิ้งทุกกรณี
 *  - automation_manage_alerts / automation_manage_schedule ประกาศซ้ำ 2 ครั้ง
 *    → associateBy เก็บตัวหลัง ตัวที่ field ครบกว่าถูกทิ้งเงียบๆ
 */
class TradingToolReachabilityTest {

    private val declaredNames = TradingToolDefinitions.allDefinitions.map { it.name }

    @Test
    fun noDuplicateFunctionDeclarations() {
        val dupes = declaredNames.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(
            dupes.isEmpty(),
            "ประกาศ tool ซ้ำ — associateBy จะเก็บแค่ตัวสุดท้าย ตัวอื่นถูกทิ้งเงียบๆ: $dupes"
        )
    }

    /**
     * ทุก trading tool ที่ register แล้วต้องมี domain ปลายทาง
     * (ยกเว้น tool ที่ ToolExecutor ดักเองก่อนถึง router)
     */
    @Test
    fun everyRegisteredTradingToolHasARoute() {
        val interceptedByExecutor = setOf(
            "automation_manage_alerts",
            "automation_manage_schedule",
            "trading_signal_anticipation",
            // ชื่อที่ใช้เป็นค่า tool_name ของ alert เท่านั้น ไม่ได้เป็น callable tool
            "trading_signal_alert"
        )
        val unroutable = ToolRegistry.tvOnlyTradingFunctionNames
            .filter { it !in interceptedByExecutor }
            .filter { it in declaredNames }
            .filter { TradingToolRouter.domainOf(it) == TradingToolRouter.Domain.UNKNOWN }

        assertTrue(unroutable.isEmpty(), "tool ที่ไม่มี domain ปลายทาง: $unroutable")
    }

    /** Elliott Wave ต้อง route เข้า TECHNICAL (บัคเดิม: handler ใช้ชื่อ branch คนละตัว) */
    @Test
    fun elliottWaveToolIsRoutedToTechnicalDomain() {
        assertTrue("trading_elliot_modern_analysis" in declaredNames)
        assertEquals(
            TradingToolRouter.Domain.TECHNICAL,
            TradingToolRouter.domainOf("trading_elliot_modern_analysis")
        )
    }

    /**
     * tool ที่ register ต้องผ่าน policy ได้อย่างน้อยหนึ่งบริบท
     * ไม่งั้นก็คือประกาศไว้แต่ AI เรียกไม่ได้เลย
     */
    @Test
    fun everyRegisteredTradingToolIsReachableInSomeContext() {
        val contexts = listOf(
            TradingToolPolicy.evaluate("ดูราคาทองหน่อย"),                  // trading ทั่วไป
            TradingToolPolicy.evaluate("ภาพรวมคริปโตวันนี้"),               // trading (คำว่า คริปโต)
            TradingToolPolicy.evaluate("วิเคราะห์เชิงลึก XAUUSD"),          // deep analysis
            TradingToolPolicy.evaluate("ดูพอร์ต mt5"),                     // strict MT5
            TradingToolPolicy.evaluate("ตั้งแจ้งเตือนสัญญาณทอง 15m")        // signal alert
        )

        val unreachable = declaredNames
            .filter { ToolRegistry.isTradingTool(it) }
            .filter { name -> contexts.none { it.isToolAllowed(name) } }

        assertTrue(
            unreachable.isEmpty(),
            "tool ที่ register แล้วแต่ policy กรองทิ้งทุกบริบท (AI เรียกไม่ได้เลย): $unreachable"
        )
    }

    /** 3 ตัวที่เคยหลุด allowlist — ล็อกไว้เฉพาะเจาะจง */
    @Test
    fun previouslyUnreachableToolsAreNowAllowed() {
        val tradingContext = TradingToolPolicy.evaluate("ภาพรวมคริปโตวันนี้")
        assertTrue(tradingContext.isTradingContext, "ประโยคนี้ต้องถูกจัดเป็น trading prompt")
        listOf("trading_crypto_overview", "trading_economic_data", "automation_manage_schedule")
            .forEach { assertTrue(tradingContext.isToolAllowed(it), "$it ยังถูกกรองทิ้ง") }
    }
}
