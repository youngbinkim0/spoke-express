import WidgetKit
import SwiftUI

struct LiveTrainsWidget: Widget {
    let kind: String = "LiveTrainsWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: LiveTrainsWidgetProvider()) { entry in
            LiveTrainsWidgetEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Live Trains")
        .description("Shows real-time arrivals for your selected station.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct LiveTrainsEntry: TimelineEntry {
    let date: Date
    let stationName: String
    let arrivalGroups: [ArrivalGroup]
    let isLoading: Bool
    let errorMessage: String?

    static let placeholder = LiveTrainsEntry(
        date: Date(),
        stationName: "Bedford-Nostrand",
        arrivalGroups: [
            ArrivalGroup(line: "G", direction: .north, arrivals: [
                Arrival(routeId: "G", direction: .north, arrivalTime: Date().timeIntervalSince1970 + 180, minutesAway: 3),
                Arrival(routeId: "G", direction: .north, arrivalTime: Date().timeIntervalSince1970 + 600, minutesAway: 10)
            ]),
            ArrivalGroup(line: "G", direction: .south, arrivals: [
                Arrival(routeId: "G", direction: .south, arrivalTime: Date().timeIntervalSince1970 + 300, minutesAway: 5),
                Arrival(routeId: "G", direction: .south, arrivalTime: Date().timeIntervalSince1970 + 720, minutesAway: 12)
            ])
        ],
        isLoading: false,
        errorMessage: nil
    )

    static let loading = LiveTrainsEntry(
        date: Date(),
        stationName: "",
        arrivalGroups: [],
        isLoading: true,
        errorMessage: nil
    )

    static func error(_ message: String) -> LiveTrainsEntry {
        LiveTrainsEntry(
            date: Date(),
            stationName: "",
            arrivalGroups: [],
            isLoading: false,
            errorMessage: message
        )
    }
}

struct LiveTrainsWidgetProvider: TimelineProvider {
    private let mtaService = MtaApiService()
    private let settingsManager = SettingsManager()
    private let stationsDataSource = StationsDataSource.shared

    func placeholder(in context: Context) -> LiveTrainsEntry {
        LiveTrainsEntry.placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (LiveTrainsEntry) -> Void) {
        if context.isPreview {
            completion(LiveTrainsEntry.placeholder)
            return
        }

        settingsManager.loadFromDefaults()

        guard let stationId = settingsManager.liveStations.first,
              let station = stationsDataSource.getStation(id: stationId) else {
            completion(LiveTrainsEntry.error("Select station in app"))
            return
        }

        Task {
            do {
                let groups = try await mtaService.getGroupedArrivals(
                    stationId: station.mtaId,
                    lines: station.lines
                )
                let entry = LiveTrainsEntry(
                    date: Date(),
                    stationName: station.name,
                    arrivalGroups: groups,
                    isLoading: false,
                    errorMessage: nil
                )
                completion(entry)
            } catch {
                completion(LiveTrainsEntry.error("Failed to load"))
            }
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LiveTrainsEntry>) -> Void) {
        settingsManager.loadFromDefaults()

        guard let stationId = settingsManager.liveStations.first,
              let station = stationsDataSource.getStation(id: stationId) else {
            let entry = LiveTrainsEntry.error("Select station in app")
            let timeline = Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(60 * 60)))
            completion(timeline)
            return
        }

        Task {
            do {
                let groups = try await mtaService.getGroupedArrivals(
                    stationId: station.mtaId,
                    lines: station.lines
                )
                let entry = LiveTrainsEntry(
                    date: Date(),
                    stationName: station.name,
                    arrivalGroups: groups,
                    isLoading: false,
                    errorMessage: nil
                )

                // Refresh every 2 minutes for live arrivals
                let nextUpdate = Date().addingTimeInterval(2 * 60)
                let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
                completion(timeline)
            } catch {
                let entry = LiveTrainsEntry.error("Failed to load")
                let nextUpdate = Date().addingTimeInterval(60)  // Retry sooner on error
                let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
                completion(timeline)
            }
        }
    }
}

