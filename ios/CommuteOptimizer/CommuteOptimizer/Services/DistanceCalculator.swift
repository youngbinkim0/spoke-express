import Foundation

struct DistanceCalculator {
    private static let earthRadiusMiles = 3959.0
    private static let bikingSpeedMph = 10.0
    private static let walkingSpeedMph = 3.0

    /// Haversine formula for great-circle distance
    static func haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ) -> Double {
        let dLat = (lat2 - lat1).degreesToRadians
        let dLon = (lon2 - lon1).degreesToRadians

        let a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1.degreesToRadians) * cos(lat2.degreesToRadians) *
                sin(dLon / 2) * sin(dLon / 2)

        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMiles * c
    }

    /// Bike time estimate - MUST match Android: ceil((distance / 10) * 60 * 1.3)
    static func estimateBikeTime(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double
    ) -> Int {
        let distance = haversineDistance(lat1: fromLat, lon1: fromLng, lat2: toLat, lon2: toLng)
        let timeHours = distance / bikingSpeedMph
        return Int(ceil(timeHours * 60 * 1.3))  // 30% padding
    }

    /// Walk time estimate - MUST match Android: ceil((distance / 3) * 60 * 1.2)
    static func estimateWalkTime(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double
    ) -> Int {
        let distance = haversineDistance(lat1: fromLat, lon1: fromLng, lat2: toLat, lon2: toLng)
        let timeHours = distance / walkingSpeedMph
        return Int(ceil(timeHours * 60 * 1.2))  // 20% padding
    }

    /// Transit time lookup table (known routes)
    static func estimateTransitTime(fromStopId: String, toStopId: String) -> Int {
        let knownRoutes: [String: Int] = [
            "G33_G22": 18,  // Bedford-Nostrand to Court Sq (G)
            "G34_G22": 16,  // Classon to Court Sq (G)
            "G35_G22": 14,  // Clinton-Washington to Court Sq (G)
            "G36_G22": 12,  // Fulton to Court Sq (G)
            "A42_G22": 15,  // Hoyt-Schermerhorn to Court Sq
            "G31_G22": 20,  // Lafayette to Court Sq
            "G32_G22": 22,  // Myrtle-Willoughby to Court Sq
            "G30_G22": 10,  // Broadway to Court Sq
            "G29_G22": 8,   // Metropolitan to Court Sq
            "G28_G22": 6,   // Nassau to Court Sq
            "G26_G22": 4    // Greenpoint to Court Sq
        ]
        return knownRoutes["\(fromStopId)_\(toStopId)"] ?? 0
    }
}

private extension Double {
    var degreesToRadians: Double { self * .pi / 180 }
}
