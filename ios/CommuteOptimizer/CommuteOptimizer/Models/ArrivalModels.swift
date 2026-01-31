import Foundation

struct Arrival: Identifiable {
    let id: UUID
    let routeId: String
    let direction: Direction
    let arrivalTime: TimeInterval  // Unix timestamp
    let minutesAway: Int

    init(routeId: String, direction: Direction, arrivalTime: TimeInterval, minutesAway: Int) {
        self.id = UUID()
        self.routeId = routeId
        self.direction = direction
        self.arrivalTime = arrivalTime
        self.minutesAway = minutesAway
    }
}

enum Direction: String {
    case north = "N"
    case south = "S"

    var headsign: String {
        switch self {
        case .north: return "Northbound"
        case .south: return "Southbound"
        }
    }
}

struct ArrivalGroup: Identifiable {
    let id: UUID
    let line: String
    let direction: Direction
    let arrivals: [Arrival]

    init(line: String, direction: Direction, arrivals: [Arrival]) {
        self.id = UUID()
        self.line = line
        self.direction = direction
        self.arrivals = arrivals
    }
}

struct NextArrivalResult {
    let nextTrain: String      // "3m", "Now", or "--"
    let arrivalTime: String    // "10:45 AM" or "--"
    let routeId: String?
    let minutesAway: Int

    static let unavailable = NextArrivalResult(nextTrain: "--", arrivalTime: "--", routeId: nil, minutesAway: 5)

    /// Display text showing "Now" for ≤0 minutes
    var displayText: String {
        if minutesAway <= 0 {
            return "Now"
        }
        return "\(minutesAway)m"
    }
}
