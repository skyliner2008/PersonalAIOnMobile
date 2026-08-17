package com.example.personalaibot.automation.smc

/**
 * SMC Engine (Kotlin port จาก mt5-core-server/src/services/auto/analyzers/smc)
 * ต้นฉบับ port มาจาก Pine Script "SMC & Multi-TF Order Blocks Sweeps V8.3"
 *
 * ใช้ Candle ของแอป (com.example.personalaibot.tools.trading.Candle) ตรงๆ
 * — คำนวณในเครื่องล้วน ไม่พึ่ง TV indicator plots
 */

enum class StructureEvent { BOS, CHoCH, SMS, BMS, NONE }
enum class StructureDirection { BULLISH, BEARISH, INIT }
enum class PremiumDiscountZone { PREMIUM, EQ, DISCOUNT }

data class SwingPoint(val index: Int, val price: Double)

data class Fvg(
    val isBull: Boolean,
    val top: Double,
    val bottom: Double,
    val barIndex: Int,
    var mitigated: Boolean = false,
    val age: Int = 0
)

data class SmcOrderBlock(
    val isBull: Boolean,
    val top: Double,
    val bottom: Double,
    val barIndex: Int,
    var mitigated: Boolean = false,
    val hasFvg: Boolean = false,
    var invalidated: Boolean = false,
    val volume: Double = 0.0
)

data class LiquidityZone(
    val price: Double,
    val barIndex: Int,
    val isHigh: Boolean,          // true = เหนือราคา (sell-side), false = ใต้ราคา (buy-side)
    var swept: Boolean = false,
    val strength: Int = 0,        // จำนวน equal highs/lows ซ้อนกัน
    val source: String = "SWING", // EQUAL_HL | SWING
    var confluenceStars: Int = 0  // 0-5★
)

data class MarketStructure(
    val direction: StructureDirection,
    val structureHigh: Double,
    val structureLow: Double,
    val structureHighBarIndex: Int,
    val structureLowBarIndex: Int,
    val lastEvent: StructureEvent,
    val lastEventSide: String?,   // "UP" | "DOWN" | null
    val continuationCount: Int,   // BOS streak
    val idmHigh: Double?,
    val idmLow: Double?,
    val idmHighSwept: Boolean,
    val idmLowSwept: Boolean
)

data class AttackForce(
    val barIndex: Int,
    val isBull: Boolean,
    val bodySize: Double,
    val atrRatio: Double,
    val volumeRatio: Double
)

data class PremiumDiscount(
    val zone: PremiumDiscountZone,
    val pctFromRange: Double,
    val structureHigh: Double,
    val structureLow: Double,
    val equilibrium: Double
)

data class SmcSnapshot(
    val activeFvgs: List<Fvg>,
    /** เรียงใหม่สุดก่อน (index 0 = ล่าสุด) — ตรงกับ TS ที่ใช้ unshift */
    val bullObs: List<SmcOrderBlock>,
    val bearObs: List<SmcOrderBlock>,
    /** เรียงตาม confluenceStars มากสุดก่อน */
    val liquidityZones: List<LiquidityZone>,
    val structure: MarketStructure,
    val attackForces: List<AttackForce>,
    val premiumDiscount: PremiumDiscount,
    val swingHighs: List<Double>,
    val swingLows: List<Double>,
    val symbol: String,
    val timeframe: String,
    val lastPrice: Double
)

internal fun emptyStructure(): MarketStructure = MarketStructure(
    direction = StructureDirection.INIT,
    structureHigh = 0.0, structureLow = 0.0,
    structureHighBarIndex = 0, structureLowBarIndex = 0,
    lastEvent = StructureEvent.NONE, lastEventSide = null,
    continuationCount = 0,
    idmHigh = null, idmLow = null,
    idmHighSwept = false, idmLowSwept = false
)
