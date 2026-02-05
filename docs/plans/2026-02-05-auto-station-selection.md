# Auto Station Selection Design

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace manual bike station selection with automatic selection of the best stations within a 4-mile radius of home, ranked by a commute-time heuristic and grouped by subway line.

**Architecture:** Each platform has its own implementation. We follow existing conventions: Kotlin for Android, Swift/SwiftUI for iOS, vanilla JS for Web.

**Tech Stack:** Kotlin (Android), Swift/SwiftUI (iOS), HTML/CSS/JavaScript (Web)

---

## Design Overview

**Problem:** Users must manually select bike-to stations. This is tedious and suboptimal — users may miss better stations on lines they didn't consider.

**Solution:** Automatically select the best stations using a scoring heuristic that estimates total commute time without any API calls. Only the top 3 candidates per subway line are kept, resulting in ~15-25 stations that get passed to Google Routes API.

**Algorithm:**
```
AUTO_SELECT(home, work, all_stations):
  1. Filter: stations within 4 miles of home (Haversine distance)
  2. Score each: bike_time(home, station) + distance(station, work) / AVG_SUBWAY_SPEED
  3. Group by subway line (a station on L+G appears in both the L group and G group)
  4. Per line: keep top 3 by lowest score
  5. Deduplicate: merge across lines so each station appears once
  6. Return deduplicated list
```

**Constants:**
- `RADIUS_MILES = 4`
- `AVG_SUBWAY_SPEED_MPH = 15` (includes stops, dwell time, acceleration)
- `TOP_PER_LINE = 3`
- Bike speed: use existing `estimateBikeTime()` / `BIKE_SPEED_MPH` already in each platform

**API call impact:** ~18-28 Google Routes calls per refresh (up from ~6), zero additional calls for the selection itself.

---

## Task 1: Add Auto-Selection Function to Web

**Files:**
- Create: `web/auto-select.js` — standalone module with the auto-selection algorithm
- Modify: `web/index.html` — import and use `autoSelectStations()` instead of `settings.bikeStations`

**Context:** The web platform loads stations from `web/stations.json` into a global `STATIONS` array. The commute calculation at `index.html:750` iterates `settings.bikeStations`. Distance utilities (`calculateDistance`, `estimateBikeTime`) are already defined inline in `index.html`.

**Step 1: Create `web/auto-select.js`**

Create a new file with a single exported function:

```javascript
function autoSelectStations(homeLat, homeLng, workLat, workLng, allStations) {
  // Constants
  const RADIUS_MILES = 4;
  const AVG_SUBWAY_SPEED_MPH = 15;
  const TOP_PER_LINE = 3;

  // 1. Filter to stations within radius
  const candidates = allStations.filter(s =>
    calculateDistance(homeLat, homeLng, s.lat, s.lng) <= RADIUS_MILES
  );

  // 2. Score each candidate
  const scored = candidates.map(s => {
    const bikeTime = estimateBikeTime(homeLat, homeLng, s.lat, s.lng);
    const transitEstimate = (calculateDistance(s.lat, s.lng, workLat, workLng) / AVG_SUBWAY_SPEED_MPH) * 60;
    return { station: s, score: bikeTime + transitEstimate };
  });

  // 3. Group by line, keep top N per line
  const byLine = {};
  for (const item of scored) {
    for (const line of item.station.lines) {
      if (!byLine[line]) byLine[line] = [];
      byLine[line].push(item);
    }
  }

  // 4. Top N per line
  const selectedIds = new Set();
  for (const line of Object.keys(byLine)) {
    byLine[line].sort((a, b) => a.score - b.score);
    for (const item of byLine[line].slice(0, TOP_PER_LINE)) {
      selectedIds.add(item.station.id);
    }
  }

  // 5. Return deduplicated list, sorted by score
  return scored
    .filter(item => selectedIds.has(item.station.id))
    .sort((a, b) => a.score - b.score)
    .map(item => item.station.id);
}
```

