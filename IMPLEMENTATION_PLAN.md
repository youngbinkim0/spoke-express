# NYC Commute Optimizer - Implementation Plan

**Goal:** Android widget showing ranked commute options (home → work) with zero clicks. Weather-aware: bike option moves to #2 on rainy days.

## Why Build This vs Citymapper?

| Pain Point | Citymapper | This Project |
|------------|-----------|--------------|
| Open app | Required | Not needed (widget) |
| Type start/end | Every time | Predefined |
| Check weather separately | Yes | Built-in |
| Auto-rank by weather | No | Yes |

**Core value:** Glance at widget → see today's best option → go.

---

## Simplified Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Android Widget                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  72°F Sunny                          7:45 AM        │    │
│  │                                                     │    │
│  │  1. 🚲 Bike → G train         28 min  ← best       │    │
│  │  2. 🚇 Walk → G train         35 min               │    │
│  │  3. 🚇 Walk → A/C             42 min               │    │
│  │                                                     │    │
│  │  ⚠️ G train: Minor delays eastbound                │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Backend API (hosted)                      │
│                                                              │
│   GET /commute                                               │
│   → Combines weather + routes + alerts                       │
│   → Returns ranked options                                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┼─────────────────┐
            ▼                 ▼                 ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │ Google       │  │ OpenWeather  │  │ MTA Alerts   │
    │ Routes API   │  │ API          │  │ API          │
    │              │  │              │  │              │
    │ Transit time │  │ Rain/snow    │  │ Delays       │
    │ directions   │  │ forecast     │  │ disruptions  │
    └──────────────┘  └──────────────┘  └──────────────┘
```

---

## Key Simplification: Use Google Routes API

Instead of parsing GTFS data ourselves, use [Google Routes API](https://developers.google.com/maps/documentation/routes) for transit routing.

**Pricing:** ~$5 per 1,000 requests (Basic tier)
- 2 requests/day × 30 days = 60 requests/month = **$0.30/month**

**What we get:**
- Real-time transit directions
- Accurate duration estimates
- Multiple route options
- No GTFS parsing needed

**What we still build:**
- Weather check (OpenWeatherMap)
- MTA alerts (free API)
- Ranking logic
- Android widget

---

## Tech Stack (Simplified)

### Backend
| Component | Technology | Why |
|-----------|------------|-----|
| Runtime | Node.js + TypeScript | Simple, fast |
| Framework | Hono or Express | Lightweight |
| Hosting | Railway / Fly.io / Vercel | Free tier works |
| Cache | In-memory (node-cache) | Reduce API calls |

### Android Widget
| Option | Pros | Cons |
|--------|------|------|
| **KWGT + Tasker** | No coding, flexible | Setup complexity |
| **Native Kotlin** | Full control, polished | More dev work |
| **Flutter** | Cross-platform | Overkill for widget |

**Recommendation:** Start with KWGT + Tasker for quick prototype, native Kotlin later if needed.

---

## API Design

### `GET /commute`

Single endpoint returns everything the widget needs.

**Request:**
```
GET /commute?home=40.6892,-73.9442&work=40.7580,-73.9855
```

**Response:**
```json
{
  "options": [
    {
      "rank": 1,
      "type": "bike_to_transit",
      "duration_minutes": 28,
      "summary": "Bike → G train",
      "legs": [
        { "mode": "bike", "duration": 8, "instruction": "Bike to Bedford-Nostrand" },
        { "mode": "subway", "duration": 18, "route": "G", "instruction": "G to Court Sq" },
        { "mode": "walk", "duration": 2, "instruction": "Walk to destination" }
      ],
      "departure_time": "7:52 AM",
      "arrival_time": "8:20 AM"
    },
    {
      "rank": 2,
      "type": "transit_only",
      "duration_minutes": 35,
      "summary": "Walk → G train",
      "legs": [...],
      "departure_time": "7:45 AM",
      "arrival_time": "8:20 AM"
    }
  ],
  "weather": {
    "temp_f": 72,
    "conditions": "Sunny",
    "is_bad": false
  },
  "alerts": [
    {
      "route": "G",
      "severity": "minor",
      "message": "Minor delays eastbound due to signal problems"
    }
  ],
  "generated_at": "2025-01-26T12:45:00Z"
}
```

---

## Core Algorithm

### Ranking Logic

```typescript
function rankOptions(
  transitRoute: Route,
  bikeToTransitRoute: Route,
  weather: Weather
): RankedOption[] {
  const options = [
    { ...transitRoute, type: 'transit_only' },
    { ...bikeToTransitRoute, type: 'bike_to_transit' }
  ];

  // Sort by duration (fastest first)
  options.sort((a, b) => a.duration_minutes - b.duration_minutes);

  // If bad weather, ensure bike isn't #1
  if (weather.is_bad && options[0].type === 'bike_to_transit') {
    // Swap positions
    [options[0], options[1]] = [options[1], options[0]];
  }

  // Add rank numbers
  return options.map((opt, i) => ({ ...opt, rank: i + 1 }));
}

