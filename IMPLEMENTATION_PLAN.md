# NYC Commute Optimizer - Implementation Plan

**Goal:** Android widget showing ranked commute options (home → work) with zero clicks. Weather-aware: bike option moves to #2 on rainy days.

## Why Build This vs Citymapper?

| Pain Point | Citymapper | This Project |
|------------|-----------|--------------|
| Open app | Required | Not needed (widget) |
| Type start/end | Every time | Predefined |
| Check weather separately | Yes | Built-in |
| Auto-rank by weather | No | Yes |
| Real-time subway data | Yes | Yes (via Transiter) |

**Core value:** Glance at widget → see today's best option → go.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Android Widget                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  72°F Sunny                          7:45 AM        │    │
│  │                                                     │    │
│  │  1. 🚲 Bike → G train         28 min  ← best       │    │
│  │  2. 🚇 Walk → G train         35 min               │    │
│  │                                                     │    │
│  │  ⚠️ G train: Minor delays eastbound                │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  [Settings] - Configure stations, home/work                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Backend API                               │
│                                                              │
│   GET  /commute           → Ranked options for widget       │
│   GET  /settings          → Current user settings           │
│   PUT  /settings          → Update settings                 │
│   GET  /stations          → Available stations list         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
          │              │              │              │
          ▼              ▼              ▼              ▼
   ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐
   │ Transiter  │ │ Google     │ │ OpenWeather│ │ Settings   │
   │ (Docker)   │ │ Routes API │ │ API        │ │ JSON file  │
   │            │ │            │ │            │ │            │
   │ Real-time  │ │ Bike time  │ │ Rain/snow  │ │ User prefs │
   │ subway     │ │ home→stn   │ │ check      │ │ & stations │
   └────────────┘ └────────────┘ └────────────┘ └────────────┘
```

---

## Tech Stack

### Backend
| Component | Technology | Why |
|-----------|------------|-----|
| Runtime | Node.js + TypeScript | Async-friendly, good ecosystem |
| Framework | Hono or Express | Lightweight |
| Subway Data | [Transiter](https://github.com/jamespfennell/transiter) (self-hosted) | Real-time arrivals, proven (powers realtimerail.nyc) |
| Bike Routing | Google Routes API | Accurate bike time estimates |
| Weather | OpenWeatherMap | Simple, free tier |
| Settings Storage | JSON file | Simple, no DB needed |
| Hosting | Railway / Fly.io | Can run Docker (for Transiter) |

### Android
| Component | Technology | Why |
|-----------|------------|-----|
| Widget | Native Kotlin | Full control, settings UI |
| HTTP | Retrofit | Standard Android networking |
| Background | WorkManager | Reliable refresh |

---

## Settings UI

### Web Settings Page (served by backend)

Simple page at `/settings` for configuring:

```
┌─────────────────────────────────────────────────────────────┐
│  ⚙️ Commute Optimizer Settings                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  📍 Home Location                                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 123 Brooklyn St, Brooklyn, NY           [Change]    │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  🏢 Work Location                                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 456 Manhattan Ave, New York, NY         [Change]    │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  🚲 Bike-to Stations (select stations to bike to)          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ ☑ Bedford-Nostrand Avs (G)              0.8 mi      │   │
│  │ ☑ Classon Av (G)                        0.5 mi      │   │
│  │ ☐ Hoyt-Schermerhorn (A/C/G)             1.2 mi      │   │
│  │ ☐ Lafayette Av (C)                      0.9 mi      │   │
│  │ ☐ Clinton-Washington (C)                1.0 mi      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  🚇 Transit-only Stations (walk to these)                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ ☑ Classon Av (G)                        0.5 mi      │   │
│  │ ☐ Clinton-Washington (G)                0.6 mi      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  🎯 Destination Station                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Court Sq (G/7/E/M)                      ▼           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│                                        [Save Settings]      │
└─────────────────────────────────────────────────────────────┘
```

### Settings Data Model

```typescript
// data/settings.json
{
  "home": {
    "lat": 40.6892,
    "lng": -73.9442,
    "address": "123 Brooklyn St, Brooklyn, NY"
  },
  "work": {
    "lat": 40.7580,
    "lng": -73.9855,
    "address": "456 Manhattan Ave, New York, NY"
  },
  "bikeToStations": ["bedford-nostrand", "classon"],  // Selected station IDs
  "walkToStations": ["classon"],
  "destinationStation": "court-sq"
}

