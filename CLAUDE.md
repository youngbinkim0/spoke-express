# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NYC Commute Optimizer - An Android widget + backend API that shows ranked commute options (home → work) with zero clicks. Weather-aware: bike option automatically moves to #2 on rainy/snowy days.

The backend is a Node.js/TypeScript API (Hono framework) that aggregates data from:
- **Transiter** (self-hosted) - Real-time NYC subway arrivals
- **Google Routes API** - Bike routing times
- **OpenWeatherMap** - Weather conditions

## Commands

```bash
# Development (with hot reload)
npm run dev

# Build TypeScript
npm run build

# Run production server
npm start

# Start infrastructure (Transiter + Postgres)
docker-compose up -d

# Install NYC subway system into Transiter
npm run transiter:install
```

## Environment Variables

Required in `.env` or environment:
- `GOOGLE_MAPS_API_KEY` - Google Routes API key
- `OPENWEATHER_API_KEY` - OpenWeatherMap API key
- `TRANSITER_URL` - Transiter service URL (default: http://localhost:8080)
- `PORT` - Backend port (default: 3000)

## Architecture

```
Routes (src/routes/)      Services (src/services/)       External
─────────────────────     ─────────────────────────      ──────────
GET /api/commute    ───→  ranking.ts (weather-aware)
                    ───→  transiter.ts             ───→  Transiter API
                    ───→  google-routes.ts         ───→  Google Routes
                    ───→  weather.ts               ───→  OpenWeatherMap

GET /api/settings   ───→  data.ts                  ───→  data/settings.json
PUT /api/settings

GET /api/stations   ───→  data.ts                  ───→  data/stations.json
```

### Key Design Decisions

1. **Ranking Logic** (`src/services/ranking.ts`): Sort by duration, then swap bike with first transit option if weather is bad. Bike stays viable, just not primary.

2. **Caching** (`node-cache`): Weather 10min, arrivals 30sec, bike times 1hr. Configured in `src/config.ts`.

3. **Graceful Degradation**: Missing Google API key → distance-based bike time estimate. Missing weather key → default "Unknown" weather.

4. **Transit Time**: Uses lookup table in `transiter.ts` for known routes (G line between stations). Falls back to 15-minute default.

### Data Files

- `data/settings.json` - User config (home, work, selected stations)
- `data/stations.json` - 23 NYC stations (G line focus, major transfer points)

Each station has a `transiterId` (e.g., "G26") that maps to Transiter's stop ID.

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/commute` | Ranked options with weather, legs breakdown, next train times |
| `GET /api/settings` | Current user settings |
| `PUT /api/settings` | Full settings update |
| `PATCH /api/settings` | Partial settings update |
| `GET /api/stations` | All stations with distance from home |
| `GET /health` | Health check (includes Transiter status) |

## Frontend

Settings UI at `web/settings.html` - vanilla HTML/CSS/JS served by the backend at root `/`. MTA line colors are authentic (G=green, 1-3=red, etc.).

## Android Widget

The Android widget (`android/`) displays the top 3 commute options on the home screen.

### Building the Widget

```bash
cd android

# Build debug APK (requires Java 17+)
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Widget Features
- **4x2 layout**: Header with weather + 3 option rows + timestamp
- **Live data**: Fetches from backend `/api/commute` endpoint
- **Auto-refresh**: WorkManager updates every 15 minutes
- **Manual refresh**: Tap refresh icon
- **MTA colors**: Authentic subway line colors

See `android/README.md` for full documentation.
