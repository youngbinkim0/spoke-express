# Android Widget Core Knowledge Base

**Generated:** Thu, Feb 12, 2026
**Commit:** d1773a9

## OVERVIEW

Android widget core implementation: providers, services, and utilities. Main entry points: `CommuteWidgetProvider.kt` (4x2 widget) and `LiveTrainsWidgetProvider.kt`.

## STRUCTURE

```
java/com/commuteoptimizer/widget/
├── CommuteWidgetProvider.kt            # Main 4x2 widget (top 3 options)
├── LiveTrainsWidgetProvider.kt         # Live trains widget
├── CommuteUpdateWorker.kt              # Background refresh worker
├── CommuteFragment.kt                  # Commute options view
├── LiveTrainsFragment.kt               # Live arrivals view
└── SettingsFragment.kt                 # Configuration UI
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Widget data fetch | `CommuteUpdateWorker.kt` | 15-minute background update |
| Widget rendering | `CommuteWidgetProvider.kt` | RemoteViews factory |
| Live trains widget | `LiveTrainsWidgetProvider.kt` | separate widget extension |
| Main view | `CommuteFragment.kt` | Options list +
| Live trains view | `LiveTrainsFragment.kt` | Real-time arrivals |
| Settings view | `SettingsFragment.kt` | User configuration |

## CONVENTIONS

- **RemoteViews:** Widget uses RemoteViews (not ViewGroups)
- **WorkManager:** Background updates via `CommuteUpdateWorker.kt`
- **Service layer:** `RankingService.kt`, `DistanceCalculator.kt` in `service/`
- **Data layer:** `CommuteRepository.kt` abstracts data sources

## ANTI-PATTERNS

- **Deprecated methods:** Use `getBikeStations()` not `getSelectedStations()`

## NOTES

- Widget size: 4x2 cells minimum
- Update interval: 15 minutes minimum (Android constraint)
- Must show top 3 commute options
