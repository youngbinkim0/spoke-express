# Spoke Express — Web App

Local development files for the Spoke Express web app.

> **For general usage, setup, and API key instructions, see the [main README](../README.md).**
>
> **Live site: [https://youngbinkim0.github.io/spoke-express/](https://youngbinkim0.github.io/spoke-express/)**

## Quick Start

```bash
./start.sh
```

Then configure your home/work locations in Settings. See [QUICKSTART.md](QUICKSTART.md) for details.

## Features

- Live train arrivals (MTA GTFS-Realtime)
- Weather-aware ranking (bike demoted on rainy days)
- Service alerts
- Walk + Transit and Bike + Transit options
- Installable as PWA (Add to Home Screen)
- Auto-refreshes every 30 seconds

## Files

| File | Description |
|------|-------------|
| `index.html` | Main commute options page (includes SEO/PWA meta tags) |
| `arrivals.html` | Live train times (includes SEO/PWA meta tags) |
| `settings.html` | Configuration (includes SEO/PWA meta tags) |
| `mta-api.js` | MTA GTFS-RT parser |
| `stations.json` | NYC subway stations data |
| `manifest.json` | PWA manifest (app name, icons, display mode) |
| `icon-192.png` | PWA icon (192×192) |
| `icon-512.png` | PWA icon (512×512) |
| `favicon.ico` | Browser tab favicon |
| `start.sh` | Startup script |

## API Key

A Google API key unlocks weather-aware ranking and accurate transit directions.

**→ [Get a Google API Key](../README.md#get-a-google-api-key-free-tier)** — step-by-step instructions in the main README.

The Cloudflare Worker URL is hardcoded to a shared default (the `WORKER_URL` constant in `index.html`). To self-host, deploy your own worker and update this constant — see the [Cloudflare Worker setup](../cloudflare-worker/).
