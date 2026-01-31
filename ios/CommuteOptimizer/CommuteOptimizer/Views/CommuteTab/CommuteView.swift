import SwiftUI

struct CommuteView: View {
    @EnvironmentObject var settingsManager: SettingsManager
    @State private var commuteResponse: CommuteResponse?
    @State private var isLoading = false
    @State private var errorMessage: String?

    private let calculator = CommuteCalculator()

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if let response = commuteResponse {
                    WeatherHeaderView(weather: response.weather)

                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(response.options) { option in
                                CommuteOptionCard(option: option)
                            }
                        }
                        .padding()
                    }
                    .refreshable {
                        await loadCommute()
                    }

                    // Timestamp footer
                    Text("Updated: \(formatTimestamp(response.generatedAt))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.bottom, 8)
                } else if isLoading {
                    Spacer()
                    ProgressView("Loading commute options...")
                    Spacer()
                } else if let error = errorMessage {
                    Spacer()
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundColor(.orange)
                        Text(error)
                            .multilineTextAlignment(.center)
                        Button("Retry") {
                            Task { await loadCommute() }
                        }
                        .buttonStyle(.bordered)
                    }
                    .padding()
                    Spacer()
                } else if !settingsManager.isConfigured {
                    Spacer()
                    VStack(spacing: 16) {
                        Image(systemName: "gear")
                            .font(.largeTitle)
                            .foregroundColor(.secondary)
                        Text("Please configure your settings")
                            .font(.headline)
                        Text("Set your home address, work address, and API key in Settings tab")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding()
                    Spacer()
                } else {
                    Spacer()
                    Button("Load Commute Options") {
                        Task { await loadCommute() }
                    }
                    .buttonStyle(.borderedProminent)
                    Spacer()
                }
            }
            .navigationTitle("Commute")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        Task { await loadCommute() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .disabled(isLoading || !settingsManager.isConfigured)
                }
            }
            .task {
                if settingsManager.isConfigured && commuteResponse == nil {
                    await loadCommute()
                }
            }
        }
    }

    private func loadCommute() async {
        guard settingsManager.isConfigured else {
            errorMessage = "Please configure settings first"
            return
        }

        isLoading = true
        errorMessage = nil

        do {
            commuteResponse = try await calculator.calculateCommute(settings: settingsManager)
        } catch {
            errorMessage = "Failed to load commute: \(error.localizedDescription)"
        }

        isLoading = false
    }

    private func formatTimestamp(_ isoString: String) -> String {
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: isoString) else {
            return isoString
        }

        let displayFormatter = DateFormatter()
        displayFormatter.dateFormat = "h:mm a"
        return displayFormatter.string(from: date)
    }
}

#Preview {
    CommuteView()
        .environmentObject(SettingsManager())
}
