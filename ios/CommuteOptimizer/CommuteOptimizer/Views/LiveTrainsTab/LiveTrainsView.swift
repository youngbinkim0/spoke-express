import SwiftUI

struct LiveTrainsView: View {
    @EnvironmentObject var settingsManager: SettingsManager
    @State private var stationArrivals: [String: [ArrivalGroup]] = [:]
    @State private var isLoading = false
    @State private var errorMessage: String?

    private let mtaService = MtaApiService()
    private let stationsDataSource = StationsDataSource.shared
    private let refreshTimer = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationView {
            Group {
                if settingsManager.liveStations.isEmpty {
                    emptyStateView
                } else if isLoading && stationArrivals.isEmpty {
                    ProgressView("Loading arrivals...")
                } else if let error = errorMessage {
                    errorView(error)
                } else {
                    stationsList
                }
            }
            .navigationTitle("Live Trains")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        Task { await loadArrivals() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .disabled(isLoading || settingsManager.liveStations.isEmpty)
                }
            }
            .task {
                if !settingsManager.liveStations.isEmpty {
                    await loadArrivals()
                }
            }
            .onReceive(refreshTimer) { _ in
                // Auto-refresh every 30 seconds like Android
                if !settingsManager.liveStations.isEmpty && !isLoading {
                    Task { await loadArrivals() }
                }
            }
        }
    }

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "tram")
                .font(.largeTitle)
                .foregroundColor(.secondary)
            Text("No Stations Selected")
                .font(.headline)
            Text("Select up to 3 stations in Settings to see live arrivals")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }

    private func errorView(_ message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.largeTitle)
                .foregroundColor(.orange)
            Text(message)
                .multilineTextAlignment(.center)
            Button("Retry") {
                Task { await loadArrivals() }
            }
            .buttonStyle(.bordered)
        }
        .padding()
    }

    private var stationsList: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                ForEach(settingsManager.liveStations, id: \.self) { stationId in
                    if let station = stationsDataSource.getStation(id: stationId) {
                        StationCard(
                            station: station,
                            arrivalGroups: stationArrivals[stationId] ?? []
                        )
                    }
                }
            }
            .padding()
        }
        .refreshable {
            await loadArrivals()
        }
    }

    private func loadArrivals() async {
        isLoading = true
        errorMessage = nil

        var newArrivals: [String: [ArrivalGroup]] = [:]

        for stationId in settingsManager.liveStations {
            guard let station = stationsDataSource.getStation(id: stationId) else { continue }

            do {
                let groups = try await mtaService.getGroupedArrivals(
                    stationId: station.mtaId,
                    lines: station.lines
                )
                newArrivals[stationId] = groups
            } catch {
                // Continue with other stations even if one fails
                newArrivals[stationId] = []
            }
        }

        stationArrivals = newArrivals
        isLoading = false
    }
}

#Preview {
    LiveTrainsView()
        .environmentObject(SettingsManager())
}
