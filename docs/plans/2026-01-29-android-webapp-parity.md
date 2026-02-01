# Android-Webapp Full Parity Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the Android app look and feel identical to the webapp, with two widget types (Live Trains and Commute Routing).

**Architecture:**
1. Create a full Android app with Activities matching webapp pages (Commute, Live Trains, Settings)
2. Update theme to dark mode matching webapp (#1a1a2e background)
3. Create two widget variants: Live Trains Widget and Commute Widget
4. Add missing features: arrival time display, service alerts, and proper styling

**Tech Stack:** Kotlin, Android Jetpack, Material Design, RecyclerView, Widgets

---

## Summary of Changes

| Component | Current State | Target State |
|-----------|--------------|--------------|
| Theme | Light mode | Dark mode (#1a1a2e) |
| Main App | Config activity only | Full app with 3 screens |
| Commute Screen | N/A | Match index.html |
| Live Trains Screen | N/A | Match arrivals.html |
| Settings Screen | Basic config | Match settings.html |
| Commute Widget | Shows 3 options | Add arrival time + alerts |
| Live Trains Widget | N/A | New widget type |

---

## Task 1: Update Theme to Dark Mode

**Files:**
- Modify: `android/app/src/main/res/values/colors.xml`
- Modify: `android/app/src/main/res/values/themes.xml`

**Purpose:** Match webapp's dark theme (#1a1a2e background, #eee text)

### colors.xml - Replace entire file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- App colors - matching webapp -->
    <color name="primary">#4ecca3</color>
    <color name="primary_dark">#3db892</color>
    <color name="accent">#4ecca3</color>

    <!-- Background colors -->
    <color name="background_dark">#1a1a2e</color>
    <color name="card_background">#16213e</color>
    <color name="card_header">#1a3a5c</color>
    <color name="divider">#0f3460</color>

    <!-- Text colors -->
    <color name="text_primary">#eeeeee</color>
    <color name="text_secondary">#888888</color>
    <color name="text_muted">#555555</color>

    <!-- Widget colors -->
    <color name="widget_background">#16213e</color>
    <color name="widget_text_primary">#eeeeee</color>
    <color name="widget_text_secondary">#888888</color>
    <color name="widget_divider">#0f3460</color>

    <!-- Rank badge colors -->
    <color name="rank_1">#FFD700</color>
    <color name="rank_2">#C0C0C0</color>
    <color name="rank_3">#CD7F32</color>

    <!-- Alert colors -->
    <color name="alert_background">#ff6b6b22</color>
    <color name="alert_border">#ff6b6b</color>
    <color name="alert_text">#ff6b6b</color>

    <!-- Weather colors -->
    <color name="weather_good">#4ecca3</color>
    <color name="weather_bad">#F44336</color>

    <!-- Arrival badge colors -->
    <color name="arrival_soon">#4ecca3</color>
    <color name="arrival_normal">#1a3a5c</color>

    <!-- MTA Line Colors (official) -->
    <color name="mta_1">#EE352E</color>
    <color name="mta_2">#EE352E</color>
    <color name="mta_3">#EE352E</color>
    <color name="mta_4">#00933C</color>
    <color name="mta_5">#00933C</color>
    <color name="mta_6">#00933C</color>
    <color name="mta_7">#B933AD</color>
    <color name="mta_A">#0039A6</color>
    <color name="mta_C">#0039A6</color>
    <color name="mta_E">#0039A6</color>
    <color name="mta_B">#FF6319</color>
    <color name="mta_D">#FF6319</color>
    <color name="mta_F">#FF6319</color>
    <color name="mta_M">#FF6319</color>
    <color name="mta_G">#6CBE45</color>
    <color name="mta_J">#996633</color>
    <color name="mta_Z">#996633</color>
    <color name="mta_L">#A7A9AC</color>
    <color name="mta_N">#FCCC0A</color>
    <color name="mta_Q">#FCCC0A</color>
    <color name="mta_R">#FCCC0A</color>
    <color name="mta_W">#FCCC0A</color>
    <color name="mta_S">#808183</color>
    <color name="mta_default">#808183</color>
</resources>
```

### themes.xml - Replace entire file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.CommuteOptimizer" parent="Theme.MaterialComponents.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryDark">@color/primary_dark</item>
        <item name="colorAccent">@color/accent</item>
        <item name="android:windowBackground">@color/background_dark</item>
        <item name="android:statusBarColor">@color/background_dark</item>
        <item name="android:navigationBarColor">@color/background_dark</item>
        <item name="android:textColorPrimary">@color/text_primary</item>
        <item name="android:textColorSecondary">@color/text_secondary</item>
    </style>

    <style name="CardStyle">
        <item name="android:background">@color/card_background</item>
        <item name="cornerRadius">12dp</item>
    </style>

    <style name="HeaderText">
        <item name="android:textColor">@color/text_primary</item>
        <item name="android:textSize">18sp</item>
        <item name="android:textStyle">bold</item>
    </style>
</resources>
```

---

## Task 2: Create Main Activity with Bottom Navigation

**Files:**
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/MainActivity.kt`
- Create: `android/app/src/main/res/layout/activity_main.xml`
- Create: `android/app/src/main/res/menu/bottom_nav_menu.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Purpose:** Create main app with bottom navigation (Commute, Live Trains, Settings)

### activity_main.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_dark">

    <FrameLayout
        android:id="@+id/fragment_container"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/bottom_nav"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_nav"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:background="@color/card_background"
        app:itemTextColor="@color/text_secondary"
        app:itemIconTint="@color/text_secondary"
        app:menu="@menu/bottom_nav_menu"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### bottom_nav_menu.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_commute"
        android:icon="@drawable/ic_subway"
        android:title="Commute" />
    <item
        android:id="@+id/nav_live_trains"
        android:icon="@drawable/ic_train"
        android:title="Live Trains" />
    <item
        android:id="@+id/nav_settings"
        android:icon="@drawable/ic_settings"
        android:title="Settings" />
</menu>
```

### MainActivity.kt:

```kotlin
package com.commuteoptimizer.widget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_commute -> CommuteFragment()
                R.id.nav_live_trains -> LiveTrainsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> CommuteFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        // Load default fragment
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_commute
        }
    }
}
```

### Update AndroidManifest.xml:

Add MainActivity as the launcher activity:
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:theme="@style/Theme.CommuteOptimizer">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

---

## Task 3: Create Commute Fragment (matches index.html)

**Files:**
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/ui/CommuteFragment.kt`
- Create: `android/app/src/main/res/layout/fragment_commute.xml`
- Create: `android/app/src/main/res/layout/item_commute_option.xml`
- Create: `android/app/src/main/res/layout/item_alert.xml`
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/ui/CommuteAdapter.kt`

**Purpose:** Create the Commute screen matching webapp's index.html

### fragment_commute.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/swipe_refresh"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/background_dark"
        android:padding="16dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <!-- Header -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:layout_marginBottom="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="Commute Options"
                    android:textColor="@color/text_primary"
                    android:textSize="20sp"
                    android:textStyle="bold" />

                <CheckBox
                    android:id="@+id/bike_toggle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="🚲 Bike"
                    android:textColor="@color/text_secondary"
                    android:checked="true"
                    android:buttonTint="@color/primary" />

                <ImageButton
                    android:id="@+id/btn_refresh"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:src="@drawable/ic_refresh"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Refresh" />
            </LinearLayout>

            <!-- Weather Bar -->
            <LinearLayout
                android:id="@+id/weather_bar"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="@drawable/card_background"
                android:padding="16dp"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:layout_marginBottom="16dp">

                <TextView
                    android:id="@+id/weather_temp"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/text_primary"
                    android:textSize="28sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/weather_conditions"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="12dp"
                    android:textColor="@color/text_secondary" />

                <TextView
                    android:id="@+id/weather_warning"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="12dp"
                    android:background="@drawable/warning_badge"
                    android:paddingHorizontal="10dp"
                    android:paddingVertical="4dp"
                    android:text="Bad for biking"
                    android:textColor="#FFFFFF"
                    android:textSize="12sp"
                    android:visibility="gone" />

                <View
                    android:layout_width="0dp"
                    android:layout_height="0dp"
                    android:layout_weight="1" />

                <TextView
                    android:id="@+id/current_time"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textColor="@color/text_secondary" />
            </LinearLayout>

            <!-- Alerts Section -->
            <LinearLayout
                android:id="@+id/alerts_container"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:visibility="gone" />

            <!-- Options List -->
            <LinearLayout
                android:id="@+id/options_container"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

            <!-- Loading -->
            <ProgressBar
                android:id="@+id/progress"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:visibility="gone" />

            <!-- Error -->
            <TextView
                android:id="@+id/error_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:textColor="@color/alert_text"
                android:visibility="gone" />

            <!-- Auto refresh info -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:gravity="center"
                android:text="Auto-refreshes every 30 seconds"
                android:textColor="@color/text_muted"
                android:textSize="12sp" />

        </LinearLayout>
    </ScrollView>
</androidx.swiperefreshlayout.widget.SwipeRefreshLayout>
```

