import { Hono } from 'hono';
import { getSettings, getStationById } from '../services/data.js';
import { getWeather } from '../services/weather.js';
import { getBikeTime, calculateWalkTime } from '../services/google-routes.js';
import {
  getStopArrivals,
  getNextArrival,
  formatArrivalTime,
  getAllStations,
} from '../services/transiter.js';
import { getTransitRoute } from '../services/routing.js';
import { rankOptions } from '../services/ranking.js';
import type { CommuteOption, CommuteResponse, Leg, Alert } from '../types/index.js';

const MAX_WALK_MINUTES = 30; // Auto-include stations within 30min walk

/**
 * Deduplicate commute options by transit route signature.
 * If multiple options use the same sequence of subway lines, keep only the fastest.
 * This prevents showing "Bike → Station A → G" and "Bike → Station B → G" separately.
 */
function deduplicateByRoute(options: CommuteOption[]): CommuteOption[] {
  const bySignature = new Map<string, CommuteOption>();

  for (const option of options) {
    // Build route signature: type + sequence of subway lines
    const subwayLegs = option.legs.filter((leg) => leg.mode === 'subway' && leg.route);
    const routeSequence = subwayLegs.map((leg) => leg.route).join('→');
    const signature = `${option.type}_${routeSequence}`;

    const existing = bySignature.get(signature);
    if (!existing || option.duration_minutes < existing.duration_minutes) {
      bySignature.set(signature, option);
    }
  }

  return Array.from(bySignature.values());
}

const app = new Hono();

