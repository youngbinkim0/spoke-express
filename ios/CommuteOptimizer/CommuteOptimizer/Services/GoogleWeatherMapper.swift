import Foundation

enum GoogleWeatherMapper {
    private static let badConditionKeywords = [
        "RAIN",
        "SNOW",
        "STORM",
        "SLEET",
        "HAIL",
        "DRIZZLE",
        "THUNDERSTORM",
    ]

    static func mapGoogleWeatherToIsBad(condition: String?, precipType: String?, precipProbability: Int?) -> Bool {
        let normalizedCondition = normalize(condition)
        let normalizedPrecipType = normalize(precipType)

        if normalizedPrecipType == "RAIN" || normalizedPrecipType == "SNOW" || normalizedPrecipType == "MIX" || normalizedPrecipType == "SLEET" {
            return true
        }

        if normalizedPrecipType == "NONE" {
            return normalizedCondition.contains("RAIN") || normalizedCondition.contains("SNOW")
        }

        if badConditionKeywords.contains(where: { normalizedCondition.contains($0) }) {
            return true
        }

        if let precipProbability, precipProbability >= 50 {
            return true
        }

        return false
    }

    private static func normalize(_ value: String?) -> String {
        return value?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? ""
    }
}