### item_commute_option.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/option_card_background"
    android:orientation="vertical"
    android:padding="16dp"
    android:layout_marginBottom="12dp">

    <!-- Header Row -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/option_rank"
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:background="@drawable/rank_badge_background"
            android:gravity="center"
            android:textColor="#FFFFFF"
            android:textSize="14sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/option_summary"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="12dp"
            android:textColor="@color/text_primary"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/option_duration"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold" />

        <ImageView
            android:id="@+id/expand_icon"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_expand"
            android:layout_marginStart="8dp" />
    </LinearLayout>

    <!-- Details Row -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/next_train_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Next train: "
            android:textColor="@color/text_secondary"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/next_train_value"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/text_secondary"
            android:textSize="13sp" />

        <View
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_weight="1" />

        <TextView
            android:id="@+id/arrival_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Arrive: "
            android:textColor="@color/text_secondary"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/arrival_value"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/text_secondary"
            android:textSize="13sp" />
    </LinearLayout>

    <!-- Legs Row (icons and badges) -->
    <LinearLayout
        android:id="@+id/legs_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:gravity="center_vertical"
        android:orientation="horizontal" />

    <!-- Expandable Transfer Details -->
    <LinearLayout
        android:id="@+id/expanded_details"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:orientation="vertical"
        android:visibility="gone" />

