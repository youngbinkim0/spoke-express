import type { CommuteOption, Weather } from '../types/index.js';

export function rankOptions(options: CommuteOption[], weather: Weather): CommuteOption[] {
  if (options.length === 0) return [];

  // Sort by total duration (fastest first)
  const sorted = [...options].sort((a, b) => a.duration_minutes - b.duration_minutes);

  // If bad weather, ensure bike options aren't #1
  if (weather.is_bad) {
    const firstBikeIndex = sorted.findIndex((o) => o.type === 'bike_to_transit');
    const firstTransitIndex = sorted.findIndex((o) => o.type === 'transit_only');

    if (firstBikeIndex === 0 && firstTransitIndex > 0) {
      // Move first transit option to #1
      const [transit] = sorted.splice(firstTransitIndex, 1);
      sorted.unshift(transit);
    }
  }

  // Assign ranks
  return sorted.map((opt, i) => ({
    ...opt,
    rank: i + 1,
  }));
}
