# Quickstart — Local Development

Get Spoke Express running locally in under 2 minutes.

> For general usage (no setup needed), visit **[the live site](https://youngbinkim0.github.io/spoke-express/)** — see the [main README](../README.md) for details.

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

### Google API Key

For the full experience (weather + accurate transit directions with transfers), you'll need a Google API key.

**→ [Get a Google API Key](../README.md#get-a-google-api-key-free-tier)** — step-by-step instructions in the main README.

Once you have a key, paste it into **Settings → API Key** in the app.

## 3. Use the app

- **Main page** — Ranked commute options (best first)
- **Live Trains** — Real-time arrivals for your stations
- **Settings** — Change locations and preferences

The app auto-refreshes every 30 seconds.

## PWA / Add to Home Screen (local testing)

The app includes a `manifest.json` for PWA support. To test "Add to Home Screen" locally:

- Serve over HTTPS (or use `localhost`, which browsers treat as secure)
- Open DevTools → **Application** tab → **Manifest** to verify it loads
- The manifest provides app name, icons, and standalone display mode
