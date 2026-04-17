/**
 * JARVIS Chart Engine V16.0
 * Lightweight Charts v5.1 integration tuned to feel closer to TradingView.
 */

let chart = null;
let candleSeries = null;
let currentSMCZones = [];
let indicators = {};

let chartReady = false;
let pendingSnapshot = null;
let hasFittedContent = false;
let currentCandleCount = 0;
let firstCandleTime = null;

function parseRgba(color) {
    const match = /^rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([.\d]+))?\)$/i.exec(color || '');
    if (!match) {
        return { r: 0, g: 188, b: 212, a: 1 };
    }
    return {
        r: Number(match[1]),
        g: Number(match[2]),
        b: Number(match[3]),
        a: match[4] !== undefined ? Number(match[4]) : 1,
    };
}

function withAlpha(color, alpha) {
    const { r, g, b } = parseRgba(color);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function normalizeSnapshot(snapshot) {
    if (!snapshot || typeof snapshot !== 'object') {
        return { candles: [], zones: [] };
    }
    return {
        candles: Array.isArray(snapshot.candles) ? snapshot.candles : [],
        zones: Array.isArray(snapshot.zones) ? snapshot.zones : [],
    };
}

function applySnapshot(snapshot) {
    const normalized = normalizeSnapshot(snapshot);
    setCandles(normalized.candles);
    drawSMC(normalized.zones);
}

function flushPendingSnapshot() {
    if (!chartReady || !chart || !candleSeries || !pendingSnapshot) {
        return;
    }
    applySnapshot(pendingSnapshot);
    pendingSnapshot = null;
}

function notifyReady() {
    if (window.kmp) {
        try {
            window.kmp.postMessage(JSON.stringify({ type: 'CHART_READY' }));
        } catch (error) {
            console.error('Failed to notify ready state', error);
        }
    }
}

function initChart() {
    const container = document.getElementById('chart');
    const chartOptions = {
        width: container.clientWidth,
        height: container.clientHeight,
        layout: {
            background: { type: LightweightCharts.ColorType.Solid, color: '#131722' },
            textColor: '#B2B5BE',
            fontSize: 12,
            fontFamily: "-apple-system, BlinkMacSystemFont, 'Trebuchet MS', Roboto, Ubuntu, sans-serif",
            panes: { separatorColor: '#2A2E39', separatorHoverColor: '#3C4252' },
        },
        grid: {
            vertLines: { color: 'rgba(42, 46, 57, 0.65)', style: LightweightCharts.LineStyle.Solid },
            horzLines: { color: 'rgba(42, 46, 57, 0.65)', style: LightweightCharts.LineStyle.Solid },
        },
        crosshair: {
            mode: LightweightCharts.CrosshairMode.Normal,
            vertLine: {
                color: '#758696',
                width: 1,
                style: LightweightCharts.LineStyle.SparseDotted,
                labelBackgroundColor: '#2962FF',
            },
            horzLine: {
                color: '#758696',
                width: 1,
                style: LightweightCharts.LineStyle.SparseDotted,
                labelBackgroundColor: '#2962FF',
            },
        },
        rightPriceScale: {
            visible: true,
            borderColor: 'rgba(197, 203, 206, 0.3)',
            autoScale: true,
            scaleMargins: {
                top: 0.1,
                bottom: 0.1,
            },
        },
        timeScale: {
            borderColor: 'rgba(197, 203, 206, 0.3)',
            rightOffset: 8,
            barSpacing: 8,
            minBarSpacing: 4,
            timeVisible: true,
            secondsVisible: false,
            fixLeftEdge: false,
            lockVisibleTimeRangeOnResize: true,
        },
        handleScroll: {
            mouseWheel: true,
            pressedMouseMove: true,
            horzTouchDrag: true,
            vertTouchDrag: true,
        },
        handleScale: {
            axisPressedMouseMove: true,
            mouseWheel: true,
            pinch: true,
        },
        localization: {
            locale: 'en-US',
        },
    };

    chart = LightweightCharts.createChart(container, chartOptions);

    candleSeries = chart.addSeries(LightweightCharts.CandlestickSeries, {
        upColor: '#26A69A',
        downColor: '#EF5350',
        wickUpColor: '#26A69A',
        wickDownColor: '#EF5350',
        borderUpColor: '#26A69A',
        borderDownColor: '#EF5350',
        priceLineVisible: true,
        lastValueVisible: true,
    });

    document.getElementById('loading').style.display = 'none';

    window.addEventListener('resize', () => {
        if (!chart) return;
        chart.applyOptions({
            width: container.clientWidth,
            height: container.clientHeight,
        });
    });

    chartReady = true;
    notifyReady();
    flushPendingSnapshot();
}

function setCandles(data) {
    if (!Array.isArray(data)) {
        return;
    }

    if (!chartReady || !candleSeries) {
        pendingSnapshot = normalizeSnapshot({ candles: data, zones: pendingSnapshot?.zones || [] });
        return;
    }

    if (data.length === 0) {
        candleSeries.setData([]);
        currentCandleCount = 0;
        firstCandleTime = null;
        hasFittedContent = false;
        return;
    }

    const incomingFirstTime = data[0].time;
    const canIncrementalUpdate =
        currentCandleCount > 0 &&
        firstCandleTime === incomingFirstTime &&
        data.length >= currentCandleCount &&
        data.length <= currentCandleCount + 1;

    if (canIncrementalUpdate) {
        const startIndex = Math.max(currentCandleCount - 1, 0);
        for (let i = startIndex; i < data.length; i += 1) {
            candleSeries.update(data[i]);
        }
    } else {
        candleSeries.setData(data);
    }

    currentCandleCount = data.length;
    firstCandleTime = incomingFirstTime;

    if (!hasFittedContent) {
        chart.timeScale().fitContent();
        hasFittedContent = true;
    }
}

function clearSMC() {
    currentSMCZones.forEach((series) => {
        try {
            chart.removeSeries(series);
        } catch (_ignored) {
            // Ignore stale references.
        }
    });
    currentSMCZones = [];
}

function createZoneBoundarySeries(zone, price, color, lineStyle) {
    const lineSeries = chart.addSeries(LightweightCharts.LineSeries, {
        color,
        lineWidth: 1,
        lineStyle,
        priceLineVisible: false,
        lastValueVisible: false,
        crosshairMarkerVisible: false,
    });

    lineSeries.setData([
        { time: zone.startTime, value: price },
        { time: zone.endTime, value: price },
    ]);
    return lineSeries;
}

function drawSMC(zones) {
    if (!Array.isArray(zones)) {
        return;
    }

    if (!chartReady || !chart) {
        pendingSnapshot = normalizeSnapshot({ candles: pendingSnapshot?.candles || [], zones });
        return;
    }

    clearSMC();

    zones.forEach((zone) => {
        const zoneColor = zone.color || 'rgba(0, 188, 212, 0.18)';

        // Fill the area between top and bottom using BaselineSeries:
        // - top side is visible and colored
        // - bottom side is transparent
        const fillSeries = chart.addSeries(LightweightCharts.BaselineSeries, {
            baseValue: { type: 'price', price: zone.bottom },
            topLineColor: withAlpha(zoneColor, 0.65),
            topFillColor1: withAlpha(zoneColor, 0.24),
            topFillColor2: withAlpha(zoneColor, 0.12),
            bottomLineColor: 'rgba(0, 0, 0, 0)',
            bottomFillColor1: 'rgba(0, 0, 0, 0)',
            bottomFillColor2: 'rgba(0, 0, 0, 0)',
            lineWidth: 1,
            priceLineVisible: false,
            lastValueVisible: false,
            crosshairMarkerVisible: false,
            title: zone.type || 'ZONE',
        });

        fillSeries.setData([
            { time: zone.startTime, value: zone.top },
            { time: zone.endTime, value: zone.top },
        ]);

        const topBoundary = createZoneBoundarySeries(
            zone,
            zone.top,
            withAlpha(zoneColor, 0.78),
            LightweightCharts.LineStyle.Solid
        );
        const bottomBoundary = createZoneBoundarySeries(
            zone,
            zone.bottom,
            withAlpha(zoneColor, 0.52),
            LightweightCharts.LineStyle.Dotted
        );

        currentSMCZones.push(fillSeries, topBoundary, bottomBoundary);
    });
}

function setSnapshot(snapshot) {
    const normalized = normalizeSnapshot(snapshot);
    if (!chartReady) {
        pendingSnapshot = normalized;
        return;
    }
    applySnapshot(normalized);
}

function addEMA(period, color = '#2196f3') {
    if (!chart || indicators[`ema_${period}`]) {
        return;
    }

    const emaSeries = chart.addSeries(LightweightCharts.LineSeries, {
        color,
        lineWidth: 2,
        title: `EMA ${period}`,
        priceLineVisible: false,
        lastValueVisible: false,
    });

    indicators[`ema_${period}`] = emaSeries;
}

async function captureScreenshot() {
    try {
        const canvas = document.querySelector('canvas');
        if (!canvas) return null;
        const base64 = canvas.toDataURL('image/png');

        if (window.kmp) {
            window.kmp.postMessage(JSON.stringify({ type: 'CAPTURE', data: base64 }));
        }
        return base64;
    } catch (error) {
        console.error('Capture failed', error);
        return null;
    }
}

window.jarvisBridge = {
    setSnapshot,
    setCandles,
    drawSMC,
    isReady: () => chartReady,
    captureScreenshot,
};

window.setCandles = setCandles;
window.drawSMC = drawSMC;
window.captureScreenshot = captureScreenshot;
window.addEMA = addEMA;

window.onload = initChart;
