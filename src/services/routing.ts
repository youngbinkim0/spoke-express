import { config } from '../config.js';
import NodeCache from 'node-cache';
import type { Location } from '../types/index.js';

const cache = new NodeCache({ stdTTL: 300 }); // Cache routes for 5 minutes

interface TransitStep {
  mode: 'walk' | 'subway' | 'bus' | 'rail';
  duration: number; // minutes
  route?: string;
  routeColor?: string;
  fromStop?: string;
  toStop?: string;
  numStops?: number;
  headsign?: string;
}

export interface TransitRoute {
  steps: TransitStep[];
  totalDuration: number; // minutes
  transfers: number;
  departureTime: string;
  arrivalTime: string;
}

interface GoogleRoutesTransitResponse {
  routes: Array<{
    duration: string;
    legs: Array<{
      steps: Array<{
        staticDuration: string;
        travelMode: string;
        transitDetails?: {
          stopDetails: {
            arrivalStop: { name: string };
            departureStop: { name: string };
            arrivalTime: string;
            departureTime: string;
          };
          transitLine: {
            name: string;
            nameShort?: string;
            color?: string;
            vehicle: { type: string };
          };
          stopCount: number;
          headsign?: string;
        };
      }>;
    }>;
    localizedValues?: {
      duration?: { text: string };
    };
  }>;
}

// Get transit route using Google Routes API
export async function getTransitRoute(
  from: Location,
  to: Location
): Promise<TransitRoute | null> {
  const cacheKey = `transit_${from.lat.toFixed(4)}_${from.lng.toFixed(4)}_${to.lat.toFixed(4)}_${to.lng.toFixed(4)}`;
  const cached = cache.get<TransitRoute>(cacheKey);
  if (cached) return cached;

  if (!config.googleMapsApiKey) {
    console.warn('Google Maps API key not configured for transit routing');
    return null;
  }

  const url = 'https://routes.googleapis.com/directions/v2:computeRoutes';

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Goog-Api-Key': config.googleMapsApiKey,
        'X-Goog-FieldMask':
          'routes.duration,routes.legs.steps.staticDuration,routes.legs.steps.travelMode,routes.legs.steps.transitDetails',
      },
      body: JSON.stringify({
        origin: {
          location: {
            latLng: { latitude: from.lat, longitude: from.lng },
          },
        },
        destination: {
          location: {
            latLng: { latitude: to.lat, longitude: to.lng },
          },
        },
        travelMode: 'TRANSIT',
        transitPreferences: {
          allowedTravelModes: ['SUBWAY', 'RAIL'],
          routingPreference: 'FEWER_TRANSFERS',
        },
      }),
    });

    if (!response.ok) {
      console.error(`Google Routes transit error: ${response.status}`);
      return null;
    }

    const data = (await response.json()) as GoogleRoutesTransitResponse;

    if (!data.routes || data.routes.length === 0) {
      return null;
    }

    const route = data.routes[0];
    const steps: TransitStep[] = [];
    let transfers = -1; // First transit leg is not a transfer

    for (const leg of route.legs) {
      for (const step of leg.steps) {
        const durationSeconds = parseInt(step.staticDuration?.replace('s', '') || '0', 10);
        const durationMinutes = Math.ceil(durationSeconds / 60);

        if (step.travelMode === 'WALK') {
          steps.push({
            mode: 'walk',
            duration: durationMinutes,
          });
        } else if (step.travelMode === 'TRANSIT' && step.transitDetails) {
          const td = step.transitDetails;
          const vehicleType = td.transitLine.vehicle.type;

          // Map Google vehicle types to our modes
          let mode: 'subway' | 'bus' | 'rail' = 'subway';
          if (vehicleType === 'BUS') mode = 'bus';
          else if (vehicleType === 'HEAVY_RAIL' || vehicleType === 'COMMUTER_TRAIN') mode = 'rail';

          // Normalize route name (remove leading/trailing whitespace, etc.)
          let routeName = td.transitLine.nameShort || td.transitLine.name || '';
          routeName = normalizeRouteName(routeName);

          steps.push({
            mode,
            duration: durationMinutes,
            route: routeName,
            routeColor: td.transitLine.color,
            fromStop: td.stopDetails.departureStop.name,
            toStop: td.stopDetails.arrivalStop.name,
            numStops: td.stopCount,
            headsign: td.headsign,
          });

          transfers++;
        }
      }
    }

    // Parse total duration
    const totalDurationSeconds = parseInt(route.duration?.replace('s', '') || '0', 10);
    const totalDuration = Math.ceil(totalDurationSeconds / 60);

    const result: TransitRoute = {
      steps,
      totalDuration,
      transfers: Math.max(0, transfers),
      departureTime: new Date().toISOString(),
      arrivalTime: new Date(Date.now() + totalDuration * 60000).toISOString(),
    };

    cache.set(cacheKey, result);
    return result;
  } catch (error) {
    console.error('Failed to get transit route:', error);
    return null;
  }
}

// Normalize route names for NYC subway
function normalizeRouteName(name: string): string {
  // Remove common prefixes/suffixes
  name = name.trim();

  // Map common variations
  const mappings: Record<string, string> = {
    'Lexington Avenue Express': '4',
    'Lexington Avenue Local': '6',
    'Broadway Express': 'N',
    'Broadway Local': 'R',
    'Eighth Avenue Express': 'A',
    'Eighth Avenue Local': 'C',
    'Sixth Avenue Express': 'B',
    'Sixth Avenue Local': 'F',
    'Crosstown': 'G',
    'Canarsie': 'L',
    'Flushing Express': '7',
    'Flushing Local': '7',
    'Franklin Avenue Shuttle': 'S',
    'Rockaway Park Shuttle': 'S',
    '42nd Street Shuttle': 'S',
  };

  for (const [pattern, replacement] of Object.entries(mappings)) {
    if (name.includes(pattern)) {
      return replacement;
    }
  }

  // If it's already a single letter/number, return as-is
  if (/^[A-Z0-9]$/.test(name)) {
    return name;
  }

  // Extract single letter/number if present
  const match = name.match(/\b([A-Z0-9])\b/);
  if (match) {
    return match[1];
  }

  return name;
}

// For backwards compatibility with old routing
export async function calculateRoute(fromStopId: string, toStopId: string) {
  // This function is deprecated - use getTransitRoute instead
  console.warn('calculateRoute is deprecated, use getTransitRoute with coordinates');
  return null;
}

export function getRouteDisplayName(routeId: string): string {
  return routeId;
}
