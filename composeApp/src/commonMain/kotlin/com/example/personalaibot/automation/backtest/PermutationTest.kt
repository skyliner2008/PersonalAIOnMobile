package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.tools.trading.Candle
import kotlin.random.Random

/**
 * PermutationTest — พิสูจน์ว่ากลยุทธ์ชนะ "ความบังเอิญ" จริงหรือไม่
 * (port จาก OLD_Code backtest/statistical_tests.py)
 * สุ่มตำแหน่งสัญญาณ (จำนวน/ฝั่งเท่าเดิม แต่แท่งสุ่ม) แล้วรัน backtest ซ้ำ N ครั้ง
 * p_value = สัดส่วนรอบสุ่มที่ Sharpe >= Sharpe จริง — ถ้า p < 0.05 = มี edge จริง
 */
object PermutationTest {

    data class PermutationResult(
        val realSharpe: Double,
        val shuffledMean: Double,
        val shuffledStd: Double,
        val pValue: Double,
        val isSignificant: Boolean,
        val nPermutations: Int
    )

    fun run(
        kind: String,
        candles: List<Candle>,
        markers: List<SignalMarkerProvider.SignalMarker>,
        engine: BacktestEngine,
        kindOf: (String) -> String,
        strategyName: (String) -> String,
        params: TpSlParams,
        config: BacktestConfig = BacktestConfig(),
        nPermutations: Int = 200,
        seed: Long = 42
    ): PermutationResult {
        val n = candles.size
        val kindMarkers = markers.filter { kindOf(it.label) == kind && it.time != candles[n - 1].timestamp }
        if (kindMarkers.size < 5 || n < 300) {
            return PermutationResult(0.0, 0.0, 0.0, 1.0, false, 0)
        }

        fun runWith(marks: List<SignalMarkerProvider.SignalMarker>): Double {
            val r = runCatching {
                engine.run(
                    symbol = "", interval = "", source = "",
                    candles = candles, markers = marks, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = strategyName,
                    tpSl = { k, s, c, i, a14, a6 -> parameterizedTpSl(k, s, c, i, a14, a6, params) },
                    config = config
                )
            }.getOrNull() ?: return 0.0
            return r.sharpe
        }

        val realSharpe = runWith(markers)
        val rng = Random(seed)
        val sides = kindMarkers.map { it.side }
        val count = kindMarkers.size
        // ช่วงแท่งที่สุ่มวางสัญญาณได้ (ต้องมี indicator พร้อม และมีอนาคตให้จำลอง)
        val validBars = (62 until n - 1).toList()

        var beatCount = 0
        val shuffledSharpes = ArrayList<Double>(nPermutations)
        repeat(nPermutations) {
            val chosen = mutableSetOf<Int>()
            while (chosen.size < count && chosen.size < validBars.size) {
                chosen += validBars[rng.nextInt(validBars.size)]
            }
            val fakeMarkers = chosen.toList().mapIndexed { idx, bar ->
                SignalMarkerProvider.SignalMarker(
                    time = candles[bar].timestamp,
                    side = sides[idx % sides.size],
                    label = "${kind}▲", // kind ต้องอ่านกลับได้จาก kindOf
                    color = "#000000"
                )
            }
            val s = runWith(fakeMarkers)
            shuffledSharpes += s
            if (s >= realSharpe) beatCount++
        }

        val p = beatCount.toDouble() / nPermutations
        val mean = shuffledSharpes.average()
        val variance = shuffledSharpes.sumOf { (it - mean) * (it - mean) } / shuffledSharpes.size
        return PermutationResult(
            realSharpe = realSharpe,
            shuffledMean = mean,
            shuffledStd = kotlin.math.sqrt(variance),
            pValue = p,
            isSignificant = p < 0.05,
            nPermutations = nPermutations
        )
    }
}
