import { newsAnalyzer } from '../src/services/auto/analyzers/news.js';

async function test() {
    console.log('--- Testing NewsAnalyzer ---');
    try {
        const events = await newsAnalyzer.fetchUpcomingEvents();
        console.log(`Fetched ${events.length} events.`);
        if (events.length > 0) {
            console.log('First event:', events[0]);
        }
        
        const currencies = newsAnalyzer.currenciesForSymbols(['XAUUSD', 'EURUSD', 'USDJPY']);
        console.log('Currencies for [XAUUSD, EURUSD, USDJPY]:', currencies);
    } catch (err) {
        console.error('Test failed:', err);
    }
}

test();
