import Foundation

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
        workerUrl: String,
        apiKey: String,
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double
    ) async throws -> RouteResult {
        guard !workerUrl.isEmpty, !apiKey.isEmpty else {
            return .error
        }

        let urlString = "\(workerUrl)/directions?origin=\(originLat),\(originLng)&destination=\(destLat),\(destLng)&mode=transit&departure_time=now&key=\(apiKey)"

        guard let url = URL(string: urlString) else {
            return .error
        }

        let (data, _) = try await URLSession.shared.data(from: url)

        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let status = json["status"] as? String,
              status == "OK" else {
            return .error
        }

        let durationMinutes = json["durationMinutes"] as? Int
        let distance = json["distance"] as? String

        var transitSteps: [TransitStep] = []
        if let stepsArray = json["transitSteps"] as? [[String: Any]] {
            transitSteps = stepsArray.map { step in
                TransitStep(
                    line: cleanLineName(step["line"] as? String ?? "?"),
                    vehicle: step["vehicle"] as? String,
                    departureStop: step["departureStop"] as? String,
                    arrivalStop: step["arrivalStop"] as? String,
                    numStops: step["numStops"] as? Int,
                    duration: step["duration"] as? Int
                )
            }
        }

        return RouteResult(
            status: status,
            durationMinutes: durationMinutes,
            distance: distance,
            transitSteps: transitSteps
        )
    }

    private func cleanLineName(_ name: String) -> String {
        return name
            .replacingOccurrences(of: " Line", with: "")
            .replacingOccurrences(of: " Train", with: "")
            .replacingOccurrences(of: "Exp", with: "")
            .trimmingCharacters(in: .whitespaces)
    }
}
