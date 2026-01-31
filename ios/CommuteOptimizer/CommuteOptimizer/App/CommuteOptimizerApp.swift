import SwiftUI

@main
struct CommuteOptimizerApp: App {
    @StateObject private var settingsManager = SettingsManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(settingsManager)
                .onAppear {
                    settingsManager.loadFromDefaults()
                }
        }
    }
}
