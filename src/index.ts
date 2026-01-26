import { serve } from '@hono/node-server';
import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { logger } from 'hono/logger';
import { serveStatic } from '@hono/node-server/serve-static';

import { config } from './config.js';
import commuteRoutes from './routes/commute.js';
import settingsRoutes from './routes/settings.js';
import stationsRoutes from './routes/stations.js';
import { checkTransiterHealth } from './services/transiter.js';

const app = new Hono();

// Middleware
app.use('*', logger());
app.use('*', cors());

// Serve static files (settings UI)
app.use('/static/*', serveStatic({ root: './', rewriteRequestPath: (path) => path.replace('/static', '/web') }));

// API routes
app.route('/api/commute', commuteRoutes);
app.route('/api/settings', settingsRoutes);
app.route('/api/stations', stationsRoutes);

// Health check
app.get('/health', async (c) => {
  const transiterOk = await checkTransiterHealth();

  return c.json({
    status: transiterOk ? 'healthy' : 'degraded',
    transiter: transiterOk ? 'connected' : 'disconnected',
    timestamp: new Date().toISOString(),
  });
});

// Serve main UI at root
app.get('/', (c) => {
  return c.redirect('/static/index.html');
});

// Start server
console.log(`Starting server on port ${config.port}...`);
console.log(`Transiter URL: ${config.transiterUrl}`);
console.log(`Google Maps API: ${config.googleMapsApiKey ? 'configured' : 'not configured'}`);
console.log(`OpenWeather API: ${config.openWeatherApiKey ? 'configured' : 'not configured'}`);

serve({
  fetch: app.fetch,
  port: config.port,
  hostname: config.hostname,
});

console.log(`Server running at http://${config.hostname}:${config.port}`);
console.log(`Settings UI: http://${config.hostname}:${config.port}/`);
console.log(`API endpoints:`);
console.log(`  GET  /api/commute   - Get ranked commute options`);
console.log(`  GET  /api/settings  - Get current settings`);
console.log(`  PUT  /api/settings  - Update settings`);
console.log(`  GET  /api/stations  - Get available stations`);
console.log(`  GET  /health        - Health check`);
