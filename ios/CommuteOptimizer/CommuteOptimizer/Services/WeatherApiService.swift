import Foundation

actor WeatherApiService {
    private let baseURL = "https://weather.googleapis.com/v1/currentConditions:lookup"

    func getWeather(lat: Double, lng: Double, apiKey: String) async throws -> Weather {
        guard !apiKey.isEmpty else {
            return defaultWeather
        }

        let urlString = "\(baseURL)?key=\(apiKey)&location.latitude=\(lat)&location.longitude=\(lng)&unitsSystem=IMPERIAL"

        guard let url = URL(string: urlString) else {
            return defaultWeather
        }

        let (data, _) = try await URLSession.shared.data(from: url)
        let response = try JSONDecoder().decode(GoogleWeatherResponse.self, from: data)
        return parseResponse(response)
    }

    private func parseResponse(_ response: GoogleWeatherResponse) -> Weather {
        let tempF = response.temperature?.degrees ?? 65
        let conditions = response.weatherCondition?.description?.text ?? "Unknown"
        let precipTypeString = response.precipitation?.type
        let precipProbability = response.precipitation?.probability?.percent ?? 0

        // Map precipitation type string to PrecipitationType enum
        let precipitationType: PrecipitationType = {
            guard let typeStr = precipTypeString?.uppercased() else { return .none }
            switch typeStr {
            case "RAIN": return .rain
            case "SNOW": return .snow
            case "MIX", "SLEET": return .mix
            default: return .none
            }
        }()

        let isBad = GoogleWeatherMapper.mapGoogleWeatherToIsBad(
            condition: conditions,
            precipType: precipTypeString,
            precipProbability: precipProbability
        )

        return Weather(
            tempF: Int(tempF),
            conditions: conditions,
            precipitationType: precipitationType,
            precipitationProbability: precipProbability,
            isBad: isBad
        )
    }

    private var defaultWeather: Weather {
        Weather(tempF: 65, conditions: "Unknown", precipitationType: .none, precipitationProbability: 0, isBad: false)
    }
}
