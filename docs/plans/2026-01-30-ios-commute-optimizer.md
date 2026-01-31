# iOS Commute Optimizer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an iOS app with full feature parity to the Android app - SwiftUI main app with 3 tabs (Commute, Live Trains, Settings) plus a WidgetKit home screen widget showing top 3 commute options.

**Architecture:** Service-layer pattern with async/await networking. Manual protobuf parsing for MTA GTFS-Realtime feeds (no SwiftProtobuf dependency). UserDefaults with App Groups for widget data sharing. All time/distance calculations must match Android exactly.

**Tech Stack:** SwiftUI, async/await, WidgetKit, UserDefaults + @AppStorage, CoreLocation (CLGeocoder), XCTest

---

## Task 1: Create Xcode Project Structure

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer.xcodeproj`
- Create: `ios/CommuteOptimizer/CommuteOptimizer/CommuteOptimizerApp.swift`
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Resources/stations.json`

**Step 1: Create iOS project directory**

```bash
mkdir -p ios/CommuteOptimizer
```

**Step 2: Create Xcode project (manual or via xcodegen)**

Open Xcode and create new project:
- Template: iOS App
- Product Name: CommuteOptimizer
- Interface: SwiftUI
- Language: Swift
- Bundle ID: `com.commuteoptimizer.app`
- Include Tests: Yes

**Step 3: Add Widget Extension target**

In Xcode: File → New → Target → Widget Extension
- Product Name: CommuteWidget
- Include Configuration App Intent: No (use static configuration)
- Bundle ID: `com.commuteoptimizer.app.widget`

**Step 4: Configure App Groups**

1. Select main app target → Signing & Capabilities → + Capability → App Groups
2. Add: `group.com.commuteoptimizer`
3. Select widget target → same steps

**Step 5: Copy stations.json**

```bash
cp data/stations.json ios/CommuteOptimizer/CommuteOptimizer/Resources/
```

**Step 6: Commit**

```bash
git add ios/
git commit -m "feat(ios): create Xcode project with widget extension"
```

---

## Task 2: Data Models

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Models/CommuteModels.swift`
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Models/WeatherModels.swift`
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Models/ArrivalModels.swift`
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Models/LocalStation.swift`

**Step 1: Create CommuteModels.swift**

```swift
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
}

enum LegMode: String, Codable {
    case bike, walk, subway
}

struct Station: Codable {
    let id: String
    let name: String
    let transiterId: String
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
}
```

**Step 2: Create WeatherModels.swift**

```swift
import Foundation

struct Weather: Codable {
    let tempF: Int
    let conditions: String
    let precipitationType: PrecipitationType
    let precipitationProbability: Int
    let isBad: Bool

    enum CodingKeys: String, CodingKey {
        case tempF = "temp_f"
        case conditions
        case precipitationType = "precipitation_type"
        case precipitationProbability = "precipitation_probability"
        case isBad = "is_bad"
    }
}

enum PrecipitationType: String, Codable {
    case none, rain, snow, mix
}

// OpenWeatherMap API response
struct OpenWeatherResponse: Codable {
    let lat: Double
    let lon: Double
    let current: CurrentWeather
    let hourly: [HourlyWeather]?
}

struct CurrentWeather: Codable {
    let temp: Double
    let weather: [WeatherCondition]
}

struct WeatherCondition: Codable {
    let id: Int
    let main: String
    let description: String
    let icon: String
}

struct HourlyWeather: Codable {
    let dt: Int
    let temp: Double
    let pop: Double  // Probability of precipitation (0-1)
}
```

**Step 3: Create ArrivalModels.swift**

```swift
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
    let nextTrain: String      // "3m" or "--"
    let arrivalTime: String    // "10:45 AM" or "--"
    let routeId: String?
    let minutesAway: Int

    static let unavailable = NextArrivalResult(nextTrain: "--", arrivalTime: "--", routeId: nil, minutesAway: 5)
}
```

**Step 4: Create LocalStation.swift**

```swift
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
```

**Step 5: Commit**

```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Models/
git commit -m "feat(ios): add data models"
```

---

## Task 3: DistanceCalculator with TDD

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Services/DistanceCalculator.swift`
- Create: `ios/CommuteOptimizer/CommuteOptimizerTests/DistanceCalculatorTests.swift`

**Step 1: Write failing test for haversineDistance**

```swift
// DistanceCalculatorTests.swift
import XCTest
@testable import CommuteOptimizer

final class DistanceCalculatorTests: XCTestCase {

