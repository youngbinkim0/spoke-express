import Foundation

actor CommuteCalculator {
    private let weatherService = WeatherApiService()
    private let mtaService = MtaApiService()
    private let mtaAlertsService = MtaAlertsService()
    private let googleRoutesService = GoogleRoutesService()
    private let stationsDataSource = StationsDataSource.shared

    func calculateCommute(settings: SettingsManager) async throws -> CommuteResponse {
        try await calculateCommute(
            settings: settings,
            originLat: settings.homeLat,
            originLng: settings.homeLng,
            destLat: settings.workLat,
            destLng: settings.workLng
        )
    }

    /// Calculate commute with custom origin/destination (for per-widget configuration)
    func calculateCommute(
        settings: SettingsManager,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ) async throws -> CommuteResponse {
        let stations = stationsDataSource.getStations()

        // 1. Fetch weather at origin
        let weather = await fetchWeather(lat: originLat, lng: originLng, apiKey: settings.openWeatherApiKey)

        // 2. Find destination station (closest to destination)
        guard let destStation = findClosestStation(
            toLat: destLat,
            toLng: destLng,
            from: stations
        ) else {
            throw CommuteError.noDestinationStation
        }

        var options: [CommuteOption] = []

        // 3. Walk-only option (if < 2 miles)
        let homeToWorkDistance = DistanceCalculator.haversineDistance(
            lat1: originLat, lon1: originLng,
            lat2: destLat, lon2: destLng
        )

        if homeToWorkDistance < 2 {
            let walkTime = DistanceCalculator.estimateWalkTime(
                fromLat: originLat, fromLng: originLng,
                toLat: destLat, toLng: destLng
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
                    mtaId: destStation.mtaId,
                    lines: destStation.lines,
                    lat: destStation.lat,
                    lng: destStation.lng,
                    borough: destStation.borough
                )
            ))
        }

        // 4. Bike-to-transit options (auto-selected stations)
        let autoSelected = stationsDataSource.autoSelectStations(
            homeLat: originLat, homeLng: originLng,
            workLat: destLat, workLng: destLng
        )

        if settings.showBikeOptions {
            for stationId in autoSelected {
                if let option = await buildBikeToTransitOption(
                    stationId: stationId,
                    destStation: destStation,
                    originLat: originLat,
                    originLng: originLng,
                    destLat: destLat,
                    destLng: destLng,
                    settings: settings
                ) {
                    options.append(option)
                }
            }
        }

        // 5. Transit-only options (top 3 closest from auto-selected)
        let autoStations = autoSelected.compactMap { stationsDataSource.getStation(id: $0) }
        let walkSorted = autoStations.sorted {
            DistanceCalculator.estimateWalkTime(fromLat: originLat, fromLng: originLng, toLat: $0.lat, toLng: $0.lng) <
            DistanceCalculator.estimateWalkTime(fromLat: originLat, fromLng: originLng, toLat: $1.lat, toLng: $1.lng)
        }

        for station in walkSorted.prefix(3) {
            if let option = await buildTransitOnlyOption(
                station: station,
                destStation: destStation,
                originLat: originLat,
                originLng: originLng,
                destLat: destLat,
                destLng: destLng,
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
        await fetchWeather(lat: settings.homeLat, lng: settings.homeLng, apiKey: settings.openWeatherApiKey)
    }

    private func fetchWeather(lat: Double, lng: Double, apiKey: String) async -> Weather {
        do {
            return try await weatherService.getWeather(
                lat: lat,
                lng: lng,
                apiKey: apiKey
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

    /// Get transit route using Google API if available, fallback to local estimate
    private func getTransitRoute(
        fromStation: LocalStation,
        toStation: LocalStation,
        workLat: Double,
        workLng: Double,
        googleApiKey: String?
    ) async -> (duration: Int, legs: [Leg]) {
        // Try Google Routes API if key is provided
        if let apiKey = googleApiKey, !apiKey.isEmpty {
            let result = await googleRoutesService.getTransitRoute(
                apiKey: apiKey,
                originLat: fromStation.lat, originLng: fromStation.lng,
                destLat: workLat, destLng: workLng
            )

            if result.status == "OK", let duration = result.durationMinutes {
                let legs = result.transitSteps.map { step in
                    Leg(
                        mode: .subway,
                        duration: step.duration ?? 0,
                        to: step.arrivalStop ?? "?",
                        route: step.line,
                        from: step.departureStop,
                        numStops: step.numStops
                    )
                }

                if !legs.isEmpty {
                    return (duration, legs)
                }

                // Google returned duration but no detailed steps
                return (duration, [Leg(mode: .subway, duration: duration, to: toStation.name, route: fromStation.lines.first, from: fromStation.name)])
            }
        }

        // Fallback to local estimate
        let transitTime = DistanceCalculator.estimateTransitTime(
            fromStopId: fromStation.mtaId,
            toStopId: toStation.mtaId
        )
        return (transitTime, [Leg(mode: .subway, duration: transitTime, to: toStation.name, route: fromStation.lines.first, from: fromStation.name)])
    }

    private func buildBikeToTransitOption(
        stationId: String,
        destStation: LocalStation,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        settings: SettingsManager
    ) async -> CommuteOption? {
        guard let station = stationsDataSource.getStation(id: stationId) else { return nil }

        let bikeTime = DistanceCalculator.estimateBikeTime(
            fromLat: originLat, fromLng: originLng,
            toLat: station.lat, toLng: station.lng
        )

        let arrival = await mtaService.getNextArrival(stationId: station.mtaId, lines: station.lines)
        let waitTime = arrival.minutesAway

        // Get transit route (uses Google API if available)
        let (transitTime, transitLegs) = await getTransitRoute(
            fromStation: station,
            toStation: destStation,
            workLat: destLat,
            workLng: destLng,
            googleApiKey: settings.googleApiKey
        )

        let totalDuration = bikeTime + waitTime + transitTime
        let route = arrival.routeId ?? transitLegs.first?.route ?? station.lines.first ?? "?"
        let finalStop = transitLegs.last?.to ?? destStation.name

        let arrivalDate = Date().addingTimeInterval(TimeInterval(totalDuration * 60))
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"

        // Build summary like Android: "Bike -> G -> Court Sq" or "Bike -> A -> G -> Court Sq"
        let summary = buildSummary(firstMode: "Bike", transitLegs: transitLegs, firstLine: route, finalStop: finalStop)

        return CommuteOption(
            id: "bike-\(stationId)",
            rank: 0,
            type: .bikeToTransit,
            durationMinutes: totalDuration,
            summary: summary,
            legs: [Leg(mode: .bike, duration: bikeTime, to: station.name)] + transitLegs,
            nextTrain: arrival.nextTrain,
            arrivalTime: formatter.string(from: arrivalDate),
            station: Station(
                id: station.id,
                name: station.name,
                mtaId: station.mtaId,
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
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        settings: SettingsManager
    ) async -> CommuteOption? {
        let walkTime = DistanceCalculator.estimateWalkTime(
            fromLat: originLat, fromLng: originLng,
            toLat: station.lat, toLng: station.lng
        )

        let arrival = await mtaService.getNextArrival(stationId: station.mtaId, lines: station.lines)
        let waitTime = arrival.minutesAway

        // Get transit route (uses Google API if available)
        let (transitTime, transitLegs) = await getTransitRoute(
            fromStation: station,
            toStation: destStation,
            workLat: destLat,
            workLng: destLng,
            googleApiKey: settings.googleApiKey
        )

        let totalDuration = walkTime + waitTime + transitTime
        let route = arrival.routeId ?? transitLegs.first?.route ?? station.lines.first ?? "?"
        let finalStop = transitLegs.last?.to ?? destStation.name

        let arrivalDate = Date().addingTimeInterval(TimeInterval(totalDuration * 60))
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"

        // Build summary like Android: "Walk -> G -> Court Sq" or "Walk -> A -> G -> Court Sq"
        let summary = buildSummary(firstMode: "Walk", transitLegs: transitLegs, firstLine: route, finalStop: finalStop)

        return CommuteOption(
            id: "transit-\(station.id)",
            rank: 0,
            type: .transitOnly,
            durationMinutes: totalDuration,
            summary: summary,
            legs: [Leg(mode: .walk, duration: walkTime, to: station.name)] + transitLegs,
            nextTrain: arrival.nextTrain,
            arrivalTime: formatter.string(from: arrivalDate),
            station: Station(
                id: station.id,
                name: station.name,
                mtaId: station.mtaId,
                lines: station.lines,
                lat: station.lat,
                lng: station.lng,
                borough: station.borough
            )
        )
    }

    /// Build summary string like Android: "Mode -> Lines -> FinalStop"
    private func buildSummary(firstMode: String, transitLegs: [Leg], firstLine: String?, finalStop: String) -> String {
        let lines = transitLegs.compactMap { $0.route }.filter { !$0.isEmpty }
        let uniqueLines = lines.isEmpty ? [firstLine ?? "?"] : Array(NSOrderedSet(array: lines)) as! [String]
        let linesSummary = uniqueLines.joined(separator: " -> ")
        let destination = transitLegs.last?.to ?? finalStop
        return "\(firstMode) -> \(linesSummary) -> \(destination)"
    }

    private func fetchAlerts(routeIds: [String]) async -> [ServiceAlert] {
        let alerts = await mtaAlertsService.fetchAlerts(routeIds: routeIds)
        // Filter to only show significant alerts and limit to 3
        return Array(alerts.filter {
            ["NO_SERVICE", "REDUCED_SERVICE", "SIGNIFICANT_DELAYS"].contains($0.effect)
        }.prefix(3))
    }
}

enum CommuteError: Error {
    case noDestinationStation
    case notConfigured
}
