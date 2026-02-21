# Spoke Express — Android App

NYC bike + transit commute optimizer. Full app with home screen widgets for at-a-glance commute info.

## Install

Download the APK from [GitHub Releases](https://github.com/youngbinkim0/spoke-express/releases) and sideload it.

Or build from source:

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires: Java 17+, Android SDK (API 26+)

## Features

### Full App (3 tabs)

- **Commute** — Ranked bike + walk + transit options with live train arrivals and weather
- **Live Trains** — Real-time arrival board for up to 3 stations
- **Settings** — Home/work addresses, station selection, API keys

### Home Screen Widgets

- **Commute Widget** (4×2) — Top 3 commute options with weather and train times
- **Live Trains Widget** — Next arrivals for a single station

Both widgets auto-refresh via WorkManager and support tap-to-refresh.

## Architecture

Zero backend — all API calls happen directly on-device:

- **MTA GTFS-Realtime** — Live subway arrivals (free, no key)
- **MTA Service Alerts** — Delays, planned work, service changes
- **Google Weather API** — Weather-aware bike ranking (uses Google API key)
- **Google Routes** — Accurate transit directions via Cloudflare Worker proxy (optional)

## Project Structure

```
app/src/main/java/com/commuteoptimizer/widget/
├── MainActivity.kt                    # Tab host (Commute / Live Trains / Settings)
├── CommuteWidgetProvider.kt           # Commute widget
├── CommuteWidgetConfigActivity.kt     # Commute widget setup
├── CommuteUpdateWorker.kt             # Background widget refresh
├── LiveTrainsWidgetProvider.kt        # Live Trains widget
├── LiveTrainsConfigActivity.kt        # Live Trains widget setup
├── data/
│   ├── api/
│   │   ├── ApiClientFactory.kt        # OkHttp client
│   │   ├── MtaApiService.kt          # GTFS-RT feed parser
│   │   ├── MtaAlertsService.kt       # Service alerts parser
│   │   ├── GoogleRoutesService.kt    # Transit directions (optional)
│   │   └── WeatherApiService.kt      # Google Weather client
│   ├── models/
│   │   ├── CommuteModels.kt          # Commute option data classes
│   │   ├── TransiterModels.kt        # Transit data classes
│   │   └── WeatherModels.kt          # Weather data classes
│   └── CommuteRepository.kt          # Data layer
├── service/
│   ├── CommuteCalculator.kt          # Route ranking engine
│   ├── DistanceCalculator.kt         # Haversine distance
│   ├── LocalDataSource.kt            # Station data
│   └── RankingService.kt             # Option scoring
├── ui/
│   ├── CommuteFragment.kt            # Commute tab
│   ├── LiveTrainsFragment.kt         # Live Trains tab
│   └── SettingsFragment.kt           # Settings tab
└── util/
    ├── MtaColors.kt                  # MTA line colors
    └── WidgetPreferences.kt          # SharedPreferences wrapper
```

## Dependencies

- OkHttp 4.12.0 — HTTP client
- Gson — JSON + protobuf parsing
- Kotlin Coroutines 1.7.3 — Async operations
- WorkManager 2.9.0 — Background widget refresh
- Material Components — UI