    func testHaversineDistance_BedfordNostrandToCourtSq() {
        // Bedford-Nostrand: 40.6896, -73.9535
        // Court Sq: 40.7471, -73.9456
        let distance = DistanceCalculator.haversineDistance(
            lat1: 40.6896, lon1: -73.9535,
            lat2: 40.7471, lon2: -73.9456
        )
        // Expected: ~4.0 miles
        XCTAssertEqual(distance, 4.0, accuracy: 0.5)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `xcodebuild test -scheme CommuteOptimizer -destination 'platform=iOS Simulator,name=iPhone 15'`
Expected: FAIL with "DistanceCalculator has no member haversineDistance"

**Step 3: Implement haversineDistance**

```swift
// DistanceCalculator.swift
import Foundation

struct DistanceCalculator {
    private static let earthRadiusMiles = 3959.0

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
}

private extension Double {
    var degreesToRadians: Double { self * .pi / 180 }
}
```

**Step 4: Run test to verify it passes**

Run: `xcodebuild test -scheme CommuteOptimizer -destination 'platform=iOS Simulator,name=iPhone 15'`
Expected: PASS

**Step 5: Write failing test for estimateBikeTime**

```swift
func testEstimateBikeTime_OneMile() {
    // 1 mile at 10 mph = 0.1 hours = 6 min
    // With 30% padding: ceil(6 * 1.3) = ceil(7.8) = 8 min
    // Using coords ~1 mile apart
    let time = DistanceCalculator.estimateBikeTime(
        fromLat: 40.6896, fromLng: -73.9535,
        toLat: 40.7041, toLng: -73.9535  // ~1 mile north
    )
    XCTAssertEqual(time, 8, accuracy: 2)
}

func testEstimateBikeTime_MatchesAndroidFormula() {
    // Formula must be: ceil((distance / 10) * 60 * 1.3)
    // For 2 miles: ceil((2/10) * 60 * 1.3) = ceil(15.6) = 16
    let time = DistanceCalculator.estimateBikeTime(
        fromLat: 40.6896, fromLng: -73.9535,
        toLat: 40.7186, toLng: -73.9535  // ~2 miles north
    )
    XCTAssertEqual(time, 16, accuracy: 2)
}
```

**Step 6: Run tests to verify they fail**

Expected: FAIL

**Step 7: Implement estimateBikeTime**

```swift
private static let bikingSpeedMph = 10.0

/// Bike time estimate - MUST match Android: ceil((distance / 10) * 60 * 1.3)
static func estimateBikeTime(
    fromLat: Double, fromLng: Double,
    toLat: Double, toLng: Double
) -> Int {
    let distance = haversineDistance(lat1: fromLat, lon1: fromLng, lat2: toLat, lon2: toLng)
    let timeHours = distance / bikingSpeedMph
    return Int(ceil(timeHours * 60 * 1.3))  // 30% padding
}
```

**Step 8: Run tests to verify they pass**

Expected: PASS

**Step 9: Write failing test for estimateWalkTime**

```swift
func testEstimateWalkTime_HalfMile() {
    // 0.5 miles at 3 mph = 10 min
    // With 20% padding: ceil(10 * 1.2) = 12 min
    let time = DistanceCalculator.estimateWalkTime(
        fromLat: 40.6896, fromLng: -73.9535,
        toLat: 40.6968, toLng: -73.9535  // ~0.5 miles north
    )
    XCTAssertEqual(time, 12, accuracy: 3)
}

func testEstimateWalkTime_MatchesAndroidFormula() {
    // Formula must be: ceil((distance / 3) * 60 * 1.2)
    // For 1 mile: ceil((1/3) * 60 * 1.2) = ceil(24) = 24
    let time = DistanceCalculator.estimateWalkTime(
        fromLat: 40.6896, fromLng: -73.9535,
        toLat: 40.7041, toLng: -73.9535  // ~1 mile north
    )
    XCTAssertEqual(time, 24, accuracy: 3)
}
```

**Step 10: Run tests to verify they fail**

Expected: FAIL

**Step 11: Implement estimateWalkTime**

```swift
private static let walkingSpeedMph = 3.0

/// Walk time estimate - MUST match Android: ceil((distance / 3) * 60 * 1.2)
static func estimateWalkTime(
    fromLat: Double, fromLng: Double,
    toLat: Double, toLng: Double
) -> Int {
    let distance = haversineDistance(lat1: fromLat, lon1: fromLng, lat2: toLat, lon2: toLng)
    let timeHours = distance / walkingSpeedMph
    return Int(ceil(timeHours * 60 * 1.2))  // 20% padding
}
```

**Step 12: Run tests to verify they pass**

Expected: PASS

**Step 13: Add estimateTransitTime with known routes**

```swift
func testEstimateTransitTime_KnownRoute() {
    // G33 (Bedford-Nostrand) to G22 (Court Sq) = 18 min
    let time = DistanceCalculator.estimateTransitTime(fromStopId: "G33", toStopId: "G22")
    XCTAssertEqual(time, 18)
}

func testEstimateTransitTime_UnknownRoute() {
    // Unknown routes default to 15 min
    let time = DistanceCalculator.estimateTransitTime(fromStopId: "XXX", toStopId: "YYY")
    XCTAssertEqual(time, 15)
}
```

```swift
/// Transit time lookup table (known routes)
static func estimateTransitTime(fromStopId: String, toStopId: String) -> Int {
    let knownRoutes: [String: Int] = [
        "G33_G22": 18,  // Bedford-Nostrand to Court Sq (G)
        "G34_G22": 16,  // Classon to Court Sq (G)
        "G35_G22": 14,  // Clinton-Washington to Court Sq (G)
        "G36_G22": 12,  // Fulton to Court Sq (G)
        "A42_G22": 15   // Hoyt-Schermerhorn to Court Sq
    ]
    return knownRoutes["\(fromStopId)_\(toStopId)"] ?? 15
}
```

**Step 14: Commit**

```bash
git add ios/CommuteOptimizer/
git commit -m "feat(ios): add DistanceCalculator with TDD"
```

---

## Task 4: RankingService with TDD

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Services/RankingService.swift`
- Create: `ios/CommuteOptimizer/CommuteOptimizerTests/RankingServiceTests.swift`

**Step 1: Write failing tests**

```swift
// RankingServiceTests.swift
import XCTest
@testable import CommuteOptimizer

final class RankingServiceTests: XCTestCase {

    private let goodWeather = Weather(
        tempF: 70,
        conditions: "Clear",
        precipitationType: .none,
        precipitationProbability: 0,
        isBad: false
    )

    private let badWeather = Weather(
        tempF: 50,
        conditions: "Rain",
        precipitationType: .rain,
        precipitationProbability: 80,
        isBad: true
    )

    private func makeOption(id: String, type: CommuteType, duration: Int) -> CommuteOption {
        CommuteOption(
            id: id,
            rank: 0,
            type: type,
            durationMinutes: duration,
            summary: "\(type.rawValue)",
            legs: [],
            nextTrain: "5m",
            arrivalTime: "10:00 AM",
            station: Station(id: "test", name: "Test", transiterId: "T1", lines: ["G"], lat: 0, lng: 0, borough: "Test")
        )
    }

    func testSortsByDuration() {
        let options = [
            makeOption(id: "1", type: .bikeToTransit, duration: 30),
            makeOption(id: "2", type: .transitOnly, duration: 20),
            makeOption(id: "3", type: .walkOnly, duration: 25)
        ]

        let ranked = RankingService.rankOptions(options, weather: goodWeather)

        XCTAssertEqual(ranked[0].id, "2")  // 20 min
        XCTAssertEqual(ranked[1].id, "3")  // 25 min
        XCTAssertEqual(ranked[2].id, "1")  // 30 min
    }

    func testAssignsRanks() {
        let options = [
            makeOption(id: "1", type: .bikeToTransit, duration: 20),
            makeOption(id: "2", type: .transitOnly, duration: 25)
        ]

        let ranked = RankingService.rankOptions(options, weather: goodWeather)

        XCTAssertEqual(ranked[0].rank, 1)
        XCTAssertEqual(ranked[1].rank, 2)
    }

    func testDemotesBikeInBadWeather() {
        let options = [
            makeOption(id: "bike", type: .bikeToTransit, duration: 20),  // Fastest
            makeOption(id: "transit", type: .transitOnly, duration: 25)
        ]

        let ranked = RankingService.rankOptions(options, weather: badWeather)

        XCTAssertEqual(ranked[0].id, "transit")  // Transit moved to #1
        XCTAssertEqual(ranked[1].id, "bike")     // Bike demoted to #2
        XCTAssertEqual(ranked[0].rank, 1)
        XCTAssertEqual(ranked[1].rank, 2)
    }

    func testKeepsBikeFirstInGoodWeather() {
        let options = [
            makeOption(id: "bike", type: .bikeToTransit, duration: 20),  // Fastest
            makeOption(id: "transit", type: .transitOnly, duration: 25)
        ]

        let ranked = RankingService.rankOptions(options, weather: goodWeather)

        XCTAssertEqual(ranked[0].id, "bike")     // Bike stays #1
        XCTAssertEqual(ranked[1].id, "transit")
    }

    func testDoesNotDemoteIfBikeNotFirst() {
        let options = [
            makeOption(id: "transit", type: .transitOnly, duration: 20),  // Fastest
            makeOption(id: "bike", type: .bikeToTransit, duration: 25)
        ]

        let ranked = RankingService.rankOptions(options, weather: badWeather)

        // No swap needed - transit already first
        XCTAssertEqual(ranked[0].id, "transit")
        XCTAssertEqual(ranked[1].id, "bike")
    }

    func testEmptyOptions() {
        let ranked = RankingService.rankOptions([], weather: goodWeather)
        XCTAssertTrue(ranked.isEmpty)
    }
}
```

**Step 2: Run tests to verify they fail**

Expected: FAIL

**Step 3: Implement RankingService**

```swift
// RankingService.swift
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
```

**Step 4: Run tests to verify they pass**

Expected: PASS

**Step 5: Commit**

```bash
git add ios/CommuteOptimizer/
git commit -m "feat(ios): add RankingService with TDD"
```

---

## Task 5: MtaColors Utility

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Utilities/MtaColors.swift`

**Step 1: Implement MtaColors**

```swift
// MtaColors.swift
import SwiftUI

struct MtaColors {
    // Official MTA line colors
    static let lineColors: [String: Color] = [
        // Red lines (1, 2, 3)
        "1": Color(hex: "#EE352E"),
        "2": Color(hex: "#EE352E"),
        "3": Color(hex: "#EE352E"),

        // Green lines (4, 5, 6)
        "4": Color(hex: "#00933C"),
        "5": Color(hex: "#00933C"),
        "6": Color(hex: "#00933C"),

        // Purple line (7)
        "7": Color(hex: "#B933AD"),

        // Blue lines (A, C, E)
        "A": Color(hex: "#0039A6"),
        "C": Color(hex: "#0039A6"),
        "E": Color(hex: "#0039A6"),

        // Orange lines (B, D, F, M)
        "B": Color(hex: "#FF6319"),
        "D": Color(hex: "#FF6319"),
        "F": Color(hex: "#FF6319"),
        "M": Color(hex: "#FF6319"),

        // Lime green line (G)
        "G": Color(hex: "#6CBE45"),

        // Brown lines (J, Z)
        "J": Color(hex: "#996633"),
        "Z": Color(hex: "#996633"),

        // Gray line (L)
        "L": Color(hex: "#A7A9AC"),

        // Yellow lines (N, Q, R, W)
        "N": Color(hex: "#FCCC0A"),
        "Q": Color(hex: "#FCCC0A"),
        "R": Color(hex: "#FCCC0A"),
        "W": Color(hex: "#FCCC0A"),

        // Shuttle (S)
        "S": Color(hex: "#808183")
    ]

    static func color(for line: String) -> Color {
        lineColors[line.uppercased()] ?? Color(hex: "#808183")
    }

    static func textColor(for line: String) -> Color {
        // Yellow lines need dark text
        switch line.uppercased() {
        case "N", "Q", "R", "W": return .black
        default: return .white
        }
    }

    static func weatherEmoji(_ conditions: String, precipType: PrecipitationType) -> String {
        switch precipType {
        case .rain: return "🌧"
        case .snow: return "❄️"
        case .mix: return "🌨"
        case .none:
            if conditions.lowercased().contains("cloud") { return "☁️" }
            if conditions.lowercased().contains("sun") || conditions.lowercased().contains("clear") { return "☀️" }
            return ""
        }
    }

    // Rank badge colors
    static let goldBadge = Color(hex: "#FFD700")
    static let silverBadge = Color(hex: "#C0C0C0")
    static let bronzeBadge = Color(hex: "#CD7F32")

    static func rankColor(for rank: Int) -> Color {
        switch rank {
        case 1: return goldBadge
        case 2: return silverBadge
        case 3: return bronzeBadge
        default: return Color.gray
        }
    }
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r, g, b: Double
        r = Double((int >> 16) & 0xFF) / 255.0
        g = Double((int >> 8) & 0xFF) / 255.0
        b = Double(int & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}
```

**Step 2: Commit**

```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Utilities/
git commit -m "feat(ios): add MtaColors utility"
```

---

## Task 6: ProtobufReader Utility

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Utilities/ProtobufReader.swift`
- Create: `ios/CommuteOptimizer/CommuteOptimizerTests/ProtobufReaderTests.swift`

**Step 1: Write basic tests**

```swift
// ProtobufReaderTests.swift
import XCTest
@testable import CommuteOptimizer

final class ProtobufReaderTests: XCTestCase {

    func testReadVarint_SingleByte() {
        // Value 1 encoded as single byte
        let data = Data([0x01])
        let reader = ProtobufReader(data: data)
        XCTAssertEqual(reader.readVarint(), 1)
    }

    func testReadVarint_MultiByte() {
        // Value 300 = 0b100101100 encoded as [0xAC, 0x02]
        let data = Data([0xAC, 0x02])
        let reader = ProtobufReader(data: data)
        XCTAssertEqual(reader.readVarint(), 300)
    }

    func testReadString() {
        let testString = "hello"
        let data = Data(testString.utf8)
        let reader = ProtobufReader(data: data)
        XCTAssertEqual(reader.readString(length: 5), "hello")
    }

    func testHasMore() {
        let data = Data([0x01, 0x02])
        let reader = ProtobufReader(data: data)
        XCTAssertTrue(reader.hasMore)
        _ = reader.readVarint()
        XCTAssertTrue(reader.hasMore)
        _ = reader.readVarint()
        XCTAssertFalse(reader.hasMore)
    }
}
```

**Step 2: Implement ProtobufReader**

```swift
// ProtobufReader.swift
import Foundation

/// Manual protobuf decoder for GTFS-Realtime feeds
/// Avoids SwiftProtobuf dependency, matches webapp/Android implementations
class ProtobufReader {
    private let data: Data
    private var position: Int = 0

    init(data: Data) {
        self.data = data
    }

    var hasMore: Bool {
        position < data.count
    }

    func readVarint() -> UInt64 {
        var result: UInt64 = 0
        var shift: UInt64 = 0

        while position < data.count {
            let byte = data[position]
            position += 1
            result |= UInt64(byte & 0x7F) << shift
            if (byte & 0x80) == 0 { break }
            shift += 7
        }
        return result
    }

    func readString(length: Int) -> String {
        let endIndex = min(position + length, data.count)
        let bytes = data[position..<endIndex]
        position = endIndex
        return String(data: Data(bytes), encoding: .utf8) ?? ""
    }

    func readBytes(length: Int) -> Data {
        let endIndex = min(position + length, data.count)
        let bytes = Data(data[position..<endIndex])
        position = endIndex
        return bytes
    }

    func skip(wireType: Int) {
        switch wireType {
        case 0: _ = readVarint()           // Varint
        case 1: position += 8              // 64-bit
        case 2:                            // Length-delimited
            let len = Int(readVarint())
            position += len
        case 5: position += 4              // 32-bit
        default: break
        }
    }
}
```

**Step 3: Run tests**

Expected: PASS

**Step 4: Commit**

```bash
git add ios/CommuteOptimizer/
git commit -m "feat(ios): add ProtobufReader for GTFS-Realtime parsing"
```

---

## Task 7: SettingsManager

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Utilities/SettingsManager.swift`

**Step 1: Implement SettingsManager**

```swift
// SettingsManager.swift
import Foundation
import SwiftUI

class SettingsManager: ObservableObject {
    private let defaults: UserDefaults
    private let suiteName = "group.com.commuteoptimizer"

    // Keys
    private enum Keys {
        static let openWeatherApiKey = "openweather_api_key"
        static let googleApiKey = "google_api_key"
        static let workerUrl = "worker_url"
        static let homeLat = "home_lat"
        static let homeLng = "home_lng"
        static let homeAddress = "home_address"
        static let workLat = "work_lat"
        static let workLng = "work_lng"
        static let workAddress = "work_address"
        static let bikeStations = "bike_stations"
        static let liveStations = "live_stations"
        static let destinationStation = "destination_station"
        static let showBikeOptions = "show_bike_options"
    }

    init() {
        if let sharedDefaults = UserDefaults(suiteName: suiteName) {
            self.defaults = sharedDefaults
        } else {
            self.defaults = UserDefaults.standard
        }
    }

    // MARK: - API Keys

    @Published var openWeatherApiKey: String {
        didSet { defaults.set(openWeatherApiKey, forKey: Keys.openWeatherApiKey) }
    }

    @Published var googleApiKey: String {
        didSet { defaults.set(googleApiKey, forKey: Keys.googleApiKey) }
    }

    @Published var workerUrl: String {
        didSet { defaults.set(workerUrl, forKey: Keys.workerUrl) }
    }

    // MARK: - Home Location

    @Published var homeLat: Double {
        didSet { defaults.set(homeLat, forKey: Keys.homeLat) }
    }

    @Published var homeLng: Double {
        didSet { defaults.set(homeLng, forKey: Keys.homeLng) }
    }

    @Published var homeAddress: String {
        didSet { defaults.set(homeAddress, forKey: Keys.homeAddress) }
    }

    // MARK: - Work Location

    @Published var workLat: Double {
        didSet { defaults.set(workLat, forKey: Keys.workLat) }
    }

    @Published var workLng: Double {
        didSet { defaults.set(workLng, forKey: Keys.workLng) }
    }

    @Published var workAddress: String {
        didSet { defaults.set(workAddress, forKey: Keys.workAddress) }
    }

    // MARK: - Stations

    @Published var bikeStations: [String] {
        didSet { defaults.set(bikeStations, forKey: Keys.bikeStations) }
    }

    @Published var liveStations: [String] {
        didSet { defaults.set(liveStations, forKey: Keys.liveStations) }
    }

    @Published var destinationStation: String {
        didSet { defaults.set(destinationStation, forKey: Keys.destinationStation) }
    }

    // MARK: - Preferences

    @Published var showBikeOptions: Bool {
        didSet { defaults.set(showBikeOptions, forKey: Keys.showBikeOptions) }
    }

    // MARK: - Initialization

    func loadFromDefaults() {
        openWeatherApiKey = defaults.string(forKey: Keys.openWeatherApiKey) ?? ""
        googleApiKey = defaults.string(forKey: Keys.googleApiKey) ?? ""
        workerUrl = defaults.string(forKey: Keys.workerUrl) ?? ""
        homeLat = defaults.double(forKey: Keys.homeLat)
        homeLng = defaults.double(forKey: Keys.homeLng)
        homeAddress = defaults.string(forKey: Keys.homeAddress) ?? ""
        workLat = defaults.double(forKey: Keys.workLat)
        workLng = defaults.double(forKey: Keys.workLng)
        workAddress = defaults.string(forKey: Keys.workAddress) ?? ""
        bikeStations = defaults.stringArray(forKey: Keys.bikeStations) ?? []
        liveStations = defaults.stringArray(forKey: Keys.liveStations) ?? []
        destinationStation = defaults.string(forKey: Keys.destinationStation) ?? "court-sq"
        showBikeOptions = defaults.object(forKey: Keys.showBikeOptions) == nil ? true : defaults.bool(forKey: Keys.showBikeOptions)
    }

    // MARK: - Validation

    var isConfigured: Bool {
        !openWeatherApiKey.isEmpty &&
        homeLat != 0 && homeLng != 0 &&
        workLat != 0 && workLng != 0 &&
        !bikeStations.isEmpty
    }
}
```

**Step 2: Commit**

```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Utilities/
git commit -m "feat(ios): add SettingsManager with App Groups"
```

---

## Task 8: StationsDataSource

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Utilities/StationsDataSource.swift`

**Step 1: Implement StationsDataSource**

```swift
// StationsDataSource.swift
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

    func getStationByTransiterId(_ transiterId: String) -> LocalStation? {
        getStations().first { $0.transiterId == transiterId }
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
```

**Step 2: Commit**

```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Utilities/
git commit -m "feat(ios): add StationsDataSource for bundled station data"
```

---

## Task 9: WeatherApiService

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Services/WeatherApiService.swift`

**Step 1: Implement WeatherApiService**

```swift
// WeatherApiService.swift
import Foundation

actor WeatherApiService {
    private let baseURL = "https://api.openweathermap.org/data/3.0/onecall"

    func getWeather(lat: Double, lng: Double, apiKey: String) async throws -> Weather {
        guard !apiKey.isEmpty else {
            return defaultWeather
        }

        let urlString = "\(baseURL)?lat=\(lat)&lon=\(lng)&units=imperial&exclude=minutely,daily,alerts&appid=\(apiKey)"

        guard let url = URL(string: urlString) else {
            return defaultWeather
        }

        let (data, _) = try await URLSession.shared.data(from: url)
        let response = try JSONDecoder().decode(OpenWeatherResponse.self, from: data)
        return parseResponse(response)
    }

    private func parseResponse(_ response: OpenWeatherResponse) -> Weather {
        let current = response.current
        let weatherId = current.weather.first?.id ?? 800
        let weatherMain = current.weather.first?.main ?? "Clear"

        let precipitationType: PrecipitationType = {
            switch weatherId {
            case 200..<600: return .rain
            case 600..<611: return .snow
            case 611..<700: return .mix
            default: return .none
            }
        }()

        let precipProbability = response.hourly?.first?.pop ?? 0.0
        let isBad = precipitationType != .none || precipProbability > 0.5

        return Weather(
            tempF: Int(current.temp),
            conditions: weatherMain,
            precipitationType: precipitationType,
            precipitationProbability: Int(precipProbability * 100),
            isBad: isBad
        )
    }

    private var defaultWeather: Weather {
        Weather(tempF: 65, conditions: "Unknown", precipitationType: .none, precipitationProbability: 0, isBad: false)
    }
}
```

**Step 2: Commit**

```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Services/
git commit -m "feat(ios): add WeatherApiService"
```

---

## Task 10: MtaApiService

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Services/MtaApiService.swift`

**Step 1: Implement MtaApiService**

Reference: `android/app/src/main/java/com/commuteoptimizer/widget/data/api/MtaApiService.kt`

```swift
// MtaApiService.swift
import Foundation

actor MtaApiService {
    private let baseURL = "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2F"

    // Line to feed mapping (matches Android exactly)
    private let lineToFeed: [String: String] = [
        "1": "gtfs", "2": "gtfs", "3": "gtfs",
        "4": "gtfs", "5": "gtfs", "6": "gtfs",
        "7": "gtfs", "S": "gtfs",
        "A": "gtfs-ace", "C": "gtfs-ace", "E": "gtfs-ace",
        "B": "gtfs-bdfm", "D": "gtfs-bdfm", "F": "gtfs-bdfm", "M": "gtfs-bdfm",
        "G": "gtfs-g",
        "J": "gtfs-jz", "Z": "gtfs-jz",
        "L": "gtfs-l",
        "N": "gtfs-nqrw", "Q": "gtfs-nqrw", "R": "gtfs-nqrw", "W": "gtfs-nqrw"
    ]

    /// Get all arrivals for a specific station
    func getStationArrivals(stationId: String, lines: [String]) async throws -> [Arrival] {
        let feeds = Set(lines.compactMap { lineToFeed[$0] })
        guard !feeds.isEmpty else { return [] }

        let targetStopIds = Set(["\(stationId)N", "\(stationId)S"])

        var allArrivals: [Arrival] = []

        for feedName in feeds {
            if let data = try? await fetchFeed(feedName) {
                let arrivals = parseFeed(data, targetStopIds: targetStopIds)
                allArrivals.append(contentsOf: arrivals)
            }
        }

        return allArrivals.sorted { $0.arrivalTime < $1.arrivalTime }
    }

    /// Get next arrival for a station
    func getNextArrival(stationId: String, lines: [String]) async -> NextArrivalResult {
        do {
            let arrivals = try await getStationArrivals(stationId: stationId, lines: lines)

            guard let next = arrivals.first else {
                return .unavailable
            }

            let formatter = DateFormatter()
            formatter.dateFormat = "h:mm a"
            let arrivalDate = Date(timeIntervalSince1970: next.arrivalTime)

            return NextArrivalResult(
                nextTrain: "\(next.minutesAway)m",
                arrivalTime: formatter.string(from: arrivalDate),
                routeId: next.routeId,
                minutesAway: next.minutesAway
            )
        } catch {
            return .unavailable
        }
    }

    /// Get grouped arrivals for display
    func getGroupedArrivals(stationId: String, lines: [String]) async throws -> [ArrivalGroup] {
        let arrivals = try await getStationArrivals(stationId: stationId, lines: lines)

        var groups: [String: [Arrival]] = [:]

        for arrival in arrivals {
            let key = "\(arrival.routeId)-\(arrival.direction.rawValue)"
            if groups[key] == nil {
                groups[key] = []
            }
            if groups[key]!.count < 3 {
                groups[key]!.append(arrival)
            }
        }

        return groups.map { key, arrivals in
            let parts = key.split(separator: "-")
            let line = String(parts[0])
            let direction: Direction = parts.count > 1 && parts[1] == "S" ? .south : .north
            return ArrivalGroup(line: line, direction: direction, arrivals: arrivals)
        }.sorted { $0.line < $1.line }
    }

    // MARK: - Private Methods

    private func fetchFeed(_ feedName: String) async throws -> Data {
        var request = URLRequest(url: URL(string: baseURL + feedName)!)
        request.setValue("NYC-Commute-Optimizer/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await URLSession.shared.data(for: request)
        return data
    }

    private func parseFeed(_ data: Data, targetStopIds: Set<String>) -> [Arrival] {
        var arrivals: [Arrival] = []
        let now = Date().timeIntervalSince1970
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 2 && wireType == 2 {
                let length = Int(reader.readVarint())
                let entityData = reader.readBytes(length: length)
                let entityArrivals = parseEntity(entityData, targetStopIds: targetStopIds, now: now)
                arrivals.append(contentsOf: entityArrivals)
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return arrivals
    }

    private func parseEntity(_ data: Data, targetStopIds: Set<String>, now: TimeInterval) -> [Arrival] {
        var arrivals: [Arrival] = []
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 3 && wireType == 2 {
                let length = Int(reader.readVarint())
                let tripUpdateData = reader.readBytes(length: length)
                let tripArrivals = parseTripUpdate(tripUpdateData, targetStopIds: targetStopIds, now: now)
                arrivals.append(contentsOf: tripArrivals)
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return arrivals
    }

    private func parseTripUpdate(_ data: Data, targetStopIds: Set<String>, now: TimeInterval) -> [Arrival] {
        var arrivals: [Arrival] = []
        let reader = ProtobufReader(data: data)

        var routeId: String?
        var stopTimeUpdates: [Data] = []

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            switch (fieldNumber, wireType) {
            case (1, 2):  // TripDescriptor
                let length = Int(reader.readVarint())
                let tripData = reader.readBytes(length: length)
                routeId = parseTripDescriptor(tripData)
            case (2, 2):  // StopTimeUpdate
                let length = Int(reader.readVarint())
                stopTimeUpdates.append(reader.readBytes(length: length))
            default:
                reader.skip(wireType: wireType)
            }
        }

        if let routeId = routeId {
            for stuData in stopTimeUpdates {
                if let arrival = parseStopTimeUpdate(stuData, routeId: routeId, targetStopIds: targetStopIds, now: now) {
                    arrivals.append(arrival)
                }
            }
        }

        return arrivals
    }

    private func parseTripDescriptor(_ data: Data) -> String? {
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 5 && wireType == 2 {
                let length = Int(reader.readVarint())
                return reader.readString(length: length)
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return nil
    }

    private func parseStopTimeUpdate(_ data: Data, routeId: String, targetStopIds: Set<String>, now: TimeInterval) -> Arrival? {
        let reader = ProtobufReader(data: data)

        var stopId: String?
        var arrivalTime: TimeInterval?

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            switch (fieldNumber, wireType) {
            case (4, 2):  // stop_id
                let length = Int(reader.readVarint())
                stopId = reader.readString(length: length)
            case (2, 2):  // arrival
                let length = Int(reader.readVarint())
                let arrivalData = reader.readBytes(length: length)
                arrivalTime = parseStopTimeEvent(arrivalData)
            case (3, 2):  // departure
                let length = Int(reader.readVarint())
                if arrivalTime == nil {
                    let departureData = reader.readBytes(length: length)
                    arrivalTime = parseStopTimeEvent(departureData)
                }
            default:
                reader.skip(wireType: wireType)
            }
        }

        guard let stopId = stopId,
              targetStopIds.contains(stopId),
              let arrivalTime = arrivalTime,
              arrivalTime >= now - 60 else {
            return nil
        }

        let direction: Direction = stopId.hasSuffix("N") ? .north : .south
        let minutesAway = max(0, Int((arrivalTime - now) / 60))

        return Arrival(
            routeId: routeId,
            direction: direction,
            arrivalTime: arrivalTime,
            minutesAway: minutesAway
        )
    }

    private func parseStopTimeEvent(_ data: Data) -> TimeInterval? {
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 2 && wireType == 0 {
                return TimeInterval(reader.readVarint())
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return nil
    }
}
```

**Step 2: Commit**

```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Services/
git commit -m "feat(ios): add MtaApiService with GTFS-Realtime parsing"
```

---

## Task 11: GoogleRoutesService

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Services/GoogleRoutesService.swift`

**Step 1: Implement GoogleRoutesService**

```swift
// GoogleRoutesService.swift
import Foundation

actor GoogleRoutesService {
    struct TransitStep {
        let line: String
        let vehicle: String?
        let departureStop: String?
        let arrivalStop: String?
        let numStops: Int?
        let duration: Int?
    }

    struct RouteResult {
        let status: String
        let durationMinutes: Int?
        let distance: String?
        let transitSteps: [TransitStep]

        static let error = RouteResult(status: "ERROR", durationMinutes: nil, distance: nil, transitSteps: [])
    }

    func getTransitRoute(
        workerUrl: String,
        apiKey: String,
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double
    ) async throws -> RouteResult {
        guard !workerUrl.isEmpty, !apiKey.isEmpty else {
            return .error
        }

        let urlString = "\(workerUrl)/directions?origin=\(originLat),\(originLng)&destination=\(destLat),\(destLng)&mode=transit&departure_time=now&key=\(apiKey)"

        guard let url = URL(string: urlString) else {
            return .error
        }

        let (data, _) = try await URLSession.shared.data(from: url)

        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let status = json["status"] as? String,
              status == "OK" else {
            return .error
        }

        let durationMinutes = json["durationMinutes"] as? Int
        let distance = json["distance"] as? String

        var transitSteps: [TransitStep] = []
        if let stepsArray = json["transitSteps"] as? [[String: Any]] {
            transitSteps = stepsArray.map { step in
                TransitStep(
                    line: cleanLineName(step["line"] as? String ?? "?"),
                    vehicle: step["vehicle"] as? String,
                    departureStop: step["departureStop"] as? String,
                    arrivalStop: step["arrivalStop"] as? String,
                    numStops: step["numStops"] as? Int,
                    duration: step["duration"] as? Int
                )
            }
        }

        return RouteResult(
            status: status,
            durationMinutes: durationMinutes,
            distance: distance,
            transitSteps: transitSteps
        )
    }

    private func cleanLineName(_ name: String) -> String {
        return name
            .replacingOccurrences(of: " Line", with: "")
            .replacingOccurrences(of: " Train", with: "")
            .replacingOccurrences(of: "Exp", with: "")
            .trimmingCharacters(in: .whitespaces)
    }
}
```

**Step 2: Commit**

```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Services/
git commit -m "feat(ios): add GoogleRoutesService"
```

---

## Task 12: CommuteCalculator (Orchestrator)

**Files:**
- Create: `ios/CommuteOptimizer/CommuteOptimizer/Services/CommuteCalculator.swift`

**Step 1: Implement CommuteCalculator**

Reference: `android/app/src/main/java/com/commuteoptimizer/widget/service/CommuteCalculator.kt`

```swift
// CommuteCalculator.swift
import Foundation

actor CommuteCalculator {
    private let weatherService = WeatherApiService()
    private let mtaService = MtaApiService()
    private let googleRoutesService = GoogleRoutesService()
    private let stationsDataSource = StationsDataSource.shared

    func calculateCommute(settings: SettingsManager) async throws -> CommuteResponse {
        let stations = stationsDataSource.getStations()

        // 1. Fetch weather
        let weather = await fetchWeather(settings: settings)

        // 2. Find destination station (closest to work)
        guard let destStation = findClosestStation(
            toLat: settings.workLat,
            toLng: settings.workLng,
            from: stations
        ) else {
            throw CommuteError.noDestinationStation
        }

        var options: [CommuteOption] = []

        // 3. Walk-only option (if < 2 miles)
        let homeToWorkDistance = DistanceCalculator.haversineDistance(
            lat1: settings.homeLat, lon1: settings.homeLng,
            lat2: settings.workLat, lon2: settings.workLng
        )

        if homeToWorkDistance < 2 {
            let walkTime = DistanceCalculator.estimateWalkTime(
                fromLat: settings.homeLat, fromLng: settings.homeLng,
                toLat: settings.workLat, toLng: settings.workLng
            )

            let arrivalDate = Date().addingTimeInterval(TimeInterval(walkTime * 60))
            let formatter = DateFormatter()
            formatter.dateFormat = "h:mm a"

            options.append(CommuteOption(
                id: "walk-only",
                rank: 0,
                type: .walkOnly,
                durationMinutes: walkTime,
                summary: "Walk to Work",
                legs: [Leg(mode: .walk, duration: walkTime, to: "Work")],
                nextTrain: "N/A",
                arrivalTime: formatter.string(from: arrivalDate),
                station: Station(
                    id: destStation.id,
                    name: destStation.name,
                    transiterId: destStation.transiterId,
                    lines: destStation.lines,
                    lat: destStation.lat,
                    lng: destStation.lng,
                    borough: destStation.borough
                )
            ))
        }

        // 4. Bike-to-transit options
        if settings.showBikeOptions {
            for stationId in settings.bikeStations {
                if let option = await buildBikeToTransitOption(
                    stationId: stationId,
                    destStation: destStation,
                    settings: settings
                ) {
                    options.append(option)
                }
            }
        }

        // 5. Transit-only options (top 3 closest by walk)
        let closestStations = stationsDataSource.getStationsSortedByDistance(
            fromLat: settings.homeLat,
            fromLng: settings.homeLng
        ).prefix(3)

        for (station, _) in closestStations {
            if let option = await buildTransitOnlyOption(
                station: station,
                destStation: destStation,
                settings: settings
            ) {
                options.append(option)
            }
        }

        // 6. Rank options
        let rankedOptions = RankingService.rankOptions(options, weather: weather)

        // 7. Fetch alerts
        let routeIds = Set(rankedOptions.flatMap { $0.station.lines })
        let alerts = await fetchAlerts(routeIds: Array(routeIds))

        // 8. Return top 3
        let formatter = ISO8601DateFormatter()
        return CommuteResponse(
            options: Array(rankedOptions.prefix(3)),
            weather: weather,
            alerts: alerts,
            generatedAt: formatter.string(from: Date())
        )
    }

    // MARK: - Private Methods

    private func fetchWeather(settings: SettingsManager) async -> Weather {
        do {
            return try await weatherService.getWeather(
                lat: settings.homeLat,
                lng: settings.homeLng,
                apiKey: settings.openWeatherApiKey
            )
        } catch {
            return Weather(tempF: 65, conditions: "Unknown", precipitationType: .none, precipitationProbability: 0, isBad: false)
        }
    }

    private func findClosestStation(toLat: Double, toLng: Double, from stations: [LocalStation]) -> LocalStation? {
        stations.min { a, b in
            let distA = DistanceCalculator.haversineDistance(lat1: toLat, lon1: toLng, lat2: a.lat, lon2: a.lng)
            let distB = DistanceCalculator.haversineDistance(lat1: toLat, lon1: toLng, lat2: b.lat, lon2: b.lng)
            return distA < distB
        }
    }

    private func buildBikeToTransitOption(
        stationId: String,
        destStation: LocalStation,
        settings: SettingsManager
    ) async -> CommuteOption? {
        guard let station = stationsDataSource.getStation(id: stationId) else { return nil }

        let bikeTime = DistanceCalculator.estimateBikeTime(
            fromLat: settings.homeLat, fromLng: settings.homeLng,
            toLat: station.lat, toLng: station.lng
        )

        let arrival = await mtaService.getNextArrival(stationId: station.transiterId, lines: station.lines)
        let waitTime = arrival.minutesAway

        let transitTime = DistanceCalculator.estimateTransitTime(
            fromStopId: station.transiterId,
            toStopId: destStation.transiterId
        )

        let totalDuration = bikeTime + waitTime + transitTime
        let route = arrival.routeId ?? station.lines.first ?? "?"

        let arrivalDate = Date().addingTimeInterval(TimeInterval(totalDuration * 60))
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"

        return CommuteOption(
            id: "bike-\(stationId)",
            rank: 0,
            type: .bikeToTransit,
            durationMinutes: totalDuration,
            summary: "Bike → \(route) → \(destStation.name)",
            legs: [
                Leg(mode: .bike, duration: bikeTime, to: station.name),
                Leg(mode: .subway, duration: transitTime, to: destStation.name, route: route, from: station.name)
            ],
            nextTrain: arrival.nextTrain,
            arrivalTime: formatter.string(from: arrivalDate),
            station: Station(
                id: station.id,
                name: station.name,
                transiterId: station.transiterId,
                lines: station.lines,
                lat: station.lat,
                lng: station.lng,
                borough: station.borough
            )
        )
    }

    private func buildTransitOnlyOption(
        station: LocalStation,
        destStation: LocalStation,
        settings: SettingsManager
    ) async -> CommuteOption? {
        let walkTime = DistanceCalculator.estimateWalkTime(
            fromLat: settings.homeLat, fromLng: settings.homeLng,
            toLat: station.lat, toLng: station.lng
        )

        let arrival = await mtaService.getNextArrival(stationId: station.transiterId, lines: station.lines)
        let waitTime = arrival.minutesAway

        let transitTime = DistanceCalculator.estimateTransitTime(
            fromStopId: station.transiterId,
            toStopId: destStation.transiterId
        )

        let totalDuration = walkTime + waitTime + transitTime
        let route = arrival.routeId ?? station.lines.first ?? "?"

        let arrivalDate = Date().addingTimeInterval(TimeInterval(totalDuration * 60))
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"

        return CommuteOption(
            id: "transit-\(station.id)",
            rank: 0,
            type: .transitOnly,
            durationMinutes: totalDuration,
            summary: "Walk → \(route) → \(destStation.name)",
            legs: [
                Leg(mode: .walk, duration: walkTime, to: station.name),
                Leg(mode: .subway, duration: transitTime, to: destStation.name, route: route, from: station.name)
            ],
            nextTrain: arrival.nextTrain,
            arrivalTime: formatter.string(from: arrivalDate),
            station: Station(
                id: station.id,
                name: station.name,
                transiterId: station.transiterId,
                lines: station.lines,
                lat: station.lat,
                lng: station.lng,
                borough: station.borough
            )
        )
    }

    private func fetchAlerts(routeIds: [String]) async -> [ServiceAlert] {
        // TODO: Implement MtaAlertsService
        return []
    }
}

enum CommuteError: Error {
    case noDestinationStation
    case notConfigured
}
```

**Step 2: Commit**

```bash
git add ios/CommuteOptimizer/CommuteOptimizer/Services/
git commit -m "feat(ios): add CommuteCalculator orchestrator"
```

---

## Tasks 13-18: UI Views (Summarized)

The remaining tasks follow the same TDD pattern for:

**Task 13: ContentView (Tab Navigation)**
- File: `ios/CommuteOptimizer/CommuteOptimizer/App/ContentView.swift`
- 3 tabs: Commute, Live Trains, Settings

**Task 14: CommuteView**
- Files: `Views/CommuteTab/CommuteView.swift`, `CommuteOptionCard.swift`, `WeatherHeaderView.swift`
- Weather header, option list, pull-to-refresh

**Task 15: LiveTrainsView**
- Files: `Views/LiveTrainsTab/LiveTrainsView.swift`, `StationCard.swift`
- Station cards with grouped arrivals

**Task 16: SettingsView**
- Files: `Views/SettingsTab/SettingsView.swift`, `StationPickerView.swift`
- API keys, addresses, station selection

**Task 17: Shared Components**
- Files: `Views/Shared/LineBadge.swift`, `ModeIcon.swift`
- Reusable UI components

**Task 18: Widget Implementation**
- Files: `CommuteWidget/CommuteWidget.swift`, `CommuteWidgetProvider.swift`, `CommuteWidgetView.swift`
- WidgetKit timeline provider, small/medium widget views

---

---

## CRITICAL UPDATES (Post-Android Review)

These features were added to Android after the initial iOS plan was written. **iOS must implement these for full parity:**

### 1. Route Deduplication (CommuteCalculator)

Before ranking, deduplicate options by route signature to prevent showing the same transit route from multiple bike stations:

```swift
// Add after building all options, before ranking
private func deduplicateOptions(_ options: [CommuteOption]) -> [CommuteOption] {
    var seen: [String: CommuteOption] = [:]

    for option in options {
        // Build route signature: "bike_to_transit_G" or "transit_only_A→6"
        let routeSequence = option.legs
            .filter { $0.mode == .subway }
            .compactMap { $0.route }
            .joined(separator: "→")
        let signature = "\(option.type.rawValue)_\(routeSequence)"

        // Keep fastest option for each signature
        if let existing = seen[signature] {
            if option.durationMinutes < existing.durationMinutes {
                seen[signature] = option
            }
        } else {
            seen[signature] = option
        }
    }

    return Array(seen.values)
}
```

### 2. Express Train Normalization (MtaApiService)

Normalize express train variants to base line:

```swift
private func normalizeRouteId(_ routeId: String) -> String {
    // Express variants: 6X→6, 7X→7, FX→F, etc.
    if routeId.hasSuffix("X") {
        return String(routeId.dropLast())
    }
    return routeId
}
```

### 3. Weather API - Use Free Tier (WeatherApiService)

**CHANGE** the endpoint from 3.0 to 2.5 (free tier):

```swift
// OLD (requires subscription):
// private let baseURL = "https://api.openweathermap.org/data/3.0/onecall"

// NEW (free tier):
private let baseURL = "https://api.openweathermap.org/data/2.5/weather"

func getWeather(lat: Double, lng: Double, apiKey: String) async throws -> Weather {
    let urlString = "\(baseURL)?lat=\(lat)&lon=\(lng)&units=imperial&appid=\(apiKey)"
    // ... rest of implementation
}

// Updated response model for 2.5 API:
struct OpenWeatherResponse: Codable {
    let main: MainWeather
    let weather: [WeatherCondition]
    let rain: RainInfo?
    let snow: SnowInfo?
}

struct MainWeather: Codable {
    let temp: Double
}

struct RainInfo: Codable {
    let oneHour: Double?
    enum CodingKeys: String, CodingKey {
        case oneHour = "1h"
    }
}

struct SnowInfo: Codable {
    let oneHour: Double?
    enum CodingKeys: String, CodingKey {
        case oneHour = "1h"
    }
}

// Also check for active precipitation:
private func parseResponse(_ response: OpenWeatherResponse) -> Weather {
    let weatherId = response.weather.first?.id ?? 800
    let hasActiveRain = (response.rain?.oneHour ?? 0) > 0
    let hasActiveSnow = (response.snow?.oneHour ?? 0) > 0

    let isBad = (weatherId >= 200 && weatherId < 700) || hasActiveRain || hasActiveSnow
    // ...
}
```

### 4. "Now" Display for Arriving Trains

```swift
// In NextArrivalResult or display logic:
var displayText: String {
    if minutesAway <= 0 {
        return "Now"
    }
    return "\(minutesAway)m"
}
```

### 5. Per-Widget Settings (SettingsManager)

Add widget-specific settings with global fallback:

```swift
// Per-widget keys
func getWidgetOriginLat(_ widgetId: String) -> Double {
    defaults.object(forKey: "widget_origin_lat_\(widgetId)") as? Double ?? homeLat
}

func getWidgetOriginLng(_ widgetId: String) -> Double {
    defaults.object(forKey: "widget_origin_lng_\(widgetId)") as? Double ?? homeLng
}

func getWidgetOriginName(_ widgetId: String) -> String {
    defaults.string(forKey: "widget_origin_name_\(widgetId)") ?? (homeAddress.isEmpty ? "Home" : homeAddress)
}

// Similar for destination...
```

### 6. UI Enhancements

**Live Trains - Arrival Badge Colors:**
```swift
// Green for ≤2 min, default otherwise
func arrivalBadgeColor(minutesAway: Int, line: String) -> Color {
    minutesAway <= 2 ? .green : MtaColors.color(for: line)
}
```

**Settings - Station Count Display:**
```swift
// Show "X selected" for bike, "X/3 selected" for live
Text("\(selectedBikeStations.count) selected")
Text("\(selectedLiveStations.count)/3 selected")
```

**Settings - Collapsible Sections:**
```swift
@State private var bikeStationsExpanded = true
@State private var liveStationsExpanded = true

DisclosureGroup("Bike-to Stations", isExpanded: $bikeStationsExpanded) {
    // Station chips
}
```

**Live Trains - Line Badge in Direction Rows:**
```swift
// Show line badge repeated in each direction group header
HStack {
    LineBadge(line: group.line)
    Text(group.direction.headsign)
    Image(systemName: group.direction == .north ? "arrow.up" : "arrow.down")
}
```

---

## Verification Checklist

- [ ] Bike time formula: `ceil((d/10)*60*1.3)` matches Android
- [ ] Walk time formula: `ceil((d/3)*60*1.2)` matches Android
- [ ] Ranking demotes bike when weather.isBad AND bike is #1
- [ ] **Route deduplication before ranking**
- [ ] **Express train normalization (6X→6, etc.)**
- [ ] **Weather uses free API (/data/2.5/weather)**
- [ ] **Active precipitation check (rain.1h/snow.1h > 0)**
- [ ] **"Now" displayed for ≤0 minute arrivals**
- [ ] **Green badge for ≤2 min arrivals**
- [ ] MTA arrivals parse from live GTFS-Realtime feeds
- [ ] Widget refreshes every 15 minutes
- [ ] **Per-widget settings with global fallback**
- [ ] Settings persist via App Groups
- [ ] CLGeocoder resolves addresses
- [ ] All 23 MTA line colors match official values
- [ ] **Collapsible station selectors in settings**
- [ ] **Station count display (X selected, X/3 selected)**
- [ ] **Line badge in Live Trains direction rows**
