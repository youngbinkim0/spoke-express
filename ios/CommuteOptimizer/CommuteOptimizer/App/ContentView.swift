import SwiftUI

struct ContentView: View {
    @EnvironmentObject var settingsManager: SettingsManager

    var body: some View {
        TabView {
            CommuteView()
                .tabItem {
                    Label("Commute", systemImage: "tram.fill")
                }

            SearchView()
                .tabItem {
                    Label("Search", systemImage: "magnifyingglass")
                }

            LiveTrainsView()
                .tabItem {
                    Label("Live Trains", systemImage: "clock.fill")
                }

            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gear")
                }
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(SettingsManager())
}
