package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.tools.trading.Candle
import kotlin.random.Random

/**
 * PermutationTest 鈥?喔炧复喔腹喔堗笝喙屶抚喙堗覆喔佮弗喔⑧父喔椸笜喙屶笂喔權赴 "喔勦抚喔侧浮喔氞副喔囙箑喔复喔? 喔堗福喔脆竾喔福喔粪腑喙勦浮喙? * (port 喔堗覆喔?OLD_Code backtest/statistical_tests.py)
 * 喔父喙堗浮喔曕赋喙佮斧喔權箞喔囙釜喔编笉喔嵿覆喔?(喔堗赋喔權抚喔?喔澿副喙堗竾喙€喔椸箞喔侧箑喔斷复喔?喙佮笗喙堗箒喔椸箞喔囙釜喔膏箞喔? 喙佮弗喙夃抚喔｀副喔?backtest 喔嬥箟喔?N 喔勦福喔编箟喔? * p_value = 喔副喔斷釜喙堗抚喔權福喔笟喔父喙堗浮喔椸傅喙?Sharpe >= Sharpe 喔堗福喔脆竾 鈥?喔栢箟喔?p < 0.05 = 喔∴傅 edge 喔堗福喔脆竾
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
        require(nPermutations > 0) { "nPermutations must be > 0" }
        if (kindMarkers.size < 5 || n < 300) {
            return PermutationResult(0.0, 0.0, 0.0, 1.0, false, 0)
        }

        fun runWith(marks: List<SignalMarkerProvider.SignalMarker>): Double? {
            val r = runCatching {
                engine.run(
                    symbol = "", interval = "", source = "",
                    candles = candles, markers = marks, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = strategyName,
                    tpSl = { k, s, c, i, a14, a6 -> parameterizedTpSl(k, s, c, i, a14, a6, params) },
                    config = config
                )
            }.getOrNull() ?: return null
            return r.sharpe
        }

        val realSharpe = runWith(markers) ?: return PermutationResult(0.0, 0.0, 0.0, 1.0, false, 0)
        val rng = Random(seed)
        val sides = kindMarkers.map { it.side }
        val count = kindMarkers.size
        // 喔娻箞喔о竾喙佮笚喙堗竾喔椸傅喙堗釜喔膏箞喔∴抚喔侧竾喔副喔嵿笉喔侧笓喙勦笖喙?(喔曕箟喔竾喔∴傅 indicator 喔炧福喙夃腑喔?喙佮弗喔班浮喔掂腑喔權覆喔勦笗喙冟斧喙夃笀喔赤弗喔竾)
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
                    label = kind,
                    color = "#000000"
                )
            }
            // run 喔椸傅喙堗笧喔编竾 (null) 喔曕箟喔竾喙勦浮喙堗笝喔编笟喙€喔傕箟喔?null distribution 鈥?喙€喔斷复喔∴箒喔椸笝喔斷箟喔о涪 0.0 喔椸赋 distribution 喙€喔氞傅喙夃涪喔о弗喔?            // 鈫?realSharpe 喔娻笝喔班竾喙堗覆喔⑧箑喔佮复喔權笀喔｀复喔?(p-value 喔曕箞喔赤箑喔佮复喔權竸喔о福)
            val s = runWith(fakeMarkers) ?: return@repeat
            shuffledSharpes += s
            if (s >= realSharpe) beatCount++
        }
        if (shuffledSharpes.isEmpty()) return PermutationResult(realSharpe, 0.0, 0.0, 1.0, false, 0)

        // p-value 喙佮笟喔?+1 correction (喔∴覆喔曕福喔愢覆喔?permutation test) 鈥?喔佮副喔?p=0.000 喔椸傅喙堗箑喔涏箛喔權箘喔涏箘喔∴箞喙勦笖喙夃笚喔侧竾喔笘喔脆笗喔?        val trials = shuffledSharpes.size
        val trials = shuffledSharpes.size
        val p = (beatCount + 1).toDouble() / (trials + 1)
        val mean = shuffledSharpes.average()
        val variance = shuffledSharpes.sumOf { (it - mean) * (it - mean) } / shuffledSharpes.size
        return PermutationResult(
            realSharpe = realSharpe,
            shuffledMean = mean,
            shuffledStd = kotlin.math.sqrt(variance),
            pValue = p,
            isSignificant = p < 0.05,
            nPermutations = trials
        )
    }
    }
