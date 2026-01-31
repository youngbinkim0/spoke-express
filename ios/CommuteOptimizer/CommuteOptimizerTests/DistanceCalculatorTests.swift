import XCTest
@testable import CommuteOptimizer

final class DistanceCalculatorTests: XCTestCase {

    // MARK: - Haversine Distance Tests

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

    func testHaversineDistance_SamePoint() {
        let distance = DistanceCalculator.haversineDistance(
            lat1: 40.6896, lon1: -73.9535,
            lat2: 40.6896, lon2: -73.9535
        )
        XCTAssertEqual(distance, 0.0, accuracy: 0.001)
    }

    // MARK: - Bike Time Tests

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

    // MARK: - Walk Time Tests

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

    // MARK: - Transit Time Tests

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
}
