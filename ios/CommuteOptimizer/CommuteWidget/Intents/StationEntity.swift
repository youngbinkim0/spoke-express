import AppIntents
import Foundation

/// App Intents entity representing a subway station for widget configuration
struct StationEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation {
        TypeDisplayRepresentation(name: "Station")
    }

    static var defaultQuery = StationEntityQuery()

    var id: String
    var name: String
    var lines: [String]
    var borough: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(
            title: "\(name)",
            subtitle: "\(lines.joined(separator: ", ")) - \(borough)"
        )
    }

    init(id: String, name: String, lines: [String], borough: String) {
        self.id = id
        self.name = name
        self.lines = lines
        self.borough = borough
    }

    init(from station: LocalStation) {
        self.id = station.id
        self.name = station.name
        self.lines = station.lines
        self.borough = station.borough
    }
}

/// Query for finding and loading stations
struct StationEntityQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [StationEntity] {
        let stations = StationsDataSource.shared.getStations()
        return identifiers.compactMap { id in
            guard let station = stations.first(where: { $0.id == id }) else {
                return nil
            }
            return StationEntity(from: station)
        }
    }

    func suggestedEntities() async throws -> [StationEntity] {
        // Return all stations as suggestions, sorted by name
        StationsDataSource.shared.getStations()
            .sorted { $0.name < $1.name }
            .map { StationEntity(from: $0) }
    }

    func defaultResult() async -> StationEntity? {
        // Return the first live station from settings if available
        let settingsManager = SettingsManager()
        settingsManager.loadFromDefaults()

        if let stationId = settingsManager.liveStations.first,
           let station = StationsDataSource.shared.getStation(id: stationId) {
            return StationEntity(from: station)
        }

        // Otherwise return first station
        return StationsDataSource.shared.getStations().first.map { StationEntity(from: $0) }
    }
}

extension StationEntityQuery: EntityStringQuery {
    func entities(matching string: String) async throws -> [StationEntity] {
        let lowercased = string.lowercased()
        return StationsDataSource.shared.getStations()
            .filter { station in
                station.name.lowercased().contains(lowercased) ||
                station.lines.contains { $0.lowercased().contains(lowercased) } ||
                station.borough.lowercased().contains(lowercased)
            }
            .sorted { $0.name < $1.name }
            .map { StationEntity(from: $0) }
    }
}
