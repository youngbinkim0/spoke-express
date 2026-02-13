# Cloudflare Worker Knowledge Base

**Generated:** Thu, Feb 12, 2026
**Commit:** d1773a9

## OVERVIEW

Cloudflare Worker acting as a proxy for Google Routes API. Bypasses CORS restrictions for browser requests to Google Maps API.

## STRUCTURE

```
cloudflare-worker/
├── worker.js                           # Worker script
└── wrangler.toml                       # Worker configuration
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Worker script | `worker.js` | Main entry point |
| Configuration | `wrangler.toml` | Worker deployment settings |
| Endpoint | `/directions` | Only允许 endpoint |
| CORS handling | `worker.js` | OPTIONS preflight support |

## CONVENTIONS

- **Single routing:** Only `/directions` endpoint allowed
- **CORS pass-through:** Returns CORS headers for all responses
- **API proxy:** Forwards requests to Google Routes API
- **Environment secrets:** API key stored as `GOOGLE_API_KEY` secret

## ANTI-PATTERNS

- **No other endpoints:** Accessing any path other than `/directions` returns 404
- **No authentication:** Worker assumes API key is provided as query parameter

## COMMANDS

```bash
# Install wrangler globally
npm install -g wrangler

# Login to Cloudflare
wrangler login

# Deploy worker
wrangler deploy

# Set API key secret
wrangler secret put GOOGLE_API_KEY
```

## NOTES

- **Paid API:** Google Routes API costs money
- **Optional deployment:** Not required for basic functionality
- **CORS workaround:** Required for browser-based transit directions
