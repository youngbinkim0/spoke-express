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

// OpenWeatherMap API response (2.5 free tier)
struct OpenWeatherResponse: Codable {
    let main: MainWeather
    let weather: [WeatherCondition]
    let rain: RainInfo?
    let snow: SnowInfo?
}

struct MainWeather: Codable {
    let temp: Double
}

struct WeatherCondition: Codable {
    let id: Int
    let main: String
    let description: String
    let icon: String
}

struct RainInfo: Codable {
    let oneHour: Double?
    let threeHour: Double?

    enum CodingKeys: String, CodingKey {
        case oneHour = "1h"
        case threeHour = "3h"
    }
}

struct SnowInfo: Codable {
    let oneHour: Double?
    let threeHour: Double?

    enum CodingKeys: String, CodingKey {
        case oneHour = "1h"
        case threeHour = "3h"
    }
}
