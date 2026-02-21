/**
 * Cloudflare Worker - Google API Proxy (Transit + Weather)
 * Bypasses CORS restrictions for browser requests
 * Uses the newer Routes API instead of legacy Directions API
 *
 * Endpoints:
 *   GET /directions - Google Routes API proxy
 *   GET /weather    - Google Weather API proxy
 *
 * Deploy: npx wrangler deploy
 */

export default {
  async fetch(request) {
    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type',
          'Access-Control-Max-Age': '86400',
        },
      });
    }

    try {
      const url = new URL(request.url);

      // Route to appropriate handler
      if (url.pathname === '/directions') {
        return await handleDirections(url);
      }

      if (url.pathname === '/weather') {
        return await handleWeather(url);
      }

      // 404 for unknown routes
      return new Response(JSON.stringify({ error: 'Not found' }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      });

    } catch (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 500,
        headers: corsHeaders({ 'Content-Type': 'application/json' }),
      });
    }
  },
};

// ─── /directions handler (unchanged) ─────────────────────────────────────────

async function handleDirections(url) {
  // Get parameters
  const origin = url.searchParams.get('origin');
  const destination = url.searchParams.get('destination');
  const mode = url.searchParams.get('mode') || 'transit';
  const departureTime = url.searchParams.get('departure_time');
  const apiKey = url.searchParams.get('key');

  if (!origin || !destination) {
    return new Response(JSON.stringify({ error: 'Missing origin or destination' }), {
      status: 400,
      headers: corsHeaders({ 'Content-Type': 'application/json' }),
    });
  }

  if (!apiKey) {
    return new Response(JSON.stringify({ error: 'Missing API key' }), {
      status: 400,
      headers: corsHeaders({ 'Content-Type': 'application/json' }),
    });
  }

  // Parse origin/destination (format: "lat,lng")
  const [originLat, originLng] = origin.split(',').map(Number);
  const [destLat, destLng] = destination.split(',').map(Number);

  // Build Routes API request body
  const requestBody = {
    origin: {
      location: {
        latLng: { latitude: originLat, longitude: originLng }
      }
    },
    destination: {
      location: {
        latLng: { latitude: destLat, longitude: destLng }
      }
    },
    travelMode: mode.toUpperCase(),
    computeAlternativeRoutes: false,
  };

  // Add departure time if specified
  if (departureTime && departureTime !== 'now') {
    requestBody.departureTime = new Date(parseInt(departureTime) * 1000).toISOString();
  } else {
    // Default to now
    requestBody.departureTime = new Date().toISOString();
  }

  // Add transit preferences if transit mode
  if (mode.toLowerCase() === 'transit') {
    requestBody.transitPreferences = {
      routingPreference: 'FEWER_TRANSFERS'
    };
  }

  // Call Google Routes API
  const response = await fetch('https://routes.googleapis.com/directions/v2:computeRoutes', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Goog-Api-Key': apiKey,
      'X-Goog-FieldMask': 'routes.duration,routes.distanceMeters,routes.legs.steps.transitDetails,routes.legs.duration,routes.legs.distanceMeters'
    },
    body: JSON.stringify(requestBody)
  });

  const data = await response.json();

  // Extract relevant info
  const result = parseRoutesResponse(data);

  return new Response(JSON.stringify(result), {
    headers: corsHeaders({ 'Content-Type': 'application/json' }),
  });
}

// ─── /weather handler ────────────────────────────────────────────────────────

async function handleWeather(url) {
  const lat = url.searchParams.get('lat');
  const lng = url.searchParams.get('lng');
  const apiKey = url.searchParams.get('key');

  // Input validation
  if (!apiKey) {
    return new Response(JSON.stringify({ error: 'Missing API key' }), {
      status: 400,
      headers: corsHeaders({ 'Content-Type': 'application/json' }),
    });
  }

  if (lat == null || lng == null) {
    return new Response(JSON.stringify({ error: 'Missing lat or lng' }), {
      status: 400,
      headers: corsHeaders({ 'Content-Type': 'application/json' }),
    });
  }

  const latNum = Number(lat);
  const lngNum = Number(lng);

  if (isNaN(latNum) || isNaN(lngNum)) {
    return new Response(JSON.stringify({ error: 'lat and lng must be valid numbers' }), {
      status: 400,
      headers: corsHeaders({ 'Content-Type': 'application/json' }),
    });
  }

  // Call Google Weather API
  const weatherUrl = `https://weather.googleapis.com/v1/currentConditions:lookup?key=${encodeURIComponent(apiKey)}&location.latitude=${latNum}&location.longitude=${lngNum}&unitsSystem=IMPERIAL`;

  const response = await fetch(weatherUrl);
  const data = await response.json();

  // Handle API errors
  if (data.error) {
    return new Response(JSON.stringify({
      error: data.error.message || 'Weather API error',
      code: data.error.code,
    }), {
      status: data.error.code || 502,
      headers: corsHeaders({ 'Content-Type': 'application/json' }),
    });
  }

  // Normalize response
  const result = parseWeatherResponse(data);

  return new Response(JSON.stringify(result), {
    headers: corsHeaders({ 'Content-Type': 'application/json' }),
  });
}

