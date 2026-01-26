import { Hono } from 'hono';
import { getStations, getSettings } from '../services/data.js';
import { calculateWalkTime } from '../services/google-routes.js';

const app = new Hono();

// Get all available stations with distance from home
app.get('/', (c) => {
  const stations = getStations();
  const settings = getSettings();

  // Calculate distance from home for each station
  const stationsWithDistance = stations.map((station) => {
    const walkMinutes = calculateWalkTime(settings.home, {
      lat: station.lat,
      lng: station.lng,
    });

    // Rough distance estimate (walking speed is ~3mph, so 20 min = ~1 mile)
    const distanceMiles = (walkMinutes * 3) / 60;

    return {
      id: station.id,
      name: station.name,
      lines: station.lines,
      borough: station.borough,
      distanceFromHome: Math.round(distanceMiles * 10) / 10,
      walkMinutes,
    };
  });

  // Sort by distance
  stationsWithDistance.sort((a, b) => a.distanceFromHome - b.distanceFromHome);

  return c.json({ stations: stationsWithDistance });
});

// Get a specific station
app.get('/:id', (c) => {
  const id = c.req.param('id');
  const stations = getStations();
  const station = stations.find((s) => s.id === id);

  if (!station) {
    return c.json({ error: 'Station not found' }, 404);
  }

  return c.json(station);
});

export default app;
