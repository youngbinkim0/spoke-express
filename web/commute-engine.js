// commute-engine.js — Shared computation functions and constants for Spoke Express

const STORAGE_KEY = 'commuteOptimizerSettings';
const WORKER_URL = 'https://commute-directions.xmicroby.workers.dev';

const lineColors = {
  '1': '#EE352E', '2': '#EE352E', '3': '#EE352E',
  '4': '#00933C', '5': '#00933C', '6': '#00933C',
  '7': '#B933AD',
  'A': '#0039A6', 'C': '#0039A6', 'E': '#0039A6',
  'B': '#FF6319', 'D': '#FF6319', 'F': '#FF6319', 'M': '#FF6319',
  'G': '#6CBE45',
  'J': '#996633', 'Z': '#996633',
  'L': '#A7A9AC',
  'N': '#FCCC0A', 'Q': '#FCCC0A', 'R': '#FCCC0A', 'W': '#FCCC0A',
  'S': '#808183',
};

function loadSettings() {
  const saved = localStorage.getItem(STORAGE_KEY);
  if (!saved) return null;

  try {
    const parsed = JSON.parse(saved);
    if (!parsed || typeof parsed !== 'object') return null;

    const { apiKey, ...settings } = parsed;
    if (apiKey !== undefined) {
      if (!settings.googleKey && typeof apiKey === 'string' && apiKey.trim()) {
        settings.googleKey = apiKey;
      }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    }

    return settings;
  } catch (e) {
    return null;
  }
}

let STATIONS = [];

async function loadStations() {
  try {
    const response = await fetch('stations.json');
    const data = await response.json();
    STATIONS = data.stations || data;  // Handle wrapped or raw array format
  } catch (e) {
    console.error('Failed to load stations:', e);
    STATIONS = [];
  }
}

function getStation(id) {
  return STATIONS.find(s => s.id === id);
}

function findNearestStation(lat, lng) {
  if (!lat || !lng || STATIONS.length === 0) return null;
  let nearest = null;
  let minDist = Infinity;
  for (const station of STATIONS) {
    const dist = calculateDistance(lat, lng, station.lat, station.lng);
    if (dist < minDist) {
      minDist = dist;
      nearest = station;
    }
  }
  return nearest;
}

function calculateDistance(lat1, lng1, lat2, lng2) {
  const R = 3959;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLng/2) * Math.sin(dLng/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
}

function estimateBikeTime(fromLat, fromLng, toLat, toLng) {
  const distance = calculateDistance(fromLat, fromLng, toLat, toLng);
  const BIKE_SPEED_MPH = 10;
  return Math.ceil((distance / BIKE_SPEED_MPH) * 60 * 1.3);
}

function estimateWalkTime(fromLat, fromLng, toLat, toLng) {
  const distance = calculateDistance(fromLat, fromLng, toLat, toLng);
  const WALK_SPEED_MPH = 3;
  return Math.ceil((distance / WALK_SPEED_MPH) * 60 * 1.2);
}

async function fetchWeather(lat, lng, apiKey) {
  if (!apiKey) {
    return { tempF: null, conditions: 'No API key', precipProb: 0, isBad: false };
  }

  try {
    const url = `${WORKER_URL}/weather?lat=${lat}&lng=${lng}&key=${encodeURIComponent(apiKey)}`;
    const response = await fetch(url);

    if (!response.ok) {
      return { tempF: null, conditions: 'Unknown', precipProb: 0, isBad: false };
    }

    const data = await response.json();
    return {
      tempF: data.tempF,
      conditions: data.conditions,
      precipProb: data.precipitationProbability || 0,
      isBad: data.isBad
    };
  } catch (e) {
    return { tempF: null, conditions: 'Unknown', precipProb: 0, isBad: false };
  }
}

async function fetchTransitRoute(originLat, originLng, destLat, destLng, workerUrl, googleKey) {
  // Require both worker URL and API key
  if (!workerUrl || !googleKey) {
    return { error: 'missing_config' };
  }

  try {
    const origin = `${originLat},${originLng}`;
    const dest = `${destLat},${destLng}`;
    const url = `${workerUrl}/directions?origin=${origin}&destination=${dest}&mode=transit&departure_time=now&key=${googleKey}`;

    const response = await fetch(url);
    if (response.ok) {
      const data = await response.json();
      if (data.status === 'OK' && data.durationMinutes) {
        return {
          duration: data.durationMinutes,
          transitSteps: data.transitSteps || [],
          distance: data.distance
        };
      }
    }
    return { error: 'api_error' };
  } catch (e) {
    console.error('Worker error:', e);
    return { error: 'fetch_error' };
  }
}

async function fetchNextArrival(stationId, lines) {
  // Use MTA API directly via mta-api.js
  if (window.MtaApi) {
    return await window.MtaApi.getNextArrival(stationId, lines || []);
  }
  return { nextTrain: '--', arrivalTime: '--', routeId: null };
}

function rankOptions(options, weather) {
  let sorted = [...options].sort((a, b) => a.duration - b.duration);

  if (weather.isBad) {
    const bikeIdx = sorted.findIndex(o => o.type === 'bike_to_transit');
    const transitIdx = sorted.findIndex(o => o.type === 'transit_only');

    if (bikeIdx === 0 && transitIdx > 0) {
      const transit = sorted.splice(transitIdx, 1)[0];
      sorted.unshift(transit);
    }
  }

  return sorted.map((o, i) => ({ ...o, rank: i + 1 }));
}

async function geocodeAddress(address, googleKey) {
  const url = `https://maps.googleapis.com/maps/api/geocode/json?address=${encodeURIComponent(address)}&key=${googleKey}`;
  const response = await fetch(url);
  const data = await response.json();
  if (data.status !== 'OK' || !data.results?.length) return null;
  const result = data.results[0];
  return { lat: result.geometry.location.lat, lng: result.geometry.location.lng, formatted: result.formatted_address };
}
