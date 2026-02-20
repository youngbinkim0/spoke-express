# Spoke Express

NYC hybrid commute optimizer — bike to the subway, ride to work, get there faster.

Regular transit apps show you one option: take the train, or ride a bike. But in NYC, the fastest commute is often **both** — bike to a nearby station, lock up, and catch the express. Spoke Express finds these hybrid routes automatically, ranking every combination of bike + subway by real-time train arrivals and weather conditions.

**Zero backend required** — calls MTA APIs directly from your browser/device.

**Current version:** v0.1

## Why Spoke Express?

Google Maps gives you a bike route OR a transit route. Citymapper does the same. Neither will tell you: "Bike 8 minutes to Court Sq, catch the E express arriving in 3 minutes, save 12 minutes vs. walking to your local stop."

Spoke Express does. It:
- Compares **walk-to-station** vs. **bike-to-station** for every nearby subway stop
- Uses **live MTA arrivals** to find the fastest connection right now
- **Demotes bike options** when it's raining (via weather API)
- Shows you the **actual best commute**, not just the default one

## Quick Start

### Web App

```bash
cd web
./start.sh
```

Then configure your home/work locations in Settings. See [web/QUICKSTART.md](web/QUICKSTART.md) for detailed setup.

### Android App

Download the APK from [GitHub Releases](https://github.com/youngbinkim0/spoke-express/releases) and sideload it.

Or build from source:

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires: Java 17+, Android SDK

### iOS App

Open `ios/CommuteOptimizer/CommuteOptimizer.xcodeproj` in Xcode and run.

## Features

| Feature | Web | Android | iOS |
|---------|-----|---------|-----|
| Live train arrivals | ✓ | ✓ | ✓ |
| Weather-aware ranking | ✓ | ✓ | ✓ |
| Service alerts | ✓ | ✓ | ✓ |
| Walk + Transit options | ✓ | ✓ | ✓ |
| Bike + Transit options | ✓ | ✓ | ✓ |
| Home screen widget | - | ✓ | ✓ |
| Auto-refresh | 30s | 30s | 30s |

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

Then add the worker URL to Settings.

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Web App /  │────▶│   MTA API    │────▶│ Real-time train │
│ Android/iOS │     │ (no key req) │     │    arrivals     │
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
├── web/                    # Web app (start here)
│   ├── start.sh           # Startup script
│   ├── index.html         # Commute options page
│   ├── arrivals.html      # Live train times
│   ├── settings.html      # Configuration
│   └── mta-api.js         # MTA GTFS-RT parser
├── android/               # Android app + widgets
├── ios/                   # iOS app + widgets
└── cloudflare-worker/     # Optional Google Routes proxy
```

## Tech Stack

- **Web**: Vanilla HTML/CSS/JS, no build step
- **Android**: Kotlin, Material Design, Coroutines
- **iOS**: Swift, SwiftUI, WidgetKit
- **APIs**: MTA GTFS-Realtime, OpenWeatherMap, Google Routes
- **Proxy**: Cloudflare Workers (optional)

## License

GPL-3.0 — see [LICENSE](LICENSE)
