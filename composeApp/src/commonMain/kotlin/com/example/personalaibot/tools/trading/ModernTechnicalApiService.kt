package com.example.personalaibot.tools.trading

import kotlin.math.abs

// ============================================================================
// DATA MODELS FOR MODERN ANALYSIS
// ============================================================================

data class HarmonicPattern(
    val type: String,           // Gartley, Bat, Butterfly, Crab
    val direction: String,      // BULLISH, BEARISH
    val points: Map<String, Double>, // X, A, B, C, D prices
    val przTop: Double,
    val przBottom: Double,
    val score: Int = 0          // 0-5 stars based on precision & SMC confluence
)

data class ElliotWaveModern(
    val stage: String,          // IMPULSE, CORRECTIVE, ACCUMULATION
    val confidence: Double,     // 0.0 - 1.0
    val impulseScore: Int,      // 0-100 (Strength of relative move)
    val momentumBias: String,   // BULLISH, BEARISH, NEUTRAL
    val reasoning: String
)

data class FibonacciCluster(
    val price: Double,
    val strength: Int,          // Number of overlapping levels
    val description: String     // Levels included e.g. "0.618 (S1) + 1.272 (S2)"
)

data class ModernAnalysisResult(
    val symbol: String,
    val interval: String,
    val harmonicPatterns: List<HarmonicPattern>,
    val elliotWave: ElliotWaveModern,
    val fiboClusters: List<FibonacciCluster>
)

/**
 * ModernTechnicalApiService — เลิกใช้สูตรเก่า เน้นหาจุด Confluence และจังหวะกวาดสภาพคล่อง
 */
class ModernTechnicalApiService(private val smcApi: SmcApiService) {

    /**
     * วิเคราะห์ทางเทคนิคระดับสูงแบบรวมศูนย์
     */
    fun analyze(candles: List<Candle>, symbol: String, interval: String, smcAnalysis: SmcAnalysisResult? = null): ModernAnalysisResult {
        val harmonics = detectHarmonics(candles, smcAnalysis)
        val elliot = analyzeElliotModern(candles)
        val clusters = detectFiboClusters(candles)

        return ModernAnalysisResult(
            symbol = symbol,
            interval = interval,
            harmonicPatterns = harmonics,
            elliotWave = elliot,
            fiboClusters = clusters
        )
    }

    /**
     * 1. Modern Harmonic Detection (เน้น PRZ ที่ซ้อนทับกับ OB)
     */
    private fun detectHarmonics(candles: List<Candle>, smc: SmcAnalysisResult?): List<HarmonicPattern> {
        val (highs, lows) = smcApi.detectSwings(candles, 5)
        val patterns = mutableListOf<HarmonicPattern>()

        // เราต้องการ 5 points (X, A, B, C, D)
        // สำหรับ MVP นี้เราจะใช้ pivot 5 ตัวล่าสุดมาคำนวณสัดส่วน
        val pivots = (highs + lows).sortedBy { it.first }.takeLast(5)
        if (pivots.size < 5) return emptyList()

        val pX = pivots[0].second
        val pA = pivots[1].second
        val pB = pivots[2].second
        val pC = pivots[3].second
        val pD = pivots[4].second

        val xa = abs(pA - pX)
        val ab = abs(pB - pA)
        val bc = abs(pC - pB)
        val cd = abs(pD - pC)

        if (xa == 0.0 || ab == 0.0) return emptyList()
        val ab_xa = ab / xa
        val bc_ab = bc / ab
        val cd_xa = cd / xa

        // ตรวจสอบสัดส่วน ด้วย Fibonacci ratios ที่ถูกต้อง
        var type = ""
        var direction = if (pD < pC) "BULLISH" else "BEARISH"

        when {
            // Gartley: B @ 61.8% of XA, C @ 38.2-88.6% of AB, D @ 78.6% of XA
            ab_xa in 0.55..0.65 && bc_ab in 0.35..0.90 && cd_xa in 0.70..0.85 -> type = "Gartley"
            // Bat: B @ 38.2-50% of XA, C @ 38.2-88.6% of AB, D @ 88.6% of XA
            ab_xa in 0.35..0.52 && bc_ab in 0.35..0.90 && cd_xa in 0.85..0.90 -> type = "Bat"
            // Butterfly: B @ 78.6% of XA, C @ 38.2-88.6% of AB, D @ 127.2% of XA
            ab_xa in 0.75..0.85 && bc_ab in 0.35..0.90 && cd_xa in 1.20..1.70 -> type = "Butterfly"
            // Crab: B @ 38.2-61.8% of XA, C @ 38.2-88.6% of AB, D @ 161.8% of XA
            ab_xa in 0.35..0.65 && bc_ab in 0.35..0.90 && cd_xa in 1.55..1.70 -> type = "Crab"
        }

        if (type.isNotEmpty()) {
            val przTop = pD * 1.005
            val przBtm = pD * 0.995

            // ตรวจสอบ SMC Confluence
            var score = 3
            smc?.let {
                val nearOB = (it.bullishOBs + it.bearishOBs).any { ob ->
                    abs(ob.top - pD) < pD * 0.005
                }
                if (nearOB) score += 2
            }

            patterns.add(
                HarmonicPattern(
                    type = type,
                    direction = direction,
                    points = mapOf("X" to pX, "A" to pA, "B" to pB, "C" to pC, "D" to pD),
                    przTop = przTop,
                    przBottom = przBtm,
                    score = score
                )
            )
        }

        return patterns
    }

