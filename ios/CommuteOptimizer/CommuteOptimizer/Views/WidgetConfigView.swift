import SwiftUI
import WidgetKit
import MapKit
import CoreLocation

struct WidgetConfigView: View {
    @EnvironmentObject var settingsManager: SettingsManager
    @Environment(\.dismiss) var dismiss

    let widgetId: String?

    @State private var useCustomOrigin = false
    @State private var useCustomDestination = false
    @State private var originAddress = ""
    @State private var destAddress = ""
    @State private var originCoord: CLLocationCoordinate2D?
    @State private var destCoord: CLLocationCoordinate2D?
    @State private var isGeocodingOrigin = false
    @State private var isGeocodingDest = false
    @State private var originError: String?
    @State private var destError: String?

    private let geocoder = CLGeocoder()

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
                        .disabled(!canSave)
                }
            }
            .onAppear { loadCurrentSettings() }
        }
    }

    private var canSave: Bool {
        // Can save if using defaults, or if custom is set with valid coordinates
        let originValid = !useCustomOrigin || originCoord != nil
        let destValid = !useCustomDestination || destCoord != nil
        return originValid && destValid && !isGeocodingOrigin && !isGeocodingDest
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
                .onChange(of: useCustomOrigin) { _, newValue in
                    if !newValue {
                        originAddress = ""
                        originCoord = nil
                        originError = nil
                    }
                }

            if useCustomOrigin {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        TextField("Enter address", text: $originAddress)
                            .textFieldStyle(.roundedBorder)
                            .autocorrectionDisabled()

                        if isGeocodingOrigin {
                            ProgressView()
                                .scaleEffect(0.8)
                        } else {
                            Button("Look up") {
                                geocodeOrigin()
                            }
                            .buttonStyle(.bordered)
                            .disabled(originAddress.isEmpty)
                        }
                    }

                    if let error = originError {
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                    } else if let coord = originCoord {
                        Text("Location: \(coord.latitude, specifier: "%.4f"), \(coord.longitude, specifier: "%.4f")")
                            .font(.caption)
                            .foregroundColor(.green)
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
                .onChange(of: useCustomDestination) { _, newValue in
                    if !newValue {
                        destAddress = ""
                        destCoord = nil
                        destError = nil
                    }
                }

            if useCustomDestination {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        TextField("Enter address", text: $destAddress)
                            .textFieldStyle(.roundedBorder)
                            .autocorrectionDisabled()

                        if isGeocodingDest {
                            ProgressView()
                                .scaleEffect(0.8)
                        } else {
                            Button("Look up") {
                                geocodeDest()
                            }
                            .buttonStyle(.bordered)
                            .disabled(destAddress.isEmpty)
                        }
                    }

                    if let error = destError {
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                    } else if let coord = destCoord {
                        Text("Location: \(coord.latitude, specifier: "%.4f"), \(coord.longitude, specifier: "%.4f")")
                            .font(.caption)
                            .foregroundColor(.green)
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
            let lat = settingsManager.getWidgetOriginLat(widgetId)
            let lng = settingsManager.getWidgetOriginLng(widgetId)
            // Only consider custom if not using defaults (0,0 or same as home)
            if lat != 0 && lng != 0 && (lat != settingsManager.homeLat || lng != settingsManager.homeLng) {
                useCustomOrigin = true
                originAddress = settingsManager.getWidgetOriginName(widgetId)
                originCoord = CLLocationCoordinate2D(latitude: lat, longitude: lng)
            }
        }

        // Check if widget has custom destination
        if settingsManager.hasWidgetDestination(widgetId) {
            let lat = settingsManager.getWidgetDestinationLat(widgetId)
            let lng = settingsManager.getWidgetDestinationLng(widgetId)
            // Only consider custom if not using defaults
            if lat != 0 && lng != 0 && (lat != settingsManager.workLat || lng != settingsManager.workLng) {
                useCustomDestination = true
                destAddress = settingsManager.getWidgetDestinationName(widgetId)
                destCoord = CLLocationCoordinate2D(latitude: lat, longitude: lng)
            }
        }
    }

    private func geocodeOrigin() {
        isGeocodingOrigin = true
        originError = nil
        originCoord = nil

        geocoder.geocodeAddressString(originAddress) { placemarks, error in
            isGeocodingOrigin = false

            if let error = error {
                originError = "Could not find address: \(error.localizedDescription)"
                return
            }

            guard let placemark = placemarks?.first,
                  let location = placemark.location else {
                originError = "No results found"
                return
            }

            originCoord = location.coordinate
            // Update address with formatted version
            if let formatted = formatPlacemark(placemark) {
                originAddress = formatted
            }
        }
    }

    private func geocodeDest() {
        isGeocodingDest = true
        destError = nil
        destCoord = nil

        geocoder.geocodeAddressString(destAddress) { placemarks, error in
            isGeocodingDest = false

            if let error = error {
                destError = "Could not find address: \(error.localizedDescription)"
                return
            }

            guard let placemark = placemarks?.first,
                  let location = placemark.location else {
                destError = "No results found"
                return
            }

            destCoord = location.coordinate
            // Update address with formatted version
            if let formatted = formatPlacemark(placemark) {
                destAddress = formatted
            }
        }
    }

    private func formatPlacemark(_ placemark: CLPlacemark) -> String? {
        var components: [String] = []
        if let street = placemark.thoroughfare {
            if let number = placemark.subThoroughfare {
                components.append("\(number) \(street)")
            } else {
                components.append(street)
            }
        }
        if let city = placemark.locality {
            components.append(city)
        }
        return components.isEmpty ? nil : components.joined(separator: ", ")
    }

    private func saveAndDismiss() {
        guard let widgetId = widgetId else {
            dismiss()
            return
        }

        // Save origin
        if useCustomOrigin, let coord = originCoord {
            settingsManager.setWidgetOrigin(widgetId, name: originAddress, lat: coord.latitude, lng: coord.longitude)
        } else {
            // Clear custom origin to use defaults
            settingsManager.setWidgetOrigin(widgetId, name: "", lat: 0, lng: 0)
        }

        // Save destination
        if useCustomDestination, let coord = destCoord {
            settingsManager.setWidgetDestination(widgetId, name: destAddress, lat: coord.latitude, lng: coord.longitude)
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
        originAddress = ""
        destAddress = ""
        originCoord = nil
        destCoord = nil
        originError = nil
        destError = nil

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
