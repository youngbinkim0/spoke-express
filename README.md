# NYC Commute Optimizer

A serverless NYC subway commute app with weather-aware routing. Shows the best way to get to work - bike, walk, or transit - with real-time train arrivals.

**Zero backend required** - calls MTA APIs directly from your browser/device.

## Quick Start

### Web App

```bash
# Option 1: Python
cd web && python3 -m http.server 8080

# Option 2: Node
npx serve web

# Option 3: Just open the file
open web/index.html
```

Then visit `http://localhost:8080` and configure your home/work locations in Settings.

### Android App

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires: Java 17+, Android SDK

## Features

| Feature | Web | Android |
|---------|-----|---------|
| Live train arrivals | ✓ | ✓ |
| Weather-aware ranking | ✓ | ✓ |
| Service alerts | ✓ | ✓ |
| Walk + Transit options | ✓ | ✓ |
| Bike + Transit options | ✓ | ✓ |
| Home screen widget | - | ✓ (2 types) |
| Auto-refresh | 30s | 30s / 15m widget |

## Configuration

Open Settings in the app and configure:

| Setting | Required | Notes |
|---------|----------|-------|
| Home address | Yes | Click "Lookup" to geocode |
| Work address | Yes | Click "Lookup" to geocode |
| Nearby stations | Yes | Select 1-3 stations you can walk/bike to |
| OpenWeatherMap API key | No | Free at openweathermap.org - enables weather |
| Google API key | No | Enables accurate transit directions |
| Worker URL | No | For Google Routes API proxy (see below) |

## Optional: Enhanced Transit Directions

For accurate transit routing with transfers, deploy the Cloudflare Worker:

```bash
cd cloudflare-worker
npm install -g wrangler
wrangler login
wrangler secret put GOOGLE_API_KEY  # paste your Google Maps API key
wrangler deploy
```

Then add the worker URL (e.g., `https://commute-directions.YOUR_ACCOUNT.workers.dev`) to Settings.

## How It Works

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Web App /  │────▶│   MTA API    │────▶│ Real-time train │
│ Android App │     │ (no key req) │     │    arrivals     │
└─────────────┘     └──────────────┘     └─────────────────┘
       │
       │ (optional)
       ▼
┌─────────────┐     ┌──────────────┐
│ CF Worker   │────▶│ Google Routes│
│   Proxy     │     │     API      │
└─────────────┘     └──────────────┘
```

- **MTA GTFS-Realtime**: Free, no API key, called directly from browser/app
- **OpenWeatherMap**: Free tier, optional, for weather-aware ranking
- **Google Routes**: Paid, optional, for accurate transit times with transfers

## Project Structure

```
├── web/                    # Standalone web app
│   ├── index.html         # Commute options page
│   ├── arrivals.html      # Live train times
│   ├── settings.html      # Configuration
│   └── mta-api.js         # MTA GTFS-RT parser
├── android/               # Android app + widgets
│   └── app/src/main/
│       ├── java/.../      # Kotlin source
│       └── res/           # Layouts & resources
├── cloudflare-worker/     # Optional Google Routes proxy
└── src/                   # Legacy Node.js backend (not required)
```

## Android Widgets

Two widget types available:

1. **Commute Routing** (4x2) - Top 3 commute options with weather
2. **Live Trains** (4x2) - Real-time arrivals for your stations

Long-press home screen → Widgets → Commute Optimizer

## Tech Stack

- **Web**: Vanilla HTML/CSS/JS, no build step
- **Android**: Kotlin, Material Design, Coroutines
- **APIs**: MTA GTFS-Realtime, OpenWeatherMap, Google Routes
- **Proxy**: Cloudflare Workers (optional)

## License

MIT