    /**
     * 2. Modern Elliot Wave Analysis (Swing-based Wave Counting + Momentum Stage)
     *
     * ปรับปรุงจาก V1: เพิ่ม swing-based wave counting, guard division-by-zero,
     * multi-segment directional analysis, และ graduated confidence
     */
    private fun analyzeElliotModern(candles: List<Candle>): ElliotWaveModern {
        if (candles.size < 20) return ElliotWaveModern(
            stage = "ACCUMULATION", confidence = 0.30, impulseScore = 0,
            momentumBias = "NEUTRAL", reasoning = "ข้อมูลไม่เพียงพอสำหรับวิเคราะห์ Elliott Wave (min 20 bars)"
        )

        val last50 = candles.takeLast(50.coerceAtMost(candles.size))
        val last10 = candles.takeLast(10.coerceAtMost(candles.size))
        val last20 = candles.takeLast(20.coerceAtMost(candles.size))

        // ── 1. Safe Impulse Score (guard totalMove == 0) ─────────────────────
        val totalMove = abs(last50.last().close - last50.first().close)
        val recentMove = abs(last10.last().close - last10.first().close)
        // Use ATR as fallback baseline when totalMove is near zero (ranging market)
        val atrLike = last50.map { abs(it.high - it.low) }.average().coerceAtLeast(0.00001)
        val impulseScore = if (totalMove > atrLike * 0.1) {
            ((recentMove / totalMove) * 100).toInt().coerceIn(0, 100)
        } else {
            // Price barely moved over 50 bars → impulse score from ATR-relative move
            ((recentMove / atrLike) * 20).toInt().coerceIn(0, 100)
        }

        // ── 2. Volume Analysis ───────────────────────────────────────────────
        val volAvg = last50.map { it.volume }.average().coerceAtLeast(1.0)
        val recentVol = last10.map { it.volume }.average()
        val volRatio = recentVol / volAvg

        // ── 3. Swing-based Wave Counting (simple directional segments) ───────
        // Count how many consecutive directional swings in last 20 bars
        val swingSegments = mutableListOf<String>() // "UP" or "DOWN"
        var segDir = if (last20.first().close <= last20.getOrElse(1){ last20.first() }.close) "UP" else "DOWN"
        for (i in 1 until last20.size) {
            val dir = if (last20[i].close >= last20[i - 1].close) "UP" else "DOWN"
            if (dir != segDir) {
                swingSegments.add(segDir)
                segDir = dir
            }
        }
        swingSegments.add(segDir) // add final segment
        val segmentCount = swingSegments.size

        // ── 4. Directional bias ──────────────────────────────────────────────
        val priceChange = last10.last().close - last10.first().close
        val momentumBias = when {
            priceChange > atrLike * 0.3  -> "BULLISH"
            priceChange < -atrLike * 0.3 -> "BEARISH"
            else -> "NEUTRAL"
        }

        // ── 5. Multi-factor stage classification ─────────────────────────────
        // Impulse: strong directional move, high volume, fewer segments (1-3)
        // Corrective: moderate move, oscillating (3-5 segments)
        // Accumulation: flat, low volume, many reversals or very tight range

        return when {
            // ─ IMPULSE ─ strong move + high volume + few segments
            impulseScore > 60 && volRatio > 1.1 && segmentCount <= 3 -> {
                val conf = (0.70 + (impulseScore - 60) * 0.005 + (volRatio - 1.0) * 0.1).coerceIn(0.60, 0.95)
                val waveHint = if (segmentCount <= 2) "Wave 3 หรือ 5 (Motive)" else "Wave 1 (Early Impulse)"
                ElliotWaveModern(
                    stage = "IMPULSE",
                    confidence = conf,
                    impulseScore = impulseScore,
                    momentumBias = momentumBias,
                    reasoning = "การเคลื่อนที่แรง (impulse=$impulseScore) พร้อม Volume ratio ${"%.2f".format(volRatio)}x " +
                        "และมี $segmentCount segments → บ่งบอกถึง $waveHint"
                )
            }
            // ─ ACCUMULATION ─ flat + low volume + many segments or tight range
            impulseScore < 25 && volRatio < 0.85 -> {
                val conf = (0.55 + (0.85 - volRatio) * 0.3).coerceIn(0.45, 0.80)
                ElliotWaveModern(
                    stage = "ACCUMULATION",
                    confidence = conf,
                    impulseScore = impulseScore,
                    momentumBias = "NEUTRAL",
                    reasoning = "ราคานิ่ง (impulse=$impulseScore) Volume ต่ำ (${"%.2f".format(volRatio)}x) " +
                        "$segmentCount segments → ช่วงสะสมพลังก่อน breakout"
                )
            }
            // ─ CORRECTIVE ─ everything else
            else -> {
                val conf = (0.45 + segmentCount * 0.04 + volRatio * 0.05).coerceIn(0.40, 0.75)
                val corrHint = when {
                    segmentCount >= 5 -> "Complex correction (ABC ขยาย)"
                    segmentCount >= 3 -> "Wave 2 หรือ 4 (Simple ABC)"
                    else -> "Transition zone (อาจเปลี่ยน Stage เร็วๆนี้)"
                }
                ElliotWaveModern(
                    stage = "CORRECTIVE",
                    confidence = conf,
                    impulseScore = impulseScore,
                    momentumBias = momentumBias,
                    reasoning = "ตลาดผันผวน (impulse=$impulseScore, vol=${"%.2f".format(volRatio)}x) " +
                        "$segmentCount segments → $corrHint"
                )
            }
        }
    }

