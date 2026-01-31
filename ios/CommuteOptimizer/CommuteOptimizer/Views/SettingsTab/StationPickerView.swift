import SwiftUI

struct StationPickerView: View {
    @Binding var selectedStations: [String]
    let maxSelections: Int?  // nil for unlimited

    private let stationsDataSource = StationsDataSource.shared

    var body: some View {
        let stations = stationsDataSource.getStations()

        // Group stations by borough
        let groupedStations = Dictionary(grouping: stations) { $0.borough }
        let sortedBoroughs = groupedStations.keys.sorted()

        ForEach(sortedBoroughs, id: \.self) { borough in
            Section(header: Text(borough).font(.caption).foregroundColor(.secondary)) {
                ForEach(groupedStations[borough] ?? [], id: \.id) { station in
                    StationRow(
                        station: station,
                        isSelected: selectedStations.contains(station.id),
                        canSelect: canSelectMore || selectedStations.contains(station.id),
                        onToggle: { toggleStation(station.id) }
                    )
                }
            }
        }
    }

    private var canSelectMore: Bool {
        guard let max = maxSelections else { return true }
        return selectedStations.count < max
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
    let isSelected: Bool
    let canSelect: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(station.name)
                        .font(.subheadline)
                        .foregroundColor(canSelect || isSelected ? .primary : .secondary)

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
            maxSelections: 3
        )
    }
}
