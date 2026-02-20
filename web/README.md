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

## Optional API Keys

Configure these in Settings for enhanced features:

| Key | Free? | Enables |
|-----|-------|---------|
| OpenWeatherMap | Yes | Weather display and bike ranking |
| Google API + Worker URL | No | Accurate transit directions with transfers |

See the [Cloudflare Worker setup](../cloudflare-worker/) for Google Routes proxy deployment.
