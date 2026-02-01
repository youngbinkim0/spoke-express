import Foundation

struct CommuteResponse: Codable {
    let options: [CommuteOption]
    let weather: Weather
    let alerts: [ServiceAlert]
    let generatedAt: String
}

struct CommuteOption: Codable, Identifiable {
    let id: String
    var rank: Int
    let type: CommuteType
    let durationMinutes: Int
    let summary: String
    let legs: [Leg]
    let nextTrain: String
    let arrivalTime: String
    let station: Station
}

enum CommuteType: String, Codable {
    case bikeToTransit = "bike_to_transit"
    case transitOnly = "transit_only"
    case walkOnly = "walk_only"
}

struct Leg: Codable, Identifiable {
    let id: UUID
    let mode: LegMode
    let duration: Int
    let to: String
    let route: String?
    let from: String?
    let numStops: Int?

    init(mode: LegMode, duration: Int, to: String, route: String? = nil, from: String? = nil, numStops: Int? = nil) {
        self.id = UUID()
        self.mode = mode
        self.duration = duration
        self.to = to
        self.route = route
        self.from = from
        self.numStops = numStops
    }

    enum CodingKeys: String, CodingKey {
        case mode, duration, to, route, from, numStops
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = UUID()
        self.mode = try container.decode(LegMode.self, forKey: .mode)
        self.duration = try container.decode(Int.self, forKey: .duration)
        self.to = try container.decode(String.self, forKey: .to)
        self.route = try container.decodeIfPresent(String.self, forKey: .route)
        self.from = try container.decodeIfPresent(String.self, forKey: .from)
        self.numStops = try container.decodeIfPresent(Int.self, forKey: .numStops)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(mode, forKey: .mode)
        try container.encode(duration, forKey: .duration)
        try container.encode(to, forKey: .to)
        try container.encodeIfPresent(route, forKey: .route)
        try container.encodeIfPresent(from, forKey: .from)
        try container.encodeIfPresent(numStops, forKey: .numStops)
    }
}

enum LegMode: String, Codable {
    case bike, walk, subway
}

struct Station: Codable {
    let id: String
    let name: String
    let mtaId: String
    let lines: [String]
    let lat: Double
    let lng: Double
    let borough: String
}

struct ServiceAlert: Codable, Identifiable {
    let id: UUID
    let routeIds: [String]
    let effect: String
    let headerText: String

    init(routeIds: [String], effect: String, headerText: String) {
        self.id = UUID()
        self.routeIds = routeIds
        self.effect = effect
        self.headerText = headerText
    }

    enum CodingKeys: String, CodingKey {
        case routeIds, effect, headerText
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = UUID()
        self.routeIds = try container.decode([String].self, forKey: .routeIds)
        self.effect = try container.decode(String.self, forKey: .effect)
        self.headerText = try container.decode(String.self, forKey: .headerText)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(routeIds, forKey: .routeIds)
        try container.encode(effect, forKey: .effect)
        try container.encode(headerText, forKey: .headerText)
    }
}
