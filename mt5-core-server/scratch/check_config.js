import { config } from './src/config.js';
console.log('PORT:', config.port);
console.log('CORS_ORIGIN:', config.corsOrigins);
console.log('DB_PATH:', config.dbPath);
