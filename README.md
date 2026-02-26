# Spoke Express

> **Try it now: [https://youngbinkim0.github.io/spoke-express/](https://youngbinkim0.github.io/spoke-express/)**
> 
> Live train arrivals work instantly — no API keys, no setup.

NYC hybrid commute optimizer — bike to the subway, ride to work, get there faster.

Regular transit apps show you one option: take the train, or ride a bike. But in NYC, the fastest commute is often **both** — bike to a nearby station, lock up, and catch the express. Spoke Express finds these hybrid routes automatically, ranking every combination of bike + subway by real-time train arrivals and weather conditions.

**Zero backend required** — calls MTA APIs directly from your browser/device.

**Current version:** v0.1

## Why Spoke Express?

Google Maps gives you a bike route OR a transit route. Citymapper offers some hybrid options, but won't exhaustively compare every nearby station against live arrivals to find the fastest combo right now.

Spoke Express does. It:
- Compares **walk-to-station** vs. **bike-to-station** for every nearby subway stop
- Uses **live MTA arrivals** to find the fastest connection right now
- **Demotes bike options** when it's raining (via weather API)
- Shows you the **actual best commute**, not just the default one

## Get Started (30 seconds)

**Works on any phone, tablet, or computer — nothing to install.**

1. Open **[https://youngbinkim0.github.io/spoke-express/](https://youngbinkim0.github.io/spoke-express/)**
2. The **Live Trains** page works immediately with 3 popular stations pre-loaded
3. For full commute routing, tap **Settings** and enter your home/work addresses
4. Use the **Search** tab for ad-hoc trips — enter any origin and destination on the fly

That's it. Pin it to your home screen (see below) and it works like a native app.

## Add to Your Home Screen

Pin the web app for instant, full-screen access — no app store needed.

**Android (Chrome):**
1. Open the site in Chrome
2. Tap the **⋮** menu (top right)
3. Tap **"Add to Home screen"**
4. Name it **Spoke Express** → tap **Add**

**iPhone (Safari):**
1. Open the site in Safari
2. Tap the **Share** button (square with arrow, bottom bar)
3. Scroll down and tap **"Add to Home Screen"**
4. Name it **Spoke Express** → tap **Add**

> After pinning, Spoke Express opens full-screen like a native app — no browser chrome.

## Get a Google API Key (free tier)

A Google API key unlocks weather-aware ranking and accurate transit directions with transfers. The free tier is generous for personal use.

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a **new project** (or select an existing one)
3. Navigate to **APIs & Services → Library** and enable these three APIs:
   - **Geocoding API** — address lookup
   - **Routes API** — transit directions with transfers
   - **Weather API** — weather-aware route ranking
4. Go to **APIs & Services → Credentials** → click **Create Credentials → API Key**
5. *(Recommended)* Click your new key → **Restrict key** → add your domain or `localhost` under HTTP referrers
6. Copy the key → open Spoke Express → **Settings** → paste it into the API key field

> Detailed link: [Google Cloud Credentials](https://console.cloud.google.com/apis/credentials)

## Features
| Feature | Web | Android | iOS |
|---------|-----|---------|-----|
| Live train arrivals | ✓ | ✓ | ✓ |
| My Commute (home → work) | ✓ | ✓ | ✓ |
| Search (ad-hoc routes) | ✓ | ✓ | ✓ |
| Weather-aware ranking | ✓ | ✓ | ✓ |
| Service alerts | ✓ | ✓ | ✓ |
| Walk + Transit options | ✓ | ✓ | ✓ |
| Bike + Transit options | ✓ | ✓ | ✓ |
| Address prefill from settings | ✓ | ✓ | ✓ |
| Recent searches | ✓ | ✓ | ✓ |
| Home screen widget | - | ✓ | ✓ |
| Auto-refresh (My Commute) | 30s | 30s | 30s |

## Configuration

Open Settings in the app and configure:

| Setting | Required | Notes |
|---------|----------|-------|
| Home address | Yes | Click "Lookup" to geocode |
| Work address | Yes | Click "Lookup" to geocode |
| Bike transfer stations | Auto | Automatically selected within 4-mile radius |
| Google API key | Yes | Enables weather + accurate transit directions |

## Android App (Alternative)

Prefer a native Android app? Download the APK or build from source.

**Install from APK:**
Download from [GitHub Releases](https://github.com/youngbinkim0/spoke-express/releases) and sideload it.

**Build from source:**
```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires: Java 17+, Android SDK

## iOS App (Developers Only)

No App Store distribution — build from source in Xcode.

```
Open ios/CommuteOptimizer/CommuteOptimizer.xcodeproj in Xcode → Run
```

Requires: Xcode 15+, Apple Developer account for device deployment.

## Optional: Self-Hosted Worker

The app uses a shared Cloudflare Worker by default (`https://commute-directions.xmicroby.workers.dev`). To self-host your own:

```bash
cd cloudflare-worker
npm install -g wrangler
wrangler login
wrangler secret put GOOGLE_API_KEY  # paste your Google Maps API key
wrangler deploy
```

Then update the `WORKER_URL` constant in `web/index.html` to point to your deployed worker.

> For local web development, see [web/QUICKSTART.md](web/QUICKSTART.md).

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Web App /  │────▶│   MTA API    │────▶│ Real-time train │
│ Android/iOS │     │ (no key req) │     │    arrivals     │
└─────────────┘     └──────────────┘     └─────────────────┘
       │
       ├────────────────────────────────▶ Google Weather API
       │
       │ (via CF Worker proxy)
       ▼
┌─────────────┐     ┌──────────────┐
│ CF Worker   │────▶│ Google Routes│
│   Proxy     │     │     API      │
└─────────────┘     └──────────────┘
```

- **MTA GTFS-Realtime**: Free, no API key, called directly from browser/app
- **Google Weather API**: Weather-aware ranking (uses your Google API key)
- **Google Routes API**: Accurate transit times with transfers (via CF Worker proxy)

## Project Structure
```
├── web/                    # Web app (start here)
│   ├── start.sh           # Startup script
│   ├── index.html         # My Commute (home → work)
│   ├── search.html        # Search (ad-hoc routes)
│   ├── arrivals.html      # Live train times
│   ├── settings.html      # Configuration
│   ├── commute-engine.js  # Shared commute calculation
│   ├── render-utils.js    # Shared rendering utilities
│   ├── shared.css         # Shared styles
│   └── mta-api.js         # MTA GTFS-RT parser
├── android/               # Android app + widgets
├── ios/                   # iOS app + widgets
└── cloudflare-worker/     # Optional Google Routes proxy
```

## Tech Stack

- **Web**: Vanilla HTML/CSS/JS, no build step
- **Android**: Kotlin, Material Design, Coroutines
- **iOS**: Swift, SwiftUI, WidgetKit
- **APIs**: MTA GTFS-Realtime, Google Weather API, Google Routes API
- **Proxy**: Cloudflare Workers (optional)

## License

GPL-3.0 — see [LICENSE](LICENSE)