</LinearLayout>
```

---

## Task 4: Create Live Trains Fragment (matches arrivals.html)

**Files:**
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/ui/LiveTrainsFragment.kt`
- Create: `android/app/src/main/res/layout/fragment_live_trains.xml`
- Create: `android/app/src/main/res/layout/item_station_card.xml`
- Create: `android/app/src/main/res/layout/item_direction.xml`

**Purpose:** Create the Live Trains screen matching webapp's arrivals.html

### fragment_live_trains.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/swipe_refresh"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/background_dark"
        android:padding="16dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <!-- Header -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:layout_marginBottom="16dp">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="Live Train Times"
                    android:textColor="@color/text_primary"
                    android:textSize="20sp"
                    android:textStyle="bold" />

                <ImageButton
                    android:id="@+id/btn_refresh"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:src="@drawable/ic_refresh"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Refresh" />
            </LinearLayout>

            <!-- Stations List -->
            <LinearLayout
                android:id="@+id/stations_container"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical" />

            <!-- Loading -->
            <ProgressBar
                android:id="@+id/progress"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:visibility="gone" />

            <!-- No Stations Message -->
            <TextView
                android:id="@+id/no_stations"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:padding="40dp"
                android:text="No stations configured.\nGo to Settings to select stations."
                android:textColor="@color/text_secondary"
                android:visibility="gone" />

            <!-- Auto refresh info -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:gravity="center"
                android:text="Auto-refreshes every 30 seconds"
                android:textColor="@color/text_muted"
                android:textSize="12sp" />

            <!-- Last updated -->
            <TextView
                android:id="@+id/last_updated"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:gravity="center"
                android:textColor="@color/text_muted"
                android:textSize="11sp" />

        </LinearLayout>
    </ScrollView>