// data/stations.json (predefined list - can be expanded)
{
  "stations": [
    {
      "id": "bedford-nostrand",
      "name": "Bedford-Nostrand Avs",
      "transiterId": "G26",
      "lines": ["G"],
      "lat": 40.6896,
      "lng": -73.9535
    },
    {
      "id": "classon",
      "name": "Classon Av",
      "transiterId": "G28",
      "lines": ["G"],
      "lat": 40.6889,
      "lng": -73.9600
    },
    {
      "id": "hoyt-schermerhorn",
      "name": "Hoyt-Schermerhorn",
      "transiterId": "A42",
      "lines": ["A", "C", "G"],
      "lat": 40.6884,
      "lng": -73.9851
    },
    {
      "id": "court-sq",
      "name": "Court Sq",
      "transiterId": "G22",
      "lines": ["G", "7", "E", "M"],
      "lat": 40.7471,
      "lng": -73.9456
    }
    // ... more stations
  ]
}
```

---

## API Design

### `GET /commute`

Returns ranked commute options for the widget.

**Response:**
```json
{
  "options": [
    {
      "rank": 1,
      "type": "bike_to_transit",
      "duration_minutes": 28,
      "summary": "Bike → Bedford-Nostrand → G",
      "legs": [
        { "mode": "bike", "duration": 8, "to": "Bedford-Nostrand Avs" },
        { "mode": "subway", "duration": 18, "route": "G", "to": "Court Sq" },
        { "mode": "walk", "duration": 2, "to": "Work" }
      ],
      "nextTrain": "7:54 AM",
      "arrival_time": "8:20 AM"
    },
    {
      "rank": 2,
      "type": "transit_only",
      "duration_minutes": 35,
      "summary": "Walk → Classon Av → G",
      "legs": [
        { "mode": "walk", "duration": 12, "to": "Classon Av" },
        { "mode": "subway", "duration": 21, "route": "G", "to": "Court Sq" },
        { "mode": "walk", "duration": 2, "to": "Work" }
      ],
      "nextTrain": "7:48 AM",
      "arrival_time": "8:23 AM"
    }
  ],
  "weather": {
    "temp_f": 72,
    "conditions": "Sunny",
    "is_bad": false
  },
  "alerts": [
    { "route": "G", "message": "Minor delays eastbound" }
  ],
  "generated_at": "2025-01-26T12:45:00Z"
}
```

### `GET /stations`

Returns available stations for the settings UI.

**Response:**
```json
{
  "stations": [
    {
      "id": "bedford-nostrand",
      "name": "Bedford-Nostrand Avs",
      "lines": ["G"],
      "distanceFromHome": 0.8
    },
    // ...
  ]
}
```

### `GET /settings`

Returns current user settings.

### `PUT /settings`

Updates user settings (home, work, selected stations).

**Request:**
```json
{
  "bikeToStations": ["bedford-nostrand", "classon"],
  "walkToStations": ["classon"],
  "destinationStation": "court-sq"
}
```

---

## Core Logic

### Route Calculation

```typescript
async function calculateCommuteOptions(settings: Settings): Promise<CommuteOption[]> {
  const options: CommuteOption[] = [];

  // 1. Calculate bike-to-transit options for each selected station
  for (const stationId of settings.bikeToStations) {
    const station = getStation(stationId);

    // Get bike time from Google Routes API
    const bikeTime = await getBikeTime(settings.home, station);

    // Get next train from Transiter
    const nextTrain = await getNextTrain(station.transiterId, settings.destinationStation);

    // Get transit time from Transiter
    const transitTime = await getTransitTime(station.transiterId, settings.destinationStation);

    options.push({
      type: 'bike_to_transit',
      station: station,
      bikeMinutes: bikeTime,
      transitMinutes: transitTime,
      totalMinutes: bikeTime + transitTime + 2, // +2 for walk from dest station
      nextTrain: nextTrain
    });
  }

  // 2. Calculate walk-to-transit options
  for (const stationId of settings.walkToStations) {
    const station = getStation(stationId);
    const walkTime = calculateWalkTime(settings.home, station); // Simple distance calc
    const nextTrain = await getNextTrain(station.transiterId, settings.destinationStation);
    const transitTime = await getTransitTime(station.transiterId, settings.destinationStation);

    options.push({
      type: 'transit_only',
      station: station,
      walkMinutes: walkTime,
      transitMinutes: transitTime,
      totalMinutes: walkTime + transitTime + 2,
      nextTrain: nextTrain
    });
  }

  return options;
}
```

### Ranking Logic

```typescript
function rankOptions(options: CommuteOption[], weather: Weather): RankedOption[] {
  // Sort by total duration (fastest first)
  const sorted = [...options].sort((a, b) => a.totalMinutes - b.totalMinutes);

  // If bad weather, ensure bike options aren't #1
  if (weather.is_bad) {
    const firstBikeIndex = sorted.findIndex(o => o.type === 'bike_to_transit');
    const firstTransitIndex = sorted.findIndex(o => o.type === 'transit_only');

    if (firstBikeIndex === 0 && firstTransitIndex > 0) {
      // Move first transit option to #1
      const [transit] = sorted.splice(firstTransitIndex, 1);
      sorted.unshift(transit);
    }
  }

  return sorted.map((opt, i) => ({ ...opt, rank: i + 1 }));
}
```

---

## Transiter Setup

### Docker Compose

```yaml
# docker-compose.yml
version: '3.8'
services:
  transiter:
    image: jamespfennell/transiter:latest
    ports:
      - "8080:8080"
    environment:
      - TRANSITER_DB_DRIVER=postgres
      - TRANSITER_DB_HOST=db
      - TRANSITER_DB_PORT=5432
      - TRANSITER_DB_USER=transiter
      - TRANSITER_DB_PASSWORD=transiter
      - TRANSITER_DB_NAME=transiter
    depends_on:
      - db

  db:
    image: postgres:15
    environment:
      - POSTGRES_USER=transiter
      - POSTGRES_PASSWORD=transiter
      - POSTGRES_DB=transiter
    volumes:
      - pgdata:/var/lib/postgresql/data

  backend:
    build: .
    ports:
      - "3000:3000"
    environment:
      - TRANSITER_URL=http://transiter:8080
      - GOOGLE_MAPS_API_KEY=${GOOGLE_MAPS_API_KEY}
      - OPENWEATHER_API_KEY=${OPENWEATHER_API_KEY}
    depends_on:
      - transiter

