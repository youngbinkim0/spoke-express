# Commute Optimizer Android Widget

A 4x2 Android home screen widget that displays the top 3 commute options with live train times, fetching data from the backend API.

## Requirements

- Android SDK (API 26+)
- Java 17 or higher (required by Android Gradle Plugin 8.2.0)
- Android Studio Hedgehog (2023.1.1) or later recommended

## Building

### Command Line

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug APK on connected device
./gradlew installDebug
```

### Android Studio

1. Open the `android/` folder in Android Studio
2. Wait for Gradle sync to complete
3. Click Run > Run 'app' or press Shift+F10

## Installation

1. Build the debug APK: `./gradlew assembleDebug`
2. Install on device: `adb install app/build/outputs/apk/debug/app-debug.apk`
3. Long-press on home screen and add the "Commute Options" widget
4. Configure the API URL in the setup screen

## Features

- **4x2 Widget Layout**: Shows top 3 commute options at a glance
- **Weather Display**: Current temperature and weather condition
- **Live Train Times**: Next arrival times for each option
- **MTA Colors**: Authentic subway line colors
- **Manual Refresh**: Tap refresh icon to update
- **Auto Updates**: Background refresh every 15 minutes via WorkManager
- **Error Handling**: Graceful error display with tap-to-retry

## Configuration

When adding the widget, you'll be prompted to enter:
- **API URL**: Your commute optimizer backend server URL (e.g., `http://192.168.1.100:8888`)

The widget allows cleartext HTTP traffic to local network addresses for development.

## Project Structure

```
app/src/main/
├── java/com/commuteoptimizer/widget/
│   ├── CommuteWidgetProvider.kt      # Main widget provider
│   ├── CommuteWidgetConfigActivity.kt # Configuration activity
│   ├── CommuteUpdateWorker.kt        # Background update worker
│   ├── data/
│   │   ├── api/
│   │   │   ├── CommuteApiService.kt  # Retrofit API interface
│   │   │   └── RetrofitClient.kt     # Network client
│   │   ├── models/CommuteModels.kt   # Data classes
│   │   └── CommuteRepository.kt      # Data layer
│   └── util/
│       ├── MtaColors.kt              # MTA line colors
│       └── WidgetPreferences.kt      # SharedPreferences wrapper
├── res/
│   ├── layout/
│   │   ├── widget_commute.xml        # Main widget layout
│   │   ├── widget_option_item.xml    # Option row layout
│   │   ├── widget_loading.xml        # Loading state
│   │   ├── widget_error.xml          # Error state
│   │   └── activity_config.xml       # Config activity
│   ├── drawable/                     # Icons and backgrounds
│   ├── xml/
│   │   ├── widget_commute_info.xml   # Widget metadata
│   │   └── network_security_config.xml
│   └── values/                       # Colors, strings, themes
└── AndroidManifest.xml
```

## Dependencies

- Retrofit 2.9.0 - HTTP client
- Gson - JSON parsing
- OkHttp 4.12.0 - HTTP client with logging
- Kotlin Coroutines 1.7.3 - Async operations
- WorkManager 2.9.0 - Background scheduling
- Material Components - UI components
