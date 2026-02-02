import SwiftUI
import WidgetKit
import MapKit
import CoreLocation

struct WidgetConfigView: View {
    @EnvironmentObject var settingsManager: SettingsManager
    @Environment(\.dismiss) var dismiss

    let widgetId: String?

    /// Effective widget ID - uses default if none provided
    private var effectiveWidgetId: String {
        widgetId ?? "commute-default"
    }

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
        // Also allow saving if address is entered (will auto-geocode on save)
        let originValid = !useCustomOrigin || originCoord != nil || !originAddress.isEmpty
        let destValid = !useCustomDestination || destCoord != nil || !destAddress.isEmpty
        return originValid && destValid && !isGeocodingOrigin && !isGeocodingDest
    }

    /// Whether we need to geocode before saving
    private var needsOriginGeocoding: Bool {
        useCustomOrigin && originCoord == nil && !originAddress.isEmpty
    }

    private var needsDestGeocoding: Bool {
        useCustomDestination && destCoord == nil && !destAddress.isEmpty
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
        let wid = effectiveWidgetId

        // Check if widget has custom origin
        if settingsManager.hasWidgetOrigin(wid) {
            let lat = settingsManager.getWidgetOriginLat(wid)
            let lng = settingsManager.getWidgetOriginLng(wid)
            // Only consider custom if not using defaults (0,0 or same as home)
            if lat != 0 && lng != 0 && (lat != settingsManager.homeLat || lng != settingsManager.homeLng) {
                useCustomOrigin = true
                originAddress = settingsManager.getWidgetOriginName(wid)
                originCoord = CLLocationCoordinate2D(latitude: lat, longitude: lng)
            }
        }

        // Check if widget has custom destination
        if settingsManager.hasWidgetDestination(wid) {
            let lat = settingsManager.getWidgetDestinationLat(wid)
            let lng = settingsManager.getWidgetDestinationLng(wid)
            // Only consider custom if not using defaults
            if lat != 0 && lng != 0 && (lat != settingsManager.workLat || lng != settingsManager.workLng) {
                useCustomDestination = true
                destAddress = settingsManager.getWidgetDestinationName(wid)
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

    /// Geocode pending addresses and then save
    private func geocodeAndSave() {
        let group = DispatchGroup()

        // Geocode origin if needed
        if needsOriginGeocoding {
            group.enter()
            isGeocodingOrigin = true
            originError = nil

            geocoder.geocodeAddressString(originAddress) { placemarks, error in
                self.isGeocodingOrigin = false

                if let error = error {
                    self.originError = "Could not find address: \(error.localizedDescription)"
                    group.leave()
                    return
                }

                guard let placemark = placemarks?.first,
                      let location = placemark.location else {
                    self.originError = "No results found"
                    group.leave()
                    return
                }

                self.originCoord = location.coordinate
                if let formatted = self.formatPlacemark(placemark) {
                    self.originAddress = formatted
                }
                group.leave()
            }
        }

        // Geocode destination if needed
        if needsDestGeocoding {
            group.enter()
            isGeocodingDest = true
            destError = nil

            geocoder.geocodeAddressString(destAddress) { placemarks, error in
                self.isGeocodingDest = false

                if let error = error {
                    self.destError = "Could not find address: \(error.localizedDescription)"
                    group.leave()
                    return
                }

                guard let placemark = placemarks?.first,
                      let location = placemark.location else {
                    self.destError = "No results found"
                    group.leave()
                    return
                }

                self.destCoord = location.coordinate
                if let formatted = self.formatPlacemark(placemark) {
                    self.destAddress = formatted
                }
                group.leave()
            }
        }

        // When all geocoding is complete, save
        group.notify(queue: .main) {
            // Check if we got valid coordinates for all required addresses
            if self.useCustomOrigin && self.originCoord == nil {
                return
            }
            if self.useCustomDestination && self.destCoord == nil {
                return
            }
            // Call saveAndDismiss again - this time needsGeocoding will be false
            self.saveAndDismiss()
        }
    }

    private func saveAndDismiss() {
        let wid = effectiveWidgetId

        // If user entered an address but didn't geocode, do it now
        if needsOriginGeocoding || needsDestGeocoding {
            geocodeAndSave()
            return
        }

        // Save origin
        if useCustomOrigin, let coord = originCoord {
            settingsManager.setWidgetOrigin(wid, name: originAddress, lat: coord.latitude, lng: coord.longitude)
        } else {
            // Clear custom origin to use defaults
            settingsManager.setWidgetOrigin(wid, name: "", lat: 0, lng: 0)
        }

        // Save destination
        if useCustomDestination, let coord = destCoord {
            settingsManager.setWidgetDestination(wid, name: destAddress, lat: coord.latitude, lng: coord.longitude)
        } else {
            // Clear custom destination to use defaults
            settingsManager.setWidgetDestination(wid, name: "", lat: 0, lng: 0)
        }

        // Force sync UserDefaults before refreshing widget
        settingsManager.synchronize()

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

        settingsManager.clearWidgetData(effectiveWidgetId)
        WidgetCenter.shared.reloadTimelines(ofKind: "CommuteWidget")
    }
}

#Preview {
    WidgetConfigView(widgetId: "preview-widget")
        .environmentObject(SettingsManager())
}
