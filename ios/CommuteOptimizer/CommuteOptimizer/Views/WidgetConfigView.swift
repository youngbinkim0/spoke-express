import SwiftUI
import WidgetKit

struct WidgetConfigView: View {
    @EnvironmentObject var settingsManager: SettingsManager
    @Environment(\.dismiss) var dismiss

    let widgetId: String?

    @State private var selectedOriginStation: String?
    @State private var selectedDestinationStation: String?
    @State private var useCustomOrigin = false
    @State private var useCustomDestination = false

    private let stationsDataSource = StationsDataSource.shared

    private var stations: [LocalStation] {
        stationsDataSource.getStations().sorted { $0.name < $1.name }
    }

    var body: some View {
        NavigationView {
            Form {
                infoSection

                originSection

                destinationSection

                actionsSection
            }
            .navigationTitle("Widget Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { saveAndDismiss() }
                }
            }
            .onAppear { loadCurrentSettings() }
        }
    }

    private var infoSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                Label("Commute Widget", systemImage: "tram.fill")
                    .font(.headline)
                Text("Customize the origin and destination for this widget, or use your default Home and Work locations.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.vertical, 4)
        }
    }

    private var originSection: some View {
        Section("Origin") {
            Toggle("Use custom origin", isOn: $useCustomOrigin)

            if useCustomOrigin {
                Picker("Station", selection: $selectedOriginStation) {
                    Text("Select a station").tag(nil as String?)
                    ForEach(stations) { station in
                        Text("\(station.name) (\(station.linesDisplay))")
                            .tag(station.id as String?)
                    }
                }
            } else {
                HStack {
                    Text("Using")
                    Spacer()
                    Text(settingsManager.homeAddress.isEmpty ? "Home" : settingsManager.homeAddress)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
        }
    }

    private var destinationSection: some View {
        Section("Destination") {
            Toggle("Use custom destination", isOn: $useCustomDestination)

            if useCustomDestination {
                Picker("Station", selection: $selectedDestinationStation) {
                    Text("Select a station").tag(nil as String?)
                    ForEach(stations) { station in
                        Text("\(station.name) (\(station.linesDisplay))")
                            .tag(station.id as String?)
                    }
                }
            } else {
                HStack {
                    Text("Using")
                    Spacer()
                    Text(settingsManager.workAddress.isEmpty ? "Work" : settingsManager.workAddress)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
        }
    }

    private var actionsSection: some View {
        Section {
            Button("Reset to Defaults", role: .destructive) {
                resetToDefaults()
            }
        }
    }

    private func loadCurrentSettings() {
        guard let widgetId = widgetId else { return }

        // Check if widget has custom origin
        if settingsManager.hasWidgetOrigin(widgetId) {
            useCustomOrigin = true
            // Try to find matching station by coordinates
            let originLat = settingsManager.getWidgetOriginLat(widgetId)
            let originLng = settingsManager.getWidgetOriginLng(widgetId)
            selectedOriginStation = findStationNear(lat: originLat, lng: originLng)
        }

        // Check if widget has custom destination
        if settingsManager.hasWidgetDestination(widgetId) {
            useCustomDestination = true
            let destLat = settingsManager.getWidgetDestinationLat(widgetId)
            let destLng = settingsManager.getWidgetDestinationLng(widgetId)
            selectedDestinationStation = findStationNear(lat: destLat, lng: destLng)
        }
    }

    private func findStationNear(lat: Double, lng: Double) -> String? {
        // Find station within ~100 meters
        let threshold = 0.001  // roughly 100m in degrees
        return stations.first { station in
            abs(station.lat - lat) < threshold && abs(station.lng - lng) < threshold
        }?.id
    }

    private func saveAndDismiss() {
        guard let widgetId = widgetId else {
            dismiss()
            return
        }

        // Save origin
        if useCustomOrigin, let stationId = selectedOriginStation,
           let station = stationsDataSource.getStation(id: stationId) {
            settingsManager.setWidgetOrigin(widgetId, name: station.name, lat: station.lat, lng: station.lng)
        } else {
            // Clear custom origin to use defaults
            settingsManager.setWidgetOrigin(widgetId, name: "", lat: 0, lng: 0)
        }

        // Save destination
        if useCustomDestination, let stationId = selectedDestinationStation,
           let station = stationsDataSource.getStation(id: stationId) {
            settingsManager.setWidgetDestination(widgetId, name: station.name, lat: station.lat, lng: station.lng)
        } else {
            // Clear custom destination to use defaults
            settingsManager.setWidgetDestination(widgetId, name: "", lat: 0, lng: 0)
        }

        // Refresh widgets
        WidgetCenter.shared.reloadTimelines(ofKind: "CommuteWidget")

        dismiss()
    }

    private func resetToDefaults() {
        useCustomOrigin = false
        useCustomDestination = false
        selectedOriginStation = nil
        selectedDestinationStation = nil

        if let widgetId = widgetId {
            settingsManager.clearWidgetData(widgetId)
            WidgetCenter.shared.reloadTimelines(ofKind: "CommuteWidget")
        }
    }
}

#Preview {
    WidgetConfigView(widgetId: "preview-widget")
        .environmentObject(SettingsManager())
}
