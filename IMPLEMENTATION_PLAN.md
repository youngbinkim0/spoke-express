# NYC Commute Optimizer - Implementation Plan

A tool that pulls real-time MTA data and weather to recommend the optimal commute mode each morning for Brooklyn to Manhattan commutes.

## Core Features

1. **Two commute modes:**
   - Public transit only (subway/bus)
   - Combo mode: bike to transit station, then take public transit

2. **Weather-aware routing:** Deprioritize bike option when raining or snowing

3. **Real-time MTA data integration** for accurate delay information

4. **Push notifications** for morning commute recommendations

---

## 1. Recommended Tech Stack

### Backend
| Component | Technology | Justification |
|-----------|------------|---------------|
| **Runtime** | Node.js 20+ with TypeScript | Strong async handling for API calls, excellent ecosystem for real-time data |
| **Framework** | Fastify | Faster than Express, built-in TypeScript support, schema validation |
| **Database** | SQLite (via better-sqlite3) | Zero-config, file-based, perfect for personal tool |
| **Caching** | node-cache (in-memory) | Simple solution for caching API responses |
| **Scheduler** | node-cron | Lightweight scheduling for morning notification triggers |
| **GTFS Parser** | gtfs-realtime-bindings + gtfs | Official Google libraries for parsing MTA feeds |

### Frontend (Optional Web Dashboard)
| Component | Technology | Justification |
|-----------|------------|---------------|
| **Framework** | React with Vite | Fast development, good for simple dashboard |
| **Styling** | Tailwind CSS | Rapid prototyping |
| **State** | Zustand | Minimal boilerplate |

### Notifications
| Component | Technology | Justification |
|-----------|------------|---------------|
| **Push Service** | Web Push API + Pushover | Web Push for browser, Pushover for mobile |
| **Alternative** | ntfy.sh | Self-hostable, free, works on all platforms |

---

## 2. System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           NYC Commute Optimizer                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐       │
│  │   Scheduler      │    │   API Server     │    │   Web Dashboard  │       │
│  │   (node-cron)    │    │   (Fastify)      │    │   (React/Vite)   │       │
│  │                  │    │                  │    │                  │       │
│  │ • Morning check  │    │ • /routes        │    │ • Route display  │       │
│  │ • Weather poll   │    │ • /weather       │    │ • Settings       │       │
│  │ • MTA poll       │    │ • /notifications │    │ • History        │       │
│  └────────┬─────────┘    └────────┬─────────┘    └──────────────────┘       │
│           │                       │                                          │
│           ▼                       ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        Core Services Layer                           │    │
│  ├─────────────────┬─────────────────┬─────────────────┬───────────────┤    │
│  │  RouteService   │  WeatherService │  MTAService     │ NotifyService │    │
│  │                 │                 │                 │               │    │
│  │ • Optimization  │ • Current       │ • Realtime      │ • Web Push    │    │
│  │ • Scoring       │ • Forecast      │ • Alerts        │ • Pushover    │    │
│  │ • Bike+Transit  │ • Rain/Snow     │ • Delays        │ • ntfy        │    │
│  └────────┬────────┴────────┬────────┴────────┬────────┴───────────────┘    │
│           │                 │                 │                              │
│           ▼                 ▼                 ▼                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        Data Layer                                    │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │  SQLite Database          │  In-Memory Cache (node-cache)           │    │
│  │  • User preferences       │  • MTA realtime (TTL: 60s)              │    │
│  │  • Route history          │  • Weather data (TTL: 10min)            │    │
│  │  • Static GTFS data       │  • Computed routes (TTL: 5min)          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          External APIs                                       │
├─────────────────┬─────────────────┬─────────────────┬───────────────────────┤
│   MTA GTFS      │   MTA Realtime  │  Weather API    │   Notification APIs   │
│   (Static)      │   (Protobuf)    │  (OpenWeather)  │   (Pushover/ntfy)     │
└─────────────────┴─────────────────┴─────────────────┴───────────────────────┘
```

---

## 3. Data Models

### Database Schema (SQLite)

```typescript
// User preferences for commute
interface UserPreferences {
  id: number;
  home_lat: number;
  home_lng: number;
  home_address: string;
  work_lat: number;
  work_lng: number;
  work_address: string;
  preferred_departure_time: string;      // "08:00"
  notification_enabled: boolean;
  notification_minutes_before: number;   // e.g., 30
  bike_max_distance_miles: number;       // max willing to bike to station
  created_at: string;
  updated_at: string;
}

