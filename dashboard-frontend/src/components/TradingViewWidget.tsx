import { useEffect, useRef, memo } from 'react';

function TradingViewWidget() {
  const container = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!container.current) return;
    container.current.innerHTML = '';
    const script = document.createElement("script");
    script.src = "https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js";
    script.type = "text/javascript";
    script.async = true;
    script.innerHTML = `
      {
        "autosize": true,
        "symbol": "OANDA:XAUUSD",
        "interval": "15",
        "timezone": "Etc/UTC",
        "theme": "dark",
        "style": "1",
        "locale": "en",
        "enable_publishing": false,
        "backgroundColor": "rgba(26, 29, 45, 1)",
        "gridColor": "rgba(42, 46, 57, 0.5)",
        "hide_top_toolbar": false,
        "hide_legend": false,
        "save_image": false,
        "container_id": "tradingview_widget"
      }`;
    container.current.appendChild(script);
  }, []);

  return (
    <div className="tradingview-widget-container" ref={container} style={{ height: "100%", width: "100%" }}>
      <div className="tradingview-widget-container__widget" style={{ height: "calc(100% - 32px)", width: "100%" }}></div>
    </div>
  );
}

export default memo(TradingViewWidget);