volumes:
  pgdata:
```

### Install NYC Subway System

```bash
# After Transiter is running, install NYC subway
curl -X POST "http://localhost:8080/systems" \
  -H "Content-Type: application/json" \
  -d '{"id": "us-ny-subway", "config_url": "https://raw.githubusercontent.com/jamespfennell/transiter-ny/main/subway.yaml"}'
```

### Transiter API Usage

```typescript
// Get next arrivals at Bedford-Nostrand (G26)
const response = await fetch('http://transiter:8080/systems/us-ny-subway/stops/G26');
const data = await response.json();

// data.stopTimes contains upcoming arrivals:
// [{ arrival: { time: "2025-01-26T12:54:00Z" }, trip: { route: { id: "G" }, direction: "NORTH" }}]
```

---

## Project Structure

```
commute-optimizer/
├── docker-compose.yml
├── Dockerfile
├── package.json
├── tsconfig.json
│
├── data/
│   ├── settings.json         # User settings (persisted)
│   └── stations.json         # Available stations list
│
├── src/
│   ├── index.ts              # Entry point
│   ├── server.ts             # HTTP server (Hono/Express)
│   │
│   ├── routes/
│   │   ├── commute.ts        # GET /commute
│   │   ├── settings.ts       # GET/PUT /settings
│   │   └── stations.ts       # GET /stations
│   │
│   ├── services/
│   │   ├── transiter.ts      # Transiter API client
│   │   ├── google-routes.ts  # Google Routes API (bike time)
│   │   ├── weather.ts        # OpenWeatherMap client
│   │   └── ranking.ts        # Ranking logic
│   │
│   ├── types/
│   │   └── index.ts
│   │
│   └── config.ts
│
├── web/                      # Settings UI
│   ├── index.html
│   ├── settings.html
│   └── styles.css
│
└── android/                  # Widget app
    └── ...
```

---

## Implementation Phases

### Phase 1: Transiter Setup
- [ ] Set up Docker Compose with Transiter + Postgres
- [ ] Install NYC subway system in Transiter
- [ ] Test querying station arrivals
- [ ] Deploy to Railway/Fly.io

**Deliverable:** Transiter running with real-time NYC subway data

### Phase 2: Backend API
- [ ] Set up Node.js + TypeScript project
- [ ] Create stations.json with Brooklyn/Manhattan stations
- [ ] Implement Transiter client (get arrivals, trip times)
- [ ] Implement Google Routes client (bike times only)
- [ ] Implement OpenWeatherMap client
- [ ] Create `/commute` endpoint with ranking logic
- [ ] Create `/stations` endpoint
- [ ] Create `/settings` GET/PUT endpoints

**Deliverable:** Working API that returns ranked commute options

### Phase 3: Settings UI
- [ ] Create simple HTML/CSS settings page
- [ ] Station multi-select with checkboxes
- [ ] Destination station dropdown
- [ ] Home/work location inputs
- [ ] Save to settings.json via API

**Deliverable:** Web UI to configure stations

### Phase 4: Android Widget
- [ ] Create Android Studio project
- [ ] Implement widget layout (options list, weather, alerts)
- [ ] Add WorkManager for background refresh
- [ ] Settings activity that opens web settings page
- [ ] Click action to open directions in Google Maps

**Deliverable:** Working Android widget

### Phase 5: Polish
- [ ] Caching to reduce API calls
- [ ] Error handling and fallbacks
- [ ] Morning notification option (ntfy.sh)

---

## Cost Estimate

| Service | Monthly Cost |
|---------|-------------|
| Google Routes API (bike times) | ~$0.30 (60 requests) |
| OpenWeatherMap | Free (1000/day) |
| Transiter | Self-hosted (free) |
| Railway/Fly.io | Free tier or ~$5 |
| **Total** | **~$0-5/month** |

---

## Next Steps

1. **Get API keys:**
   - [Google Cloud Console](https://console.cloud.google.com/) - Enable Routes API
   - [OpenWeatherMap](https://openweathermap.org/api) - Sign up for free tier

2. **Set up Transiter locally:**
   ```bash
   docker-compose up -d
   # Install NYC subway system
   curl -X POST "http://localhost:8080/systems" ...
   ```

3. **Create stations.json** with your preferred Brooklyn stations

4. **Build backend** - Start with `/commute` endpoint

5. **Build settings UI** - Simple HTML page with station checkboxes
