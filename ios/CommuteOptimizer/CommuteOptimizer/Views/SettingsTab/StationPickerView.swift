import SwiftUI

struct StationPickerView: View {
    @Binding var selectedStations: [String]
    let maxSelections: Int?  // nil for unlimited
    let homeLat: Double
    let homeLng: Double

    @State private var searchText = ""

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

    private var filteredStations: [(station: LocalStation, distance: Double)] {
        guard !searchText.isEmpty else { return sortedStations }

        let query = searchText.lowercased()
        return sortedStations.filter { item in
            // Match station name
            if item.station.name.lowercased().contains(query) {
                return true
            }
            // Match line letter/number (e.g., "G", "1", "A")
            if item.station.lines.contains(where: { $0.lowercased() == query || $0.lowercased().contains(query) }) {
                return true
            }
            return false
        }
    }

    private var canSelectMore: Bool {
        guard let max = maxSelections else { return true }
        return selectedStations.count < max
    }

    var body: some View {
        VStack(spacing: 0) {
            // Search bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.secondary)
                TextField("Search by name or line", text: $searchText)
                    .textFieldStyle(.plain)
                    .autocorrectionDisabled()

                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .padding(8)
            .background(Color(.systemGray6))
            .cornerRadius(8)
            .padding(.bottom, 8)

            // Station count info
            HStack {
                Text("\(selectedStations.count) selected")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Spacer()
                Text("\(filteredStations.count) shown")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.bottom, 4)

            // Station list
            ForEach(filteredStations, id: \.station.id) { item in
                StationRow(
                    station: item.station,
                    distance: homeLat != 0 ? item.distance : nil,
                    isSelected: selectedStations.contains(item.station.id),
                    canSelect: canSelectMore || selectedStations.contains(item.station.id),
                    onToggle: { toggleStation(item.station.id) }
                )
            }
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
