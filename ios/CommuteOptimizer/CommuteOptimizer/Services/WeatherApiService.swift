import Foundation

actor WeatherApiService {
    // Use free tier API (2.5), NOT the 3.0 subscription API
    private let baseURL = "https://api.openweathermap.org/data/2.5/weather"

    func getWeather(lat: Double, lng: Double, apiKey: String) async throws -> Weather {
        guard !apiKey.isEmpty else {
            return defaultWeather
        }

        let urlString = "\(baseURL)?lat=\(lat)&lon=\(lng)&units=imperial&appid=\(apiKey)"

        guard let url = URL(string: urlString) else {
            return defaultWeather
        }

        let (data, _) = try await URLSession.shared.data(from: url)
        let response = try JSONDecoder().decode(OpenWeatherResponse.self, from: data)
        return parseResponse(response)
    }

    private func parseResponse(_ response: OpenWeatherResponse) -> Weather {
        let weatherId = response.weather.first?.id ?? 800
        let weatherMain = response.weather.first?.main ?? "Clear"

        // Determine precipitation type from weather ID
        let precipitationType: PrecipitationType = {
            switch weatherId {
            case 200..<600: return .rain
            case 600..<611: return .snow
            case 611..<700: return .mix
            default: return .none
            }
        }()

        // Check for active precipitation (1h or 3h fallback like Android)
        let hasActiveRain = (response.rain?.oneHour ?? response.rain?.threeHour ?? 0) > 0
        let hasActiveSnow = (response.snow?.oneHour ?? response.snow?.threeHour ?? 0) > 0

        let isBad = precipitationType != .none || hasActiveRain || hasActiveSnow

        return Weather(
            tempF: Int(response.main.temp),
            conditions: weatherMain,
            precipitationType: precipitationType,
            precipitationProbability: isBad ? 100 : 0,
            isBad: isBad
        )
    }

    private var defaultWeather: Weather {
        Weather(tempF: 65, conditions: "Unknown", precipitationType: .none, precipitationProbability: 0, isBad: false)
    }
}
