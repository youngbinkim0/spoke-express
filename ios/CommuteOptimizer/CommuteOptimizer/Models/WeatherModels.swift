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

// Google Weather API response (currentConditions:lookup)
struct GoogleWeatherResponse: Codable {
    let temperature: GoogleTemperature?
    let weatherCondition: GoogleWeatherCondition?
    let precipitation: GooglePrecipitation?
}

struct GoogleTemperature: Codable {
    let degrees: Double?
}

struct GoogleWeatherCondition: Codable {
    let type: String?
    let description: GoogleConditionDescription?
}

struct GoogleConditionDescription: Codable {
    let text: String?
}

struct GooglePrecipitation: Codable {
    let type: String?
    let probability: GooglePrecipitationProbability?
}

struct GooglePrecipitationProbability: Codable {
    let percent: Int?
}
