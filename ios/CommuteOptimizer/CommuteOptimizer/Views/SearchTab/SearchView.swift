import SwiftUI
import CoreLocation

struct SearchView: View {
    @EnvironmentObject var settingsManager: SettingsManager

    @State private var fromAddress = ""
    @State private var toAddress = ""
    @State private var fromCoord: CLLocationCoordinate2D?
    @State private var toCoord: CLLocationCoordinate2D?
    @State private var isGeocodingFrom = false
    @State private var isGeocodingTo = false
    @State private var fromError: String?
    @State private var toError: String?
    @State private var searchResponse: CommuteResponse?
    @State private var isSearching = false
    @State private var searchError: String?
    @State private var recentSearches: [SettingsManager.RecentSearch] = []

    private let fromGeocoder = CLGeocoder()
    private let toGeocoder = CLGeocoder()
    private let calculator = CommuteCalculator()

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    // Recent searches chips
                    if !recentSearches.isEmpty {
                        recentSearchesSection
                    }

                    // Search form
                    searchFormSection

                    // Results
                    if let response = searchResponse {
                        WeatherHeaderView(weather: response.weather)
                        if !response.alerts.isEmpty {
                            AlertsSection(alerts: response.alerts)
                        }
                        LazyVStack(spacing: 12) {
                            ForEach(response.options) { option in
                                CommuteOptionCard(option: option)
                            }
                        }
                    } else if isSearching {
                        ProgressView("Finding routes...")
                            .padding()
                    } else if let error = searchError {
                        VStack(spacing: 12) {
                            Image(systemName: "exclamationmark.triangle")
                                .font(.largeTitle)
                                .foregroundColor(.orange)
                            Text(error)
                                .multilineTextAlignment(.center)
                                .foregroundColor(.secondary)
                        }
                        .padding()
                    }
                }
                .padding()
            }
            .navigationTitle("Search Routes")
            .onAppear {
                recentSearches = settingsManager.getRecentSearches()
                // Prefill from/to with saved home/work addresses if fields are empty
                if fromAddress.isEmpty, !settingsManager.homeAddress.isEmpty {
                    fromAddress = settingsManager.homeAddress
                    if settingsManager.homeLat != 0 && settingsManager.homeLng != 0 {
                        fromCoord = CLLocationCoordinate2D(
                            latitude: settingsManager.homeLat,
                            longitude: settingsManager.homeLng
                        )
                    }
                }
                if toAddress.isEmpty, !settingsManager.workAddress.isEmpty {
                    toAddress = settingsManager.workAddress
                    if settingsManager.workLat != 0 && settingsManager.workLng != 0 {
                        toCoord = CLLocationCoordinate2D(
                            latitude: settingsManager.workLat,
                            longitude: settingsManager.workLng
                        )
                    }
                }
            }
        }
    }

    // MARK: - Recent Searches Section

    private var recentSearchesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Recent searches")
                .font(.caption)
                .foregroundColor(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(recentSearches.indices, id: \.self) { i in
                        let r = recentSearches[i]
                        Button {
                            loadRecent(r)
                        } label: {
                            Text("\(r.fromAddress.components(separatedBy: ",").first ?? r.fromAddress) → \(r.toAddress.components(separatedBy: ",").first ?? r.toAddress)")
                                .font(.caption)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(Color(.systemGray6))
                                .cornerRadius(20)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - Search Form Section

    private var searchFormSection: some View {
        VStack(spacing: 12) {
            // From field
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    TextField("From (e.g. 123 Main St, Brooklyn, NY)", text: $fromAddress)
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()
                        .onSubmit { geocodeFrom() }
                    if isGeocodingFrom {
                        ProgressView().scaleEffect(0.8)
                    } else {
                        Button("📍") { geocodeFrom() }
                            .buttonStyle(.bordered)
                            .disabled(fromAddress.isEmpty)
                    }
                }
                if let error = fromError {
                    Text(error).font(.caption).foregroundColor(.red)
                } else if let coord = fromCoord {
                    Text("✓ \(coord.latitude, specifier: "%.4f"), \(coord.longitude, specifier: "%.4f")")
                        .font(.caption).foregroundColor(.green)
                }
            }

            // To field
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    TextField("To (e.g. Times Square, NY)", text: $toAddress)
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()
                        .onSubmit { geocodeTo() }
                    if isGeocodingTo {
                        ProgressView().scaleEffect(0.8)
                    } else {
                        Button("📍") { geocodeTo() }
                            .buttonStyle(.bordered)
                            .disabled(toAddress.isEmpty)
                    }
                }
                if let error = toError {
                    Text(error).font(.caption).foregroundColor(.red)
                } else if let coord = toCoord {
                    Text("✓ \(coord.latitude, specifier: "%.4f"), \(coord.longitude, specifier: "%.4f")")
                        .font(.caption).foregroundColor(.green)
                }
            }

            // Search button
            Button {
                Task { await performSearch() }
            } label: {
                HStack {
                    if isSearching {
                        ProgressView().scaleEffect(0.8)
                    }
                    Text(isSearching ? "Searching..." : "Search Routes")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(fromCoord == nil || toCoord == nil || isSearching || settingsManager.googleApiKey.isEmpty)

            if settingsManager.googleApiKey.isEmpty {
                Text("A Google API key is required. Add it in Settings.")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }

    // MARK: - Geocoding

    private func geocodeFrom() {
        isGeocodingFrom = true
        fromError = nil
        fromCoord = nil
        fromGeocoder.geocodeAddressString(fromAddress) { placemarks, error in
            DispatchQueue.main.async {
                isGeocodingFrom = false
                if let error = error {
                    fromError = "Not found: \(error.localizedDescription)"
                    return
                }
                guard let placemark = placemarks?.first, let location = placemark.location else {
                    fromError = "No results found"
                    return
                }
                fromCoord = location.coordinate
                fromAddress = [placemark.name, placemark.locality, placemark.administrativeArea]
                    .compactMap { $0 }
                    .joined(separator: ", ")
            }
        }
    }

    private func geocodeTo() {
        isGeocodingTo = true
        toError = nil
        toCoord = nil
        toGeocoder.geocodeAddressString(toAddress) { placemarks, error in
            DispatchQueue.main.async {
                isGeocodingTo = false
                if let error = error {
                    toError = "Not found: \(error.localizedDescription)"
                    return
                }
                guard let placemark = placemarks?.first, let location = placemark.location else {
                    toError = "No results found"
                    return
                }
                toCoord = location.coordinate
                toAddress = [placemark.name, placemark.locality, placemark.administrativeArea]
                    .compactMap { $0 }
                    .joined(separator: ", ")
            }
        }
    }

    // MARK: - Search

    @MainActor
    private func performSearch() async {
        guard let from = fromCoord, let to = toCoord else { return }
        guard !settingsManager.googleApiKey.isEmpty else { return }

        isSearching = true
        searchError = nil
        searchResponse = nil

        do {
            searchResponse = try await calculator.calculateCommute(
                settings: settingsManager,
                originLat: from.latitude,
                originLng: from.longitude,
                destLat: to.latitude,
                destLng: to.longitude
            )
            // Save to recent searches
            let recent = SettingsManager.RecentSearch(
                fromAddress: fromAddress,
                fromLat: from.latitude,
                fromLng: from.longitude,
                toAddress: toAddress,
                toLat: to.latitude,
                toLng: to.longitude,
                timestamp: Date()
            )
            settingsManager.addRecentSearch(recent)
            recentSearches = settingsManager.getRecentSearches()
        } catch {
            searchError = "Search failed: \(error.localizedDescription)"
        }

        isSearching = false
    }

    // MARK: - Load Recent

    private func loadRecent(_ r: SettingsManager.RecentSearch) {
        fromAddress = r.fromAddress
        toAddress = r.toAddress
        fromCoord = CLLocationCoordinate2D(latitude: r.fromLat, longitude: r.fromLng)
        toCoord = CLLocationCoordinate2D(latitude: r.toLat, longitude: r.toLng)
        fromError = nil
        toError = nil
        Task { await performSearch() }
    }
}

#Preview {
    SearchView()
        .environmentObject(SettingsManager())
}