Note: `calculateDistance` and `estimateBikeTime` are already defined in `index.html`. Either move them to a shared utils file or pass them as dependencies. The simplest approach: define them in `auto-select.js` as well (they're small pure functions), or include `auto-select.js` after the existing `<script>` block so they're in scope.

**Step 2: Update `index.html` commute loading**

In `loadCommute()` function:

1. Remove the guard at line 690 that checks `settings.bikeStations.length === 0`. Replace with a check for `homeLat/homeLng` and `workLat/workLng` only.

2. After loading stations and finding `destStation`, compute auto-selected stations:
```javascript
const autoSelected = autoSelectStations(
  settings.homeLat, settings.homeLng,
  settings.workLat, settings.workLng,
  STATIONS
);
```

3. Line 750: Replace `for (const stationId of settings.bikeStations)` with `for (const stationId of autoSelected)`.

4. Line 813: Replace `settings.bikeStations` in the walk options section with `autoSelected`.

5. Add `<script src="auto-select.js"></script>` to the HTML head.

---

## Task 2: Add Auto-Selection Function to iOS

**Files:**
- Modify: `ios/CommuteOptimizer/CommuteOptimizer/Utilities/StationsDataSource.swift` — add `autoSelectStations()` method
- Modify: `ios/CommuteOptimizer/CommuteOptimizer/Services/CommuteCalculator.swift` — use auto-selected stations

**Context:** iOS loads stations via `StationsDataSource.shared.getStations()`. Distance calculation exists in `DistanceCalculator` utility. `CommuteCalculator.swift:83` iterates `settings.bikeStations`.

**Step 1: Add auto-selection to `StationsDataSource.swift`**

Add a new method:
```swift
func autoSelectStations(homeLat: Double, homeLng: Double, workLat: Double, workLng: Double) -> [String] {
    let radiusMiles = 4.0
    let avgSubwaySpeedMph = 15.0
    let topPerLine = 3

    let allStations = getStations()

    // 1. Filter within radius
    let candidates = allStations.filter { station in
        DistanceCalculator.calculateDistance(homeLat, homeLng, station.lat, station.lng) <= radiusMiles
    }

    // 2. Score each
    struct ScoredStation {
        let station: LocalStation
        let score: Double
    }
    let scored = candidates.map { station in
        let bikeTime = DistanceCalculator.estimateBikeTime(homeLat, homeLng, station.lat, station.lng)
        let transitEst = (DistanceCalculator.calculateDistance(station.lat, station.lng, workLat, workLng) / avgSubwaySpeedMph) * 60
        return ScoredStation(station: station, score: bikeTime + transitEst)
    }

    // 3. Group by line, top N per line
    var byLine: [String: [ScoredStation]] = [:]
    for item in scored {
        for line in item.station.lines {
            byLine[line, default: []].append(item)
        }
    }

    // 4. Collect top per line
    var selectedIds = Set<String>()
    for (_, stations) in byLine {
        let sorted = stations.sorted { $0.score < $1.score }
        for item in sorted.prefix(topPerLine) {
            selectedIds.insert(item.station.id)
        }
    }

    // 5. Return sorted by score
    return scored
        .filter { selectedIds.contains($0.station.id) }
        .sorted { $0.score < $1.score }
        .map { $0.station.id }
}
```

**Step 2: Update `CommuteCalculator.swift`**

In `calculateCommute()`:

1. Replace `settings.bikeStations` with:
```swift
let autoSelected = stationsDataSource.autoSelectStations(
    homeLat: originLat, homeLng: originLng,
    workLat: destLat, workLng: destLng
)
```

2. Iterate `autoSelected` instead of `settings.bikeStations` for bike-to-transit options.

3. For walk-only options: use `autoSelected`, sort by walk time, take top 3 (same pattern as current code).

---

## Task 3: Add Auto-Selection Function to Android

**Files:**
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/service/LocalDataSource.kt` — add `autoSelectStations()` method
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/service/CommuteCalculator.kt` — use auto-selected stations

**Context:** Android loads stations via `LocalDataSource.getStations()`. Distance utilities are in `DistanceCalculator.kt`. `CommuteCalculator.kt:57` reads `prefs.getBikeStations()`.

**Step 1: Add auto-selection to `LocalDataSource.kt`**

Add a new method with the same algorithm as iOS/Web (adapted to Kotlin):
```kotlin
fun autoSelectStations(homeLat: Double, homeLng: Double, workLat: Double, workLng: Double): List<String> {
    val radiusMiles = 4.0
    val avgSubwaySpeedMph = 15.0
    val topPerLine = 3

    val allStations = getStations()

    // 1. Filter within radius
    val candidates = allStations.filter { station ->
        DistanceCalculator.calculateDistance(homeLat, homeLng, station.lat, station.lng) <= radiusMiles
    }

    // 2. Score each
    data class ScoredStation(val station: LocalStation, val score: Double)
    val scored = candidates.map { station ->
        val bikeTime = DistanceCalculator.estimateBikeTime(homeLat, homeLng, station.lat, station.lng)
        val transitEst = (DistanceCalculator.calculateDistance(station.lat, station.lng, workLat, workLng) / avgSubwaySpeedMph) * 60
        ScoredStation(station, bikeTime + transitEst)
    }

    // 3. Group by line, top N per line
    val byLine = mutableMapOf<String, MutableList<ScoredStation>>()
    for (item in scored) {
        for (line in item.station.lines) {
            byLine.getOrPut(line) { mutableListOf() }.add(item)
        }
    }

    // 4. Collect top per line
    val selectedIds = mutableSetOf<String>()
    for ((_, stations) in byLine) {
        stations.sortBy { it.score }
        stations.take(topPerLine).forEach { selectedIds.add(it.station.id) }
    }

    // 5. Return sorted by score
    return scored
        .filter { it.station.id in selectedIds }
        .sortedBy { it.score }
        .map { it.station.id }
}
```

**Step 2: Update `CommuteCalculator.kt`**

In `calculateCommute()`:

1. Replace `prefs.getBikeStations()` with:
```kotlin
val autoSelected = localDataSource.autoSelectStations(homeLat, homeLng, workLat, workLng)
```

2. Iterate `autoSelected` instead of `selectedStations` for bike-to-transit options.

3. Walk options: use `autoSelected`, sort by walk time, take top 3.

---

## Task 4: Update Web Settings UI

**Files:**
- Modify: `web/settings.html` — replace bike station picker with read-only display

**Context:** The settings page has a "Bike-to Stations" card with checkboxes and search. This becomes a read-only display. The live station picker remains unchanged.

**Step 1: Remove bike station picker**

Remove the bike station card (search input, checkbox list, station count). Replace with a read-only card:

```html
<div class="card">
  <h2>Auto-Selected Bike Stations</h2>
  <p>Stations within 4 miles, ranked by estimated commute time (top 3 per line)</p>
  <div id="autoSelectedStations">
    <p class="muted">Set home and work locations to see stations</p>
  </div>
</div>
```

**Step 2: Add auto-selection display logic**

Include `auto-select.js` in settings page. After stations load, if home and work are set, compute and display:

```javascript
function renderAutoSelectedStations(settings) {
  if (!settings.homeLat || !settings.workLat) return;

  const autoSelected = autoSelectStations(
    settings.homeLat, settings.homeLng,
    settings.workLat, settings.workLng,
    STATIONS
  );

  // Group selected stations by line for display
  const grouped = groupByLine(autoSelected, STATIONS);

  // Render grouped read-only list with distances
  const container = document.getElementById('autoSelectedStations');
  container.innerHTML = `<p>${autoSelected.length} stations selected</p>` +
    Object.entries(grouped).map(([line, stations]) =>
      `<div><strong>${line}:</strong> ${stations.map(s =>
        `${s.name} (${s.distance.toFixed(1)} mi)`
      ).join(', ')}</div>`
    ).join('');
}
```

**Step 3: Update `saveSettings()`**

- Remove `bikeStations` from the saved settings object
- Remove the "at least one bike station" validation
- Add validation: home and work locations are both required

**Step 4: Update `loadSettings()` display**

Call `renderAutoSelectedStations()` after station data loads and whenever home/work location changes.

---

## Task 5: Update iOS Settings UI

**Files:**
- Modify: `ios/CommuteOptimizer/CommuteOptimizer/Views/SettingsTab/SettingsView.swift` — replace bike station picker with read-only display
- Modify: `ios/CommuteOptimizer/CommuteOptimizer/Utilities/SettingsManager.swift` — remove `bikeStations` property

**Context:** iOS settings uses SwiftUI. The bike station section is a `DisclosureGroup` containing a `StationPickerView`. Live station picker is a separate `DisclosureGroup` with the same `StationPickerView` component (max 3).

**Step 1: Update `SettingsView.swift`**

Replace the bike station `DisclosureGroup` + `StationPickerView` with a read-only disclosure group:

```swift
Section {
  DisclosureGroup("Auto-Selected Stations (\(autoSelectedStations.count))") {
    if autoSelectedStations.isEmpty {
      Text("Set home and work locations to see stations")
        .foregroundColor(.secondary)
    } else {
      ForEach(groupedByLine, id: \.line) { group in
        VStack(alignment: .leading) {
          Text(group.line).font(.headline)
          ForEach(group.stations) { station in
            Text("\(station.name) (\(String(format: "%.1f", station.distance)) mi)")
              .font(.caption)
          }
        }
      }
    }
  }
}
```

Compute `autoSelectedStations` as a computed property using `StationsDataSource.shared.autoSelectStations(...)`.

**Step 2: Update `SettingsManager.swift`**

- Remove the `bikeStations` stored property and its `@Published` wrapper
- Remove the `bikeStations` key from `Keys` enum
- Update `isConfigured` computed property: only require home + work + API keys (no bike stations)

**Step 3: Keep live station picker unchanged**

The `StationPickerView` with `maxSelections: 3` for live stations remains as-is.

---

## Task 6: Update Android Settings UI

**Files:**
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/ui/SettingsFragment.kt` — replace bike station chips with read-only display
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/util/WidgetPreferences.kt` — remove bike station persistence
- Modify: `android/app/src/main/res/layout/fragment_settings.xml` (if layout XML exists) — update UI

**Context:** Android settings uses Material `Chip` components in a `ChipGroup`. Bike stations are checkable chips. Live stations are a separate chip group (max 3).

**Step 1: Update `SettingsFragment.kt`**

Replace `setupBikeStationChips()` with `renderAutoSelectedStations()`:

- Make bike station chips non-checkable (read-only)
- Group by line with section headers
- Show distance from home
- Compute auto-selected list using `LocalDataSource.autoSelectStations(...)`
- Only render if home and work are both set

**Step 2: Update `WidgetPreferences.kt`**

- Remove `setBikeStations()` method (or deprecate)
- Keep `getBikeStations()` only if needed for migration (can remove entirely)
- Update validation: settings are valid with home + work + API keys

**Step 3: Update `saveSettings()` in fragment**

- Remove bike station collection from chip group
- Remove "at least one bike station" validation
- Ensure home + work validation remains

**Step 4: Keep live station chips unchanged**

The live station `ChipGroup` with manual selection and max 3 enforcement remains as-is.

---

## Task 7: Cleanup and Validation

**Files (all platforms):**
- Remove any dead code referencing `bikeStations` in commute calculation paths
- Ensure settings migration: existing users with saved `bikeStations` don't get errors (graceful ignore)

**Validation checks:**
- Web: Confirm `loadCommute()` works with only home + work + API keys configured
- iOS: Confirm `CommuteCalculator.calculateCommute()` works without `settings.bikeStations`
- Android: Confirm `CommuteCalculator.calculateCommute()` works without `prefs.getBikeStations()`
- All platforms: Verify auto-selection returns sensible results for edge cases:
  - Home in Manhattan (high station density)
  - Home in outer borough (low density)
  - Home with no stations within 4 miles (should show helpful error)
  - Home and work very close (short commute, few candidates)

---

## Summary

| What changes | Web | iOS | Android |
|---|---|---|---|
| New auto-select function | `auto-select.js` | `StationsDataSource.swift` | `LocalDataSource.kt` |
| Commute calculator | `index.html` | `CommuteCalculator.swift` | `CommuteCalculator.kt` |
| Settings UI | `settings.html` | `SettingsView.swift` | `SettingsFragment.kt` |
| Settings storage | `saveSettings()` | `SettingsManager.swift` | `WidgetPreferences.kt` |
| Files modified | 2 new, 1 modified | 3 modified | 3 modified |

**API call estimate:** ~18-28 Google Routes calls per refresh (up from ~6). Zero additional calls for station selection itself.
