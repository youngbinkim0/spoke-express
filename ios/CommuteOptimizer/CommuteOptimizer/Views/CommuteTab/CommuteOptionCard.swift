import SwiftUI

struct CommuteOptionCard: View {
    let option: CommuteOption

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header row with rank, summary, and duration
            HStack {
                // Rank badge
                ZStack {
                    Circle()
                        .fill(MtaColors.rankColor(for: option.rank))
                        .frame(width: 28, height: 28)
                    Text("#\(option.rank)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.black)
                }

                // Summary and type icon
                VStack(alignment: .leading, spacing: 2) {
                    Text(option.summary)
                        .font(.headline)
                        .lineLimit(1)

                    HStack(spacing: 4) {
                        typeIcon
                        Text(typeLabel)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Spacer()

                // Duration
                VStack(alignment: .trailing, spacing: 2) {
                    Text("\(option.durationMinutes)")
                        .font(.title2)
                        .fontWeight(.bold)
                    Text("min")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Divider()

            // Legs breakdown
            HStack(spacing: 16) {
                ForEach(option.legs) { leg in
                    LegView(leg: leg)
                }
            }

            // Train info row
            if option.type != .walkOnly {
                HStack {
                    // Line badges
                    LineBadgeRow(lines: option.station.lines, size: 20)

                    Spacer()

                    // Next train
                    HStack(spacing: 4) {
                        Image(systemName: "clock")
                            .font(.caption)
                        Text("Next: \(option.nextTrain)")
                            .font(.subheadline)
                    }
                    .foregroundColor(.secondary)

                    // Arrival time
                    Text("Arrive \(option.arrivalTime)")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
    }

    private var typeIcon: some View {
        Group {
            switch option.type {
            case .bikeToTransit:
                Image(systemName: "bicycle")
                    .foregroundColor(.green)
            case .transitOnly:
                Image(systemName: "tram.fill")
                    .foregroundColor(.blue)
            case .walkOnly:
                Image(systemName: "figure.walk")
                    .foregroundColor(.orange)
            }
        }
        .font(.caption)
    }

    private var typeLabel: String {
        switch option.type {
        case .bikeToTransit: return "Bike to Transit"
        case .transitOnly: return "Transit Only"
        case .walkOnly: return "Walk"
        }
    }
}

struct LegView: View {
    let leg: Leg

    var body: some View {
        VStack(spacing: 4) {
            ModeIcon(mode: leg.mode, size: 16)

            Text("\(leg.duration)m")
                .font(.caption)
                .fontWeight(.medium)

            if let route = leg.route {
                LineBadge(line: route, size: 18)
            }

            Text(leg.to)
                .font(.caption2)
                .foregroundColor(.secondary)
                .lineLimit(1)
        }
    }
}

#Preview {
    VStack {
        CommuteOptionCard(option: CommuteOption(
            id: "preview",
            rank: 1,
            type: .bikeToTransit,
            durationMinutes: 25,
            summary: "Bike -> G -> Court Sq",
            legs: [
                Leg(mode: .bike, duration: 8, to: "Bedford-Nostrand"),
                Leg(mode: .subway, duration: 12, to: "Court Sq", route: "G")
            ],
            nextTrain: "3m",
            arrivalTime: "9:45 AM",
            station: Station(id: "test", name: "Bedford-Nostrand", mtaId: "G33", lines: ["G"], lat: 0, lng: 0, borough: "Brooklyn")
        ))
    }
    .padding()
    .background(Color(.systemGroupedBackground))
}