struct LiveTrainsWidgetEntryView: View {
    var entry: LiveTrainsEntry
    @Environment(\.widgetFamily) var family

    var body: some View {
        if entry.isLoading {
            ProgressView()
        } else if let error = entry.errorMessage {
            VStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle")
                    .foregroundColor(.orange)
                Text(error)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
        } else {
            switch family {
            case .systemSmall:
                smallView
            default:
                mediumView
            }
        }
    }

    private var smallView: some View {
        VStack(alignment: .leading, spacing: 4) {
            // Header
            Text(entry.stationName)
                .font(.system(size: 12, weight: .semibold))
                .lineLimit(1)

            Divider()

            // Show first 2 arrival groups
            ForEach(entry.arrivalGroups.prefix(2)) { group in
                SmallArrivalRow(group: group)
            }

            Spacer()

            // Timestamp
            Text(formatTime(entry.date))
                .font(.system(size: 9))
                .foregroundColor(.secondary)
        }
        .padding(8)
    }

    private var mediumView: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Header
            HStack {
                Text(entry.stationName)
                    .font(.system(size: 14, weight: .semibold))
                Spacer()
                Text(formatTime(entry.date))
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            }

            Divider()

            // Show up to 4 arrival groups
            ForEach(entry.arrivalGroups.prefix(4)) { group in
                MediumArrivalRow(group: group)
            }

            if entry.arrivalGroups.isEmpty {
                Text("No arrivals available")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()
        }
        .padding(10)
    }

    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        return formatter.string(from: date)
    }
}

struct SmallArrivalRow: View {
    let group: ArrivalGroup

    var body: some View {
        HStack(spacing: 4) {
            WidgetLineBadge(line: group.line, size: 16)

            Image(systemName: group.direction == .north ? "arrow.up" : "arrow.down")
                .font(.system(size: 8))
                .foregroundColor(.secondary)

            Spacer()

            // Show first 2 arrivals
            HStack(spacing: 2) {
                ForEach(group.arrivals.prefix(2)) { arrival in
                    WidgetArrivalBadge(minutesAway: arrival.minutesAway, line: group.line, compact: true)
                }
            }
        }
    }
}

struct MediumArrivalRow: View {
    let group: ArrivalGroup

    var body: some View {
        HStack(spacing: 6) {
            WidgetLineBadge(line: group.line, size: 20)

            VStack(alignment: .leading) {
                Text(group.direction.headsign)
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            }

            Spacer()

            // Show up to 3 arrivals
            HStack(spacing: 4) {
                ForEach(group.arrivals.prefix(3)) { arrival in
                    WidgetArrivalBadge(minutesAway: arrival.minutesAway, line: group.line, compact: false)
                }
            }
        }
    }
}

struct WidgetArrivalBadge: View {
    let minutesAway: Int
    let line: String
    let compact: Bool

    var body: some View {
        Text(displayText)
            .font(.system(size: compact ? 10 : 11, weight: .semibold))
            .foregroundColor(.white)
            .padding(.horizontal, compact ? 4 : 6)
            .padding(.vertical, compact ? 2 : 3)
            .background(badgeColor)
            .cornerRadius(4)
    }

    private var displayText: String {
        if minutesAway <= 0 {
            return "Now"
        }
        return "\(minutesAway)m"
    }

    private var badgeColor: Color {
        MtaColors.arrivalBadgeColor(minutesAway: minutesAway, line: line)
    }
}

#Preview(as: .systemSmall) {
    LiveTrainsWidget()
} timeline: {
    LiveTrainsEntry.placeholder
    LiveTrainsEntry.loading
    LiveTrainsEntry.error("Not configured")
}

#Preview(as: .systemMedium) {
    LiveTrainsWidget()
} timeline: {
    LiveTrainsEntry.placeholder
}
