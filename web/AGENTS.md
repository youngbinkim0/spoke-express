# Web App Knowledge Base

**Generated:** Thu, Feb 12, 2026
**Commit:** d1773a9

## OVERVIEW

Vanilla HTML/CSS/JS web app for NYC subway commute optimization. No build tools, no dependencies, no TypeScript. Calls MTA APIs directly from browser.

## STRUCTURE

```
web/
├── index.html                          # Main commute options page
├── arrivals.html                       # Live train arrivals
├── settings.html                       # Configuration page
├── mta-api.js                          # MTA GTFS-RT parser
├── auto-select.js                      # Station auto-selection
├── stations.json                       # NYC subway stations data
└── start.sh                            # Development server script
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Commute options page | `index.html` | Main entry point |
| Live arrivals | `arrivals.html` | Shows real-time train times |
| Configuration | `settings.html` | User settings (home/work/stations) |
| MTA API parsing | `mta-api.js` | Manual GTFS-Realtime parsing |
| Station data | `stations.json` | NYC subway station list |
| Station auto-selection | `auto-select.js` | 4mi radius heuristic |

## CONVENTIONS

- **Vanilla JS:** No frameworks, no build tools
- **localStorage:** Simple key-value persistence
- **Direct API calls:** No backend required
- **No dependencies:** Self-contained web app

## ANTI-PATTERNS

- **No npm/dependencies:** Do not add package.json or npm dependencies
- **No TypeScript:** Keep everything as vanilla JavaScript
- **No build step:** Development is raw HTML/CSS/JS

## COMMANDS

```bash
# Start development server
cd web && ./start.sh

# Then open: http://localhost:8080
```

## NOTES

- Opens station lookup in Google Maps (no API key required)
- Weather data optional via OpenWeatherMap API key
- Widget updates: 30-second auto-refresh
