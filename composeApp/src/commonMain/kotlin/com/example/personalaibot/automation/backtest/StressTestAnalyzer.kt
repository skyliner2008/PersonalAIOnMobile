package com.example.personalaibot.automation.backtest

object StressTestAnalyzer {
 enum class Verdict { ROBUST, DEGRADED, FRAGILE, INSUFFICIENT }
 data class Scenario(val name:String,val costMultiplier:Double)
 data class ScenarioResult(val scenario:Scenario,val totalReturnPct:Double,val profitFactor:Double,val sharpe:Double)
 data class Report(val scenarios:List<ScenarioResult>,val worstReturnPct:Double,val worstProfitFactor:Double,val worstSharpe:Double,val verdict:Verdict)
 val defaultScenarios=listOf(Scenario("BASELINE",1.0),Scenario("COST_1_5X",1.5),Scenario("COST_2X",2.0),Scenario("COST_3X",3.0))
 fun analyze(baseline:BacktestResult,scenarios:List<Scenario> = defaultScenarios):Report {
  if(baseline.trades.size<10)return Report(emptyList(),baseline.totalReturnPct,baseline.profitFactor,baseline.sharpe,Verdict.INSUFFICIENT)
  val costPerTrade=baseline.trades.map{t->kotlin.math.abs(t.pnlMoney-t.pnlR)}.average()
  val results=scenarios.map{sc->
   val pnls=baseline.trades.map{t->t.pnlMoney-kotlin.math.max(0.0,costPerTrade*(sc.costMultiplier-1.0))}
   val grossWin=pnls.filter{it>0}.sum(); val grossLoss=-pnls.filter{it<0}.sum()
   val pf=if(grossLoss>0)grossWin/grossLoss else if(grossWin>0)Double.POSITIVE_INFINITY else 0.0
   val ret=pnls.sum()/baseline.config.initialBalance*100.0
   val mean=pnls.average(); val variance=pnls.sumOf{(it-mean)*(it-mean)}/pnls.size; val sh=if(variance>0)mean/kotlin.math.sqrt(variance)*kotlin.math.sqrt(pnls.size.toDouble()) else 0.0
   ScenarioResult(sc,ret,pf,sh)
  }
  val worstReturn=results.minOf{it.totalReturnPct}; val worstPf=results.minOf{it.profitFactor}; val worstSh=results.minOf{it.sharpe}
  val verdict=when{worstPf>=1.0&&worstReturn>0->Verdict.ROBUST;worstPf>=0.9&&worstReturn>-5.0->Verdict.DEGRADED;else->Verdict.FRAGILE}
  return Report(results,worstReturn,worstPf,worstSh,verdict)
 }
}
