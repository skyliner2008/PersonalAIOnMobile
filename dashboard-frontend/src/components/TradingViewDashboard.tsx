import { useEffect, useRef, memo } from 'react';

const TickerTapeWidget = memo(() => {
  const container = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!container.current) return;
    container.current.innerHTML = '';
    const script = document.createElement("script");
    script.src = "https://s3.tradingview.com/external-embedding/embed-widget-ticker-tape.js";
    script.async = true;
    script.innerHTML = `{"symbols":[{"proName":"FOREXCOM:SPXUSD","title":"S&P 500"},{"proName":"FOREXCOM:NSXUSD","title":"US 100"},{"proName":"FX_IDC:EURUSD","title":"EUR to USD"},{"proName":"BITSTAMP:BTCUSD","title":"Bitcoin"},{"proName":"OANDA:XAUUSD","title":"Gold"}],"showSymbolLogo":true,"isTransparent":true,"displayMode":"adaptive","colorTheme":"dark","locale":"en"}`;
    container.current.appendChild(script);
  }, []);
  return <div className="tradingview-widget-container" ref={container} />;
});

const AdvancedChartWidget = memo(() => {
  const container = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!container.current) return;
    container.current.innerHTML = '';
    const script = document.createElement("script");
    script.src = "https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js";
    script.async = true;
    script.innerHTML = `{"autosize":true,"symbol":"OANDA:XAUUSD","interval":"15","timezone":"Etc/UTC","theme":"dark","style":"1","locale":"en","enable_publishing":false,"backgroundColor":"rgba(26, 29, 45, 1)","gridColor":"rgba(42, 46, 57, 0.5)","hide_top_toolbar":false,"hide_legend":false,"save_image":false,"container_id":"tradingview_widget"}`;
    container.current.appendChild(script);
  }, []);
  return <div className="tradingview-widget-container w-full h-full" ref={container} />;
});

const ScreenerWidget = memo(() => {
  const container = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!container.current) return;
    container.current.innerHTML = '';
    const script = document.createElement("script");
    script.src = "https://s3.tradingview.com/external-embedding/embed-widget-screener.js";
    script.async = true;
    script.innerHTML = `{"width":"100%","height":"100%","defaultColumn":"overview","defaultScreen":"general","market":"forex","showToolbar":true,"colorTheme":"dark","locale":"en","isTransparent":true}`;
    container.current.appendChild(script);
  }, []);
  return <div className="tradingview-widget-container w-full h-full" ref={container} />;
});

const CalendarWidget = memo(() => {
  const container = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!container.current) return;
    container.current.innerHTML = '';
    const script = document.createElement("script");
    script.src = "https://s3.tradingview.com/external-embedding/embed-widget-events.js";
    script.async = true;
    script.innerHTML = `{"colorTheme":"dark","isTransparent":true,"width":"100%","height":"100%","locale":"en","importanceFilter":"-1,0,1","currencyFilter":"USD,EUR,GBP,JPY,AUD,CAD,CHF,NZD"}`;
    container.current.appendChild(script);
  }, []);
  return <div className="tradingview-widget-container w-full h-full" ref={container} />;
});

const MarketDataWidget = memo(() => {
  const container = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!container.current) return;
    container.current.innerHTML = '';
    const script = document.createElement("script");
    script.src = "https://s3.tradingview.com/external-embedding/embed-widget-market-overview.js";
    script.async = true;
    script.innerHTML = `{"colorTheme":"dark","dateRange":"12M","showChart":true,"locale":"en","largeChartUrl":"","isTransparent":true,"showSymbolLogo":true,"showFloatingTooltip":false,"width":"100%","height":"100%","tabs":[{"title":"Forex","symbols":[{"s":"FX:EURUSD","d":"EUR to USD"},{"s":"FX:GBPUSD","d":"GBP to USD"},{"s":"FX:USDJPY","d":"USD to JPY"},{"s":"FX:AUDUSD","d":"AUD to USD"}]},{"title":"Commodities","symbols":[{"s":"OANDA:XAUUSD","d":"Gold"},{"s":"OANDA:XAGUSD","d":"Silver"},{"s":"TVC:USOIL","d":"Crude Oil"}]}]}`;
    container.current.appendChild(script);
  }, []);
  return <div className="tradingview-widget-container w-full h-full" ref={container} />;
});

export default function TradingViewDashboard() {
  return (
    <div className="flex flex-col h-full gap-4">
      {/* Top Ticker Tape */}
      <div className="w-full bg-[#1a1d2d] rounded-xl border border-slate-800 overflow-hidden shrink-0">
        <TickerTapeWidget />
      </div>

      <div className="flex-1 grid grid-cols-1 lg:grid-cols-4 gap-4 min-h-0">
        {/* Main Chart */}
        <div className="lg:col-span-3 bg-[#1a1d2d] rounded-xl border border-slate-800 p-2 overflow-hidden flex flex-col min-h-[400px]">
          <AdvancedChartWidget />
        </div>

        {/* Right Sidebar: Watchlist & Market Data */}
        <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 p-2 overflow-hidden min-h-[300px]">
          <MarketDataWidget />
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 shrink-0 h-[350px]">
        {/* Forex Screener */}
        <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 p-2 overflow-hidden">
          <ScreenerWidget />
        </div>

        {/* Economic Calendar */}
        <div className="bg-[#1a1d2d] rounded-xl border border-slate-800 p-2 overflow-hidden">
          <CalendarWidget />
        </div>
      </div>
    </div>
  );
}
