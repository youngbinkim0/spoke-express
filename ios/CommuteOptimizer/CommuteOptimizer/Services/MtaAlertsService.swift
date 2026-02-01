import Foundation

/// Service to fetch MTA service alerts from GTFS-realtime alerts feed.
/// Parses protobuf manually to match webapp/Android implementations.
actor MtaAlertsService {
    private let alertsURL = "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/camsys%2Fsubway-alerts"

    private let alertEffects: [Int: String] = [
        1: "NO_SERVICE",
        2: "REDUCED_SERVICE",
        3: "SIGNIFICANT_DELAYS",
        4: "DETOUR",
        5: "ADDITIONAL_SERVICE",
        6: "MODIFIED_SERVICE"
    ]

    /// Fetch alerts, optionally filtered by route IDs
    func fetchAlerts(routeIds: [String] = []) async -> [ServiceAlert] {
        do {
            var request = URLRequest(url: URL(string: alertsURL)!)
            request.setValue("NYC-Commute-Optimizer/1.0", forHTTPHeaderField: "User-Agent")
            request.timeoutInterval = 10

            let (data, _) = try await URLSession.shared.data(for: request)

            let alerts = parseAlertsFeed(data)

            if routeIds.isEmpty {
                return alerts
            } else {
                return alerts.filter { alert in
                    alert.routeIds.contains { routeIds.contains($0) }
                }
            }
        } catch {
            return []
        }
    }

    // MARK: - Private Parsing Methods

    private func parseAlertsFeed(_ data: Data) -> [ServiceAlert] {
        var alerts: [ServiceAlert] = []
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 1 && wireType == 2 {
                // Entity field (note: alerts uses field 1, not 2 like trip updates)
                let length = Int(reader.readVarint())
                let entityData = reader.readBytes(length: length)
                if let alert = parseEntity(entityData) {
                    alerts.append(alert)
                }
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return alerts
    }

    private func parseEntity(_ data: Data) -> ServiceAlert? {
        let reader = ProtobufReader(data: data)
        var routeIds: [String] = []
        var effect = "UNKNOWN"
        var headerText = ""

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 2 && wireType == 2 {
                // Alert field
                let length = Int(reader.readVarint())
                let alertData = reader.readBytes(length: length)
                let result = parseAlert(alertData)
                routeIds = result.routeIds
                effect = result.effect
                headerText = result.headerText
            } else {
                reader.skip(wireType: wireType)
            }
        }

        guard !routeIds.isEmpty && !headerText.isEmpty else {
            return nil
        }

        return ServiceAlert(routeIds: routeIds, effect: effect, headerText: headerText)
    }

    private func parseAlert(_ data: Data) -> (routeIds: [String], effect: String, headerText: String) {
        let reader = ProtobufReader(data: data)
        var routeIds: [String] = []
        var effect = "UNKNOWN"
        var headerText = ""

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            switch (fieldNumber, wireType) {
            case (6, 0):  // Effect enum
                let value = Int(reader.readVarint())
                effect = alertEffects[value] ?? "UNKNOWN"
            case (5, 2):  // InformedEntity
                let length = Int(reader.readVarint())
                let entityData = reader.readBytes(length: length)
                if let routeId = parseInformedEntity(entityData) {
                    if !routeIds.contains(routeId) {
                        routeIds.append(routeId)
                    }
                }
            case (10, 2):  // HeaderText (TranslatedString)
                let length = Int(reader.readVarint())
                let textData = reader.readBytes(length: length)
                headerText = parseTranslatedString(textData)
            default:
                reader.skip(wireType: wireType)
            }
        }

        return (routeIds, effect, headerText)
    }

    private func parseInformedEntity(_ data: Data) -> String? {
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 3 && wireType == 2 {
                // route_id field
                let length = Int(reader.readVarint())
                return reader.readString(length: length)
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return nil
    }

    private func parseTranslatedString(_ data: Data) -> String {
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 1 && wireType == 2 {
                // Translation field
                let length = Int(reader.readVarint())
                let translationData = reader.readBytes(length: length)
                return parseTranslation(translationData)
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return ""
    }

    private func parseTranslation(_ data: Data) -> String {
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 1 && wireType == 2 {
                // text field
                let length = Int(reader.readVarint())
                return reader.readString(length: length)
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return ""
    }
}
