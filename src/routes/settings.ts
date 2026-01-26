import { Hono } from 'hono';
import { getSettings, saveSettings } from '../services/data.js';
import type { Settings } from '../types/index.js';

const app = new Hono();

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