</androidx.swiperefreshlayout.widget.SwipeRefreshLayout>
```

### item_station_card.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/card_background"
    android:orientation="vertical"
    android:layout_marginBottom="16dp">

    <!-- Station Header -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/card_header"
        android:padding="14dp"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/station_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold" />

        <LinearLayout
            android:id="@+id/station_lines"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal" />
    </LinearLayout>

    <!-- Line Groups -->
    <LinearLayout
        android:id="@+id/line_groups_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp" />

</LinearLayout>
```

---

## Task 5: Create Live Trains Widget

**Files:**
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/LiveTrainsWidgetProvider.kt`
- Create: `android/app/src/main/res/layout/widget_live_trains.xml`
- Create: `android/app/src/main/res/layout/widget_train_row.xml`
- Create: `android/app/src/main/res/xml/widget_live_trains_info.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Purpose:** Create a new Live Trains widget showing real-time arrivals

### widget_live_trains.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_background"
    android:orientation="vertical"
    android:padding="12dp">

    <!-- Header -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingBottom="8dp">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Live Trains"
            android:textColor="@color/widget_text_primary"
            android:textSize="14sp"
            android:textStyle="bold" />

        <ImageView
            android:id="@+id/btn_refresh"
            android:layout_width="20dp"
            android:layout_height="20dp"
            android:src="@drawable/ic_refresh" />
    </LinearLayout>

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="@color/widget_divider" />

    <!-- Train rows -->
    <include android:id="@+id/train_1" layout="@layout/widget_train_row" />
    <include android:id="@+id/train_2" layout="@layout/widget_train_row" />
    <include android:id="@+id/train_3" layout="@layout/widget_train_row" />
    <include android:id="@+id/train_4" layout="@layout/widget_train_row" />

    <!-- Last updated -->
    <TextView
        android:id="@+id/widget_updated"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="end"
        android:paddingTop="4dp"
        android:textColor="@color/widget_text_secondary"
        android:textSize="10sp" />

</LinearLayout>
```

### widget_train_row.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingVertical="6dp">

    <!-- Line Badge -->
    <TextView
        android:id="@+id/line_badge"
        android:layout_width="22dp"
        android:layout_height="22dp"
        android:background="@drawable/line_badge_background"
        android:gravity="center"
        android:textColor="#FFFFFF"
        android:textSize="11sp"
        android:textStyle="bold" />

    <!-- Direction Arrow -->
    <TextView
        android:id="@+id/direction_arrow"
        android:layout_width="24dp"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:textColor="@color/primary"
        android:textSize="16sp" />

    <!-- Headsign -->
    <TextView
        android:id="@+id/headsign"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:ellipsize="end"
        android:maxLines="1"
        android:textColor="@color/widget_text_secondary"
        android:textSize="12sp" />

    <!-- Arrival Times -->
    <LinearLayout
        android:id="@+id/arrivals_container"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="horizontal" />

</LinearLayout>
```

### widget_live_trains_info.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:updatePeriodMillis="900000"
    android:initialLayout="@layout/widget_loading"
    android:configure="com.commuteoptimizer.widget.CommuteWidgetConfigActivity"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:previewLayout="@layout/widget_live_trains" />
```

---

## Task 6: Update Commute Widget with Arrival Time & Alerts

**Files:**
- Modify: `android/app/src/main/res/layout/widget_option_item.xml`
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/CommuteWidgetProvider.kt`

**Purpose:** Add arrival time display and alert indicator to commute widget

### Updated widget_option_item.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingVertical="6dp">

    <!-- Rank Badge -->
    <TextView
        android:id="@+id/option_rank"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:background="@drawable/rank_badge_background"
        android:gravity="center"
        android:textColor="#FFFFFF"
        android:textSize="12sp"
        android:textStyle="bold" />

    <!-- Mode Icon -->
    <ImageView
        android:id="@+id/option_mode_icon"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:layout_marginStart="6dp" />

    <!-- Summary + Arrival Time -->
    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginHorizontal="6dp"
        android:orientation="vertical">

        <TextView
            android:id="@+id/option_summary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:maxLines="1"
            android:textColor="@color/widget_text_primary"
            android:textSize="12sp" />

        <TextView
            android:id="@+id/option_arrival"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/widget_text_secondary"
            android:textSize="10sp" />
    </LinearLayout>

    <!-- Duration -->
    <TextView
        android:id="@+id/option_duration"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/widget_text_primary"
        android:textSize="12sp"
        android:textStyle="bold" />

    <!-- Next Train Badge -->
    <TextView
        android:id="@+id/option_next_train"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="6dp"
        android:background="@drawable/next_train_background"
        android:paddingHorizontal="6dp"
        android:paddingVertical="2dp"
        android:textColor="#FFFFFF"
        android:textSize="10sp" />

