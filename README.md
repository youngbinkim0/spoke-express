# Spoke Express

NYC bike + transit commute optimizer with weather-aware routing. Compare bike, walk, and subway options side-by-side with real-time train arrivals.

**Zero backend required** — calls MTA APIs directly from your browser/device.

**Current version:** v0.1

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
