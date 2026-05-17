import { AutoTradingConfig } from '../types.js';
import { atLog, atWarn, atError, getLogTime } from '../utils.js';

export interface EconomicEvent {
    title: string;
    impact: 'LOW' | 'MEDIUM' | 'HIGH';
    currency: string; // e.g. USD, EUR, JPY — normalized to 3-letter code
    time: number; // epoch ms
    country?: string;
}

// ForexFactory publishes a free weekly JSON at this URL — no key required.
// Format: [{ title, country, date, impact: 'Low'|'Medium'|'High'|'Holiday', ... }]
const FF_THIS_WEEK = 'https://nfs.faireconomy.media/ff_calendar_thisweek.json';
const FF_NEXT_WEEK = 'https://nfs.faireconomy.media/ff_calendar_nextweek.json';

// Map ForexFactory country labels to 3-letter ISO currency codes.
const COUNTRY_TO_CURRENCY: Record<string, string> = {
    'USD': 'USD', 'EUR': 'EUR', 'JPY': 'JPY', 'GBP': 'GBP', 'AUD': 'AUD',
    'CAD': 'CAD', 'CHF': 'CHF', 'NZD': 'NZD', 'CNY': 'CNY',
    'United States': 'USD', 'Euro Area': 'EUR', 'Japan': 'JPY',
    'United Kingdom': 'GBP', 'Australia': 'AUD', 'Canada': 'CAD',
    'Switzerland': 'CHF', 'New Zealand': 'NZD', 'China': 'CNY',
};

/**
 * News Intelligence Analyzer
 * Fetches economic calendar events from ForexFactory (free, no key) and
 * determines if it's safe to trade given news-risk settings.
 */
export class NewsAnalyzer {
    private lastFetch = 0;
    private cachedEvents: EconomicEvent[] = [];
    private readonly FETCH_INTERVAL = 15 * 60_000; // 15 minutes
    private fetchingPromise: Promise<EconomicEvent[]> | null = null;

    /**
     * Get upcoming economic events. Cached for FETCH_INTERVAL.
     * Concurrent callers share the in-flight promise to avoid request storms.
     */
    public async fetchUpcomingEvents(_apiKey?: string): Promise<EconomicEvent[]> {
        const now = Date.now();
        // 2026-05-02 Fix: Remove '&& this.cachedEvents.length > 0' check so backoff is honored even if cache is empty
        if (now - this.lastFetch < this.FETCH_INTERVAL) {
            return this.cachedEvents;
        }
        if (this.fetchingPromise) return this.fetchingPromise;

        this.fetchingPromise = (async () => {
            try {
                // 2026-05-02 Fix: Fetch sequentially to avoid HTTP 429 from ForexFactory's strict concurrency limits
                const thisWeek = await this.fetchForexFactoryWeek(FF_THIS_WEEK);
                await new Promise(r => setTimeout(r, 1000)); // 1s delay
                const nextWeek = await this.fetchForexFactoryWeek(FF_NEXT_WEEK).catch(() => [] as EconomicEvent[]);
                const events = [...thisWeek, ...nextWeek]
                    .filter((e) => e.time > now - 60 * 60_000) // keep events from last hour onward
                    .sort((a, b) => a.time - b.time);
                this.cachedEvents = events;
                this.lastFetch = now;
                atLog(`[NewsAnalyzer] 📰 Fetched ${events.length} upcoming economic events.`);
                return events;
            } catch (err) {
                atError('[NewsAnalyzer] ❌ Failed to fetch news feed:', err);
                // 2026-05-02 Fix: Apply backoff (15m) on error to prevent hammering the API and staying rate-limited (HTTP 429)
                this.lastFetch = now;
                // Keep stale cache on error so a blip doesn't disable the gate.
                return this.cachedEvents;
            } finally {
                this.fetchingPromise = null;
            }
        })();
        return this.fetchingPromise;
    }