// Saved transit stations for quick lookup
interface TransitStation {
  id: string;               // MTA stop_id
  name: string;
  lat: number;
  lng: number;
  routes: string;           // JSON array of route IDs, e.g., '["A","C","G"]'
  station_type: 'subway' | 'bus';
  borough: string;
}

// Route calculation history
interface RouteHistory {
  id: number;
  calculated_at: string;
  recommended_type: 'transit_only' | 'bike_to_transit';
  duration_minutes: number;
  was_bad_weather: boolean;
  details: string;          // JSON blob with full route details
}
```

### Application Types

```typescript
interface TransitLeg {
  type: 'subway' | 'bus' | 'walk' | 'bike';
  from: string;              // Station/location name
  to: string;
  route_id?: string;         // e.g., "G", "B62"
  duration_minutes: number;
  distance_miles?: number;
}

interface CommuteOption {
  id: string;
  type: 'transit_only' | 'bike_to_transit';
  legs: TransitLeg[];
  duration_minutes: number;
  departure_time: Date;
  arrival_time: Date;
}

interface Weather {
  temperature_f: number;
  conditions: string;        // "Sunny", "Cloudy", "Rain", etc.
  precipitation_type: 'none' | 'rain' | 'snow' | 'mix';
  precipitation_probability: number;  // 0-1
  is_bad: boolean;           // Computed: should we avoid biking?
}

interface CommuteResponse {
  options: CommuteOption[];  // All options, ranked (first = recommended)
  weather: Weather;
  alerts: MTAAlert[];
  generated_at: Date;
}
```

---

## 4. API Integrations

### MTA GTFS Feeds

```typescript
// MTA API endpoints (requires API key from https://api.mta.info/)

export const MTA_FEEDS = {
  // Static GTFS (download periodically)
  static: {
    subway: 'http://web.mta.info/developers/data/nyct/subway/google_transit.zip',
    bus_brooklyn: 'http://web.mta.info/developers/data/nyct/bus/google_transit_brooklyn.zip',
  },

  // GTFS-Realtime feeds (poll every 30-60 seconds)
  realtime: {
    ace: 'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-ace',
    bdfm: 'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-bdfm',
    g: 'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-g',
    jz: 'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-jz',
    l: 'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-l',
    nqrw: 'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-nqrw',
    '1234567': 'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs',
    alerts: 'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/camsys%2Fsubway-alerts',
  }
};
```

### Weather API (OpenWeatherMap)

```typescript
// OpenWeatherMap One Call API 3.0
// Free tier: 1000 calls/day
// Register at: https://openweathermap.org/api

const WEATHER_API = {
  base_url: 'https://api.openweathermap.org/data/3.0/onecall',
  params: 'units=imperial&exclude=minutely,daily'
};

// Alternative: Weather.gov (free, no key, US only)
const WEATHER_GOV_API = {
  base_url: 'https://api.weather.gov',
  // Requires User-Agent header
};
```

### Notification Services

| Service | Cost | Notes |
|---------|------|-------|
| **Pushover** | $5 one-time | Best for mobile, reliable |
| **ntfy.sh** | Free | Self-hostable, works everywhere |
| **Web Push** | Free | Browser only, requires service worker |

---

## 5. Core Algorithm

### Simple Ranking Logic

**Primary sort: fastest route wins.** If it's raining or snowing, bike option moves to #2.

```typescript
interface CommuteOption {
  type: 'transit_only' | 'bike_to_transit';
  duration_minutes: number;
  legs: TransitLeg[];
}

interface Weather {
  is_bad: boolean;  // rain or snow
  conditions: string;
}

function isBadWeather(weather: WeatherConditions): boolean {
  return weather.precipitation_type === 'rain'
      || weather.precipitation_type === 'snow'
      || weather.precipitation_type === 'mix';
}

