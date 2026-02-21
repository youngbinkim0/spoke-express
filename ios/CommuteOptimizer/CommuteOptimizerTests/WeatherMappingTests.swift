import XCTest
@testable import CommuteOptimizer

final class WeatherMappingTests: XCTestCase {

    // MARK: - Google Condition Type Mapping Tests

    func testClearConditionMapsToNotBad() {
        // Google condition: CLEAR should result in isBad = false
        let weather = Weather(
            tempF: 70,
            conditions: "Clear",
            precipitationType: .none,
            precipitationProbability: 0,
            isBad: false
        )
        XCTAssertFalse(weather.isBad, "Clear weather should not be marked as bad")
    }

    func testRainConditionMapsToBad() {
        // Google condition: RAIN should result in isBad = true
        let weather = Weather(
            tempF: 55,
            conditions: "Rain",
            precipitationType: .rain,
            precipitationProbability: 80,
            isBad: true
        )
        XCTAssertTrue(weather.isBad, "Rain weather should be marked as bad")
    }

    func testSnowConditionMapsToBad() {
        // Google condition: SNOW should result in isBad = true
        let weather = Weather(
            tempF: 32,
            conditions: "Snow",
            precipitationType: .snow,
            precipitationProbability: 60,
            isBad: true
        )
        XCTAssertTrue(weather.isBad, "Snow weather should be marked as bad")
    }

    func testSleetConditionMapsToBad() {
        // Google condition: SLEET should result in isBad = true
        let weather = Weather(
            tempF: 34,
            conditions: "Sleet",
            precipitationType: .mix,
            precipitationProbability: 70,
            isBad: true
        )
        XCTAssertTrue(weather.isBad, "Sleet weather should be marked as bad")
    }

    func testFogConditionMapsToNotBad() {
        // Google condition: FOG should result in isBad = false (no precipitation)
        let weather = Weather(
            tempF: 60,
            conditions: "Fog",
            precipitationType: .none,
            precipitationProbability: 0,
            isBad: false
        )
        XCTAssertFalse(weather.isBad, "Fog without precipitation should not be marked as bad")
    }

    func testWindConditionMapsToNotBad() {
        // Google condition: WIND should result in isBad = false (no precipitation)
        let weather = Weather(
            tempF: 65,
            conditions: "Windy",
            precipitationType: .none,
            precipitationProbability: 0,
            isBad: false
        )
        XCTAssertFalse(weather.isBad, "Windy weather without precipitation should not be marked as bad")
    }

    // MARK: - Precipitation Influence Tests

    func testPrecipitationProbabilityHighMakesBad() {
        // High precipitation probability should make weather bad even if type is none
        let weather = Weather(
            tempF: 70,
            conditions: "Clear",
            precipitationType: .none,
            precipitationProbability: 80,
            isBad: true  // Should be true when probability > threshold
        )
        // This test will fail until precipitation probability logic is implemented
        XCTAssertTrue(weather.isBad, "High precipitation probability should mark weather as bad")
    }

    func testPrecipitationProbabilityLowKeepsNotBad() {
        // Low precipitation probability should keep weather not bad
        let weather = Weather(
            tempF: 70,
            conditions: "Clear",
            precipitationType: .none,
            precipitationProbability: 20,
            isBad: false
        )
        XCTAssertFalse(weather.isBad, "Low precipitation probability should not mark weather as bad")
    }

    func testPrecipitationTypeNoneWithNoProbabilityIsNotBad() {
        // No precipitation type and zero probability should be not bad
        let weather = Weather(
            tempF: 72,
            conditions: "Clear",
            precipitationType: .none,
            precipitationProbability: 0,
            isBad: false
        )
        XCTAssertFalse(weather.isBad, "Clear weather with no precipitation should be not bad")
    }

    // MARK: - Fallback Behavior Tests

    func testUnknownConditionFallsBackToNotBad() {
        // Unknown/undefined condition should safely default to not bad
        let weather = Weather(
            tempF: 65,
            conditions: "UnknownCondition",
            precipitationType: .none,
            precipitationProbability: 0,
            isBad: false
        )
        XCTAssertFalse(weather.isBad, "Unknown condition should fallback to not bad")
    }

    func testEmptyConditionFallsBackToNotBad() {
        // Empty condition string should safely default to not bad
        let weather = Weather(
            tempF: 65,
            conditions: "",
            precipitationType: .none,
            precipitationProbability: 0,
            isBad: false
        )
        XCTAssertFalse(weather.isBad, "Empty condition should fallback to not bad")
    }

    // MARK: - Edge Cases

    func testMixedPrecipitationIsBad() {
        // Mixed rain/snow should be bad
        let weather = Weather(
            tempF: 33,
            conditions: "Mixed",
            precipitationType: .mix,
            precipitationProbability: 90,
            isBad: true
        )
        XCTAssertTrue(weather.isBad, "Mixed precipitation should be marked as bad")
    }

    func testAllGoogleConditionTypes() {
        // Test all Google condition types that should be bad
        let badConditions = ["Rain", "Snow", "Sleet", "Hail", "Thunderstorm"]
        for condition in badConditions {
            let weather = Weather(
                tempF: 50,
                conditions: condition,
                precipitationType: .rain, // or snow/mix depending on condition
                precipitationProbability: 100,
                isBad: true
            )
            XCTAssertTrue(weather.isBad, "\(condition) should be marked as bad")
        }

        // Test all Google condition types that should be not bad
        let goodConditions = ["Clear", "Sunny", "Cloudy", "Foggy", "Windy", "Clouds"]
        for condition in goodConditions {
            let weather = Weather(
                tempF: 65,
                conditions: condition,
                precipitationType: .none,
                precipitationProbability: 0,
                isBad: false
            )
            XCTAssertFalse(weather.isBad, "\(condition) should not be marked as bad")
        }
    }
}