    private async fetchForexFactoryWeek(url: string): Promise<EconomicEvent[]> {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 10_000);
        try {
            const resp = await fetch(url, {
                signal: controller.signal,
                headers: { 'User-Agent': 'PersonalAIBot/1.0 (mt5-core-server)' },
            });
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const raw = await resp.json();
            if (!Array.isArray(raw)) throw new Error('Unexpected JSON shape');
            const events: EconomicEvent[] = [];
            for (const row of raw as any[]) {
                const title = String(row?.title ?? '').trim();
                if (!title) continue;
                const countryRaw = String(row?.country ?? '').trim();
                const currency = COUNTRY_TO_CURRENCY[countryRaw] ?? COUNTRY_TO_CURRENCY[countryRaw.toUpperCase()];
                if (!currency) continue;
                const impactRaw = String(row?.impact ?? 'Low').toLowerCase();
                const impact: 'LOW' | 'MEDIUM' | 'HIGH' =
                    impactRaw === 'high' ? 'HIGH' :
                    impactRaw === 'medium' ? 'MEDIUM' :
                    impactRaw === 'holiday' ? 'LOW' : 'LOW';
                const timeStr = String(row?.date ?? '').trim();
                const time = timeStr ? new Date(timeStr).getTime() : NaN;
                if (!isFinite(time)) continue;
                events.push({ title, impact, currency, time, country: countryRaw });
            }
            return events;
        } finally {
            clearTimeout(timer);
        }
    }

    /**
     * Update the newsRisk config based on upcoming events for a given currency.
     * Called each cycle per quote currency the watchlist depends on.
     */
    public async updateNewsRisk(currency: string, config: AutoTradingConfig): Promise<void> {
        if (!config.newsRisk?.enabled) return;
        const events = await this.fetchUpcomingEvents(config.apiKey);
        const now = Date.now();
        const quote = currency.toUpperCase();

        // Nearest HIGH-impact event on this currency (ALL currencies event also blocks)
        const nearest = events
            .filter((e) => e.impact === 'HIGH' && (e.currency === quote || e.currency === 'ALL'))
            .find((e) => e.time > now - 30 * 60_000); // include events that started up to 30m ago

        if (nearest) {
            const minutesToEvent = Math.round((nearest.time - now) / 60_000);
            config.newsRisk.activeEvent = `${nearest.currency} ${nearest.title}`;
            config.newsRisk.impactLevel = nearest.impact;
            config.newsRisk.minutesToEvent = minutesToEvent;
            if (minutesToEvent >= 0 && minutesToEvent <= 30) {
                atWarn(`[NewsAnalyzer] ⚠️ HIGH IMPACT NEWS SOON: ${nearest.currency} ${nearest.title} in ${minutesToEvent}m`);
            } else if (minutesToEvent < 0 && minutesToEvent >= -30) {
                atWarn(`[NewsAnalyzer] 🔴 HIGH IMPACT EVENT LIVE: ${nearest.currency} ${nearest.title} started ${-minutesToEvent}m ago`);
            }
        } else {
            config.newsRisk.activeEvent = undefined;
            config.newsRisk.impactLevel = undefined;
            config.newsRisk.minutesToEvent = undefined;
        }
    }

    /**
     * Check if trading is blocked due to news risk.
     * Blocks from 30min before to 30min after any HIGH-impact event.
     */
    public isBlockedByNews(config: AutoTradingConfig): { blocked: boolean; reason: string } {
        if (!config.newsRisk?.enabled) return { blocked: false, reason: '' };
        const risk = config.newsRisk;
        if (risk.impactLevel !== 'HIGH') return { blocked: false, reason: '' };
        const m = risk.minutesToEvent ?? 999;
        if (m >= 0 && m <= 30) {
            return { blocked: true, reason: `News Risk: ${risk.activeEvent} starts in ${m}m` };
        }
        if (m < 0 && m >= -30) {
            return { blocked: true, reason: `News Risk: ${risk.activeEvent} started ${-m}m ago (cooldown)` };
        }
        return { blocked: false, reason: '' };
    }

    /**
     * Return the quote-currency set covered by a list of symbols.
     * XAUUSD → {USD}, EURUSD → {EUR,USD}, USDJPY → {USD,JPY}
     */
    public static currenciesForSymbols(symbols: string[]): string[] {
        const out = new Set<string>();
        for (const s of symbols) {
            const u = s.toUpperCase();
            // Metals / crypto: treat as USD-quoted unless ends in another known ISO
            if (u.startsWith('XAU') || u.startsWith('XAG') || u.startsWith('XPT') || u.startsWith('BTC') || u.startsWith('ETH')) {
                out.add('USD');
                const tail = u.slice(3);
                if (/^(EUR|JPY|GBP|AUD|CAD|CHF|NZD|CNY)$/.test(tail)) out.add(tail);
                continue;
            }
            // Index aliases
            if (/^(US30|NAS100|SPX500|US500|DJI|NDX)$/.test(u)) { out.add('USD'); continue; }
            if (/^(GER40|DAX|FDAX)$/.test(u)) { out.add('EUR'); continue; }
            if (/^(UK100|FTSE)$/.test(u)) { out.add('GBP'); continue; }
            if (/^(JPN225|NIKKEI)$/.test(u)) { out.add('JPY'); continue; }
            // Standard 6-letter forex
            if (/^[A-Z]{6}$/.test(u)) {
                out.add(u.slice(0, 3));
                out.add(u.slice(3, 6));
            }
        }
        return [...out];
    }
}

export const newsAnalyzer = new NewsAnalyzer();
