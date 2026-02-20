
## 2026-02-13: MtaAlertsService URL Handling Fix

- Replaced force-unwrap `URL(string: alertsURL)!` with guard statement
- Pattern: `guard let url = URL(string: ...) else { print(...); return [] }`
- Early return with empty array prevents crashes from malformed URLs
- Added debug print for invalid URL diagnostics
