import { config } from '../config.js';
import type { TransiterStopResponse, TransiterArrival, Station } from '../types/index.js';
import NodeCache from 'node-cache';

const cache = new NodeCache({ stdTTL: config.cacheTtl.arrivals });
const stationsCache = new NodeCache({ stdTTL: 3600 }); // Cache stations for 1 hour

interface TransiterStop {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  type: string;
  serviceMaps?: Array<{
    configId: string;
    routes?: Array<{ id: string; color?: string }>;
  }>;
}

interface TransiterStopsResponse {
  stops: TransiterStop[];
  nextId?: string;
}

// Fetch all stations from Transiter (with pagination)
export async function getAllStations(): Promise<Station[]> {
  const cacheKey = 'all_stations';
  const cached = stationsCache.get<Station[]>(cacheKey);
  if (cached) return cached;

  const stations: Station[] = [];
  let nextId: string | undefined;

  try {
    do {
      const url = nextId
        ? `${config.transiterUrl}/systems/${config.transitSystem}/stops?limit=100&first_id=${nextId}`
        : `${config.transiterUrl}/systems/${config.transitSystem}/stops?limit=100`;

      const response = await fetch(url);
      if (!response.ok) {
        console.error(`Transiter stops error: ${response.status}`);
        break;
      }

      const data = (await response.json()) as TransiterStopsResponse;

      for (const stop of data.stops) {
        // Only include STATION type (not individual platforms like G33N, G33S)
        if (stop.type !== 'STATION') continue;

        // Get routes from serviceMaps (use 'alltimes' or first available)
        const serviceMap = stop.serviceMaps?.find((sm) => sm.configId === 'alltimes') || stop.serviceMaps?.[0];
        const lines = serviceMap?.routes?.map((r) => r.id) || [];

        // Determine borough based on coordinates (rough approximation)
        const borough = getBoroughFromCoords(stop.latitude, stop.longitude);

        stations.push({
          id: stop.id.toLowerCase(),
          name: stop.name,
          transiterId: stop.id,
          lines,
          lat: stop.latitude,
          lng: stop.longitude,
          borough,
        });
      }

      nextId = data.nextId;
    } while (nextId);

    stationsCache.set(cacheKey, stations);
    console.log(`Loaded ${stations.length} stations from Transiter`);
    return stations;
  } catch (error) {
    console.error('Failed to fetch stations from Transiter:', error);
    return [];
  }
}

// Rough borough determination based on coordinates
function getBoroughFromCoords(lat: number, lng: number): string {
  // Manhattan: roughly lat > 40.7 and lng > -74.02 and lng < -73.93
  if (lat > 40.7 && lng > -74.02 && lng < -73.93) return 'Manhattan';
  // Brooklyn: lat < 40.7 and lng > -74.04 and lng < -73.85
  if (lat < 40.71 && lng > -74.04 && lng < -73.85) return 'Brooklyn';
  // Queens: lat > 40.7 and lng > -73.93
  if (lat > 40.7 && lng > -73.96) return 'Queens';
  // Bronx: lat > 40.8
  if (lat > 40.8) return 'Bronx';
  // Staten Island (no subway, but just in case)
  if (lng < -74.05) return 'Staten Island';
  return 'Brooklyn'; // Default
}

export async function getStopArrivals(stopId: string): Promise<TransiterArrival[]> {
  const cacheKey = `arrivals_${stopId}`;
  const cached = cache.get<TransiterArrival[]>(cacheKey);
  if (cached) return cached;

  const url = `${config.transiterUrl}/systems/${config.transitSystem}/stops/${stopId}`;

  try {
    const response = await fetch(url);
    if (!response.ok) {
      console.error(`Transiter error: ${response.status} for stop ${stopId}`);
      return [];
    }

    const data = (await response.json()) as TransiterStopResponse;
    const arrivals = data.stopTimes || [];

    cache.set(cacheKey, arrivals);
    return arrivals;
  } catch (error) {
    console.error(`Failed to fetch arrivals for stop ${stopId}:`, error);
    return [];
  }
}

// Parse Transiter timestamp (Unix seconds as string) to milliseconds
function parseArrivalTime(timeStr: string): number {
  return parseInt(timeStr, 10) * 1000;
}

export function getNextArrival(arrivals: TransiterArrival[], direction?: string): TransiterArrival | null {
  const now = Date.now();

  const validArrivals = arrivals.filter((a) => {
    const arrivalTime = parseArrivalTime(a.arrival.time);
    // Only include arrivals in the future (with 1 min buffer)
    if (arrivalTime < now - 60000) return false;
    // Filter by direction if specified
    if (direction && a.trip.direction !== direction) return false;
    return true;
  });

  if (validArrivals.length === 0) return null;

  // Sort by arrival time and return the first
  validArrivals.sort((a, b) => {
    return parseArrivalTime(a.arrival.time) - parseArrivalTime(b.arrival.time);
  });

  return validArrivals[0];
}

export function formatArrivalTime(arrival: TransiterArrival): string {
  const date = new Date(parseArrivalTime(arrival.arrival.time));
  return date.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  });
}

export function getMinutesUntilArrival(arrival: TransiterArrival): number {
  const now = Date.now();
  const arrivalTime = parseArrivalTime(arrival.arrival.time);
  return Math.max(0, Math.round((arrivalTime - now) / 60000));
}

// Estimate transit time between two stops (simplified - uses average)
// In a full implementation, you'd calculate this from GTFS schedule data
export function estimateTransitTime(fromStopId: string, toStopId: string): number {
  // Rough estimates based on NYC subway (about 2-3 min per stop on average)
  // This is a simplification - real implementation would use Transiter's trip data
  const avgMinutesPerStop = 2.5;

  // For now, return a reasonable default based on common routes
  // You can expand this with actual stop-to-stop times
  const knownRoutes: Record<string, number> = {
    'G33_G22': 18, // Bedford-Nostrand to Court Sq (G)
    'G34_G22': 16, // Classon to Court Sq (G)
    'G35_G22': 14, // Clinton-Washington to Court Sq (G)
    'G36_G22': 12, // Fulton to Court Sq (G)
    'A42_G22': 15, // Hoyt-Schermerhorn to Court Sq
  };

  const key = `${fromStopId}_${toStopId}`;
  if (knownRoutes[key]) {
    return knownRoutes[key];
  }

  // Default estimate
  return 15;
}

export async function checkTransiterHealth(): Promise<boolean> {
  try {
    const response = await fetch(`${config.transiterUrl}/systems/${config.transitSystem}`);
    return response.ok;
  } catch {
    return false;
  }
}