</LinearLayout>
```

### Update CommuteWidgetProvider.kt bindOptionRow():

Add arrival time binding:
```kotlin
// In bindOptionRow(), add:
val arrivalId = getNestedViewId(context, containerResId, "option_arrival")
views.setTextViewText(arrivalId, "Arrive: ${option.arrivalTime}")
```

---

## Task 7: Add Settings Fragment

**Files:**
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/ui/SettingsFragment.kt`
- Create: `android/app/src/main/res/layout/fragment_settings.xml`

**Purpose:** Convert existing config activity to a fragment for the main app

### fragment_settings.xml:

Use the same layout as activity_config.xml but adapted for fragment use.

### SettingsFragment.kt:

Adapt CommuteWidgetConfigActivity logic into a Fragment.

---

## Task 8: Add Alert Indicator to Widget Header

**Files:**
- Modify: `android/app/src/main/res/layout/widget_commute.xml`
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/CommuteWidgetProvider.kt`

**Purpose:** Show alert indicator in widget when there are service alerts

### Add to widget_commute.xml header (after weather):

```xml
<ImageView
    android:id="@+id/alert_indicator"
    android:layout_width="16dp"
    android:layout_height="16dp"
    android:layout_marginEnd="8dp"
    android:src="@drawable/ic_alert"
    android:visibility="gone" />
```

### Update CommuteWidgetProvider.kt:

```kotlin
// In buildWidgetViews(), add:
if (data.alerts.isNotEmpty()) {
    views.setViewVisibility(R.id.alert_indicator, View.VISIBLE)
} else {
    views.setViewVisibility(R.id.alert_indicator, View.GONE)
}
```

---

## Task 9: Create Drawable Resources

**Files:**
- Create: `android/app/src/main/res/drawable/card_background.xml`
- Create: `android/app/src/main/res/drawable/option_card_background.xml`
- Create: `android/app/src/main/res/drawable/warning_badge.xml`
- Create: `android/app/src/main/res/drawable/line_badge_background.xml`
- Create: `android/app/src/main/res/drawable/ic_expand.xml`
- Create: `android/app/src/main/res/drawable/ic_alert.xml`
- Create: `android/app/src/main/res/drawable/ic_train.xml`
- Create: `android/app/src/main/res/drawable/ic_settings.xml`

**Purpose:** Create visual assets matching webapp style

---

## Task 10: Register Widgets and Update Manifest

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`

**Purpose:** Register both widget types and update app launcher

Add LiveTrainsWidgetProvider:
```xml
<receiver
    android:name=".LiveTrainsWidgetProvider"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/widget_live_trains_info" />
</receiver>
```

---

## Parallel Execution Strategy

| Wave | Tasks | Dependencies |
|------|-------|--------------|
| 1 | Task 1 (Theme), Task 9 (Drawables) | None |
| 2 | Task 2 (Main Activity), Task 5 (Live Trains Widget) | Wave 1 |
| 3 | Task 3 (Commute Fragment), Task 4 (Live Trains Fragment), Task 7 (Settings Fragment) | Wave 2 |
| 4 | Task 6 (Update Commute Widget), Task 8 (Alert Indicator) | Wave 1 |
| 5 | Task 10 (Manifest) | All above |

---

## Verification

1. Build: `cd android && ./gradlew assembleDebug`
2. Install: `adb install app/build/outputs/apk/debug/app-debug.apk`
3. Verify:
   - App opens with dark theme
   - Bottom navigation works
   - Commute screen matches webapp
   - Live Trains screen matches webapp
   - Both widget types appear in widget picker
   - Commute widget shows arrival times
   - Live Trains widget shows real-time arrivals
