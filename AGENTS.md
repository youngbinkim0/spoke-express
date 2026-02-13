# NYC Commute Optimizer Knowledge Base

**Generated:** Thu, Feb 12, 2026
**Commit:** d1773a9
**Branch:** claude/nyc-commute-optimizer-plan-iM2Sh

## OVERVIEW

Multi-platform NYC subway commute optimizer with weather-aware routing. Serverless architecture - calls MTA APIs directly from browser/device. Zero backend required for core functionality.

## STRUCTURE

```
commute-optimizer/
├── web/                    # Web app (vanilla HTML/CSS/JS)
├── android/                # Android widget app (Kotlin)
├── ios/                    # iOS app + widgets (Swift/SwiftUI)
├── cloudflare-worker/      # Optional Google Routes proxy
├── docs/plans/             # Feature & implementation plans
└── README.md               # Multi-platform overview
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| MTA GTFS-RT parsing | `web/mta-api.js`, `android/data/api/`, `ios/Utilities/ProtobufReader.swift` | All platforms parse protobuf manually |
| Weather-aware ranking | `android/service/RankingService.kt`, `ios/service/RankingService.swift` | Demotes bike on rainy days |
| Widget data fetching | `android/service/CommuteUpdateWorker.kt`, `ios/CommuteWidget.swift` | Background refresh every 15-30s |
| Google Routes proxy | `cloudflare-worker/worker.js` | Bypasses CORS for transit directions |
| Configuration | `web/settings.html`, `android/SettingsFragment.kt`, `ios/SettingsView.swift` |UserDefaults/UserPreferences/LocalStorage |

## CODEMAP

| Symbol | Type | Location | Role |
|--------|------|----------|------|
| `CommuteResponse` | struct | `android/data/models/`, `ios/Models/`, `web/mta-api.js` | API response model |
| `CommuteOption` | struct | `android/data/models/`, `ios/Models/`, `web/mta-api.js` | Single commute option |
| `RankingService` | service | `android/service/`, `ios/service/` | Sorts options by duration + weather |
| `DistanceCalculator` | utility | `android/service/`, `ios/Utilities/` | Haversine distance + walking time |
| `CommuteWidgetProvider` | widget | `android/widget/` | Android home screen widget |
| `CommuteWidget` | extension | `ios/CommuteWidget/` | iOS home screen widget |

## CONVENTIONS

**Cross-platform:**
- MTA GTFS-Realtime parsed manually (no protobuf library)
- 30s refresh rate for live data, 15s for widgets
- Weather data from OpenWeatherMap (optional)
- Google Routes via optional Cloudflare proxy (paid)

**Android:**
- Kotlin with Coroutines for async
- sharedPreferences for persistence
- WorkManager for background refresh

**iOS:**
- Swift/SwiftUI with async/await
- UserDefaults + App Groups for widget data sharing
- XCTest for unit tests

**Web:**
- Vanilla HTML/CSS/JS, no build step
- localStorage for persistence
- No dependencies

## ANTI-PATTERNS (THIS PROJECT)

- **Deprecated methods (Android):** `getSelectedStations()` and `setSelectedStations()` in `WidgetPreferences.kt` - use `getBikeStations()` and `setBikeStations()` instead
- **Multiple entry points:** No single main entry - platform-specific
- **No TypeScript:** Web uses vanilla JS, no build tools
- **No common code sharing:** Each platform implemented independently

## UNIQUE STYLES

- **Service-layer pattern:** Android uses separate data layer, repository, and services
- **Manual protobuf parsing:** No SwiftProtobuf/protobuf library - all platforms parse manually
- **Widget as first-class citizen:** Android widget is full app, iOS widget shares main app data

## COMMANDS

```bash
# Web app
cd web && ./start.sh

# Android
cd android && ./gradlew assembleDebug

# iOS
open ios/CommuteOptimizer/CommuteOptimizer.xcodeproj
```

## NOTES

- Cloudflare Worker optional for Google Routes API proxy
- OpenWeatherMap API key optional for weather-aware ranking
- Google API key + worker required for accurate transit directions with transfers
