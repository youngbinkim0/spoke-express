import SwiftUI

struct StationPickerView: View {
    @Binding var selectedStations: [String]
    let maxSelections: Int?  // nil for unlimited
    let homeLat: Double
    let homeLng: Double

    private let stationsDataSource = StationsDataSource.shared

    private var sortedStations: [(station: LocalStation, distance: Double)] {
        // Sort by distance from home if home is set, otherwise alphabetically
        if homeLat != 0 && homeLng != 0 {
            return stationsDataSource.getStationsSortedByDistance(
                fromLat: homeLat,
                fromLng: homeLng
            )
        } else {
            return stationsDataSource.getStations()
                .sorted { $0.name < $1.name }
                .map { ($0, 0.0) }
        }
    }

    private var canSelectMore: Bool {
        guard let max = maxSelections else { return true }
        return selectedStations.count < max
    }

    var body: some View {
        ForEach(sortedStations, id: \.station.id) { item in
            StationRow(
                station: item.station,
                distance: homeLat != 0 ? item.distance : nil,
                isSelected: selectedStations.contains(item.station.id),
                canSelect: canSelectMore || selectedStations.contains(item.station.id),
                onToggle: { toggleStation(item.station.id) }
            )
        }
    }

    private func toggleStation(_ stationId: String) {
        if selectedStations.contains(stationId) {
            selectedStations.removeAll { $0 == stationId }
        } else if canSelectMore {
            selectedStations.append(stationId)
        }
    }
}

struct StationRow: View {
    let station: LocalStation
    let distance: Double?  // nil if home not set
    let isSelected: Bool
    let canSelect: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(station.name)
                            .font(.subheadline)
                            .foregroundColor(canSelect || isSelected ? .primary : .secondary)

                        if let distance = distance {
                            Text(String(format: "%.1f mi", distance))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    LineBadgeRow(lines: station.lines, size: 18)
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.blue)
                } else if canSelect {
                    Image(systemName: "circle")
                        .foregroundColor(.secondary)
                } else {
                    Image(systemName: "circle")
                        .foregroundColor(.secondary.opacity(0.3))
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!canSelect && !isSelected)
    }
}

#Preview {
    Form {
        StationPickerView(
            selectedStations: .constant(["bedford-nostrand", "classon"]),
            maxSelections: 3,
            homeLat: 40.6896,
            homeLng: -73.9535
        )
    }
}
