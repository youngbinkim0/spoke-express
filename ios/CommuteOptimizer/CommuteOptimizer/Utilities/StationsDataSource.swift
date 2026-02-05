import Foundation

class StationsDataSource {
    static let shared = StationsDataSource()

    private var cachedStations: [LocalStation]?

    private init() {}

    func getStations() -> [LocalStation] {
        if let cached = cachedStations {
            return cached
        }

        // Try multiple bundle locations for App Intents compatibility
        let bundles = [
            Bundle.main,
            Bundle(for: StationsDataSource.self),
            Bundle(identifier: "com.commuteoptimizer.app"),
            Bundle(identifier: "com.commuteoptimizer.app.widget")
        ].compactMap { $0 }

        for bundle in bundles {
            if let url = bundle.url(forResource: "stations", withExtension: "json"),
               let data = try? Data(contentsOf: url),
               let file = try? JSONDecoder().decode(StationsFile.self, from: data) {
                cachedStations = file.stations
                return file.stations
            }
        }

        return []
    }

    func getStation(id: String) -> LocalStation? {
        getStations().first { $0.id == id }
    }

    func getStationByMtaId(_ mtaId: String) -> LocalStation? {
        getStations().first { $0.mtaId == mtaId }
    }

    /// Get stations sorted by distance from a location
    func getStationsSortedByDistance(fromLat: Double, fromLng: Double) -> [(station: LocalStation, distance: Double)] {
        getStations().map { station in
            let distance = DistanceCalculator.haversineDistance(
                lat1: fromLat, lon1: fromLng,
                lat2: station.lat, lon2: station.lng
            )
            return (station, distance)
        }.sorted { $0.distance < $1.distance }
    }

    /// Auto-select best bike-to stations within radius, ranked by estimated commute time
    func autoSelectStations(homeLat: Double, homeLng: Double, workLat: Double, workLng: Double) -> [String] {
        let radiusMiles = 4.0
        let avgSubwaySpeedMph = 15.0
        let topPerLine = 3

        let allStations = getStations()

        // 1. Filter within radius
        let candidates = allStations.filter { station in
            DistanceCalculator.haversineDistance(
                lat1: homeLat, lon1: homeLng,
                lat2: station.lat, lon2: station.lng
            ) <= radiusMiles
        }

        // 2. Score each: bike_time + estimated_transit_time
        struct ScoredStation {
            let station: LocalStation
            let score: Double
        }
        let scored = candidates.map { station in
            let bikeTime = Double(DistanceCalculator.estimateBikeTime(
                fromLat: homeLat, fromLng: homeLng,
                toLat: station.lat, toLng: station.lng
            ))
            let transitEst = (DistanceCalculator.haversineDistance(
                lat1: station.lat, lon1: station.lng,
                lat2: workLat, lon2: workLng
            ) / avgSubwaySpeedMph) * 60
            return ScoredStation(station: station, score: bikeTime + transitEst)
        }

        // 3. Group by line, top N per line
        var byLine: [String: [ScoredStation]] = [:]
        for item in scored {
            for line in item.station.lines {
                byLine[line, default: []].append(item)
            }
        }

        // 4. Collect top per line
        var selectedIds = Set<String>()
        for (_, stations) in byLine {
            let sorted = stations.sorted { $0.score < $1.score }
            for item in sorted.prefix(topPerLine) {
                selectedIds.insert(item.station.id)
            }
        }

        // 5. Return sorted by score
        return scored
            .filter { selectedIds.contains($0.station.id) }
            .sorted { $0.score < $1.score }
            .map { $0.station.id }
    }
}
