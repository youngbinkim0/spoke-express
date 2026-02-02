# Cross-Platform Feature Parity Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Achieve feature parity across Android, iOS, and Web apps by adding missing features to each platform.

**Architecture:** Each platform has its own implementation patterns. We'll follow existing conventions for each: Kotlin/SharedPreferences for Android, SwiftUI/UserDefaults for iOS, vanilla JS/localStorage for Web.

**Tech Stack:** Kotlin (Android), Swift/SwiftUI (iOS), HTML/CSS/JavaScript (Web)

---

## Summary of Disparities

| Feature | Android | iOS | Web |
|---------|---------|-----|-----|
| Service Alerts Display | ✅ | ❌ Fetched but not shown | ✅ |
| Bike Toggle in Settings | ✅ Persisted | ✅ Persisted | ⚠️ Runtime only |
| Station Search in Settings | ❌ Main settings | ✅ | ✅ |

**Note on Line Badges:** Android/iOS widgets show max 2 badges due to space constraints. This is intentional UX, not a bug. Main apps show all badges.

---

## Task 1: Add Service Alerts UI to iOS

**Files:**
- Modify: `ios/CommuteOptimizer/CommuteOptimizer/Views/CommuteTab/CommuteView.swift`
- Modify: `ios/CommuteOptimizer/CommuteOptimizer/Views/Shared/LineBadge.swift` (if needed)

**Context:** iOS already fetches alerts via `MtaAlertsService.swift` and returns them in `CommuteResponse`. The UI just needs to display them.

**Step 1: Read the current CommuteView structure**

Run: Open `ios/CommuteOptimizer/CommuteOptimizer/Views/CommuteTab/CommuteView.swift` and understand where alerts should appear (after weather, before options).

**Step 2: Add AlertsSection view**

Create a new view component that displays alerts similar to Android's implementation:

```swift
struct AlertsSection: View {
    let alerts: [ServiceAlert]

    var body: some View {
        if !alerts.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(alerts.prefix(3), id: \.headerText) { alert in
                    AlertCard(alert: alert)
                }
            }
            .padding(.horizontal)
        }
    }
}

struct AlertCard: View {
    let alert: ServiceAlert

    private var effectColor: Color {
        switch alert.effect {
        case "NO_SERVICE": return .red
        case "REDUCED_SERVICE": return .orange
        case "SIGNIFICANT_DELAYS": return .yellow
        default: return .gray
        }
    }

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(effectColor)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    ForEach(alert.routeIds.prefix(4), id: \.self) { route in
                        LineBadge(line: route, size: 16)
                    }
                }
                Text(alert.headerText)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }
        }
        .padding(10)
        .background(effectColor.opacity(0.1))
        .cornerRadius(8)
    }
}
```

**Step 3: Integrate AlertsSection into CommuteView**

Add after weather display, before the options list:

```swift
// In CommuteView body, after weather section:
if let alerts = viewModel.commuteResponse?.alerts, !alerts.isEmpty {
    AlertsSection(alerts: alerts)
}
```

**Step 4: Build and test**

Run: `cd ios/CommuteOptimizer && xcodebuild -scheme CommuteOptimizer -destination 'platform=iOS Simulator,name=iPhone 15' build`

Expected: Build succeeds. Launch app and verify alerts appear when there are service disruptions.

**Step 5: Commit**

```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Views/
git commit -m "$(cat <<'EOF'
feat(ios): add service alerts display to CommuteView

Display up to 3 service alerts with color-coded severity and
line badges, matching Android's alert display behavior.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Persist Bike Toggle in Web Settings

**Files:**
- Modify: `web/settings.html` - Add bike toggle UI
- Modify: `web/index.html` - Load persisted setting on page load

**Context:** Web has a bike toggle in the header but it resets on page refresh. Android/iOS persist this setting.

**Step 1: Add bike toggle to settings.html**

Add a toggle in the settings page, after the API keys card:

```html
<!-- Add after API keys card, before Home Location card -->
<div class="card">
  <h3>Display Options</h3>
  <label class="toggle-row">
    <input type="checkbox" id="showBikeOptions" checked>
    <span>Show bike-to-transit options</span>
  </label>
</div>
```

Add CSS for toggle row:

```css
.toggle-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  cursor: pointer;
}

.toggle-row input[type="checkbox"] {
  width: 20px;
  height: 20px;
}
```

**Step 2: Update settings object and save/load logic**

In `loadSettings()`:
```javascript
const defaultSettings = {
  // ... existing fields
  showBikeOptions: true  // Add this field
};
```

In `renderSettings(settings)`:
```javascript
document.getElementById('showBikeOptions').checked = settings.showBikeOptions ?? true;
```

In `saveSettings()`:
```javascript
const settings = {
  // ... existing fields
  showBikeOptions: document.getElementById('showBikeOptions').checked
};
```

**Step 3: Update index.html to load persisted setting**

In `loadCommute()`, after loading settings:
```javascript
// Set bike toggle from persisted settings
const bikeToggle = document.getElementById('bikeToggle');
if (bikeToggle && settings.showBikeOptions !== undefined) {
  bikeToggle.checked = settings.showBikeOptions;
}
```

**Step 4: Test the flow**

1. Open settings.html, uncheck "Show bike-to-transit options", save
2. Open index.html, verify bike toggle is unchecked
3. Refresh page, verify setting persists

**Step 5: Commit**

```bash
git add web/settings.html web/index.html
git commit -m "$(cat <<'EOF'
feat(web): persist bike toggle setting to localStorage

