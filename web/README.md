# Spoke Express — Web App

NYC bike + transit commute optimizer. Compare bike, walk, and subway options with real-time train arrivals and weather-aware routing.

## Quick Start

```bash
./start.sh
```

Then configure your home/work locations in Settings.

## Features

- Live train arrivals (MTA GTFS-Realtime)
- Weather-aware ranking (bike demoted on rainy days)
- Service alerts
- Walk + Transit and Bike + Transit options
- Auto-refreshes every 30 seconds

## Files

| File | Description |
|------|-------------|
| `index.html` | Main commute options page |
| `arrivals.html` | Live train times |
| `settings.html` | Configuration |
| `mta-api.js` | MTA GTFS-RT parser |
| `stations.json` | NYC subway stations data |
| `start.sh` | Startup script |

## API Key

Configure your Google API key in Settings. One key enables all features:
- Geocoding (address lookup)
- Weather-aware routing (Google Weather API)
- Accurate transit directions (Google Routes API, via Cloudflare Worker proxy)

The Cloudflare Worker URL is pre-configured with a shared default. To self-host, see the [Cloudflare Worker setup](../cloudflare-worker/).
