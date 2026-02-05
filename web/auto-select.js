// Auto-select stations based on home/work locations
// Shared utility functions (also defined in index.html inline script)

function calculateDistance(lat1, lng1, lat2, lng2) {
  const R = 3959;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLng/2) * Math.sin(dLng/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
}

function estimateBikeTime(fromLat, fromLng, toLat, toLng) {
  const distance = calculateDistance(fromLat, fromLng, toLat, toLng);
  const BIKE_SPEED_MPH = 10;
  return Math.ceil((distance / BIKE_SPEED_MPH) * 60 * 1.3);
}

function autoSelectStations(homeLat, homeLng, workLat, workLng, allStations) {
  const RADIUS_MILES = 4;
  const AVG_SUBWAY_SPEED_MPH = 15;
  const TOP_PER_LINE = 3;

  // 1. Filter to stations within radius
  const candidates = allStations.filter(s =>
    calculateDistance(homeLat, homeLng, s.lat, s.lng) <= RADIUS_MILES
  );

  // 2. Score each candidate
  const scored = candidates.map(s => {
    const bikeTime = estimateBikeTime(homeLat, homeLng, s.lat, s.lng);
    const transitEstimate = (calculateDistance(s.lat, s.lng, workLat, workLng) / AVG_SUBWAY_SPEED_MPH) * 60;
    return { station: s, score: bikeTime + transitEstimate };
  });

  // 3. Group by line, keep top N per line
  const byLine = {};
  for (const item of scored) {
    for (const line of item.station.lines) {
      if (!byLine[line]) byLine[line] = [];
      byLine[line].push(item);
    }
  }

  // 4. Top N per line
  const selectedIds = new Set();
  for (const line of Object.keys(byLine)) {
    byLine[line].sort((a, b) => a.score - b.score);
    for (const item of byLine[line].slice(0, TOP_PER_LINE)) {
      selectedIds.add(item.station.id);
    }
  }

  // 5. Return deduplicated list, sorted by score
  return scored
    .filter(item => selectedIds.has(item.station.id))
    .sort((a, b) => a.score - b.score)
    .map(item => item.station.id);
}