Add showBikeOptions toggle to settings page and load the
persisted value on the main commute page, matching Android/iOS.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Add Station Search to Android Settings

**Files:**
- Modify: `android/app/src/main/res/layout/fragment_settings.xml` - Add search inputs
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/ui/SettingsFragment.kt` - Add filter logic

**Context:** Android's LiveTrainsConfigActivity has search, but SettingsFragment doesn't. iOS and Web both have search in their station selection UI.

**Step 1: Add search input to fragment_settings.xml**

Add a search TextInputLayout before each station ChipGroup:

```xml
<!-- Before bikeStationsChipGroup -->
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/bikeSearchLayout"
    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="Search bike stations"
    app:startIconDrawable="@drawable/ic_search">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/bikeSearchInput"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="text"
        android:imeOptions="actionSearch" />
</com.google.android.material.textfield.TextInputLayout>

<!-- Before liveStationsChipGroup -->
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/liveSearchLayout"
    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="Search live train stations"
    app:startIconDrawable="@drawable/ic_search">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/liveSearchInput"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="text"
        android:imeOptions="actionSearch" />
</com.google.android.material.textfield.TextInputLayout>
```

**Step 2: Add search icon drawable if missing**

Check if `@drawable/ic_search` exists. If not, use Material icon or create vector:

```xml
<!-- res/drawable/ic_search.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#666666"
        android:pathData="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"/>
</vector>
```

**Step 3: Add TextWatcher for filtering in SettingsFragment.kt**

```kotlin
private lateinit var bikeSearchInput: TextInputEditText
private lateinit var liveSearchInput: TextInputEditText
private var allBikeStations: List<Station> = emptyList()
private var allLiveStations: List<Station> = emptyList()

// In onViewCreated:
bikeSearchInput = view.findViewById(R.id.bikeSearchInput)
liveSearchInput = view.findViewById(R.id.liveSearchInput)

bikeSearchInput.addTextChangedListener(object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) {
        filterBikeStations(s?.toString()?.trim() ?: "")
    }
})

liveSearchInput.addTextChangedListener(object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) {
        filterLiveStations(s?.toString()?.trim() ?: "")
    }
})

// Add filter functions:
private fun filterBikeStations(query: String) {
    val filtered = if (query.isEmpty()) {
        allBikeStations
    } else {
        allBikeStations.filter { station ->
            station.name.contains(query, ignoreCase = true) ||
            station.lines.any { it.contains(query, ignoreCase = true) }
        }
    }
    renderBikeStationChips(filtered)
}

private fun filterLiveStations(query: String) {
    val filtered = if (query.isEmpty()) {
        allLiveStations
    } else {
        allLiveStations.filter { station ->
            station.name.contains(query, ignoreCase = true) ||
            station.lines.any { it.contains(query, ignoreCase = true) }
        }
    }
    renderLiveStationChips(filtered)
}
```

**Step 4: Store full station list when loading**

Modify the station loading to save the full list before filtering:

```kotlin
// When loading stations:
allBikeStations = sortedStations
allLiveStations = sortedStations
renderBikeStationChips(allBikeStations)
renderLiveStationChips(allLiveStations)
```

**Step 5: Build and test**

Run: `cd android && ./gradlew assembleDebug`

Expected: Build succeeds. Open Settings, type in search box, stations filter by name/line.

**Step 6: Commit**

```bash
git add android/app/src/main/res/layout/fragment_settings.xml
git add android/app/src/main/res/drawable/ic_search.xml
git add android/app/src/main/java/com/commuteoptimizer/widget/ui/SettingsFragment.kt
git commit -m "$(cat <<'EOF'
feat(android): add station search to Settings screen

Add search inputs for bike stations and live train stations
with real-time filtering by station name or line, matching
iOS and Web station selection UX.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Final Verification

**Step 1: Test each platform**

- **Android:** Open Settings, search for stations, toggle bike options, verify commute view
- **iOS:** Open app, verify alerts display when there are service disruptions
- **Web:** Open settings, toggle bike options, verify persists on commute page

**Step 2: Create summary commit (optional)**

If all tasks are complete on same branch:

```bash
git log --oneline -3  # Verify all feature commits
```

---

## Completion Criteria

- [ ] iOS displays service alerts in CommuteView (color-coded, max 3)
- [ ] Web bike toggle persists to localStorage and loads on page refresh
- [ ] Android Settings screen has working station search for both bike and live stations
- [ ] All platforms build successfully
- [ ] No regressions in existing functionality
