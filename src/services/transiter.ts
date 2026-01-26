import { config } from '../config.js';
import type { TransiterStopResponse, TransiterArrival } from '../types/index.js';
import NodeCache from 'node-cache';

const cache = new NodeCache({ stdTTL: config.cacheTtl.arrivals });

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
