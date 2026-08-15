/**
 * JARVIS Chart Dashboard Engine V1.0
 * Multi-pane Lightweight Charts v5.1 dashboard — mobile-first, offline, AI-controllable.
 *
 * Layouts: single | rsi | macd | rsi_macd | volume | full (vol+rsi+macd)
 * Overlays (main pane): ema20 | ema50 | ema200 | bb
 * Subpanes: RSI(14), MACD(12,26,9), Volume, ATR(14)
 * SMC zones overlay (reuse style from chart_engine V16)
 *
 * Bridge: window.jarvisDashboard
 */

(function () {
    'use strict';

    // ─── State ───────────────────────────────────────────────────────────────
    const state = {
        symbol: 'XAUUSD',
        interval: '15m',
        layout: 'single',
        overlays: { ema14: false, ema20: false, ema50: true, ema60: false, ema200: false, bb: false, donchian: false, signals: false },
        candles: [],   // [{time(sec), open, high, low, close, volume}]
        smcZones: [],  // [{top, bottom, startTime, endTime, color, type}]
        signalMarkers: [], // [{time, side:'BUY'|'SELL', label, color}]
    };

    let chart = null;
    let seriesRefs = { candle: null, volume: null, rsi: null, macd: null, macdSignal: null, macdHist: null, atr: null };
    let overlayRefs = {};   // key -> series (bb = 3 series: bb_upper/bb_basis/bb_lower)
    let smcSeries = [];
    let markersPlugin = null; // LightweightCharts.createSeriesMarkers instance (v5 API)
    let chartReady = false;
    let pendingInit = null;
    let hasFitted = false;

    // ─── Layout config ───────────────────────────────────────────────────────
    // pane order: 0=main, then subpanes in listed order
    const LAYOUTS = {
        single:   { subpanes: [] },
        rsi:      { subpanes: ['rsi'] },
        macd:     { subpanes: ['macd'] },
        rsi_macd: { subpanes: ['rsi', 'macd'] },
        volume:   { subpanes: ['volume'] },
        full:     { subpanes: ['volume', 'rsi', 'macd'] },
    };

    const OVERLAY_DEFS = {
        ema14:  { color: '#F6C343', title: 'EMA 14',  period: 14 },
        ema20:  { color: '#2962FF', title: 'EMA 20',  period: 20 },
        ema50:  { color: '#FF6D00', title: 'EMA 50',  period: 50 },
        ema60:  { color: '#26C6DA', title: 'EMA 60',  period: 60 },
        ema200: { color: '#AB47BC', title: 'EMA 200', period: 200 },
        bb:     { color: '#78909C', title: 'BB 20,2', period: 20, mult: 2 },
        donchian: { color: '#8D6E63', title: 'DC 20', period: 20 },
    };

    // ─── Indicator math ──────────────────────────────────────────────────────
    function ema(values, period) {
        const k = 2 / (period + 1);
        const out = new Array(values.length).fill(null);
        let prev = null;
        let sum = 0;
        for (let i = 0; i < values.length; i++) {
            if (i < period - 1) { sum += values[i]; continue; }
            if (i === period - 1) { sum += values[i]; prev = sum / period; out[i] = prev; continue; }
            prev = values[i] * k + prev * (1 - k);
            out[i] = prev;
        }
        return out;
    }

    function sma(values, period) {
        const out = new Array(values.length).fill(null);
        let sum = 0;
        for (let i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= period) sum -= values[i - period];
            if (i >= period - 1) out[i] = sum / period;
        }
        return out;
    }

    function stdev(values, period, means) {
        const out = new Array(values.length).fill(null);
        for (let i = period - 1; i < values.length; i++) {
            let acc = 0;
            for (let j = i - period + 1; j <= i; j++) {
                const d = values[j] - means[i];
                acc += d * d;
            }
            out[i] = Math.sqrt(acc / period);
        }
        return out;
    }

    function rsiWilder(closes, period) {
        const out = new Array(closes.length).fill(null);
        if (closes.length <= period) return out;
        let gain = 0, loss = 0;
        for (let i = 1; i <= period; i++) {
            const d = closes[i] - closes[i - 1];
            if (d >= 0) gain += d; else loss -= d;
        }
        let avgGain = gain / period, avgLoss = loss / period;
        out[period] = avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
        for (let i = period + 1; i < closes.length; i++) {
            const d = closes[i] - closes[i - 1];
            avgGain = (avgGain * (period - 1) + Math.max(d, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-d, 0)) / period;
            out[i] = avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
        }
        return out;
    }

    function atrWilder(candles, period) {
        const out = new Array(candles.length).fill(null);
        if (candles.length <= period) return out;
        const trs = candles.map((c, i) => {
            if (i === 0) return c.high - c.low;
            const pc = candles[i - 1].close;
            return Math.max(c.high - c.low, Math.abs(c.high - pc), Math.abs(c.low - pc));
        });
        let prev = 0;
        for (let i = 1; i <= period; i++) prev += trs[i];
        prev /= period;
        out[period] = prev;
        for (let i = period + 1; i < candles.length; i++) {
            prev = (prev * (period - 1) + trs[i]) / period;
            out[i] = prev;
        }
        return out;
    }

    function toSeriesData(times, values) {
        const out = [];
        for (let i = 0; i < times.length; i++) {
            if (values[i] !== null && values[i] !== undefined && isFinite(values[i])) {
                out.push({ time: times[i], value: values[i] });
            }
        }
        return out;
    }

    // ─── Chart construction ──────────────────────────────────────────────────
    function destroyChart() {
        if (chart) {
            try { chart.remove(); } catch (_e) {}
        }
        chart = null;
        seriesRefs = { candle: null, volume: null, rsi: null, macd: null, macdSignal: null, macdHist: null, atr: null };
        overlayRefs = {};
        smcSeries = [];
    }

    function baseChartOptions() {
        return {
            width: document.getElementById('chart').clientWidth,
            height: document.getElementById('chart').clientHeight,
            layout: {
                background: { type: LightweightCharts.ColorType.Solid, color: '#131722' },
                textColor: '#B2B5BE',
                fontSize: 11,
                fontFamily: "-apple-system, BlinkMacSystemFont, 'Trebuchet MS', Roboto, Ubuntu, sans-serif",
                panes: { separatorColor: '#2A2E39', separatorHoverColor: '#3C4252', enableResize: true },
            },
            grid: {
                vertLines: { color: 'rgba(42, 46, 57, 0.6)' },
                horzLines: { color: 'rgba(42, 46, 57, 0.6)' },
            },
            crosshair: { mode: LightweightCharts.CrosshairMode.Normal },
            rightPriceScale: { visible: true, borderColor: 'rgba(197,203,206,0.3)', autoScale: true },
            timeScale: {
                borderColor: 'rgba(197,203,206,0.3)',
                rightOffset: 6,
                barSpacing: 8,
                minBarSpacing: 3,
                timeVisible: true,
                secondsVisible: false,
            },
            handleScroll: { pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: true },
            handleScale: { axisPressedMouseMove: true, pinch: true },
            localization: { locale: 'en-US' },
        };
    }

    function buildChart() {
        destroyChart();
        markersPlugin = null;
        const layoutDef = LAYOUTS[state.layout] || LAYOUTS.single;
        chart = LightweightCharts.createChart(document.getElementById('chart'), baseChartOptions());

        // Pane 0: candles
        seriesRefs.candle = chart.addSeries(LightweightCharts.CandlestickSeries, {
            upColor: '#26A69A', downColor: '#EF5350',
            wickUpColor: '#26A69A', wickDownColor: '#EF5350',
            borderUpColor: '#26A69A', borderDownColor: '#EF5350',
            priceLineVisible: true, lastValueVisible: true,
        }, 0);

        // Subpanes
        layoutDef.subpanes.forEach((kind, idx) => {
            const paneIndex = idx + 1;
            if (kind === 'volume') {
                seriesRefs.volume = chart.addSeries(LightweightCharts.HistogramSeries, {
                    priceFormat: { type: 'volume' },
                    priceLineVisible: false, lastValueVisible: false,
                }, paneIndex);
            } else if (kind === 'rsi') {
                seriesRefs.rsi = chart.addSeries(LightweightCharts.LineSeries, {
                    color: '#B39DDB', lineWidth: 2, title: 'RSI 14',
                    priceLineVisible: false, lastValueVisible: true,
                    priceScale: { scaleMargins: { top: 0.15, bottom: 0.15 } },
                }, paneIndex);
                // guide lines 30/70
                const mkGuide = (price, color) => {
                    const g = chart.addSeries(LightweightCharts.LineSeries, {
                        color, lineWidth: 1, lineStyle: LightweightCharts.LineStyle.Dotted,
                        priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false,
                    }, paneIndex);
                    overlayRefs[`rsi_guide_${price}`] = g;
                };
                mkGuide(70, 'rgba(239,83,80,0.5)');
                mkGuide(30, 'rgba(38,166,154,0.5)');
            } else if (kind === 'macd') {
                seriesRefs.macdHist = chart.addSeries(LightweightCharts.HistogramSeries, {
                    priceLineVisible: false, lastValueVisible: false, title: 'Hist',
                }, paneIndex);
                seriesRefs.macd = chart.addSeries(LightweightCharts.LineSeries, {
                    color: '#2962FF', lineWidth: 2, title: 'MACD',
                    priceLineVisible: false, lastValueVisible: false,
                }, paneIndex);
                seriesRefs.macdSignal = chart.addSeries(LightweightCharts.LineSeries, {
                    color: '#FF6D00', lineWidth: 1, title: 'Signal',
                    priceLineVisible: false, lastValueVisible: false,
                }, paneIndex);
            } else if (kind === 'atr') {
                seriesRefs.atr = chart.addSeries(LightweightCharts.LineSeries, {
                    color: '#FFD54F', lineWidth: 2, title: 'ATR 14',
                    priceLineVisible: false, lastValueVisible: true,
                }, paneIndex);
            }
        });

        // Overlays on main pane
        Object.keys(OVERLAY_DEFS).forEach((key) => {
            if (!state.overlays[key]) return;
            addOverlaySeries(key);
        });

        // Pane stretch: main taller
        try {
            const panes = chart.panes();
            panes.forEach((p, i) => p.setStretchFactor(i === 0 ? 3 : 1));
        } catch (_e) {}

        document.getElementById('loading').style.display = 'none';
        chartReady = true;
        applyAllData();
        updateLegend();
    }

    function addOverlaySeries(key) {
        const def = OVERLAY_DEFS[key];
        if (!def) return;
        if (key === 'bb') {
            overlayRefs.bb_upper = chart.addSeries(LightweightCharts.LineSeries, {
                color: 'rgba(120,144,156,0.9)', lineWidth: 1, title: 'BB Upper',
                priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false,
            }, 0);
            overlayRefs.bb_basis = chart.addSeries(LightweightCharts.LineSeries, {
                color: 'rgba(120,144,156,0.7)', lineWidth: 1, lineStyle: LightweightCharts.LineStyle.Dashed, title: 'BB Basis',
                priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false,
            }, 0);
            overlayRefs.bb_lower = chart.addSeries(LightweightCharts.LineSeries, {
                color: 'rgba(120,144,156,0.9)', lineWidth: 1, title: 'BB Lower',
                priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false,
            }, 0);
        } else if (key === 'donchian') {
            overlayRefs.dc_upper = chart.addSeries(LightweightCharts.LineSeries, {
                color: 'rgba(141,110,99,0.95)', lineWidth: 1, title: 'DC Upper',
                priceLineVisible: false, lastValueVisible: true, crosshairMarkerVisible: false,
            }, 0);
            overlayRefs.dc_mid = chart.addSeries(LightweightCharts.LineSeries, {
                color: 'rgba(141,110,99,0.6)', lineWidth: 1, lineStyle: LightweightCharts.LineStyle.Dashed, title: 'DC Mid',
                priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false,
            }, 0);
            overlayRefs.dc_lower = chart.addSeries(LightweightCharts.LineSeries, {
                color: 'rgba(141,110,99,0.95)', lineWidth: 1, title: 'DC Lower',
                priceLineVisible: false, lastValueVisible: true, crosshairMarkerVisible: false,
            }, 0);
        } else {
            overlayRefs[key] = chart.addSeries(LightweightCharts.LineSeries, {
                color: def.color, lineWidth: 1, title: def.title,
                priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false,
            }, 0);
        }
    }

    function removeOverlaySeries(key) {
        if (key === 'bb') {
            ['bb_upper', 'bb_basis', 'bb_lower'].forEach((k) => {
                if (overlayRefs[k]) { try { chart.removeSeries(overlayRefs[k]); } catch (_e) {} delete overlayRefs[k]; }
            });
        } else if (key === 'donchian') {
            ['dc_upper', 'dc_mid', 'dc_lower'].forEach((k) => {
                if (overlayRefs[k]) { try { chart.removeSeries(overlayRefs[k]); } catch (_e) {} delete overlayRefs[k]; }
            });
        } else if (overlayRefs[key]) {
            try { chart.removeSeries(overlayRefs[key]); } catch (_e) {}
            delete overlayRefs[key];
        }
    }

    // ─── Data application ────────────────────────────────────────────────────
    function applyAllData() {
        if (!chartReady || !chart) return;
        const candles = state.candles;
        if (!candles.length) {
            seriesRefs.candle && seriesRefs.candle.setData([]);
            return;
        }
        const times = candles.map((c) => c.time);
        const closes = candles.map((c) => c.close);

        seriesRefs.candle.setData(candles.map((c) => ({
            time: c.time, open: c.open, high: c.high, low: c.low, close: c.close,
        })));

        // Subpanes
        if (seriesRefs.volume) {
            seriesRefs.volume.setData(candles.map((c) => ({
                time: c.time, value: c.volume || 0,
                color: c.close >= c.open ? 'rgba(38,166,154,0.55)' : 'rgba(239,83,80,0.55)',
            })));
        }
        if (seriesRefs.rsi) {
            const values = rsiWilder(closes, 14);
            seriesRefs.rsi.setData(toSeriesData(times, values));
            const guideData = (p) => [{ time: times[0], value: p }, { time: times[times.length - 1], value: p }];
            if (overlayRefs.rsi_guide_70) overlayRefs.rsi_guide_70.setData(guideData(70));
            if (overlayRefs.rsi_guide_30) overlayRefs.rsi_guide_30.setData(guideData(30));
        }
        if (seriesRefs.macd) {
            const e12 = ema(closes, 12);
            const e26 = ema(closes, 26);
            const macdLine = closes.map((_, i) => (e12[i] !== null && e26[i] !== null) ? e12[i] - e26[i] : null);
            const macdValid = macdLine.map((v) => v === null ? 0 : v);
            const signalLine = ema(macdValid, 9).map((v, i) => macdLine[i] === null ? null : v);
            const hist = macdLine.map((v, i) => (v !== null && signalLine[i] !== null) ? v - signalLine[i] : null);
            seriesRefs.macd.setData(toSeriesData(times, macdLine));
            seriesRefs.macdSignal.setData(toSeriesData(times, signalLine));
            seriesRefs.macdHist.setData(times.map((t, i) => ({
                time: t, value: hist[i] === null ? 0 : hist[i],
                color: (hist[i] || 0) >= 0 ? 'rgba(38,166,154,0.6)' : 'rgba(239,83,80,0.6)',
            })).filter((d, i) => hist[i] !== null));
        }
        if (seriesRefs.atr) {
            seriesRefs.atr.setData(toSeriesData(times, atrWilder(candles, 14)));
        }

        // Overlays
        Object.keys(OVERLAY_DEFS).forEach((key) => {
            if (!state.overlays[key]) return;
            if (key === 'bb') {
                const basis = sma(closes, 20);
                const sd = stdev(closes, 20, basis);
                const upper = closes.map((_, i) => basis[i] !== null ? basis[i] + 2 * sd[i] : null);
                const lower = closes.map((_, i) => basis[i] !== null ? basis[i] - 2 * sd[i] : null);
                overlayRefs.bb_upper && overlayRefs.bb_upper.setData(toSeriesData(times, upper));
                overlayRefs.bb_basis && overlayRefs.bb_basis.setData(toSeriesData(times, basis));
                overlayRefs.bb_lower && overlayRefs.bb_lower.setData(toSeriesData(times, lower));
            } else if (key === 'donchian') {
                // Donchian channel 20 แท่ง (ไม่รวมแท่งปัจจุบัน — ตรงกับ StrategySignalProvider)
                const p = 20;
                const up = new Array(candles.length).fill(null);
                const lo = new Array(candles.length).fill(null);
                const mid = new Array(candles.length).fill(null);
                for (let i = p; i < candles.length; i++) {
                    let hh = -Infinity, ll = Infinity;
                    for (let j = i - p; j < i; j++) { if (candles[j].high > hh) hh = candles[j].high; if (candles[j].low < ll) ll = candles[j].low; }
                    up[i] = hh; lo[i] = ll; mid[i] = (hh + ll) / 2;
                }
                overlayRefs.dc_upper && overlayRefs.dc_upper.setData(toSeriesData(times, up));
                overlayRefs.dc_mid && overlayRefs.dc_mid.setData(toSeriesData(times, mid));
                overlayRefs.dc_lower && overlayRefs.dc_lower.setData(toSeriesData(times, lo));
            } else {
                const def = OVERLAY_DEFS[key];
                overlayRefs[key] && overlayRefs[key].setData(toSeriesData(times, ema(closes, def.period)));
            }
        });

        // SMC zones
        drawSMCInternal(state.smcZones);

        // Signal markers (BUY/SELL arrows จากทุกกลยุทธ์)
        applyMarkers();

        // Header
        const last = candles[candles.length - 1];
        const first = candles[0];
        const chg = first.close !== 0 ? ((last.close - first.close) / first.close) * 100 : 0;
        const priceEl = document.getElementById('priceLabel');
        const chgEl = document.getElementById('chgLabel');
        if (priceEl) {
            priceEl.textContent = last.close.toFixed(last.close >= 100 ? 2 : 4);
            priceEl.style.color = last.close >= first.close ? '#26A69A' : '#EF5350';
        }
        if (chgEl) {
            chgEl.textContent = `${chg >= 0 ? '+' : ''}${chg.toFixed(2)}%`;
            chgEl.style.color = chg >= 0 ? '#26A69A' : '#EF5350';
        }

        if (!hasFitted) {
            chart.timeScale().fitContent();
            hasFitted = true;
        }
    }

    // ─── SMC zones (ported from chart_engine V16) ────────────────────────────
    function parseRgba(color) {
        const match = /^rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([.\d]+))?\)$/i.exec(color || '');
        if (!match) return { r: 0, g: 188, b: 212, a: 1 };
        return { r: +match[1], g: +match[2], b: +match[3], a: match[4] !== undefined ? +match[4] : 1 };
    }
    function withAlpha(color, alpha) {
        const { r, g, b } = parseRgba(color);
        return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    }

    function clearSMC() {
        smcSeries.forEach((s) => { try { chart.removeSeries(s); } catch (_e) {} });
        smcSeries = [];
    }

    function drawSMCInternal(zones) {
        if (!chartReady || !chart) return;
        clearSMC();
        (zones || []).forEach((zone) => {
            const zoneColor = zone.color || 'rgba(0, 188, 212, 0.18)';
            // ─── zone แบบเส้น (Liquidity EQL/EQH, Premium/Discount/Equilibrium) ───
            if (zone.line) {
                const ls = chart.addSeries(LightweightCharts.LineSeries, {
                    color: zoneColor,
                    lineWidth: 1, lineStyle: LightweightCharts.LineStyle.Dashed,
                    priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false,
                    title: zone.type || 'LIQ',
                }, 0);
                ls.setData([
                    { time: zone.startTime, value: zone.price },
                    { time: zone.endTime, value: zone.price },
                ]);
                smcSeries.push(ls);
                return;
            }
            // ─── zone แบบกล่อง (OB / FVG) ───
            const fillSeries = chart.addSeries(LightweightCharts.BaselineSeries, {
                baseValue: { type: 'price', price: zone.bottom },
                topLineColor: withAlpha(zoneColor, 0.65),
                topFillColor1: withAlpha(zoneColor, 0.24),
                topFillColor2: withAlpha(zoneColor, 0.12),
                bottomLineColor: 'rgba(0,0,0,0)',
                bottomFillColor1: 'rgba(0,0,0,0)',
                bottomFillColor2: 'rgba(0,0,0,0)',
                lineWidth: 1,
                priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false,
                title: zone.type || 'ZONE',
            }, 0);
            fillSeries.setData([
                { time: zone.startTime, value: zone.top },
                { time: zone.endTime, value: zone.top },
            ]);
            const mkBoundary = (price, style) => {
                const ls = chart.addSeries(LightweightCharts.LineSeries, {
                    color: withAlpha(zoneColor, style === LightweightCharts.LineStyle.Solid ? 0.78 : 0.52),
                    lineWidth: 1, lineStyle: style,
                    priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false,
                }, 0);
                ls.setData([
                    { time: zone.startTime, value: price },
                    { time: zone.endTime, value: price },
                ]);
                return ls;
            };
            smcSeries.push(fillSeries, mkBoundary(zone.top, LightweightCharts.LineStyle.Solid), mkBoundary(zone.bottom, LightweightCharts.LineStyle.Dotted));
        });
    }

    // ─── Signal markers (lightweight-charts v5 — ต้องใช้ createSeriesMarkers plugin) ───
    function applyMarkers() {
        if (!chartReady || !chart || !seriesRefs.candle) return;
        if (!state.overlays.signals || !state.signalMarkers.length) {
            if (markersPlugin) { try { markersPlugin.setMarkers([]); } catch (_e) {} }
            return;
        }
        const validTimes = new Set(state.candles.map((c) => c.time));
        const ms = state.signalMarkers
            .map((m) => ({ side: m.side, label: m.label, color: m.color, time: m.time > 1e12 ? Math.floor(m.time / 1000) : Math.floor(m.time) }))
            .filter((m) => validTimes.has(m.time))
            .map((m) => ({
                time: m.time,
                position: m.side === 'BUY' ? 'belowBar' : 'aboveBar',
                color: m.color || (m.side === 'BUY' ? '#26A69A' : '#EF5350'),
                shape: m.side === 'BUY' ? 'arrowUp' : 'arrowDown',
                text: m.label || '',
            }))
            .sort((a, b) => a.time - b.time);
        try {
            if (!markersPlugin && window.LightweightCharts && LightweightCharts.createSeriesMarkers) {
                markersPlugin = LightweightCharts.createSeriesMarkers(seriesRefs.candle, ms);
            } else if (markersPlugin) {
                markersPlugin.setMarkers(ms);
            }
        } catch (e) { try { console.warn('markers error', e); } catch (_e) {} }
    }

    // ─── Legend ──────────────────────────────────────────────────────────────
    function updateLegend() {
        const el = document.getElementById('legend');
        if (!el) return;
        const parts = [];
        Object.keys(OVERLAY_DEFS).forEach((key) => {
            if (state.overlays[key]) {
                parts.push(`<span style="color:${OVERLAY_DEFS[key].color}">● ${OVERLAY_DEFS[key].title}</span>`);
            }
        });
        if (state.smcZones.length) parts.push(`<span style="color:#00bcd4">▮ SMC ${state.smcZones.length} zones</span>`);
        if (state.overlays.signals && state.signalMarkers.length) parts.push(`<span style="color:#FFD54F">◆ Signals ${state.signalMarkers.length}</span>`);
        el.innerHTML = parts.join('&nbsp;&nbsp;');
    }

    // ─── Public bridge ───────────────────────────────────────────────────────
    function normalizeCandles(raw) {
        if (!Array.isArray(raw)) return [];
        return raw
            .map((c) => ({
                time: c.time > 1e12 ? Math.floor(c.time / 1000) : Math.floor(c.time),
                open: +c.open, high: +c.high, low: +c.low, close: +c.close, volume: +(c.volume || 0),
            }))
            .filter((c) => isFinite(c.time) && isFinite(c.close))
            .sort((a, b) => a.time - b.time)
            .filter((c, i, arr) => i === 0 || c.time !== arr[i - 1].time);
    }

    window.jarvisDashboard = {
        /** config: {symbol, interval, layout, overlays:{ema20,ema50,ema200,bb}} */
        init(configJson) {
            let cfg = configJson;
            if (typeof configJson === 'string') { try { cfg = JSON.parse(configJson); } catch (_e) { cfg = {}; } }
            cfg = cfg || {};
            state.symbol = cfg.symbol || state.symbol;
            state.interval = cfg.interval || state.interval;
            state.layout = LAYOUTS[cfg.layout] ? cfg.layout : state.layout;
            if (cfg.overlays) {
                Object.keys(state.overlays).forEach((k) => { state.overlays[k] = !!cfg.overlays[k]; });
            }
            const symEl = document.getElementById('symLabel');
            if (symEl) symEl.textContent = `${state.symbol} · ${state.interval}`;
            buildChart();
        },
        setSymbol(symbol, interval) {
            state.symbol = symbol || state.symbol;
            state.interval = interval || state.interval;
            const symEl = document.getElementById('symLabel');
            if (symEl) symEl.textContent = `${state.symbol} · ${state.interval}`;
            hasFitted = false;
        },
        /** candlesJson: [{time, open, high, low, close, volume}] — time sec or ms (auto-detect) */
        setCandles(candlesJson) {
            let raw = candlesJson;
            if (typeof candlesJson === 'string') { try { raw = JSON.parse(candlesJson); } catch (_e) { raw = []; } }
            const normalized = normalizeCandles(raw);
            const isNewSeries = normalized.length && state.candles.length && normalized[0].time !== state.candles[0].time;
            if (isNewSeries) hasFitted = false;
            state.candles = normalized;
            if (chartReady) applyAllData();
        },
        setLayout(layout) {
            if (!LAYOUTS[layout]) return false;
            state.layout = layout;
            buildChart();
            return true;
        },
        setOverlay(name, visible) {
            if (!(name in state.overlays)) return false;
            state.overlays[name] = !!visible;
            if (chartReady) {
                if (visible) { addOverlaySeries(name); } else { removeOverlaySeries(name); }
                applyAllData();
                updateLegend();
            }
            return true;
        },
        drawSMC(zonesJson) {
            let zones = zonesJson;
            if (typeof zonesJson === 'string') { try { zones = JSON.parse(zonesJson); } catch (_e) { zones = []; } }
            state.smcZones = Array.isArray(zones) ? zones : [];
            if (chartReady) { drawSMCInternal(state.smcZones); updateLegend(); }
        },
        /** markersJson: [{time(sec|ms), side:'BUY'|'SELL', label, color}] */
        drawMarkers(markersJson) {
            let ms = markersJson;
            if (typeof markersJson === 'string') { try { ms = JSON.parse(markersJson); } catch (_e) { ms = []; } }
            state.signalMarkers = Array.isArray(ms) ? ms : [];
            if (chartReady) { applyMarkers(); updateLegend(); }
        },
        fit() {
            if (chart) chart.timeScale().fitContent();
        },
        isReady: () => chartReady,
        captureScreenshot() {
            try {
                const canvas = document.querySelector('canvas');
                if (!canvas) return null;
                const base64 = canvas.toDataURL('image/png');
                if (window.kmp) {
                    window.kmp.postMessage(JSON.stringify({ type: 'CAPTURE', data: base64 }));
                }
                return base64;
            } catch (e) { return null; }
        },
    };

    window.addEventListener('resize', () => {
        if (!chart) return;
        chart.applyOptions({
            width: document.getElementById('chart').clientWidth,
            height: document.getElementById('chart').clientHeight,
        });
    });

    window.onload = () => {
        buildChart();
        if (window.kmp) {
            try { window.kmp.postMessage(JSON.stringify({ type: 'DASHBOARD_READY' })); } catch (_e) {}
        }
    };
})();
