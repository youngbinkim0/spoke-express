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
  bike_max_distance_miles: number;       // max willing to bike
  rain_threshold_mm: number;             // precipitation threshold to avoid biking
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
  route_type: 'transit_only' | 'bike_to_transit';
  total_duration_minutes: number;
  weather_score: number;    // 0-100, higher = better for biking
  was_recommended: boolean;
  details: string;          // JSON blob with full route details
}
```

### Application Types

```typescript
interface ComputedRoute {
  id: string;
  type: 'transit_only' | 'bike_to_transit';
  legs: TransitLeg[];
  total_duration_minutes: number;
  total_walking_minutes: number;
  total_biking_minutes?: number;
  departure_time: Date;
  arrival_time: Date;
  weather_penalty: number;   // 0-50 points subtracted
  delay_penalty: number;     // Based on realtime MTA data
  score: number;             // Final score, higher = better
}

interface WeatherConditions {
  temperature_f: number;
  feels_like_f: number;
  precipitation_mm_per_hour: number;
  precipitation_type: 'none' | 'rain' | 'snow' | 'mix';
  precipitation_probability: number;
  wind_speed_mph: number;
  visibility_miles: number;
  conditions: string;
}

interface CommuteRecommendation {
  recommended_route: ComputedRoute;
  alternative_routes: ComputedRoute[];
  weather: WeatherConditions;
  alerts: MTAAlert[];
  reasoning: string;         // Human-readable explanation
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

## 5. Core Algorithms

### Weather-Based Scoring

```typescript
function calculateWeatherPenalty(weather: WeatherConditions): number {
  let penalty = 0;

  // Precipitation penalty (most important)
  if (weather.precipitation_type === 'snow') {
    penalty += 40;  // Heavy penalty for snow
  } else if (weather.precipitation_type === 'rain') {
    if (weather.precipitation_mm_per_hour > 2.5) {
      penalty += 30;  // Heavy rain
    } else {
      penalty += 15;  // Light rain
    }
  }

  // Probability of precipitation (next hour)
  if (weather.precipitation_probability > 0.7) {
    penalty += 15;
  } else if (weather.precipitation_probability > 0.4) {
    penalty += 8;
  }

  // Temperature penalty
  if (weather.feels_like_f < 32) {
    penalty += 20;  // Freezing
  } else if (weather.feels_like_f < 40) {
    penalty += 10;  // Cold
  } else if (weather.feels_like_f > 90) {
    penalty += 15;  // Very hot
  }

  // Wind penalty
  if (weather.wind_speed_mph > 20) {
    penalty += 10;
  } else if (weather.wind_speed_mph > 15) {
    penalty += 5;
  }

  return Math.min(50, penalty);  // Cap at 50 points
}
```

### Route Scoring

```typescript
function scoreRoute(
  route: ComputedRoute,
  weather: WeatherConditions,
  alerts: MTAAlert[]
): number {
  let score = 100;  // Start with perfect score

  // 1. Duration penalty (1 point per minute over 30)
  score -= Math.max(0, route.total_duration_minutes - 30);

  // 2. Weather penalty for bike routes
  if (route.type === 'bike_to_transit') {
    score -= calculateWeatherPenalty(weather);
  }

  // 3. Delay penalty (2 points per minute of delay)
  score -= route.delay_penalty;

  // 4. Transfer penalty (5 points per transfer)
  const transfers = route.legs.filter(l =>
    l.type === 'subway' || l.type === 'bus'
  ).length - 1;
  score -= Math.max(0, transfers) * 5;

  return Math.max(0, score);
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

### Phase 4: Scoring & Recommendations
**Goal: Weather-aware route scoring**

- [ ] Implement scoring service with weather penalties
- [ ] Create recommendation generator
- [ ] Add unit tests for scoring edge cases

**Deliverable:** Weather correctly deprioritizes bike option

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
│   │   │   ├── BikeRouter.ts
│   │   │   └── ScoringService.ts
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
    ├── scoring.test.ts
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

1. **`src/services/routing/RouteService.ts`** - Core orchestration
2. **`src/services/routing/ScoringService.ts`** - Weather-aware scoring
3. **`src/services/mta/MTAService.ts`** - MTA realtime integration
4. **`src/db/schema.sql`** - Database schema (implement first)
5. **`src/scheduler/MorningCommute.ts`** - Notification scheduler
