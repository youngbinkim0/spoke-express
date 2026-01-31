import Foundation

struct RankingService {
    /// Rank options with weather-aware adjustments
    /// - Sort by total duration (fastest first)
    /// - If weather is bad AND bike is #1, swap with first transit option
    static func rankOptions(_ options: [CommuteOption], weather: Weather) -> [CommuteOption] {
        guard !options.isEmpty else { return [] }

        // Sort by duration (fastest first)
        var sorted = options.sorted { $0.durationMinutes < $1.durationMinutes }

        // Weather adjustment: demote bike if weather is bad
        if weather.isBad {
            if let firstBikeIndex = sorted.firstIndex(where: { $0.type == .bikeToTransit }),
               firstBikeIndex == 0,
               let firstTransitIndex = sorted.firstIndex(where: { $0.type == .transitOnly }),
               firstTransitIndex > 0 {
                // Swap: move first transit option to #1
                let transit = sorted.remove(at: firstTransitIndex)
                sorted.insert(transit, at: 0)
            }
        }

        // Assign ranks (1-based)
        return sorted.enumerated().map { index, option in
            var ranked = option
            ranked.rank = index + 1
            return ranked
        }
    }
}
