package com.example.personalaibot.automation.backtest

import com.example.personalaibot.automation.SignalMarkerProvider
import com.example.personalaibot.tools.trading.Candle

data class StrategyForensicsRow(val kind:String,val name:String,val regime:String,val signals:Int,val trades:Int,val winRate:Double,val profitFactor:Double,val expectancyR:Double,val maxDrawdownPct:Double,val score:Double,val verdict:String)
data class StrategyForensicsReport(val symbol:String,val interval:String,val bars:Int,val rows:List<StrategyForensicsRow>)

class StrategyForensics(private val markerProvider: SignalMarkerProvider) {
 private val kinds=listOf("MOM","TR","REV","DC","52H","E","UT","3BR")
 fun run(symbol:String,interval:String,candles:List<Candle>,source:String="forensics"):StrategyForensicsReport {
  require(candles.size>=62){"喙佮笚喙堗竾喙€喔椸傅喔⑧笝喙勦浮喙堗笧喔釜喔赤斧喔｀副喔?strategy forensics: ${candleSize(candles)}"}; val markers=markerProvider.compute(candles, kindsOverride = kinds.toSet()); val engine=BacktestEngine()
  val kindOf:(String)->String={l->when {l.startsWith("MOM")->"MOM";l.startsWith("TR")->"TR";l.startsWith("REV")->"REV";l.startsWith("DC")->"DC";l.startsWith("52H")->"52H";l.startsWith("E")->"E";l.startsWith("UT")->"UT";l.startsWith("3BR")->"3BR";else->""}}
  val names=mapOf("MOM" to "Momentum","TR" to "Trend","REV" to "Reversal","DC" to "Donchian Breakout","52H" to "52H Proximity","E" to "EMA 14/60","UT" to "UT Bot","3BR" to "3-Bar Reversal"); val regime=classifyRegime(candles)
  val rows=kinds.map{kind-> val r=engine.run(symbol,interval,source,candles,markers,setOf(kind),kindOf,{names[it]?:it},{_,side,_,i,a14,a6->parameterizedTpSl(kind,side,candles,i,a14,a6,TpSlParams(slMult = 1.0, tpMult = 1.8))}); val pf=r.profitFactor; val score=r.expectancyR*40+(pf-1).coerceIn(-1.0,2.0)*20+r.winRate*10-r.maxDrawdownPct*.15; val v=when{r.totalTrades<10->"INSUFFICIENT";score>=20&&pf>=1.2->"PRIMARY";score>=8&&pf>=1->"SECONDARY";else->"WEAK"}; StrategyForensicsRow(kind,names[kind]?:kind,regime,r.perStrategy.firstOrNull()?.signals?:0,r.totalTrades,r.winRate,pf,r.expectancyR,r.maxDrawdownPct,score,v)}.sortedByDescending{it.score}; return StrategyForensicsReport(symbol,interval,candles.size,rows)
 }
 private fun candleSize(c:List<Candle>)=c.size
 private fun classifyRegime(c:List<Candle>):String {val x=c.takeLast(minOf(50,c.size-1)); val range=x.maxOf{it.high}-x.minOf{it.low}; val drift=kotlin.math.abs(x.last().close-x.first().close); return when{range<=0->"UNKNOWN";drift/range>=.60->"TRENDING";drift/range<=.25->"RANGE";else->"MIXED"}}
}

