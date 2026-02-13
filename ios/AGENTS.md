# iOS Commute Optimizer Knowledge Base

**Generated:** Thu, Feb 12, 2026
**Commit:** d1773a9

## OVERVIEW

iOS app + home screen widget with SwiftUI forNYC Subway commute optimization. Async/await networking, manual protobuf parsing, UserDefaults with App Groups for widget data sharing.

## STRUCTURE

```
ios/CommuteOptimizer/
├── CommuteOptimizer/                   # Main app
│   ├── App/
│   │   ├── CommuteOptimizerApp.swift   # App entry point
│   │   └── ContentView.swift           # Main container
│   ├── Models/                         # Data models
│   │   ├── CommuteModels.swift
│   │   ├── WeatherModels.swift
│   │   ├── ArrivalModels.swift
│   │   └── LocalStation.swift
│   ├── Services/                       # Business logic
│   │   ├── RankingService.swift
│   │   ├── DistanceCalculator.swift
│   │   └── MtaAlertsService.swift
│   ├── Utilities/                      # Helpers
│   │   ├── ProtobufReader.swift        # Manual GTFS-RT parsing
│   │   ├── StationsDataSource.swift
│   │   ├── MtaColors.swift
│   │   └── SettingsManager.swift
│   └── Views/
│       ├── CommuteTab/                 # Main commute view
│       │   ├── CommuteView.swift
│       │   ├── CommuteOptionCard.swift
│       │   └── WeatherHeaderView.swift
│       ├── LiveTrainsTab/              # Live arrivals
│       │   └── LiveTrainsView.swift
│       ├── SettingsTab/                # Settings
│       │   ├── SettingsView.swift
│       │   └── StationPickerView.swift
│       └── Shared/                     # Reusable components
│           ├── ModeIcon.swift
│           └── LineBadge.swift
└── CommuteWidget/                      # Widget extension
    ├── CommuteWidget.swift             # Widget definition
    ├── Intents/                        # Widget configuration
    └── CommuteWidgetBundle.swift

CommuteOptimizerTests/
├── RankingServiceTests.swift
├── DistanceCalculatorTests.swift
└── ProtobufReaderTests.swift
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Widget definition | `CommuteWidget/CommuteWidget.swift` | WidgetKit integration |
| Data fetching | `Services/RankingService.swift` | Retrieves and ranks options |
| Manual protobuf parsing | `Utilities/ProtobufReader.swift` | GTFS-Realtime parser |
| Settings persistence | `Utilities/SettingsManager.swift` | UserDefaults wrapper |
| App Group data sharing | `SettingsManager.swift` | Widget ↔ app communication |
| Distance calculation | `Services/DistanceCalculator.swift` | Haversine + walking time |

## CONVENTIONS

- **Service layer pattern:** Models → Services → ViewModels → Views
- **SwiftUI:** All UI built with SwiftUI
- **Async/await:** All networking uses async/await
- **UserDefaults + App Groups:** Data sharing between app and widget
- **XCTest:** Unit tests in `CommuteOptimizerTests/`

## ANTI-PATTERNS

- **No external protobuf library:** Manual parsing required for MTA GTFS-Realtime
- **App Group required:** Widget must share data via App Group `group.com.commuteoptimizer`

## COMMANDS

```bash
# Build (via Xcode)
open ios/CommuteOptimizer/CommuteOptimizer.xcodeproj

# Run tests
xcodebuild -scheme CommuteOptimizer -destination 'platform=iOS Simulator,name=iPhone 15' test
```

## NOTES

- Minimum iOS: 16.0 (WidgetKit requirement)
- Testing: Only iOS platform has formal unit tests
- Widget updates: Background refresh every 15-30 seconds
