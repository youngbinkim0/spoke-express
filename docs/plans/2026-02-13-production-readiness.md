# Production Readiness Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the NYC Commute Optimizer production-ready across all three platforms (iOS, Android, Web) — scrub private data, fix all bugs, and meet App Store / Play Store requirements.

**Architecture:** No structural changes. This is a hardening pass — defensive coding, security scrubbing, and store compliance additions.

**Tech Stack:** Swift/SwiftUI (iOS), Kotlin (Android), Vanilla JS (Web), git filter-repo (history rewrite)

---

## Phase 1: Security — Scrub Private Data from Git History

### Task 1: Install git-filter-repo and scrub `.env` from all commits

The `.env` file was committed in `b9f4cdc` and contains live API keys (Google Maps, OpenWeatherMap, Cloudflare token). Even though `.gitignore` now excludes it, the keys are in git history.

**Files:**
- Modify: git history (all commits touching `.env`)

**Step 1: Back up the repo**
```bash
cp -r /Users/youngbinkim/Git/commute-optimizer /Users/youngbinkim/Git/commute-optimizer-backup
```

**Step 2: Install git-filter-repo**
```bash
brew install git-filter-repo
```

**Step 3: Remove `.env` from all history**
```bash
cd /Users/youngbinkim/Git/commute-optimizer
git filter-repo --path .env --invert-paths --force
```

**Step 4: Verify .env is gone from history**
```bash
git log --all --oneline -- .env
```
Expected: No output (empty)

