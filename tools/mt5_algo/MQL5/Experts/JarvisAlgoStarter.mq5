//+------------------------------------------------------------------+
//|                                               JarvisAlgoStarter  |
//| Purpose: MT5 EA starter with basic risk and execution controls   |
//+------------------------------------------------------------------+
#property strict

#include <Trade/Trade.mqh>

input int      FastEmaPeriod         = 20;
input int      SlowEmaPeriod         = 50;
input int      AtrPeriod             = 14;
input double   AtrStopMultiplier     = 1.5;
input double   RiskPercentPerTrade   = 1.0;
input double   RewardToRisk          = 2.0;
input double   MaxSpreadPoints       = 35.0;
input ulong    MagicNumber           = 26041801;
input ENUM_TIMEFRAMES SignalTF       = PERIOD_M15;

CTrade trade;

int hFastEma = INVALID_HANDLE;
int hSlowEma = INVALID_HANDLE;
int hAtr     = INVALID_HANDLE;

datetime lastBarTime = 0;

bool GetLatestValue(const int handle, double &outValue)
{
   double buffer[];
   ArraySetAsSeries(buffer, true);
   if(CopyBuffer(handle, 0, 0, 2, buffer) < 2)
      return false;
   outValue = buffer[0];
   return true;
}

bool IsNewBar()
{
   datetime t = iTime(_Symbol, SignalTF, 0);
   if(t == 0)
      return false;
   if(t != lastBarTime)
   {
      lastBarTime = t;
      return true;
   }
   return false;
}

double NormalizeVolume(const double rawLots)
{
   const double minLot  = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MIN);
   const double maxLot  = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MAX);
   const double lotStep = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_STEP);

   if(lotStep <= 0.0)
      return 0.0;

   double lots = MathFloor(rawLots / lotStep) * lotStep;
   lots = MathMax(minLot, MathMin(maxLot, lots));
   return NormalizeDouble(lots, 2);
}

double CalculateLotsFromRisk(const double stopDistancePrice)
{
   if(stopDistancePrice <= 0.0)
      return 0.0;

   const double balance   = AccountInfoDouble(ACCOUNT_BALANCE);
   const double riskMoney = balance * (RiskPercentPerTrade / 100.0);
   if(riskMoney <= 0.0)
      return 0.0;

   const double tickSize  = SymbolInfoDouble(_Symbol, SYMBOL_TRADE_TICK_SIZE);
   const double tickValue = SymbolInfoDouble(_Symbol, SYMBOL_TRADE_TICK_VALUE);
   if(tickSize <= 0.0 || tickValue <= 0.0)
      return 0.0;

   const double ticksToSL   = stopDistancePrice / tickSize;
   const double lossPerLot  = ticksToSL * tickValue;
   if(lossPerLot <= 0.0)
      return 0.0;

   const double rawLots = riskMoney / lossPerLot;
   return NormalizeVolume(rawLots);
}

bool HasOpenPositionForSymbol()
{
   for(int i = PositionsTotal() - 1; i >= 0; --i)
   {
      if(!PositionSelectByTicket(PositionGetTicket(i)))
         continue;

      string sym = PositionGetString(POSITION_SYMBOL);
      long   mgc = PositionGetInteger(POSITION_MAGIC);
      if(sym == _Symbol && (ulong)mgc == MagicNumber)
         return true;
   }
   return false;
}

int OnInit()
{
   trade.SetExpertMagicNumber(MagicNumber);
   trade.SetAsyncMode(false);

   hFastEma = iMA(_Symbol, SignalTF, FastEmaPeriod, 0, MODE_EMA, PRICE_CLOSE);
   hSlowEma = iMA(_Symbol, SignalTF, SlowEmaPeriod, 0, MODE_EMA, PRICE_CLOSE);
   hAtr     = iATR(_Symbol, SignalTF, AtrPeriod);

   if(hFastEma == INVALID_HANDLE || hSlowEma == INVALID_HANDLE || hAtr == INVALID_HANDLE)
   {
      Print("Init failed: invalid indicator handle(s).");
      return INIT_FAILED;
   }

   Print("JarvisAlgoStarter initialized.");
   return INIT_SUCCEEDED;
}

void OnDeinit(const int reason)
{
   if(hFastEma != INVALID_HANDLE) IndicatorRelease(hFastEma);
   if(hSlowEma != INVALID_HANDLE) IndicatorRelease(hSlowEma);
   if(hAtr     != INVALID_HANDLE) IndicatorRelease(hAtr);
}

void OnTick()
{
   if(!IsNewBar())
      return;

   // Guardrail: spread filter
   const double ask = SymbolInfoDouble(_Symbol, SYMBOL_ASK);
   const double bid = SymbolInfoDouble(_Symbol, SYMBOL_BID);
   if(ask <= 0.0 || bid <= 0.0)
      return;

   const double spreadPoints = (ask - bid) / _Point;
   if(spreadPoints > MaxSpreadPoints)
      return;

   // Guardrail: one open position for this EA and symbol
   if(HasOpenPositionForSymbol())
      return;

   // Indicators
   double fastEma, slowEma, atr;
   if(!GetLatestValue(hFastEma, fastEma)) return;
   if(!GetLatestValue(hSlowEma, slowEma)) return;
   if(!GetLatestValue(hAtr, atr)) return;
   if(atr <= 0.0) return;

   // Direction by EMA regime
   const bool bullish = fastEma > slowEma;
   const bool bearish = fastEma < slowEma;
   if(!bullish && !bearish)
      return;

   const double stopDistance = atr * AtrStopMultiplier;
   const double lots = CalculateLotsFromRisk(stopDistance);
   if(lots <= 0.0)
      return;

   if(bullish)
   {
      const double sl = ask - stopDistance;
      const double tp = ask + (stopDistance * RewardToRisk);
      trade.Buy(lots, _Symbol, ask, sl, tp, "Jarvis EMA/ATR Long");
   }
   else if(bearish)
   {
      const double sl = bid + stopDistance;
      const double tp = bid - (stopDistance * RewardToRisk);
      trade.Sell(lots, _Symbol, bid, sl, tp, "Jarvis EMA/ATR Short");
   }
}

