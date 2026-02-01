import Foundation

class StationsDataSource {
    static let shared = StationsDataSource()

    private var cachedStations: [LocalStation]?

    private init() {}

    func getStations() -> [LocalStation] {
        if let cached = cachedStations {
            return cached
        }

        guard let url = Bundle.main.url(forResource: "stations", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(StationsFile.self, from: data) else {
            return []
        }

        cachedStations = file.stations
        return file.stations
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
}