// ─── Shared helpers ──────────────────────────────────────────────────────────

function corsHeaders(headers = {}) {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    ...headers,
  };
}

function parseWeatherResponse(data) {
  const conditions = data.weatherCondition?.description?.text ?? 'Unknown';
  const precipitationType = data.precipitation?.type ?? 'NONE';
  const precipitationProbability = data.precipitation?.probability?.percent ?? 0;

  return {
    tempF: data.temperature?.degrees ?? null,
    conditions,
    precipitationType,
    precipitationProbability,
    isBad: mapGoogleWeatherToIsBad(conditions, precipitationType, precipitationProbability),
  };
}

function mapGoogleWeatherToIsBad(condition, precipType, precipProbability) {
  const normalizedCondition = normalizeWeatherValue(condition);
  const normalizedPrecipType = normalizeWeatherValue(precipType);

  if (normalizedPrecipType === 'RAIN' || normalizedPrecipType === 'SNOW' || normalizedPrecipType === 'MIX' || normalizedPrecipType === 'SLEET') {
    return true;
  }

  if (normalizedPrecipType === 'NONE') {
    return normalizedCondition.includes('RAIN') || normalizedCondition.includes('SNOW');
  }

  if (containsBadConditionKeyword(normalizedCondition)) {
    return true;
  }

  if (typeof precipProbability === 'number' && precipProbability >= 50) {
    return true;
  }

  return false;
}

function containsBadConditionKeyword(condition) {
  return [
    'RAIN',
    'SNOW',
    'STORM',
    'SLEET',
    'HAIL',
    'DRIZZLE',
    'THUNDERSTORM',
  ].some((keyword) => condition.includes(keyword));
}

function normalizeWeatherValue(value) {
  if (typeof value !== 'string') {
    return '';
  }
  return value.trim().toUpperCase();
}

function parseRoutesResponse(data) {
  if (data.error) {
    return {
      status: 'ERROR',
      error: data.error.message || 'API error',
      durationMinutes: null,
    };
  }

  if (!data.routes?.length) {
    return {
      status: 'ZERO_RESULTS',
      error: 'No routes found',
      durationMinutes: null,
    };
  }

  const route = data.routes[0];
  const leg = route.legs?.[0];

  // Parse duration (format: "1234s")
  const durationSeconds = parseInt(route.duration?.replace('s', '') || '0');
  const durationMinutes = Math.round(durationSeconds / 60);

  // Extract transit details from steps
  const steps = leg?.steps || [];
  const transitSteps = steps
    .filter(s => s.transitDetails)
    .map(s => {
      const td = s.transitDetails;
      // Calculate duration from departure/arrival times
      let duration = null;
      if (td.stopDetails?.departureTime && td.stopDetails?.arrivalTime) {
        const dep = new Date(td.stopDetails.departureTime).getTime();
        const arr = new Date(td.stopDetails.arrivalTime).getTime();
        duration = Math.round((arr - dep) / 60000); // minutes
      }
      return {
        line: td.transitLine?.nameShort || td.transitLine?.name,
        vehicle: td.transitLine?.vehicle?.type,
        departureStop: td.stopDetails?.departureStop?.name,
        arrivalStop: td.stopDetails?.arrivalStop?.name,
        numStops: td.stopCount,
        duration,
        departureTime: td.stopDetails?.departureTime,
        arrivalTime: td.stopDetails?.arrivalTime,
      };
    });

  // Calculate distance
  const distanceMeters = route.distanceMeters || 0;
  const distanceMiles = (distanceMeters / 1609.34).toFixed(1);

  return {
    status: 'OK',
    durationMinutes,
    distance: `${distanceMiles} mi`,
    transitSteps,
  };
}