**Step 5: Verify .env still exists as untracked file**
```bash
ls -la .env
```
Expected: File exists (it's gitignored, just removed from history)

---

### Task 2: Scrub Cloudflare account ID from `wrangler.toml` history

The `wrangler.toml` contains a real Cloudflare account ID (`ae1aab49185436972769f65c1e037467`) committed in `1ba78a0`.

**Files:**
- Modify: `cloudflare-worker/wrangler.toml` (replace account_id with placeholder)

**Step 1: Replace account_id in wrangler.toml with placeholder**

Change line 4 of `cloudflare-worker/wrangler.toml` from:
```toml
account_id = "ae1aab49185436972769f65c1e037467"
```
to:
```toml
# Set via: wrangler login (or CLOUDFLARE_ACCOUNT_ID env var)
# account_id = "your_account_id"
```

**Step 2: Use git-filter-repo to replace the account ID in all commits**
```bash
git filter-repo --replace-text <(echo 'ae1aab49185436972769f65c1e037467==>YOUR_CLOUDFLARE_ACCOUNT_ID') --force
```

**Step 3: Verify the account ID is scrubbed**
```bash
git log -p --all -S 'ae1aab49' -- cloudflare-worker/wrangler.toml
```
Expected: No output

**Step 4: Commit the wrangler.toml change**
```bash
git add cloudflare-worker/wrangler.toml
git commit -m "chore: remove cloudflare account_id from wrangler.toml"
```

---

### Task 3: Add `.env` safety and update `.env.example`

**Files:**
- Modify: `.env.example` (add Cloudflare token placeholder)
- Modify: `.gitignore` (add extra safety entries)

**Step 1: Update `.env.example` to include the Cloudflare token placeholder**

Replace contents of `.env.example` with:
```env
# Google Routes API (for bike time calculations)
# Get key at: https://console.cloud.google.com/
# Enable: Routes API
GOOGLE_MAPS_API_KEY=your_google_maps_api_key

# OpenWeatherMap API (for weather data)
# Get key at: https://openweathermap.org/api
OPENWEATHER_API_KEY=your_openweather_api_key

# Cloudflare API Token (for deploying worker)
# Get token at: https://dash.cloudflare.com/profile/api-tokens
CLOUDFLARE_API_TOKEN=your_cloudflare_api_token

# Transiter URL (default for Docker Compose setup)
TRANSITER_URL=http://localhost:8080

# Server port
PORT=3000
```

**Step 2: Verify `.gitignore` already covers `.env`**

Confirm `.env` is listed in `.gitignore` (it is — line 8). No changes needed.

**Step 3: Commit**
```bash
git add .env.example
git commit -m "chore: update .env.example with all required keys"
```

---

### Task 4: Rotate all compromised API keys

This is a **manual step** for the developer. Create a checklist.

**Action items (do these manually after the git scrub):**
1. **Google Maps API Key**: Go to https://console.cloud.google.com/apis/credentials → delete old key → create new key → update `.env` and Cloudflare Worker secret
2. **OpenWeatherMap API Key**: Go to https://home.openweathermap.org/api_keys → regenerate key → update `.env`
3. **Cloudflare API Token**: Go to https://dash.cloudflare.com/profile/api-tokens → revoke old token → create new token → update `.env`
4. **Update app settings**: Re-enter API keys in each platform's settings after rotation

---

## Phase 2: Bug Fixes — iOS (Swift)

### Task 5: Fix force-unwrap crash on URL construction in MtaAlertsService

`URL(string:)!` will crash if the URL string is malformed.

**Files:**
- Modify: `ios/CommuteOptimizer/CommuteOptimizer/Services/MtaAlertsService.swift:20`

**Step 1: Replace force unwrap with guard**

Change line 20 from:
```swift
var request = URLRequest(url: URL(string: alertsURL)!)
```
to:
```swift
guard let url = URL(string: alertsURL) else {
    print("MtaAlertsService: Invalid alerts URL: \(alertsURL)")
    return []
}
var request = URLRequest(url: url)
```

**Step 2: Run iOS tests**
```bash
cd ios/CommuteOptimizer && xcodebuild -scheme CommuteOptimizer -destination 'platform=iOS Simulator,name=iPhone 16' test 2>&1 | tail -20
```
Expected: Tests pass

**Step 3: Commit**
```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Services/MtaAlertsService.swift
git commit -m "fix(ios): replace force-unwrap with guard on alerts URL construction"
```

---

### Task 6: Fix force-unwrap crash on URL construction in MtaApiService

**Files:**
- Modify: `ios/CommuteOptimizer/CommuteOptimizer/Services/MtaApiService.swift:95`

**Step 1: Replace force unwrap with guard**

Change line 95 from:
```swift
var request = URLRequest(url: URL(string: baseURL + feedName)!)
```
to:
```swift
guard let url = URL(string: baseURL + feedName) else {
    throw URLError(.badURL)
}
var request = URLRequest(url: url)
```

This is inside a `throws` function so throwing is appropriate (callers already handle errors).

**Step 2: Run iOS tests**
```bash
cd ios/CommuteOptimizer && xcodebuild -scheme CommuteOptimizer -destination 'platform=iOS Simulator,name=iPhone 16' test 2>&1 | tail -20
```
Expected: Tests pass

**Step 3: Commit**
```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Services/MtaApiService.swift
git commit -m "fix(ios): replace force-unwrap with guard on MTA feed URL construction"
```

---

### Task 7: Add bounds checking to iOS ProtobufReader.readVarint()

Malformed MTA data could cause `shift` to exceed 63 bits, leading to undefined behavior.

**Files:**
- Modify: `ios/CommuteOptimizer/CommuteOptimizer/Utilities/ProtobufReader.swift:17-29`

**Step 1: Add shift bounds check**

Replace `readVarint()` (lines 17-29) with:
```swift
func readVarint() -> UInt64 {
    var result: UInt64 = 0
    var shift: UInt64 = 0

    while position < data.count {
        let byte = data[position]
        position += 1
        result |= UInt64(byte & 0x7F) << shift
        if (byte & 0x80) == 0 { break }
        shift += 7
        if shift >= 64 { break } // Prevent overflow on malformed data
    }
    return result
}
```

**Step 2: Run iOS tests (includes ProtobufReaderTests)**
```bash
cd ios/CommuteOptimizer && xcodebuild -scheme CommuteOptimizer -destination 'platform=iOS Simulator,name=iPhone 16' test 2>&1 | tail -20
```
Expected: Tests pass

**Step 3: Commit**
```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Utilities/ProtobufReader.swift
git commit -m "fix(ios): add bounds check to ProtobufReader.readVarint() for malformed data"
```

---

### Task 8: Add PrivacyInfo.xcprivacy for iOS App Store compliance

Apple requires a privacy manifest since Spring 2024. This app uses `UserDefaults` and `URLSession` which are listed as "required reason APIs."

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/PrivacyInfo.xcprivacy`
- Modify: `ios/CommuteOptimizer/project.yml` (add to resources)

**Step 1: Create PrivacyInfo.xcprivacy**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>NSPrivacyTracking</key>
    <false/>
    <key>NSPrivacyTrackingDomains</key>
    <array/>
    <key>NSPrivacyCollectedDataTypes</key>
    <array/>
    <key>NSPrivacyAccessedAPITypes</key>
    <array>
        <dict>
            <key>NSPrivacyAccessedAPIType</key>
            <string>NSPrivacyAccessedAPICategoryUserDefaults</string>
            <key>NSPrivacyAccessedAPITypeReasons</key>
            <array>
                <string>CA92.1</string>
            </array>
        </dict>
    </array>
</dict>
</plist>
```

Reason `CA92.1`: "Access info from same app, same or an associated app group" — matches our UserDefaults + App Groups usage.

**Step 2: Add to project.yml resources**

In `ios/CommuteOptimizer/project.yml`, under the `CommuteOptimizer` target's `resources:` key, add:
```yaml
    resources:
      - path: CommuteOptimizer/Resources
      - path: CommuteOptimizer/Assets.xcassets
      - path: CommuteOptimizer/PrivacyInfo.xcprivacy
```

**Step 3: Regenerate Xcode project (if using XcodeGen)**
```bash
cd ios/CommuteOptimizer && xcodegen generate
```

**Step 4: Commit**
```bash
git add ios/CommuteOptimizer/CommuteOptimizer/PrivacyInfo.xcprivacy ios/CommuteOptimizer/project.yml
git commit -m "feat(ios): add PrivacyInfo.xcprivacy for App Store compliance"
```

---

## Phase 3: Bug Fixes — Android (Kotlin)

### Task 9: Replace `!!` assertions with idiomatic Kotlin

Three `!!` usages identified. Two are safe (after null checks) but non-idiomatic. One is redundant.

**Files:**
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/data/api/MtaApiService.kt:410-413`
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/LiveTrainsConfigActivity.kt:191-201`
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/service/CommuteCalculator.kt:144-146`

**Step 1: Fix MtaApiService.kt — use getOrPut instead of containsKey + !!**

Replace lines 410-413:
```kotlin
if (!groups.containsKey(key)) {
    groups[key] = mutableListOf()
}
val groupList = groups[key]!!
```
with:
```kotlin
val groupList = groups.getOrPut(key) { mutableListOf() }
```

**Step 2: Fix LiveTrainsConfigActivity.kt — use local val binding**

Replace lines 191-201 pattern:
```kotlin
if (selectedStationId == null) {
    Log.d(TAG, "Validation failed: no station selected")
    showStatus("Please select a station", isError = true)
    return
}

Log.d(TAG, "Saving station: $selectedStationId")

try {
    // Save the station for this widget
    prefs.setLiveTrainsWidgetStation(appWidgetId, selectedStationId!!)
```
with:
```kotlin
val stationId = selectedStationId
if (stationId == null) {
    Log.d(TAG, "Validation failed: no station selected")
    showStatus("Please select a station", isError = true)
    return
}

Log.d(TAG, "Saving station: $stationId")

try {
    // Save the station for this widget
    prefs.setLiveTrainsWidgetStation(appWidgetId, stationId)
```

**Step 3: Fix CommuteCalculator.kt — use safe call with let**

Replace lines 144-146:
```kotlin
if (response.isSuccessful && response.body() != null) {
    android.util.Log.d("CommuteCalc", "Weather fetched successfully: ${response.body()?.main?.temp}")
    parseWeatherResponse(response.body()!!)
```
with:
```kotlin
val body = response.body()
if (response.isSuccessful && body != null) {
    android.util.Log.d("CommuteCalc", "Weather fetched successfully: ${body.main?.temp}")
    parseWeatherResponse(body)
```

**Step 4: Build Android**
```bash
cd android && ./gradlew assembleDebug 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

**Step 5: Commit**
```bash
git add -A android/
git commit -m "fix(android): replace force-unwrap (!!) with idiomatic Kotlin null handling"
```

---

### Task 10: Add bounds checking to Android ProtobufReader.readVarint()

**Files:**
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/data/api/MtaApiService.kt:90-99`

**Step 1: Add shift bounds check**

Replace `readVarint()` (lines 90-100) with:
```kotlin
fun readVarint(): Long {
    var result = 0L
    var shift = 0
    while (pos < buffer.size) {
        val byte = buffer[pos++].toInt() and 0xFF
        result = result or ((byte and 0x7F).toLong() shl shift)
        if ((byte and 0x80) == 0) break
        shift += 7
        if (shift >= 64) break // Prevent overflow on malformed data
    }
    return result
}
```

**Step 2: Build Android**
```bash
cd android && ./gradlew assembleDebug 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add android/app/src/main/java/com/commuteoptimizer/widget/data/api/MtaApiService.kt
git commit -m "fix(android): add bounds check to readVarint() for malformed protobuf data"
```

---

### Task 11: Fix silent error swallowing in CommuteCalculator.kt

Empty catch block on line 293 silently swallows Google Routes API errors.

**Files:**
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/service/CommuteCalculator.kt:293`

**Step 1: Add logging to empty catch block**

Replace line 293:
```kotlin
} catch (e: Exception) { }
```
with:
```kotlin
} catch (e: Exception) {
    android.util.Log.w("CommuteCalc", "Google Routes API failed: ${e.message}")
}
```

**Step 2: Build Android**
```bash
cd android && ./gradlew assembleDebug 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add android/app/src/main/java/com/commuteoptimizer/widget/service/CommuteCalculator.kt
git commit -m "fix(android): log Google Routes API errors instead of silently swallowing"
```

---

### Task 12: Enable R8 minification for Android release builds

`isMinifyEnabled = false` means the release APK ships unoptimized with all debug symbols.

**Files:**
- Modify: `android/app/build.gradle.kts:19-25`
- Create: `android/app/proguard-rules.pro` (if missing)

**Step 1: Check if proguard-rules.pro exists**
```bash
ls android/app/proguard-rules.pro
```

**Step 2: Create proguard-rules.pro with Retrofit/Gson keep rules**

```pro
# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.commuteoptimizer.widget.data.models.** { *; }
-keep class com.commuteoptimizer.widget.data.api.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
```

**Step 3: Enable minification in build.gradle.kts**

Change lines 19-25 from:
```kotlin
release {
    isMinifyEnabled = false
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```
to:
```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```

**Step 4: Build release to verify ProGuard rules work**
```bash
cd android && ./gradlew assembleRelease 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL (no ProGuard errors)

**Step 5: Commit**
```bash
git add android/app/build.gradle.kts android/app/proguard-rules.pro
git commit -m "feat(android): enable R8 minification for release builds"
```

---

## Phase 4: Bug Fixes — Web (JavaScript)

### Task 13: Add bounds checking to Web ProtobufReader.readVarint()

JavaScript bitwise operations are 32-bit. Large varints will silently lose precision.

**Files:**
- Modify: `web/mta-api.js:44-54`

**Step 1: Add shift bounds check**

Replace `readVarint()` (lines 44-54) with:
```javascript
readVarint() {
    let result = 0;
    let shift = 0;
    while (this.pos < this.buffer.length) {
        const byte = this.buffer[this.pos++];
        result |= (byte & 0x7f) << shift;
        if ((byte & 0x80) === 0) break;
        shift += 7;
        if (shift >= 35) break; // JS bitwise ops are 32-bit; prevent overflow
    }
    return result >>> 0; // Ensure unsigned
}
```

Note: JS bitwise ops are 32-bit signed. `>>> 0` converts to unsigned 32-bit. Shift limit is 35 (5 bytes × 7 bits = 35, which covers full 32-bit range).

**Step 2: Test the web app**
```bash
cd web && python3 -m http.server 8080 &
# Manual test: open http://localhost:8080 and verify arrivals load
kill %1
```

**Step 3: Commit**
```bash
git add web/mta-api.js
git commit -m "fix(web): add bounds check to readVarint() for malformed protobuf data"
```

---

## Phase 5: iOS App Store Metadata

### Task 14: Verify Xcode project configuration

Review and fix the Xcode project settings for App Store submission.

**Files:**
- Modify: `ios/CommuteOptimizer/project.yml` (set DEVELOPMENT_TEAM)

**Step 1: Set DEVELOPMENT_TEAM**

The developer must provide their Apple Developer Team ID. In `project.yml` line 12, change:
```yaml
DEVELOPMENT_TEAM: ""
```
to:
```yaml
DEVELOPMENT_TEAM: "XXXXXXXXXX"  # Replace with your Apple Developer Team ID
```

Note: This is a **manual step** — the developer must look up their Team ID at https://developer.apple.com/account → Membership Details.

**Step 2: Verify app icon is configured**

Already confirmed: `Assets.xcassets/AppIcon.appiconset/AppIcon.png` (1024x1024 universal) — this is the modern single-icon format. ✅

**Step 3: Verify entitlements are correct**

Already confirmed: Both app and widget have `group.com.commuteoptimizer` app group. ✅

**Step 4: Commit if any changes**
```bash
git add ios/CommuteOptimizer/project.yml
git commit -m "chore(ios): set development team for App Store signing"
```

---

## Phase 6: Final Verification

### Task 15: Full build verification across all platforms

**Step 1: Run iOS tests**
```bash
cd ios/CommuteOptimizer && xcodebuild -scheme CommuteOptimizer -destination 'platform=iOS Simulator,name=iPhone 16' test 2>&1 | tail -30
```
Expected: All tests pass

**Step 2: Build Android debug + release**
```bash
cd android && ./gradlew assembleDebug assembleRelease 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL for both

**Step 3: Verify web app loads**
```bash
cd web && python3 -m http.server 8080 &
sleep 2
curl -s http://localhost:8080 | head -5
kill %1
```
Expected: HTML content returned

**Step 4: Verify no secrets remain in git history**
```bash
git log -p --all -S 'AIzaSy' 2>&1 | head -5
git log -p --all -S '2a8158128' 2>&1 | head -5
git log -p --all -S 'TeAapxH' 2>&1 | head -5
git log -p --all -S 'ae1aab49' 2>&1 | head -5
```
Expected: No output for all four

**Step 5: Final commit with clean state**
```bash
git status
```
Expected: Clean working tree (nothing to commit)

---

## Summary of Changes

| Phase | Tasks | Platform | Type |
|-------|-------|----------|------|
| 1: Security | 1-4 | All | Git history scrub, key rotation |
| 2: iOS Bugs | 5-8 | iOS | Force unwrap fixes, overflow guard, privacy manifest |
| 3: Android Bugs | 9-12 | Android | Null safety, overflow guard, error logging, R8 |
| 4: Web Bugs | 13 | Web | Overflow guard |
| 5: iOS Store | 14 | iOS | Dev team, project config |
| 6: Verification | 15 | All | Build + test + history audit |

### Manual Steps Required (developer only):
1. **Rotate API keys** (Task 4) — Google, OpenWeatherMap, Cloudflare
2. **Set DEVELOPMENT_TEAM** (Task 14) — Apple Developer Team ID
3. **App Store Connect** — Screenshots, description, privacy questionnaire (outside scope of this plan)
4. **Play Store Console** — Create app listing, upload AAB, fill out content rating (outside scope of this plan)
