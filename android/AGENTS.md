# Android Widget Knowledge Base

**Generated:** Thu, Feb 12, 2026
**Commit:** d1773a9

## OVERVIEW

Android 4x2 home screen widget displaying top 3 commute options with live train times. Kotlin/Coroutines architecture with Retrofit networking.

## STRUCTURE

```
android/app/src/main/
├── java/com/commuteoptimizer/widget/
│   ├── CommuteWidgetProvider.kt        # Main widget provider
│   ├── LiveTrainsWidgetProvider.kt     # Live trains widget
│   ├── CommuteUpdateWorker.kt          # Background refresh (15min)
│   ├── CommuteFragment.kt              # Commute options view
│   ├── LiveTrainsFragment.kt           # Live train arrivals
│   └── SettingsFragment.kt             # App configuration
├── data/
│   ├── api/                            # Retrofit services
│   │   ├── MtaAlertsService.kt
│   │   └── CommuteModels.kt            # Data classes
│   ├── models/                         # Business models
│   │   ├── CommuteModels.kt
│   │   ├── WeatherModels.kt
│   │   └── TransiterModels.kt
│   └── CommuteRepository.kt            # Data layer
├── service/                            # Business logic
│   ├── RankingService.kt               # Weather-aware sorting
│   ├── DistanceCalculator.kt           # Haversine + walking time
│   └── CommuteCalculator.kt            # Route computation
└── util/
    ├── MtaColors.kt                    # Subway line colors
    └── WidgetPreferences.kt            # SharedPreferences wrapper
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Widget data fetching | `CommuteUpdateWorker.kt` | 15-minute background refresh |
| Commute ranking | `RankingService.kt` | Demotes bike on rainy days |
| Distance calculation | `DistanceCalculator.kt` | Haversine + walking time est |
| MTA API integration | `data/api/MtaAlertsService.kt` | Retrofit interface |
| Preferences | `util/WidgetPreferences.kt` | SharedPreferences wrapper |
| Deprecated methods | `WidgetPreferences.kt` | `getSelectedStations()` → use `getBikeStations()` |

## CONVENTIONS

- **Service layer pattern:** Repository → Data layer → API clients
- **Coroutines:** All async operations use Kotlin Coroutines
- **SharedPreferences:** Simple key-value persistence via `WidgetPreferences.kt`
- **WorkManager:** Background tasks scheduled via `CommuteUpdateWorker.kt`

## ANTI-PATTERNS

- **Deprecated methods:** `getSelectedStations()` and `setSelectedStations()` - use `getBikeStations()` and `setBikeStations()` instead

## COMMANDS

```bash
# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Run tests
./gradlew testDebugUnitTest
```

## NOTES

- Requires Java 17+ (Android Gradle Plugin 8.2.0)
- Minimum SDK: API 26 (Android 8.0)
- Uses Material Components for UI
