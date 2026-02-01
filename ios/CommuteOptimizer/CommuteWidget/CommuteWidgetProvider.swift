import WidgetKit
import SwiftUI

struct CommuteWidgetProvider: TimelineProvider {
    private let calculator = CommuteCalculator()
    private let settingsManager = SettingsManager()

    // Default widget ID for StaticConfiguration widgets
    private let defaultWidgetId = "commute-default"

    func placeholder(in context: Context) -> CommuteEntry {
        CommuteEntry.placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (CommuteEntry) -> Void) {
        if context.isPreview {
            completion(CommuteEntry.placeholder)
            return
        }

        settingsManager.loadFromDefaults()

        if !settingsManager.isConfigured {
            completion(CommuteEntry.error("Open app to configure"))
            return
        }

        // Get per-widget origin/destination (falls back to home/work)
        let originLat = settingsManager.getWidgetOriginLat(defaultWidgetId)
        let originLng = settingsManager.getWidgetOriginLng(defaultWidgetId)
        let destLat = settingsManager.getWidgetDestinationLat(defaultWidgetId)
        let destLng = settingsManager.getWidgetDestinationLng(defaultWidgetId)

        Task {
            do {
                let response = try await calculator.calculateCommute(
                    settings: settingsManager,
                    originLat: originLat,
                    originLng: originLng,
                    destLat: destLat,
                    destLng: destLng
                )
                let entry = CommuteEntry(
                    date: Date(),
                    options: response.options,
                    weather: response.weather,
                    isLoading: false,
                    errorMessage: nil
                )
                completion(entry)
            } catch {
                completion(CommuteEntry.error("Failed to load"))
            }
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CommuteEntry>) -> Void) {
        settingsManager.loadFromDefaults()

        if !settingsManager.isConfigured {
            let entry = CommuteEntry.error("Open app to configure")
            let timeline = Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(60 * 60)))
            completion(timeline)
            return
        }

        // Get per-widget origin/destination (falls back to home/work)
        let originLat = settingsManager.getWidgetOriginLat(defaultWidgetId)
        let originLng = settingsManager.getWidgetOriginLng(defaultWidgetId)
        let destLat = settingsManager.getWidgetDestinationLat(defaultWidgetId)
        let destLng = settingsManager.getWidgetDestinationLng(defaultWidgetId)

        Task {
            do {
                let response = try await calculator.calculateCommute(
                    settings: settingsManager,
                    originLat: originLat,
                    originLng: originLng,
                    destLat: destLat,
                    destLng: destLng
                )
                let entry = CommuteEntry(
                    date: Date(),
                    options: response.options,
                    weather: response.weather,
                    isLoading: false,
                    errorMessage: nil
                )

                // Refresh every 15 minutes
                let nextUpdate = Date().addingTimeInterval(15 * 60)
                let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
                completion(timeline)
            } catch {
                let entry = CommuteEntry.error("Failed to load")
                let nextUpdate = Date().addingTimeInterval(5 * 60)  // Retry sooner on error
                let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
                completion(timeline)
            }
        }
    }
}
