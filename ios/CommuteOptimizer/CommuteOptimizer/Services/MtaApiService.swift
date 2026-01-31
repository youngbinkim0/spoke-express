import Foundation

actor MtaApiService {
    private let baseURL = "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2F"

    // Line to feed mapping (matches Android exactly)
    private let lineToFeed: [String: String] = [
        "1": "gtfs", "2": "gtfs", "3": "gtfs",
        "4": "gtfs", "5": "gtfs", "6": "gtfs",
        "7": "gtfs", "S": "gtfs",
        "A": "gtfs-ace", "C": "gtfs-ace", "E": "gtfs-ace",
        "B": "gtfs-bdfm", "D": "gtfs-bdfm", "F": "gtfs-bdfm", "M": "gtfs-bdfm",
        "G": "gtfs-g",
        "J": "gtfs-jz", "Z": "gtfs-jz",
        "L": "gtfs-l",
        "N": "gtfs-nqrw", "Q": "gtfs-nqrw", "R": "gtfs-nqrw", "W": "gtfs-nqrw"
    ]

    /// Get all arrivals for a specific station
    func getStationArrivals(stationId: String, lines: [String]) async throws -> [Arrival] {
        let feeds = Set(lines.compactMap { lineToFeed[$0] })
        guard !feeds.isEmpty else { return [] }

        let targetStopIds = Set(["\(stationId)N", "\(stationId)S"])

        var allArrivals: [Arrival] = []

        for feedName in feeds {
            if let data = try? await fetchFeed(feedName) {
                let arrivals = parseFeed(data, targetStopIds: targetStopIds)
                allArrivals.append(contentsOf: arrivals)
            }
        }

        return allArrivals.sorted { $0.arrivalTime < $1.arrivalTime }
    }

    /// Get next arrival for a station
    func getNextArrival(stationId: String, lines: [String]) async -> NextArrivalResult {
        do {
            let arrivals = try await getStationArrivals(stationId: stationId, lines: lines)

            guard let next = arrivals.first else {
                return .unavailable
            }

            let formatter = DateFormatter()
            formatter.dateFormat = "h:mm a"
            let arrivalDate = Date(timeIntervalSince1970: next.arrivalTime)

            // Use "Now" for <=0 minutes
            let displayText = next.minutesAway <= 0 ? "Now" : "\(next.minutesAway)m"

            return NextArrivalResult(
                nextTrain: displayText,
                arrivalTime: formatter.string(from: arrivalDate),
                routeId: next.routeId,
                minutesAway: next.minutesAway
            )
        } catch {
            return .unavailable
        }
    }

    /// Get grouped arrivals for display
    func getGroupedArrivals(stationId: String, lines: [String]) async throws -> [ArrivalGroup] {
        let arrivals = try await getStationArrivals(stationId: stationId, lines: lines)

        var groups: [String: [Arrival]] = [:]

        for arrival in arrivals {
            // Normalize express variants (6X -> 6, 7X -> 7, FX -> F)
            let cleanedLine = MtaColors.cleanExpressLine(arrival.routeId)
            let key = "\(cleanedLine)-\(arrival.direction.rawValue)"

            if groups[key] == nil {
                groups[key] = []
            }
            if groups[key]!.count < 3 {
                groups[key]!.append(arrival)
            }
        }

        return groups.map { key, arrivals in
            let parts = key.split(separator: "-")
            let line = String(parts[0])
            let direction: Direction = parts.count > 1 && parts[1] == "S" ? .south : .north
            return ArrivalGroup(line: line, direction: direction, arrivals: arrivals)
        }.sorted { $0.line < $1.line }
    }

    // MARK: - Private Methods

    private func fetchFeed(_ feedName: String) async throws -> Data {
        var request = URLRequest(url: URL(string: baseURL + feedName)!)
        request.setValue("NYC-Commute-Optimizer/1.0", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await URLSession.shared.data(for: request)
        return data
    }

    private func parseFeed(_ data: Data, targetStopIds: Set<String>) -> [Arrival] {
        var arrivals: [Arrival] = []
        let now = Date().timeIntervalSince1970
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 2 && wireType == 2 {
                let length = Int(reader.readVarint())
                let entityData = reader.readBytes(length: length)
                let entityArrivals = parseEntity(entityData, targetStopIds: targetStopIds, now: now)
                arrivals.append(contentsOf: entityArrivals)
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return arrivals
    }

    private func parseEntity(_ data: Data, targetStopIds: Set<String>, now: TimeInterval) -> [Arrival] {
        var arrivals: [Arrival] = []
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 3 && wireType == 2 {
                let length = Int(reader.readVarint())
                let tripUpdateData = reader.readBytes(length: length)
                let tripArrivals = parseTripUpdate(tripUpdateData, targetStopIds: targetStopIds, now: now)
                arrivals.append(contentsOf: tripArrivals)
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return arrivals
    }

    private func parseTripUpdate(_ data: Data, targetStopIds: Set<String>, now: TimeInterval) -> [Arrival] {
        var arrivals: [Arrival] = []
        let reader = ProtobufReader(data: data)

        var routeId: String?
        var stopTimeUpdates: [Data] = []

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            switch (fieldNumber, wireType) {
            case (1, 2):  // TripDescriptor
                let length = Int(reader.readVarint())
                let tripData = reader.readBytes(length: length)
                routeId = parseTripDescriptor(tripData)
            case (2, 2):  // StopTimeUpdate
                let length = Int(reader.readVarint())
                stopTimeUpdates.append(reader.readBytes(length: length))
            default:
                reader.skip(wireType: wireType)
            }
        }

        if let routeId = routeId {
            for stuData in stopTimeUpdates {
                if let arrival = parseStopTimeUpdate(stuData, routeId: routeId, targetStopIds: targetStopIds, now: now) {
                    arrivals.append(arrival)
                }
            }
        }

        return arrivals
    }

    private func parseTripDescriptor(_ data: Data) -> String? {
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 5 && wireType == 2 {
                let length = Int(reader.readVarint())
                return reader.readString(length: length)
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return nil
    }

    private func parseStopTimeUpdate(_ data: Data, routeId: String, targetStopIds: Set<String>, now: TimeInterval) -> Arrival? {
        let reader = ProtobufReader(data: data)

        var stopId: String?
        var arrivalTime: TimeInterval?

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            switch (fieldNumber, wireType) {
            case (4, 2):  // stop_id
                let length = Int(reader.readVarint())
                stopId = reader.readString(length: length)
            case (2, 2):  // arrival
                let length = Int(reader.readVarint())
                let arrivalData = reader.readBytes(length: length)
                arrivalTime = parseStopTimeEvent(arrivalData)
            case (3, 2):  // departure
                let length = Int(reader.readVarint())
                if arrivalTime == nil {
                    let departureData = reader.readBytes(length: length)
                    arrivalTime = parseStopTimeEvent(departureData)
                }
            default:
                reader.skip(wireType: wireType)
            }
        }

        guard let stopId = stopId,
              targetStopIds.contains(stopId),
              let arrivalTime = arrivalTime,
              arrivalTime >= now - 60 else {
            return nil
        }

        let direction: Direction = stopId.hasSuffix("N") ? .north : .south
        let minutesAway = max(0, Int((arrivalTime - now) / 60))

        return Arrival(
            routeId: routeId,
            direction: direction,
            arrivalTime: arrivalTime,
            minutesAway: minutesAway
        )
    }

    private func parseStopTimeEvent(_ data: Data) -> TimeInterval? {
        let reader = ProtobufReader(data: data)

        while reader.hasMore {
            let tag = reader.readVarint()
            let fieldNumber = Int(tag >> 3)
            let wireType = Int(tag & 0x7)

            if fieldNumber == 2 && wireType == 0 {
                return TimeInterval(reader.readVarint())
            } else {
                reader.skip(wireType: wireType)
            }
        }

        return nil
    }
}
