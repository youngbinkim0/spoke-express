import SwiftUI

struct CommuteView: View {
    @EnvironmentObject var settingsManager: SettingsManager
    @State private var commuteResponse: CommuteResponse?
    @State private var isLoading = false
    @State private var errorMessage: String?

    private let calculator = CommuteCalculator()
    private let refreshTimer = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if let response = commuteResponse {
                    WeatherHeaderView(weather: response.weather)

                    // Service Alerts Section
                    if !response.alerts.isEmpty {
                        AlertsSection(alerts: response.alerts)
                    }

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
            .onReceive(refreshTimer) { _ in
                // Auto-refresh every 30 seconds like Android
                if settingsManager.isConfigured && !isLoading {
                    Task { await loadCommute() }
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

// MARK: - Alerts Section

struct AlertsSection: View {
    let alerts: [ServiceAlert]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(alerts) { alert in
                AlertRow(alert: alert)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
}

struct AlertRow: View {
    let alert: ServiceAlert

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: alertIcon)
                .foregroundColor(alertColor)
                .font(.system(size: 14))

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    ForEach(alert.routeIds.prefix(4), id: \.self) { routeId in
                        LineBadge(line: routeId, size: 18)
                    }
                }

                Text(alert.headerText)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }

            Spacer()
        }
        .padding(8)
        .background(alertColor.opacity(0.1))
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(alertColor.opacity(0.3), lineWidth: 1)
        )
    }

    private var alertIcon: String {
        switch alert.effect {
        case "NO_SERVICE": return "xmark.circle.fill"
        case "SIGNIFICANT_DELAYS": return "clock.badge.exclamationmark.fill"
        default: return "exclamationmark.triangle.fill"
        }
    }

    private var alertColor: Color {
        switch alert.effect {
        case "NO_SERVICE": return .red
        case "SIGNIFICANT_DELAYS": return .orange
        default: return .yellow
        }
    }
}

#Preview {
    CommuteView()
        .environmentObject(SettingsManager())
}