app.get('/', async (c) => {
  const settings = getSettings();
  const options: CommuteOption[] = [];

  // Get weather for home location
  const weather = await getWeather(settings.home.lat, settings.home.lng);

  // Calculate bike-to-transit options
  for (const stationId of settings.bikeToStations) {
    const station = await getStationById(stationId);
    if (!station) continue;

    try {
      // Get bike time from home to station
      const bikeTime = await getBikeTime(settings.home, { lat: station.lat, lng: station.lng });

      // Get transit route from station to work using Google
      const transitRoute = await getTransitRoute(
        { lat: station.lat, lng: station.lng },
        settings.work
      );

      if (!transitRoute || transitRoute.steps.length === 0) {
        console.warn(`No transit route from ${station.name} to work`);
        continue;
      }

      // Get next train arrival at this station from Transiter (live times)
      const arrivals = await getStopArrivals(station.transiterId);
      const nextArrival = getNextArrival(arrivals);

      const totalTime = bikeTime + transitRoute.totalDuration;

      // Build legs from Google's transit route
      const legs: Leg[] = [{ mode: 'bike', duration: bikeTime, to: station.name }];

      // Find first and last transit step indices
      const transitIndices = transitRoute.steps
        .map((s, i) => ({ s, i }))
        .filter(({ s }) => s.mode === 'subway' || s.mode === 'rail')
        .map(({ i }) => i);
      const firstTransitIndex = transitIndices[0] ?? transitRoute.steps.length;
      const lastTransitIndex = transitIndices[transitIndices.length - 1] ?? -1;

      for (let i = 0; i < transitRoute.steps.length; i++) {
        const step = transitRoute.steps[i];
        // Only include transfer walks (between first and last transit legs)
        if (step.mode === 'walk' && step.duration > 1 && i > firstTransitIndex && i < lastTransitIndex) {
          legs.push({
            mode: 'walk',
            duration: step.duration,
            to: step.toStop || 'Transfer',
          });
        } else if (step.mode === 'subway' || step.mode === 'rail') {
          legs.push({
            mode: 'subway',
            duration: step.duration,
            to: step.toStop || 'Destination',
            route: step.route,
          });
        }
      }

      // Build summary
      const transitLegs = transitRoute.steps.filter((s) => s.mode === 'subway' || s.mode === 'rail');
      const routeNames = transitLegs.map((l) => l.route).join(' → ');
      const summary =
        transitRoute.transfers > 0
          ? `Bike → ${station.name} → ${routeNames} (${transitRoute.transfers} transfer${transitRoute.transfers > 1 ? 's' : ''})`
          : `Bike → ${station.name} → ${routeNames}`;

      // Calculate arrival time
      const now = new Date();
      const arrivalTime = new Date(now.getTime() + totalTime * 60000);

      options.push({
        id: `bike_${stationId}`,
        rank: 0,
        type: 'bike_to_transit',
        duration_minutes: totalTime,
        summary,
        legs,
        nextTrain: nextArrival ? formatArrivalTime(nextArrival) : 'N/A',
        arrival_time: arrivalTime.toLocaleTimeString('en-US', {
          hour: 'numeric',
          minute: '2-digit',
          hour12: true,
        }),
        station,
      });
    } catch (error) {
      console.error(`Error calculating bike option for ${stationId}:`, error);
    }
  }

  // Calculate walk-to-transit options (auto-inferred from home location)
  const allStations = await getAllStations();
  const walkableStations = allStations.filter((station) => {
    const walkTime = calculateWalkTime(settings.home, { lat: station.lat, lng: station.lng });
    return walkTime <= MAX_WALK_MINUTES;
  });

  for (const station of walkableStations) {
    try {
      // Calculate walk time from home to station
      const walkTime = calculateWalkTime(settings.home, { lat: station.lat, lng: station.lng });

      // Get transit route from station to work using Google
      const transitRoute = await getTransitRoute(
        { lat: station.lat, lng: station.lng },
        settings.work
      );

      if (!transitRoute || transitRoute.steps.length === 0) {
        continue;
      }

      // Get next train arrival from Transiter (live times)
      const arrivals = await getStopArrivals(station.transiterId);
      const nextArrival = getNextArrival(arrivals);

      const totalTime = walkTime + transitRoute.totalDuration;

      // Build legs
      const legs: Leg[] = [{ mode: 'walk', duration: walkTime, to: station.name }];

      // Find first and last transit step indices
      const transitIndices = transitRoute.steps
        .map((s, i) => ({ s, i }))
        .filter(({ s }) => s.mode === 'subway' || s.mode === 'rail')
        .map(({ i }) => i);
      const firstTransitIndex = transitIndices[0] ?? transitRoute.steps.length;
      const lastTransitIndex = transitIndices[transitIndices.length - 1] ?? -1;

      for (let i = 0; i < transitRoute.steps.length; i++) {
        const step = transitRoute.steps[i];
        // Only include transfer walks (between first and last transit legs)
        if (step.mode === 'walk' && step.duration > 1 && i > firstTransitIndex && i < lastTransitIndex) {
          legs.push({
            mode: 'walk',
            duration: step.duration,
            to: step.toStop || 'Transfer',
          });
        } else if (step.mode === 'subway' || step.mode === 'rail') {
          legs.push({
            mode: 'subway',
            duration: step.duration,
            to: step.toStop || 'Destination',
            route: step.route,
          });
        }
      }

      // Build summary
      const transitLegs = transitRoute.steps.filter((s) => s.mode === 'subway' || s.mode === 'rail');
      const routeNames = transitLegs.map((l) => l.route).join(' → ');
      const summary =
        transitRoute.transfers > 0
          ? `Walk → ${station.name} → ${routeNames} (${transitRoute.transfers} transfer${transitRoute.transfers > 1 ? 's' : ''})`
          : `Walk → ${station.name} → ${routeNames}`;

      const now = new Date();
      const arrivalTime = new Date(now.getTime() + totalTime * 60000);

      options.push({
        id: `walk_${station.id}`,
        rank: 0,
        type: 'transit_only',
        duration_minutes: totalTime,
        summary,
        legs,
        nextTrain: nextArrival ? formatArrivalTime(nextArrival) : 'N/A',
        arrival_time: arrivalTime.toLocaleTimeString('en-US', {
          hour: 'numeric',
          minute: '2-digit',
          hour12: true,
        }),
        station,
      });
    } catch (error) {
      console.error(`Error calculating walk option for ${station.id}:`, error);
    }
  }

  // Deduplicate options by transit route signature (keep fastest for each unique route)
  const deduplicatedOptions = deduplicateByRoute(options);

  // Rank options (weather-aware)
  const rankedOptions = rankOptions(deduplicatedOptions, weather);

  // TODO: Fetch real MTA alerts
  const alerts: Alert[] = [];

  const response: CommuteResponse = {
    options: rankedOptions,
    weather,
    alerts,
    generated_at: new Date().toISOString(),
  };

  return c.json(response);
});

export default app;
