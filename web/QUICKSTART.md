# Quickstart

Get Spoke Express running in under 2 minutes.

## 1. Start the app

```bash
./start.sh
```

This starts a local web server and opens the app in your browser.

**Requirements:** Python 3 or Node.js

## 2. Configure Settings

1. Click **Settings** in the top right
2. Enter your **home address** and click "Lookup"
3. Enter your **work address** and click "Lookup"
4. Select **1-3 nearby subway stations** you can walk or bike to
5. Click **Save Settings**

### Optional: Google API Key

Weather is powered by Google (same API key — no separate weather key needed).

For the full experience (weather + accurate transit directions with transfers), add a Google API key in Settings and optionally deploy the [Cloudflare Worker](../cloudflare-worker/) proxy.

## 3. Use the app

- **Main page** - Ranked commute options (best first)
- **Live Trains** - Real-time arrivals for your stations
- **Settings** - Change locations and preferences

The app auto-refreshes every 30 seconds.