function isBadWeather(weather: Weather): boolean {
  return (
    weather.precipitation_type !== 'none' ||
    weather.precipitation_probability > 0.5
  );
}
```

---

## Implementation Phases

### Phase 1: Backend MVP (1-2 days)
- [ ] Set up Node.js project with TypeScript
- [ ] Integrate OpenWeatherMap API
- [ ] Integrate Google Routes API (transit directions)
- [ ] Add MTA alerts API
- [ ] Implement `/commute` endpoint
- [ ] Deploy to Railway/Fly.io

**Deliverable:** Working API that returns ranked commute options

### Phase 2: Bike Route Calculation (1 day)
- [ ] Calculate bike time to nearby subway stations
- [ ] Query Google Routes for transit from station → work
- [ ] Combine bike + transit into single option

**Deliverable:** Bike-to-transit option included in response

### Phase 3: Android Widget (1-2 days)

**Option A: KWGT + Tasker (no coding)**
- [ ] Install KWGT and Tasker
- [ ] Create Tasker task to fetch `/commute` endpoint
- [ ] Parse JSON response
- [ ] Design KWGT widget to display results
- [ ] Set up periodic refresh (every 15 min in morning)

**Option B: Native Kotlin widget**
- [ ] Create Android Studio project
- [ ] Implement AppWidgetProvider
- [ ] Add WorkManager for background refresh
- [ ] Design widget layout
- [ ] Handle click to open Google Maps

**Deliverable:** Widget showing commute options on home screen

### Phase 4: Polish (optional)
- [ ] Add caching to reduce API calls
- [ ] Morning notification via ntfy.sh
- [ ] Settings screen for home/work addresses
- [ ] History tracking

---

## Project Structure (Simplified)

```
commute-optimizer/
├── package.json
├── tsconfig.json
├── .env.example
│
├── src/
│   ├── index.ts              # Entry point
│   ├── server.ts             # HTTP server
│   │
│   ├── routes/
│   │   └── commute.ts        # GET /commute endpoint
│   │
│   ├── services/
│   │   ├── google-routes.ts  # Google Routes API client
│   │   ├── weather.ts        # OpenWeatherMap client
│   │   ├── mta-alerts.ts     # MTA alerts client
│   │   └── ranking.ts        # Ranking logic
│   │
│   ├── types/
│   │   └── index.ts          # TypeScript types
│   │
│   └── config.ts             # Environment config
│
└── android/                  # Widget (if native)
    └── ...
```

---

## Environment Variables

```bash
# .env.example

# Google Routes API (required)
# Get key at: https://console.cloud.google.com/
GOOGLE_MAPS_API_KEY=your_key

# OpenWeatherMap (required)
# Get key at: https://openweathermap.org/api
OPENWEATHER_API_KEY=your_key

# MTA API (optional, for alerts)
# Get key at: https://api.mta.info/
MTA_API_KEY=your_key

# Predefined locations
HOME_LAT=40.6892
HOME_LNG=-73.9442
HOME_ADDRESS="123 Brooklyn St, Brooklyn, NY"

WORK_LAT=40.7580
WORK_LNG=-73.9855
WORK_ADDRESS="456 Manhattan Ave, New York, NY"

# Server
PORT=3000
```

---

## Cost Estimate

| Service | Monthly Cost |
|---------|-------------|
| Google Routes API | ~$0.30 (60 requests) |
| OpenWeatherMap | Free (1000/day) |
| MTA Alerts | Free |
| Hosting (Railway) | Free tier |
| **Total** | **~$0.30/month** |

---

## Android Widget Options

### Option A: KWGT + Tasker (Recommended for MVP)

**Pros:**
- No Android development needed
- Highly customizable visuals
- Quick to set up

**Setup:**
1. Install [KWGT](https://play.google.com/store/apps/details?id=org.kustom.widget)
2. Install [Tasker](https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm)
3. Create Tasker HTTP Request task to call your API
4. Parse JSON and store in Tasker variables
5. Design KWGT widget using Tasker variables

### Option B: Native Kotlin Widget

**Pros:**
- Better performance
- More polished UX
- Click actions (open in Google Maps)

**Key components:**
- `AppWidgetProvider` - Widget lifecycle
- `WorkManager` - Background refresh
- `RemoteViews` - Widget layout
- `Retrofit` - API calls

---

## What This Gets You

**Morning routine:**
1. Wake up
2. Glance at home screen widget
3. See: "🚲 Bike → G train, 28 min" or "🚇 Walk → G (rain today)"
4. Go

**No more:**
- Opening Citymapper
- Typing start/end addresses
- Checking weather app separately
- Deciding whether to bike

---

## Next Steps

1. **Get API keys:**
   - [Google Cloud Console](https://console.cloud.google.com/) - Enable Routes API
   - [OpenWeatherMap](https://openweathermap.org/api) - Sign up for free tier

2. **Start with backend** - Get `/commute` endpoint working

3. **Test with curl** - Verify response format

4. **Build widget** - KWGT for quick win, native later if needed
