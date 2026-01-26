import { config } from '../config.js';
import type { Location } from '../types/index.js';
import NodeCache from 'node-cache';

const cache = new NodeCache({ stdTTL: config.cacheTtl.bikeTime });

interface GoogleRoutesResponse {
  routes: Array<{
    duration: string; // e.g., "480s"
    distanceMeters: number;
  }>;
}

export async function getBikeTime(from: Location, to: Location): Promise<number> {
  const cacheKey = `bike_${from.lat.toFixed(4)}_${from.lng.toFixed(4)}_${to.lat.toFixed(4)}_${to.lng.toFixed(4)}`;
  const cached = cache.get<number>(cacheKey);
  if (cached !== undefined) return cached;

  // If no API key, fall back to distance-based estimate
  if (!config.googleMapsApiKey) {
    const estimate = estimateBikeTimeFromDistance(from, to);
    cache.set(cacheKey, estimate);
    return estimate;
  }

  const url = 'https://routes.googleapis.com/directions/v2:computeRoutes';

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Goog-Api-Key': config.googleMapsApiKey,
        'X-Goog-FieldMask': 'routes.duration,routes.distanceMeters',
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
        travelMode: 'BICYCLE',
      }),
    });

    if (!response.ok) {
      console.error(`Google Routes error: ${response.status}`);
      const estimate = estimateBikeTimeFromDistance(from, to);
      cache.set(cacheKey, estimate);
      return estimate;
    }

    const data: GoogleRoutesResponse = await response.json();

    if (data.routes && data.routes.length > 0) {
      // Duration is returned as "480s" format
      const durationStr = data.routes[0].duration;
      const seconds = parseInt(durationStr.replace('s', ''), 10);
      const minutes = Math.ceil(seconds / 60);

      cache.set(cacheKey, minutes);
      return minutes;
    }
  } catch (error) {
    console.error('Failed to get bike time from Google Routes:', error);
  }

  // Fallback to distance estimate
  const estimate = estimateBikeTimeFromDistance(from, to);
  cache.set(cacheKey, estimate);
  return estimate;
}

function estimateBikeTimeFromDistance(from: Location, to: Location): number {
  const distanceMiles = haversineDistance(from.lat, from.lng, to.lat, to.lng);

  // Add 20% for NYC grid (non-straight routes)
  const adjustedDistance = distanceMiles * 1.2;

  // Calculate time at average biking speed
  const timeHours = adjustedDistance / config.bikingSpeedMph;
  const timeMinutes = Math.ceil(timeHours * 60);

  // Add 2 minutes for locking bike
  return timeMinutes + 2;
}

export function calculateWalkTime(from: Location, to: Location): number {
  const distanceMiles = haversineDistance(from.lat, from.lng, to.lat, to.lng);

  // Add 10% for walking routes
  const adjustedDistance = distanceMiles * 1.1;

  // Calculate time at average walking speed
  const timeHours = adjustedDistance / config.walkingSpeedMph;
  const timeMinutes = Math.ceil(timeHours * 60);

  return timeMinutes;
}

// Haversine formula to calculate distance between two points
function haversineDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 3959; // Earth's radius in miles

  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);

  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return R * c;
}

function toRad(deg: number): number {
  return deg * (Math.PI / 180);
}