    /**
     * 3. Fibonacci Golden Clusters (หาจุดซ้อนทับของหลายระยะ)
     */
    private fun detectFiboClusters(candles: List<Candle>): List<FibonacciCluster> {
        val (_, lows) = smcApi.detectSwings(candles, 20) // Swing ใหญ่
        val majorLows = lows.takeLast(3)
        if (majorLows.size < 2) return emptyList()
        
        val lastHigh = candles.maxOf { it.high }
        val allLevels = mutableListOf<Double>()
        
        // คำนวณ 0.618 และ 0.786 จากทุก Swing Low ใหญ่ไปยัง High ล่าสุด
        for ((_, low) in majorLows) {
            val diff = lastHigh - low
            allLevels.add(lastHigh - diff * 0.618)
            allLevels.add(lastHigh - diff * 0.786)
            allLevels.add(lastHigh - diff * 0.5)
        }
        
        // จัดกลุ่ม (Clustering) ภายใน 0.2% price tolerance
        val clusters = mutableListOf<FibonacciCluster>()
        val sorted = allLevels.sorted()
        
        var currentCluster = mutableListOf<Double>()
        for (lvl in sorted) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(lvl)
            } else {
                val last = currentCluster.last()
                if (abs(lvl - last) < last * 0.002) {
                    currentCluster.add(lvl)
                } else {
                    if (currentCluster.size >= 2) {
                        clusters.add(FibonacciCluster(currentCluster.average(), currentCluster.size, "Golden Cluster (${currentCluster.size} levels)"))
                    }
                    currentCluster = mutableListOf(lvl)
                }
            }
        }
        // Flush last cluster (fix: ก่อนหน้านี้ cluster สุดท้ายหายไป)
        if (currentCluster.size >= 2) {
            clusters.add(FibonacciCluster(currentCluster.average(), currentCluster.size, "Golden Cluster (${currentCluster.size} levels)"))
        }

        return clusters.sortedByDescending { it.strength }.take(5)
    }
}
