import SwiftUI
import WidgetKit

struct CommuteWidgetEntryView: View {
    var entry: CommuteEntry
    @Environment(\.widgetFamily) var family

    var body: some View {
        if entry.isLoading {
            loadingView
        } else if let error = entry.errorMessage {
            errorView(error)
        } else if entry.options.isEmpty {
            emptyView
        } else {
            contentView
        }
    }

    private var loadingView: some View {
        VStack {
            ProgressView()
            Text("Loading...")
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }

    private func errorView(_ message: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle")
                .font(.title)
                .foregroundColor(.orange)
            Text(message)
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
    }

    private var emptyView: some View {
        VStack(spacing: 8) {
            Image(systemName: "tram")
                .font(.title)
                .foregroundColor(.secondary)
            Text("No options available")
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }

    private var contentView: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Weather header
            if let weather = entry.weather {
                WidgetWeatherHeader(weather: weather)
            }

            Divider()

            // Commute options
            VStack(spacing: 6) {
                ForEach(entry.options.prefix(3)) { option in
                    WidgetOptionRow(option: option)
                }
            }

            Spacer(minLength: 0)

            // Timestamp
            Text("Updated \(formatTime(entry.date))")
                .font(.system(size: 10))
                .foregroundColor(.secondary)
        }
        .padding(12)
    }

    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        return formatter.string(from: date)
    }
}

struct WidgetWeatherHeader: View {
    let weather: Weather

    var body: some View {
        HStack {
            Image(systemName: MtaColors.weatherEmoji(weather.conditions, precipType: weather.precipitationType))
                .font(.title3)
                .foregroundColor(weather.isBad ? .blue : .yellow)

            Text("\(weather.tempF)")
                .font(.headline)

            Text(weather.conditions)
                .font(.caption)
                .foregroundColor(.secondary)

            Spacer()

            if weather.isBad {
                Image(systemName: "bicycle")
                    .font(.caption)
                    .foregroundColor(.orange)
                    .opacity(0.7)
            }
        }
    }
}

struct WidgetOptionRow: View {
    let option: CommuteOption

    var body: some View {
        HStack(spacing: 8) {
            // Rank badge
            ZStack {
                Circle()
                    .fill(MtaColors.rankColor(for: option.rank))
                    .frame(width: 22, height: 22)
                Text("\(option.rank)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.black)
            }

            // Type icon
            typeIcon
                .font(.caption)
                .frame(width: 16)

            // Lines
            HStack(spacing: 2) {
                ForEach(uniqueRoutes.prefix(2), id: \.self) { route in
                    WidgetLineBadge(line: route)
                }
            }

            Spacer()

            // Next train
            Text(option.nextTrain)
                .font(.caption)
                .foregroundColor(.secondary)

            // Duration
            Text("\(option.durationMinutes)m")
                .font(.system(size: 14, weight: .semibold))
        }
    }

    private var typeIcon: some View {
        Group {
            switch option.type {
            case .bikeToTransit:
                Image(systemName: "bicycle")
                    .foregroundColor(.green)
            case .transitOnly:
                Image(systemName: "figure.walk")
                    .foregroundColor(.orange)
            case .walkOnly:
                Image(systemName: "figure.walk")
                    .foregroundColor(.orange)
            }
        }
    }

    private var uniqueRoutes: [String] {
        option.legs
            .filter { $0.mode == .subway }
            .compactMap { $0.route }
    }
}

struct WidgetLineBadge: View {
    let line: String
    var size: CGFloat = 18

    var body: some View {
        ZStack {
            Circle()
                .fill(MtaColors.color(for: line))
                .frame(width: size, height: size)
            Text(MtaColors.cleanExpressLine(line))
                .font(.system(size: size * 0.55, weight: .bold))
                .foregroundColor(MtaColors.textColor(for: line))
        }
    }
}

#Preview(as: .systemMedium) {
    CommuteWidget()
} timeline: {
    CommuteEntry(
        date: Date(),
        options: [
            CommuteOption(
                id: "1",
                rank: 1,
                type: .bikeToTransit,
                durationMinutes: 22,
                summary: "Bike -> G",
                legs: [
                    Leg(mode: .bike, duration: 8, to: "Bedford"),
                    Leg(mode: .subway, duration: 12, to: "Court Sq", route: "G")
                ],
                nextTrain: "3m",
                arrivalTime: "9:45 AM",
                station: Station(id: "test", name: "Test", mtaId: "G33", lines: ["G"], lat: 0, lng: 0, borough: "Brooklyn")
            ),
            CommuteOption(
                id: "2",
                rank: 2,
                type: .transitOnly,
                durationMinutes: 28,
                summary: "Walk -> G",
                legs: [
                    Leg(mode: .walk, duration: 10, to: "Bedford"),
                    Leg(mode: .subway, duration: 12, to: "Court Sq", route: "G")
                ],
                nextTrain: "5m",
                arrivalTime: "9:52 AM",
                station: Station(id: "test", name: "Test", mtaId: "G33", lines: ["G"], lat: 0, lng: 0, borough: "Brooklyn")
            ),
            CommuteOption(
                id: "3",
                rank: 3,
                type: .transitOnly,
                durationMinutes: 32,
                summary: "Walk -> A -> G",
                legs: [
                    Leg(mode: .walk, duration: 5, to: "Hoyt"),
                    Leg(mode: .subway, duration: 8, to: "Jay St", route: "A"),
                    Leg(mode: .subway, duration: 15, to: "Court Sq", route: "G")
                ],
                nextTrain: "2m",
                arrivalTime: "9:55 AM",
                station: Station(id: "test", name: "Test", mtaId: "A42", lines: ["A", "C", "G"], lat: 0, lng: 0, borough: "Brooklyn")
            )
        ],
        weather: Weather(tempF: 68, conditions: "Partly Cloudy", precipitationType: .none, precipitationProbability: 10, isBad: false),
        isLoading: false,
        errorMessage: nil
    )
}