function rankRoutes(
  routes: CommuteOption[],
  weather: Weather
): CommuteOption[] {
  // Step 1: Sort all routes by duration (fastest first)
  const sorted = [...routes].sort((a, b) =>
    a.duration_minutes - b.duration_minutes
  );

  // Step 2: If bad weather and bike option is #1, swap it to #2
  if (weather.is_bad && sorted[0]?.type === 'bike_to_transit') {
    // Find the fastest transit-only option
    const transitIndex = sorted.findIndex(r => r.type === 'transit_only');
    if (transitIndex > 0) {
      // Swap: move transit-only to #1, bike to #2
      const [transit] = sorted.splice(transitIndex, 1);
      sorted.unshift(transit);
    }
  }

  return sorted;
}
```

### Example Output

**Good weather (sunny, 65°F):**
```
1. 🚲 Bike + G train     → 28 min  ← recommended
2. 🚇 Walk + G train     → 35 min
3. 🚇 Walk + A/C train   → 38 min
```

**Bad weather (raining):**
```
1. 🚇 Walk + G train     → 35 min  ← recommended
2. 🚲 Bike + G train     → 28 min  (not recommended: rain)
3. 🚇 Walk + A/C train   → 38 min
```

### Weather Check

```typescript
// Simple check - is it currently raining/snowing or about to?
function isBadWeather(weather: WeatherConditions): boolean {
  // Currently precipitating
  if (weather.precipitation_type !== 'none') {
    return true;
  }

  // High chance of rain in next hour (>50%)
  if (weather.precipitation_probability > 0.5) {
    return true;
  }

  return false;
}
```

---

## 6. Implementation Phases

### Phase 1: Foundation
**Goal: Basic project structure and data layer**

- [ ] Initialize Node.js/TypeScript project with Fastify
- [ ] Set up SQLite schema and migrations
- [ ] Download and parse MTA static GTFS data
- [ ] Populate transit_stations table
- [ ] Create config system with environment variables

**Deliverable:** Can query nearby stations from database

### Phase 2: Core Services
**Goal: External API integrations working**

- [ ] Implement MTA realtime feed fetching (GTFS-realtime)
- [ ] Implement weather service (OpenWeatherMap)
- [ ] Add in-memory caching layer
- [ ] Register for MTA + weather API keys

**Deliverable:** Can fetch live MTA delays and current weather

### Phase 3: Routing Engine
**Goal: Route computation working**

- [ ] Build route constructor from GTFS data
- [ ] Implement transit pathfinding algorithm
- [ ] Add bike time estimation
- [ ] Create main RouteService orchestrator

**Deliverable:** Can compute transit-only and bike+transit routes

### Phase 4: Ranking & Recommendations
**Goal: Weather-aware route ranking**

- [ ] Implement simple ranking (sort by time, bump bike if rain/snow)
- [ ] Create response builder with all options listed
- [ ] Add unit tests for ranking logic

**Deliverable:** Bad weather moves bike option to #2

### Phase 5: API Server
**Goal: REST API endpoints**

- [ ] Set up Fastify server
- [ ] `GET /api/routes` - Get route recommendations
- [ ] `GET /api/weather` - Get current weather
- [ ] `GET/PUT /api/preferences` - User preferences
- [ ] `GET /api/alerts` - MTA service alerts

**Deliverable:** Can query routes via HTTP

### Phase 6: Notifications
**Goal: Morning push notifications**

- [ ] Implement notification service (Pushover/ntfy)
- [ ] Create node-cron scheduler for morning job
- [ ] Track notification history

**Deliverable:** Receive morning commute recommendations on phone

### Phase 7: Web Dashboard (Optional)
**Goal: Simple web UI**

- [ ] Set up React + Vite + Tailwind
- [ ] Dashboard view with current recommendation
- [ ] Settings page for preferences
- [ ] History page for past recommendations

**Deliverable:** Full working application

---

## 7. Project Structure

```
commute-optimizer/
├── package.json
├── tsconfig.json
├── .env.example
├── .gitignore
├── README.md
│
├── scripts/
│   ├── import-gtfs.ts          # One-time GTFS import
│   └── test-notification.ts    # Manual notification test
│
├── data/
│   ├── commute.db              # SQLite database
│   └── gtfs/                   # Downloaded GTFS files (gitignored)
│
├── src/
│   ├── index.ts                # Main entry point
│   │
│   ├── config/
│   │   ├── index.ts            # Configuration loader
│   │   ├── mta.ts              # MTA API config
│   │   └── weather.ts          # Weather API config
│   │
│   ├── db/
│   │   ├── schema.sql          # SQLite schema
│   │   ├── Database.ts         # DB connection wrapper
│   │   └── repositories/
│   │       ├── PreferencesRepo.ts
│   │       ├── StationsRepo.ts
│   │       └── HistoryRepo.ts
│   │
│   ├── types/
│   │   ├── index.ts            # Shared types
│   │   ├── gtfs.ts             # GTFS types
│   │   └── routes.ts           # Route types
│   │
│   ├── services/
│   │   ├── mta/
│   │   │   ├── MTAService.ts
│   │   │   ├── GTFSImporter.ts
│   │   │   └── AlertParser.ts
│   │   │
│   │   ├── weather/
│   │   │   └── WeatherService.ts
│   │   │
│   │   ├── routing/
│   │   │   ├── RouteService.ts
│   │   │   ├── TransitPathfinder.ts
│   │   │   └── BikeRouter.ts
│   │   │
│   │   ├── notification/
│   │   │   ├── NotificationService.ts
│   │   │   ├── PushoverProvider.ts
│   │   │   └── NtfyProvider.ts
│   │   │
│   │   └── cache/
│   │       └── CacheService.ts
│   │
│   ├── scheduler/
│   │   └── MorningCommute.ts
│   │
│   ├── server/
│   │   ├── index.ts
│   │   └── routes/
│   │       ├── routes.ts
│   │       ├── weather.ts
│   │       ├── alerts.ts
│   │       └── preferences.ts
│   │
│   └── utils/
│       ├── geo.ts              # Haversine distance
│       ├── time.ts             # Time utilities
│       └── logger.ts
│
├── web/                        # React frontend (optional)
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.tsx
│       ├── pages/
│       │   ├── Dashboard.tsx
│       │   ├── Settings.tsx
│       │   └── History.tsx
│       └── components/
│           ├── RouteCard.tsx
│           └── WeatherBadge.tsx
│
└── tests/
    ├── ranking.test.ts
    └── routing.test.ts
