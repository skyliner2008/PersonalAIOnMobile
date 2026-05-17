import fs from 'fs';
import path from 'path';

// MT5 Files directory path (Absolute path to the user's terminal)
const MT5_FILES_DIR = 'C:\\Users\\JOJO\\AppData\\Roaming\\MetaQuotes\\Terminal\\B9CD1E3322764DD9E8845B7C56122B7D\\MQL5\\Files';

export interface EAConfig {
    beEnable: boolean;
    bePips: number;
    beLockPips: number;
    trailEnable: boolean;
    trailStartPips: number;
    trailStepPips: number;
}

export class EAConfigService {
    /**
     * Updates the shared configuration file used by the MT5 Expert Advisor.
     * This allows the AI (Node.js) to dynamically control the zero-latency execution parameters
     * inside MT5 without incurring network delay on every tick.
     */
    static updateConfig(config: EAConfig): void {
        const configPath = path.join(MT5_FILES_DIR, 'jarvis_config.txt');

        const lines = [
            `BE_ENABLE=${config.beEnable ? 1 : 0}`,
            `BE_PIPS=${config.bePips}`,
            `BE_LOCK_PIPS=${config.beLockPips}`,
            `TRAIL_ENABLE=${config.trailEnable ? 1 : 0}`,
            `TRAIL_START_PIPS=${config.trailStartPips}`,
            `TRAIL_STEP_PIPS=${config.trailStepPips}`,
            `LAST_UPDATE=${Date.now()}`
        ];

        try {
            // Write asynchronously or synchronously depending on the main loop.
            // Sync is used here to ensure the file is immediately available if triggered by an acute event.
            fs.writeFileSync(configPath, lines.join('\n'), 'utf8');
            console.log(`[EAConfigService] Synced AI parameters to MT5 EA. BE: ${config.bePips}pips, Trail: ${config.trailStartPips}pips`);
        } catch (err) {
            console.error('[EAConfigService] Failed to write EA config to MT5 Files directory:', err);
        }
    }
}
