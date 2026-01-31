import Foundation

struct LocalStation: Codable, Identifiable {
    let id: String
    let name: String
    let transiterId: String
    let lines: [String]
    let lat: Double
    let lng: Double
    let borough: String

    var linesDisplay: String {
        lines.joined(separator: ", ")
    }
}

struct StationsFile: Codable {
    let stations: [LocalStation]
}
