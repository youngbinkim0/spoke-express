import { Hono } from 'hono';
import { getSettings, getStationById } from '../services/data.js';
import { getWeather } from '../services/weather.js';
import { getBikeTime, calculateWalkTime } from '../services/google-routes.js';
import {
  getStopArrivals,
  getNextArrival,
  formatArrivalTime,
  estimateTransitTime,
} from '../services/transiter.js';
import { rankOptions } from '../services/ranking.js';
import type { CommuteOption, CommuteResponse, Leg, Alert } from '../types/index.js';

const app = new Hono();

app.get('/', async (c) => {
  const settings = getSettings();
  const options: CommuteOption[] = [];

  // Get weather for home location
  const weather = await getWeather(settings.home.lat, settings.home.lng);

  // Get destination station
  const destStation = await getStationById(settings.destinationStation);
  if (!destStation) {
    return c.json({ error: 'Destination station not configured' }, 400);
  }

  // Calculate bike-to-transit options
  for (const stationId of settings.bikeToStations) {
    const station = await getStationById(stationId);
    if (!station) continue;

    try {
      // Get bike time from home to station
      const bikeTime = await getBikeTime(settings.home, { lat: station.lat, lng: station.lng });

      // Get next train arrival at this station
      const arrivals = await getStopArrivals(station.transiterId);
      const nextArrival = getNextArrival(arrivals);

      // Estimate transit time to destination
      const transitTime = estimateTransitTime(station.transiterId, destStation.transiterId);

      // Walk time from destination station to work
      const walkToWork = calculateWalkTime({ lat: destStation.lat, lng: destStation.lng }, settings.work);

      const totalTime = bikeTime + transitTime + walkToWork;

      const legs: Leg[] = [
        { mode: 'bike', duration: bikeTime, to: station.name },
        {
          mode: 'subway',
          duration: transitTime,
          to: destStation.name,
          route: station.lines[0],
        },
        { mode: 'walk', duration: walkToWork, to: 'Work' },
      ];

      // Calculate arrival time
      const now = new Date();
      const arrivalTime = new Date(now.getTime() + totalTime * 60000);

      options.push({
        id: `bike_${stationId}`,
        rank: 0, // Will be set by ranking
        type: 'bike_to_transit',
        duration_minutes: totalTime,
        summary: `Bike → ${station.name} → ${station.lines[0]}`,
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

  // Calculate walk-to-transit options
  for (const stationId of settings.walkToStations) {
    const station = await getStationById(stationId);
    if (!station) continue;

    try {
      // Calculate walk time from home to station
      const walkTime = calculateWalkTime(settings.home, { lat: station.lat, lng: station.lng });

      // Get next train arrival
      const arrivals = await getStopArrivals(station.transiterId);
      const nextArrival = getNextArrival(arrivals);

      // Estimate transit time
      const transitTime = estimateTransitTime(station.transiterId, destStation.transiterId);

      // Walk time from destination station to work
      const walkToWork = calculateWalkTime({ lat: destStation.lat, lng: destStation.lng }, settings.work);

      const totalTime = walkTime + transitTime + walkToWork;

      const legs: Leg[] = [
        { mode: 'walk', duration: walkTime, to: station.name },
        {
          mode: 'subway',
          duration: transitTime,
          to: destStation.name,
          route: station.lines[0],
        },
        { mode: 'walk', duration: walkToWork, to: 'Work' },
      ];

      const now = new Date();
      const arrivalTime = new Date(now.getTime() + totalTime * 60000);

      options.push({
        id: `walk_${stationId}`,
        rank: 0,
        type: 'transit_only',
        duration_minutes: totalTime,
        summary: `Walk → ${station.name} → ${station.lines[0]}`,
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
      console.error(`Error calculating walk option for ${stationId}:`, error);
    }
  }

  // Rank options (weather-aware)
  const rankedOptions = rankOptions(options, weather);

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
