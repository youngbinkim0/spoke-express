import SwiftUI

@main
struct CommuteOptimizerApp: App {
    @StateObject private var settingsManager = SettingsManager()
    @State private var widgetConfigId: String?
    @State private var showWidgetConfig = false

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(settingsManager)
                .onAppear {
                    settingsManager.loadFromDefaults()
                }
                .onOpenURL { url in
                    handleDeepLink(url)
                }
                .sheet(isPresented: $showWidgetConfig) {
                    WidgetConfigView(widgetId: widgetConfigId)
                        .environmentObject(settingsManager)
                }
        }
    }

    private func handleDeepLink(_ url: URL) {
        // Handle widget://configure?widgetId=xxx
        guard url.scheme == "widget",
              url.host == "configure" else { return }

        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        widgetConfigId = components?.queryItems?.first(where: { $0.name == "widgetId" })?.value

        showWidgetConfig = true
    }
}
