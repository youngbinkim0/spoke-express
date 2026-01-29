/**
 * Cloudflare Worker - Google Directions API Proxy
 * Bypasses CORS restrictions for browser requests
 *
 * Deploy: npx wrangler deploy
 * Set secret: npx wrangler secret put GOOGLE_API_KEY
 */

export default {
  async fetch(request, env) {
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

      // Only allow /directions endpoint
      if (url.pathname !== '/directions') {
        return new Response(JSON.stringify({ error: 'Not found' }), {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      // Get parameters
      const origin = url.searchParams.get('origin');
      const destination = url.searchParams.get('destination');
      const mode = url.searchParams.get('mode') || 'transit';
      const departureTime = url.searchParams.get('departure_time') || 'now';

      if (!origin || !destination) {
        return new Response(JSON.stringify({ error: 'Missing origin or destination' }), {
          status: 400,
          headers: corsHeaders({ 'Content-Type': 'application/json' }),
        });
      }

      // Call Google Directions API
      const googleUrl = new URL('https://maps.googleapis.com/maps/api/directions/json');
      googleUrl.searchParams.set('origin', origin);
      googleUrl.searchParams.set('destination', destination);
      googleUrl.searchParams.set('mode', mode);
      googleUrl.searchParams.set('departure_time', departureTime);
      googleUrl.searchParams.set('key', env.GOOGLE_API_KEY);

      const response = await fetch(googleUrl.toString());
      const data = await response.json();

      // Extract relevant info
      const result = parseDirectionsResponse(data);

      return new Response(JSON.stringify(result), {
        headers: corsHeaders({ 'Content-Type': 'application/json' }),
      });

    } catch (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 500,
        headers: corsHeaders({ 'Content-Type': 'application/json' }),
      });
    }
  },
};

function corsHeaders(headers = {}) {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    ...headers,
  };
}

function parseDirectionsResponse(data) {
  if (data.status !== 'OK' || !data.routes?.length) {
    return {
      status: data.status,
      error: data.error_message || 'No routes found',
      durationMinutes: null,
    };
  }

  const route = data.routes[0];
  const leg = route.legs[0];

  // Extract transit details
  const steps = leg.steps || [];
  const transitSteps = steps
    .filter(s => s.travel_mode === 'TRANSIT')
    .map(s => ({
      line: s.transit_details?.line?.short_name || s.transit_details?.line?.name,
      vehicle: s.transit_details?.line?.vehicle?.type,
      departureStop: s.transit_details?.departure_stop?.name,
      arrivalStop: s.transit_details?.arrival_stop?.name,
      numStops: s.transit_details?.num_stops,
      duration: Math.round(s.duration?.value / 60),
    }));

  return {
    status: 'OK',
    durationMinutes: Math.round(leg.duration?.value / 60),
    durationInTraffic: leg.duration_in_traffic
      ? Math.round(leg.duration_in_traffic.value / 60)
      : null,
    distance: leg.distance?.text,
    departureTime: leg.departure_time?.text,
    arrivalTime: leg.arrival_time?.text,
    summary: route.summary,
    transitSteps,
  };
}
