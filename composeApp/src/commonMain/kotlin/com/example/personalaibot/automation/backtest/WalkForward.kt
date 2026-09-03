package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.tools.trading.Candle

/**
 * WalkForward 闂?train 闁哥姵姊归惄鈧紒妤佺箓閺嬫牠顢樺▎鎴犳噸闁哥姵姊瑰鍓佺箔?闁哥姵姊归妵妯肩箔閺嵮勭亙妞ゅ氦鍎婚崢顒勫窗閺冣偓閻┾偓缂佹鍠庨弸鏍р枎婵犲懎骞€闁哥姵姊归悷鐘垫啺閸℃鐏冮柛鏇閻?闁哥姵鏋崜褏鎳欓柛鐘虫煥椤戔偓缂佺姷鍠庨弸?(port 闁哥姵鏌ㄩ悧鈧悷鏇炴閺?OLD_Code backtest/walk_forward.py)
 * 闁哥姵姊瑰ú澶岀矉韫囨挻鐏冮懞顖滅箔閳?overfitting 闁哥姵鐟ラ崐宕囩箔閺嵮勭亙闁冲爢鍛殸闁哥姵姊归妵姗€宕掗崨顓熺亙闁冲爢鍛嚌 in-sample vs out-of-sample Sharpe
 * 闁哥姵妫忛崐钘壝归鍏肩亙濞撴皜鍕丢闁哥姵鐟㈤崑鎾诲窗閺冩垵鈧晫绮Δ鈧弸? markers 闁哥姵鏌ㄧ€氥倗鎸х€ｎ亝鐏冩繛鍡楋攻婵酣宕伴弮鈧幐顓犵不閺嵮勭亙妞ゆ劖绻冪拠鐐哄窗濞嗗繒澧嶇紒妤冨枎閺?slice 闁哥姵鐟㈤崑鎾诲窗閺冩垟鍋撻幐搴ょ檨闁?(indicator 闁哥姵鐟ラ崯娑氱箔閸屾碍鐏愬璺哄暟椤忔娊宕板▎蹇ｆО闁煎灚鍨甸弸鏍焾绾惧鎮呴柛鐘虫煟閸庮剟鎳欓幋婵囩亙闁哄倸鍢查崐鏇㈠窗閺冣偓濞插顕ｅΔ鈧弸鈩冨緞閸愨晛顫撻柛?闁哥姵鐟ョ€氥倕霉椤斿吋鐏愰柛顐ｎ殕鐠囩偤宕?lookahead)
 */
object WalkForward {

    data class WindowResult(
        val split: Int,
        val trainBars: Int,
        val testBars: Int,
        val bestParams: TpSlParams,
        val inSampleSharpe: Double,
        val oosSharpe: Double,
        val oosWinRate: Double,
        val oosProfitFactor: Double,
        val oosTrades: Int
    )

    data class WalkForwardResult(
        val nSplits: Int,
        val windows: List<WindowResult>,
        val inSampleAvgSharpe: Double,
        val oosAvgSharpe: Double,
        val overfittingRatio: Double,   // OOS / IS (1.0 = 闁哥姵姊归弻妤呭磼閸涱厽鐏冮柍顓狀棎椤╊偊宕? <0.5 = 闁哥姵姊归悷鐘电不閻愬弶鐏冨〒姘€鍥ф疂闁哥姵鏌ㄥú鎰版煂濠婂啯鐏冪紓鍌涚墬娑?
        val likelyOverfit: Boolean,
        val paramStabilityCv: Double    // coefficient of variation 闁哥姵鐟㈤崑鎾诲窗閺傛妲扮€殿喗顨呴弸鏍箳閸屾粎鏉介柛鐘虫煛閹冲懐绮╃捄鐑樼亙妞ゆ挻鐟ч?best params (闁哥姵姊瑰ú澶岀不閻愬弶鐏?= 闁哥姵鐟㈤崑鎾诲窗閺冩垟鍋撶捄銊ф噽闁哥姵姊瑰璺衡槈椤忓嫭鐏?
    )

