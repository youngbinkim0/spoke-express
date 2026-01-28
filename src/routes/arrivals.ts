import { Hono } from 'hono';
import { getSettings } from '../services/data.js';
import {
  getStopArrivals,
  getAllStations,
  formatArrivalTime,
  getMinutesUntilArrival,
} from '../services/transiter.js';
import type { ArrivalsResponse, StationArrivals, ArrivalGroup, TransiterArrival } from '../types/index.js';

const app = new Hono();

// Get live train arrivals for configured stations
app.get('/', async (c) => {
  const settings = getSettings();
  const liveStationIds = settings.liveTrainStations || [];

  if (liveStationIds.length === 0) {
    return c.json({
      stations: [],
      generatedAt: new Date().toISOString(),
      message: 'No stations configured. Go to Settings to select up to 3 stations.',
    });
  }

  const allStations = await getAllStations();
  const stationArrivals: StationArrivals[] = [];

  for (const stationId of liveStationIds.slice(0, 3)) {
    const station = allStations.find(
      (s) => s.transiterId === stationId || s.id === stationId.toLowerCase()
    );

    if (!station) {
      console.warn(`Station not found: ${stationId}`);
      continue;
    }

    // Fetch arrivals from Transiter
    const arrivals = await getStopArrivals(station.transiterId);

    // Group by line and direction
    const groups = groupArrivals(arrivals);

    stationArrivals.push({
      station: {
        id: station.transiterId,
        name: station.name,
        lines: station.lines,
      },
      groups,
    });
  }

  const response: ArrivalsResponse = {
    stations: stationArrivals,
    generatedAt: new Date().toISOString(),
  };

  return c.json(response);
});

// Group arrivals by line and direction, return top 3 per group
function groupArrivals(arrivals: TransiterArrival[]): ArrivalGroup[] {
  const now = Date.now();

  // Filter out past arrivals
  const validArrivals = arrivals.filter((a) => {
    const arrivalTime = parseInt(a.arrival.time, 10) * 1000;
    return arrivalTime > now - 60000; // Within 1 min buffer
  });

  // Group by route + direction (from stop.id suffix like F24N, F24S)
  const groupMap = new Map<string, { arrivals: TransiterArrival[]; headsign: string }>();

  for (const arrival of validArrivals) {
    const line = arrival.trip.route.id;
    // Get direction from stop.id suffix (e.g., F24N -> N, F24S -> S)
    const stopId = arrival.stop?.id || '';
    const directionSuffix = stopId.slice(-1);
    const direction = directionSuffix === 'N' || directionSuffix === 'S' ? directionSuffix : 'U';
    const key = `${line}_${direction}`;

    // Get headsign from destination name (terminal station)
    const headsign =
      arrival.destination?.name ||
      arrival.trip.destination?.name ||
      arrival.headsign ||
      (direction === 'N' ? 'Northbound' : direction === 'S' ? 'Southbound' : 'Unknown');

    if (!groupMap.has(key)) {
      groupMap.set(key, { arrivals: [], headsign });
    }

    groupMap.get(key)!.arrivals.push(arrival);
  }

  // Convert to array and sort
  const groups: ArrivalGroup[] = [];

  for (const [key, value] of groupMap) {
    const [line, direction] = key.split('_');

    // Sort arrivals by time and take top 3
    value.arrivals.sort((a, b) => {
      return parseInt(a.arrival.time, 10) - parseInt(b.arrival.time, 10);
    });

    const topArrivals = value.arrivals.slice(0, 3).map((a) => ({
      minutesAway: getMinutesUntilArrival(a),
      time: formatArrivalTime(a),
    }));

    if (topArrivals.length > 0) {
      groups.push({
        line,
        direction,
        headsign: value.headsign,
        arrivals: topArrivals,
      });
    }
  }

  // Sort groups by line name, then by direction (N before S)
  groups.sort((a, b) => {
    const lineCompare = a.line.localeCompare(b.line);
    if (lineCompare !== 0) return lineCompare;
    return a.direction.localeCompare(b.direction);
  });

  return groups;
}

export default app;
