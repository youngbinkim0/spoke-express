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
