import { Hono } from 'hono';
import { getSettings, saveSettings } from '../services/data.js';
import { geocodeAddress } from '../services/google-routes.js';
import { config } from '../config.js';
import type { Settings } from '../types/index.js';

const app = new Hono();

// Get Google Maps API key for client-side Places Autocomplete
app.get('/maps-key', (c) => {
  if (!config.googleMapsApiKey) {
    return c.json({ error: 'Google Maps API key not configured' }, 500);
  }
  return c.json({ apiKey: config.googleMapsApiKey });
});

// Geocode an address
app.post('/geocode', async (c) => {
  try {
    const { address } = await c.req.json();
    if (!address) {
      return c.json({ error: 'Address is required' }, 400);
    }

    const result = await geocodeAddress(address);
    if (!result) {
      return c.json({ error: 'Could not geocode address' }, 404);
    }

    return c.json(result);
  } catch (error) {
    console.error('Geocoding error:', error);
    return c.json({ error: 'Geocoding failed' }, 500);
  }
});

// Get current settings
app.get('/', (c) => {
  const settings = getSettings();
  return c.json(settings);
});

// Update settings
app.put('/', async (c) => {
  try {
    const body = await c.req.json();
    const currentSettings = getSettings();

    // Merge with existing settings
    const newSettings: Settings = {
      home: body.home ?? currentSettings.home,
      work: body.work ?? currentSettings.work,
      bikeToStations: body.bikeToStations ?? currentSettings.bikeToStations,
      walkToStations: body.walkToStations ?? currentSettings.walkToStations,
      destinationStation: body.destinationStation ?? currentSettings.destinationStation,
    };

    saveSettings(newSettings);

    return c.json({ success: true, settings: newSettings });
  } catch (error) {
    console.error('Error updating settings:', error);
    return c.json({ error: 'Failed to update settings' }, 500);
  }
});

// Partial update (PATCH)
app.patch('/', async (c) => {
  try {
    const body = await c.req.json();
    const currentSettings = getSettings();

    // Only update provided fields
    const newSettings: Settings = { ...currentSettings };

    if (body.home) newSettings.home = { ...currentSettings.home, ...body.home };
    if (body.work) newSettings.work = { ...currentSettings.work, ...body.work };
    if (body.bikeToStations) newSettings.bikeToStations = body.bikeToStations;
    if (body.walkToStations) newSettings.walkToStations = body.walkToStations;
    if (body.destinationStation) newSettings.destinationStation = body.destinationStation;

    saveSettings(newSettings);

    return c.json({ success: true, settings: newSettings });
  } catch (error) {
    console.error('Error updating settings:', error);
    return c.json({ error: 'Failed to update settings' }, 500);
  }
});

export default app;
