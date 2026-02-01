import Foundation

/// Service to call Google Directions API directly for transit routes.
/// Returns full transit routes with all transfer details.
/// Matches Android implementation for feature parity.
actor GoogleRoutesService {
    struct TransitStep {
        let line: String
        let vehicle: String?
        let departureStop: String?
        let arrivalStop: String?
        let numStops: Int?
        let duration: Int?
    }

    struct RouteResult {
        let status: String
        let durationMinutes: Int?
        let distance: String?
        let transitSteps: [TransitStep]

        static let error = RouteResult(status: "ERROR", durationMinutes: nil, distance: nil, transitSteps: [])
    }

    func getTransitRoute(
        apiKey: String,
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double
    ) async -> RouteResult {
        guard !apiKey.isEmpty else {
            return .error
        }

        let urlString = "https://maps.googleapis.com/maps/api/directions/json" +
            "?origin=\(originLat),\(originLng)" +
            "&destination=\(destLat),\(destLng)" +
            "&mode=transit" +
            "&departure_time=now" +
            "&key=\(apiKey)"

        guard let url = URL(string: urlString) else {
            return .error
        }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)

            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let status = json["status"] as? String else {
                return .error
            }

            if status != "OK" {
                return RouteResult(status: status, durationMinutes: nil, distance: nil, transitSteps: [])
            }

            guard let routes = json["routes"] as? [[String: Any]],
                  let route = routes.first,
                  let legs = route["legs"] as? [[String: Any]],
                  let leg = legs.first else {
                return RouteResult(status: "NO_ROUTES", durationMinutes: nil, distance: nil, transitSteps: [])
            }

            let durationSeconds = (leg["duration"] as? [String: Any])?["value"] as? Int ?? 0
            let durationMinutes = durationSeconds / 60
            let distance = (leg["distance"] as? [String: Any])?["text"] as? String

            guard let steps = leg["steps"] as? [[String: Any]] else {
                return RouteResult(status: status, durationMinutes: durationMinutes, distance: distance, transitSteps: [])
            }

            var transitSteps: [TransitStep] = []
            for step in steps {
                let travelMode = step["travel_mode"] as? String ?? ""

                if travelMode == "TRANSIT" {
                    guard let transitDetails = step["transit_details"] as? [String: Any] else { continue }

                    let line = transitDetails["line"] as? [String: Any]
                    let shortName = (line?["short_name"] as? String) ?? (line?["name"] as? String) ?? "?"

                    let vehicle = (line?["vehicle"] as? [String: Any])?["type"] as? String
                    let departureStop = (transitDetails["departure_stop"] as? [String: Any])?["name"] as? String
                    let arrivalStop = (transitDetails["arrival_stop"] as? [String: Any])?["name"] as? String
                    let numStops = transitDetails["num_stops"] as? Int
                    let stepDuration = ((step["duration"] as? [String: Any])?["value"] as? Int).map { $0 / 60 }

                    transitSteps.append(TransitStep(
                        line: cleanLineName(shortName),
                        vehicle: vehicle,
                        departureStop: departureStop,
                        arrivalStop: arrivalStop,
                        numStops: numStops,
                        duration: stepDuration
                    ))
                }
            }

            return RouteResult(
                status: status,
                durationMinutes: durationMinutes,
                distance: distance,
                transitSteps: transitSteps
            )
        } catch {
            return .error
        }
    }

    private func cleanLineName(_ name: String) -> String {
        var cleaned = name
            .replacingOccurrences(of: " Line", with: "")
            .replacingOccurrences(of: " Train", with: "")
            .replacingOccurrences(of: "Exp", with: "")
            .trimmingCharacters(in: .whitespaces)
            .uppercased()

        // Express variants: 6X -> 6, 7X -> 7, FX -> F
        if cleaned.count == 2 && cleaned.hasSuffix("X") {
            cleaned = String(cleaned.dropLast())
        }

        return cleaned
    }
}
