import SwiftUI

struct StationCard: View {
    let station: LocalStation
    let arrivalGroups: [ArrivalGroup]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Station header
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(station.name)
                        .font(.headline)
                    Text(station.borough)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                LineBadgeRow(lines: station.lines, size: 22)
            }

            Divider()

            // Arrival groups
            if arrivalGroups.isEmpty {
                HStack {
                    Image(systemName: "clock.badge.questionmark")
                        .foregroundColor(.secondary)
                    Text("No arrivals available")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .padding(.vertical, 8)
            } else {
                ForEach(arrivalGroups) { group in
                    ArrivalGroupRow(group: group)
                }
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
    }
}

struct ArrivalGroupRow: View {
    let group: ArrivalGroup

    var body: some View {
        HStack(spacing: 12) {
            // Line badge and direction
            HStack(spacing: 6) {
                LineBadge(line: group.line, size: 24)

                VStack(alignment: .leading, spacing: 2) {
                    Text(group.direction.headsign)
                        .font(.subheadline)
                        .fontWeight(.medium)

                    Image(systemName: group.direction == .north ? "arrow.up" : "arrow.down")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            // Arrival times
            HStack(spacing: 8) {
                ForEach(group.arrivals.prefix(3)) { arrival in
                    ArrivalBadge(arrival: arrival, line: group.line)
                }
            }
        }
        .padding(.vertical, 4)
    }
}

struct ArrivalBadge: View {
    let arrival: Arrival
    let line: String

    var body: some View {
        Text(displayText)
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(badgeColor)
            .cornerRadius(8)
    }

    private var displayText: String {
        if arrival.minutesAway <= 0 {
            return "Now"
        }
        return "\(arrival.minutesAway)m"
    }

    private var badgeColor: Color {
        MtaColors.arrivalBadgeColor(minutesAway: arrival.minutesAway, line: line)
    }
}

#Preview {
    VStack {
        StationCard(
            station: LocalStation(
                id: "bedford-nostrand",
                name: "Bedford-Nostrand Avs",
                transiterId: "G33",
                lines: ["G"],
                lat: 40.6896,
                lng: -73.9535,
                borough: "Brooklyn"
            ),
            arrivalGroups: [
                ArrivalGroup(
                    line: "G",
                    direction: .north,
                    arrivals: [
                        Arrival(routeId: "G", direction: .north, arrivalTime: Date().timeIntervalSince1970 + 120, minutesAway: 2),
                        Arrival(routeId: "G", direction: .north, arrivalTime: Date().timeIntervalSince1970 + 480, minutesAway: 8),
                        Arrival(routeId: "G", direction: .north, arrivalTime: Date().timeIntervalSince1970 + 840, minutesAway: 14)
                    ]
                ),
                ArrivalGroup(
                    line: "G",
                    direction: .south,
                    arrivals: [
                        Arrival(routeId: "G", direction: .south, arrivalTime: Date().timeIntervalSince1970 + 300, minutesAway: 5),
                        Arrival(routeId: "G", direction: .south, arrivalTime: Date().timeIntervalSince1970 + 720, minutesAway: 12)
                    ]
                )
            ]
        )
    }
    .padding()
    .background(Color(.systemGroupedBackground))
}
