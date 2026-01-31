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

        // 6. Deduplicate options by route signature
        let deduplicated = deduplicateOptions(options)

        // 7. Rank options
        let rankedOptions = RankingService.rankOptions(deduplicated, weather: weather)

        // 8. Fetch alerts
        let routeIds = Set(rankedOptions.flatMap { $0.station.lines })
        let alerts = await fetchAlerts(routeIds: Array(routeIds))

        // 9. Return top 3
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

    /// Deduplicate options by route signature to prevent showing same transit route from multiple bike stations
    private func deduplicateOptions(_ options: [CommuteOption]) -> [CommuteOption] {
        var seen: [String: CommuteOption] = [:]

        for option in options {
            // Build route signature: "bike_to_transit_G" or "transit_only_A->6"
            let routeSequence = option.legs
                .filter { $0.mode == .subway }
                .compactMap { $0.route }
                .joined(separator: "->")
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
            summary: "Bike -> \(route) -> \(destStation.name)",
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
            summary: "Walk -> \(route) -> \(destStation.name)",
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