```

---

## 8. Environment Variables

```bash
# .env.example

# MTA API (required) - Register at https://api.mta.info/
MTA_API_KEY=your_mta_api_key

# Weather API (pick one)
OPENWEATHER_API_KEY=your_openweather_key
# OR use Weather.gov (no key needed)
USE_WEATHER_GOV=true

# Notifications (pick one or more)
PUSHOVER_APP_TOKEN=your_pushover_app_token
PUSHOVER_USER_KEY=your_pushover_user_key
# OR
NTFY_TOPIC=your_ntfy_topic

# Database
DATABASE_PATH=./data/commute.db

# Server
PORT=3000
HOST=localhost

# Default locations (Brooklyn to Manhattan)
DEFAULT_HOME_LAT=40.6892
DEFAULT_HOME_LNG=-73.9442
DEFAULT_WORK_LAT=40.7580
DEFAULT_WORK_LNG=-73.9855
```

---

## 9. Brooklyn-Manhattan Focus

### Key Subway Lines
`A`, `C`, `G`, `F`, `R`, `N`, `Q`, `B`, `D`, `2`, `3`, `4`, `5`, `L`

### Major Brooklyn Transit Hubs (for bike-to-transit)
| Station | Lines |
|---------|-------|
| Atlantic Ave-Barclays Ctr | B, Q, D, N, R, 2, 3, 4, 5 |
| Jay St-MetroTech | A, C, F, R |
| Bedford Ave | L |
| Hoyt-Schermerhorn | A, C, G |

### Bike Time Estimation
- **Flat ground:** 12 mph average
- **Bridge crossing:** 10 mph average
- **Add 20%** for NYC grid (non-straight routes)
- **Add 2 minutes** for locking bike at station

---

## Critical Implementation Files

1. **`src/services/routing/RouteService.ts`** - Core orchestration + ranking logic
2. **`src/services/mta/MTAService.ts`** - MTA realtime integration
3. **`src/services/weather/WeatherService.ts`** - Weather API + bad weather check
4. **`src/db/schema.sql`** - Database schema (implement first)
5. **`src/scheduler/MorningCommute.ts`** - Notification scheduler
