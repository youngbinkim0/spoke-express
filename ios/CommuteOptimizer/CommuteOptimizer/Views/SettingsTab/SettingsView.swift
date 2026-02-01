import SwiftUI
import CoreLocation

struct SettingsView: View {
    @EnvironmentObject var settingsManager: SettingsManager
    @State private var homeAddressInput = ""
    @State private var workAddressInput = ""
    @State private var isGeocodingHome = false
    @State private var isGeocodingWork = false
    @State private var geocodeError: String?
    @State private var bikeStationsExpanded = true
    @State private var liveStationsExpanded = true

    private let geocoder = CLGeocoder()
    private let stationsDataSource = StationsDataSource.shared

    var body: some View {
        NavigationView {
            Form {
                // API Keys Section
                Section("API Configuration") {
                    TextField("Google API Key", text: Binding(
                        get: { settingsManager.googleApiKey },
                        set: { settingsManager.googleApiKey = $0 }
                    ))
                    .autocapitalization(.none)
                    .autocorrectionDisabled()

                    Text("Required - for transit routing")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    TextField("OpenWeather API Key (optional)", text: Binding(
                        get: { settingsManager.openWeatherApiKey },
                        set: { settingsManager.openWeatherApiKey = $0 }
                    ))
                    .autocapitalization(.none)
                    .autocorrectionDisabled()

                    Text("Optional - for weather data (openweathermap.org)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                // Home Address Section
                Section("Home Location") {
                    HStack {
                        TextField("Home Address", text: $homeAddressInput)
                            .textContentType(.fullStreetAddress)
                            .onAppear {
                                homeAddressInput = settingsManager.homeAddress
                            }

                        if isGeocodingHome {
                            ProgressView()
                        } else {
                            Button("Set") {
                                geocodeHome()
                            }
                            .disabled(homeAddressInput.isEmpty)
                        }
                    }

                    if settingsManager.homeLat != 0 {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                            Text(settingsManager.homeAddress.isEmpty ? "Location set" : settingsManager.homeAddress)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }

                // Work Address Section
                Section("Work Location") {
                    HStack {
                        TextField("Work Address", text: $workAddressInput)
                            .textContentType(.fullStreetAddress)
                            .onAppear {
                                workAddressInput = settingsManager.workAddress
                            }

                        if isGeocodingWork {
                            ProgressView()
                        } else {
                            Button("Set") {
                                geocodeWork()
                            }
                            .disabled(workAddressInput.isEmpty)
                        }
                    }

                    if settingsManager.workLat != 0 {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                            Text(settingsManager.workAddress.isEmpty ? "Location set" : settingsManager.workAddress)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }

                // Bike Stations Section
                Section {
                    DisclosureGroup(
                        isExpanded: $bikeStationsExpanded,
                        content: {
                            StationPickerView(
                                selectedStations: Binding(
                                    get: { settingsManager.bikeStations },
                                    set: { settingsManager.bikeStations = $0 }
                                ),
                                maxSelections: nil,
                                homeLat: settingsManager.homeLat,
                                homeLng: settingsManager.homeLng
                            )
                        },
                        label: {
                            HStack {
                                Text("Bike-to Stations")
                                Spacer()
                                Text("\(settingsManager.bikeStations.count) selected")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    )
                }

                // Live Train Stations Section
                Section {
                    DisclosureGroup(
                        isExpanded: $liveStationsExpanded,
                        content: {
                            StationPickerView(
                                selectedStations: Binding(
                                    get: { settingsManager.liveStations },
                                    set: { settingsManager.liveStations = $0 }
                                ),
                                maxSelections: 3,
                                homeLat: settingsManager.homeLat,
                                homeLng: settingsManager.homeLng
                            )
                        },
                        label: {
                            HStack {
                                Text("Live Train Stations")
                                Spacer()
                                Text("\(settingsManager.liveStations.count)/3 selected")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    )
                }

                // Preferences Section
                Section("Preferences") {
                    Toggle("Show Bike Options", isOn: Binding(
                        get: { settingsManager.showBikeOptions },
                        set: { settingsManager.showBikeOptions = $0 }
                    ))
                }

                // Status Section
                Section("Status") {
                    HStack {
                        Text("Configuration")
                        Spacer()
                        if settingsManager.isConfigured {
                            Label("Complete", systemImage: "checkmark.circle.fill")
                                .foregroundColor(.green)
                        } else {
                            Label("Incomplete", systemImage: "exclamationmark.triangle.fill")
                                .foregroundColor(.orange)
                        }
                    }
                }
            }
            .navigationTitle("Settings")
            .alert("Geocoding Error", isPresented: .constant(geocodeError != nil)) {
                Button("OK") { geocodeError = nil }
            } message: {
                Text(geocodeError ?? "")
            }
        }
    }

    private func geocodeHome() {
        isGeocodingHome = true
        geocoder.geocodeAddressString(homeAddressInput) { placemarks, error in
            isGeocodingHome = false

            if let error = error {
                geocodeError = "Could not find address: \(error.localizedDescription)"
                return
            }

            guard let placemark = placemarks?.first,
                  let location = placemark.location else {
                geocodeError = "No location found for this address"
                return
            }

            settingsManager.homeLat = location.coordinate.latitude
            settingsManager.homeLng = location.coordinate.longitude
            settingsManager.homeAddress = homeAddressInput
        }
    }

    private func geocodeWork() {
        isGeocodingWork = true
        geocoder.geocodeAddressString(workAddressInput) { placemarks, error in
            isGeocodingWork = false

            if let error = error {
                geocodeError = "Could not find address: \(error.localizedDescription)"
                return
            }

            guard let placemark = placemarks?.first,
                  let location = placemark.location else {
                geocodeError = "No location found for this address"
                return
            }

            settingsManager.workLat = location.coordinate.latitude
            settingsManager.workLng = location.coordinate.longitude
            settingsManager.workAddress = workAddressInput
        }
    }
}

#Preview {
    SettingsView()
        .environmentObject(SettingsManager())
}