    fun run(
        kind: String,
        candles: List<Candle>,
        markerProvider: SignalMarkerProvider,
        engine: BacktestEngine,
        kindOf: (String) -> String,
        strategyName: (String) -> String,
        nSplits: Int = 5,
        trainPct: Double = 0.7,
        config: BacktestConfig = BacktestConfig(),
        entryParams: Map<String, EntryParams> = emptyMap()
    ): WalkForwardResult {
        val total = candles.size
        if (total < 500) {
            return WalkForwardResult(0, emptyList(), 0.0, 0.0, 0.0, false, 0.0)
        }
        val safeSplits = nSplits.coerceIn(2, 10)
        val splitSize = total / safeSplits
        val windows = mutableListOf<WindowResult>()
        val isSharpes = mutableListOf<Double>()
        val oosSharpes = mutableListOf<Double>()
        val bestParamsList = mutableListOf<TpSlParams>()

        for (s in 0 until safeSplits) {
            // anchored: train 闁哥姵鐟㈤崑鎾诲窗閺冩挾韫堝璺虹Т閺嬶繝宕煎Δ浣界檨闁哥姵姊归妵姗€宕掗崨顓熺亹?0 闁哥姵鐟㈤崑鎾诲窗閺冩垟鍋撻幐搴ょ檨闁?(expanding window)
            val splitEnd = if (s < safeSplits - 1) (s + 1) * splitSize else total
            val trainEnd = (splitEnd * trainPct.coerceIn(0.5, 0.85)).toInt()
            val testStart = trainEnd
            if (splitEnd - testStart < 100 || trainEnd < 300) continue

            val trainCandles = candles.subList(0, trainEnd)
            val prefixCandles = candles.subList(0, splitEnd)

            val trainMarkers = markerProvider.compute(trainCandles, Int.MAX_VALUE, entryParams)
            val testStartTs = candles[testStart].timestamp
            val testMarkers = markerProvider.compute(prefixCandles, Int.MAX_VALUE, entryParams)
                .filter { it.time >= testStartTs }

            val ranked = ParamOptimizer.gridSearch(
                kind, trainCandles, trainMarkers, engine, kindOf, strategyName, config
            )
            val best = ranked.firstOrNull { it.score > -999.0 } ?: continue
            bestParamsList += best.params
            isSharpes += best.sharpe

            val oos = runCatching {
                engine.run(
                    symbol = "", interval = "", source = "",
                    candles = prefixCandles, markers = testMarkers, kindFilter = setOf(kind),
                    kindOf = kindOf, strategyName = strategyName,
                    tpSl = { k, sd, c, i, a14, a6 -> parameterizedTpSl(k, sd, c, i, a14, a6, best.params) },
                    config = config,
                    startIndex = testStart   // ATR/indicator warm 闁哥姵鏌ㄩ悧鈧悷鏇炴閺?prefix 闁哥姵鐟ら悿鍡欑箔濡も偓閺嬶繝宕煎Δ浣割潛闁哥姵姊荤槐顏嗙箔閺嵮勭亙婵犙呮嚀缁鳖剟宕板▎灞戒壕闁哥姵鏌ㄩˇ銊х箔瑜嶉弸鏍ㄧ瑹瑜戦懡锟犲窗閺傚彲鎴犵不閻愬弶鐏冮懞顖滅博?OOS
                )
            }.getOrNull() ?: continue
            oosSharpes += oos.sharpe

            windows += WindowResult(
                split = s + 1,
                trainBars = trainCandles.size,
                testBars = splitEnd - testStart,
                bestParams = best.params,
                inSampleSharpe = best.sharpe,
                oosSharpe = oos.sharpe,
                oosWinRate = oos.winRate,
                oosProfitFactor = oos.profitFactor,
                oosTrades = oos.totalTrades
            )
        }

        val isAvg = if (isSharpes.isNotEmpty()) isSharpes.average() else 0.0
        val oosAvg = if (oosSharpes.isNotEmpty()) oosSharpes.average() else 0.0
        val ratio = if (isAvg > 0) oosAvg / isAvg else 0.0

        // param stability: CV 闁哥姵鐟㈤崑鎾诲窗閺傛妲扮€殿喗顨呴弸鏍箳閸屾粎鏉介柛鐘虫煛閹冲懐绮╃捄鐑樼亙妞ゆ挻鐟ч?sl/tp mults 闁哥姵鏌ㄩ崐宕囩不閻旈攱鐏冨〒姘€鍕檨 windows (闁哥姵姊瑰ú澶岀不閻愬弶鐏?= params 闁哥姵姊归悷鐘冲緞瀹ュ懏鐏愰柛顐ｎ殘椤?= overfit 闁哥姵姊归悷鐘电不閻旈攱鐏冩い鎾寸懄娑?
        fun cv(values: List<Double>): Double {
            if (values.size < 2) return 0.0
            val mean = values.average()
            if (mean == 0.0) return 0.0
            val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
            return kotlin.math.sqrt(variance) / kotlin.math.abs(mean)
        }
        val stabCv = listOf(
            cv(bestParamsList.map { it.slMult }),
            cv(bestParamsList.map { it.tpMult })
        ).average()

        return WalkForwardResult(
            nSplits = windows.size,
            windows = windows,
            inSampleAvgSharpe = isAvg,
            oosAvgSharpe = oosAvg,
            overfittingRatio = ratio,
            likelyOverfit = ratio < 0.5 && isAvg > 0,
            paramStabilityCv = stabCv
        )
    }
}




