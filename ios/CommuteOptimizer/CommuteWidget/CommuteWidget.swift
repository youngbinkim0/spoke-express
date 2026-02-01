import WidgetKit
import SwiftUI

struct CommuteWidget: Widget {
    let kind: String = "CommuteWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: CommuteWidgetProvider()) { entry in
            CommuteWidgetEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
                .widgetURL(URL(string: "widget://configure?widgetId=commute-default"))
        }
        .configurationDisplayName("Commute Options")
        .description("Shows your top 3 commute options with weather and arrival times.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct CommuteEntry: TimelineEntry {
    let date: Date
    let options: [CommuteOption]
    let weather: Weather?
    let isLoading: Bool
    let errorMessage: String?

    static let placeholder = CommuteEntry(
        date: Date(),
        options: [],
        weather: Weather(tempF: 72, conditions: "Clear", precipitationType: .none, precipitationProbability: 0, isBad: false),
        isLoading: false,
        errorMessage: nil
    )

    static let loading = CommuteEntry(
        date: Date(),
        options: [],
        weather: nil,
        isLoading: true,
        errorMessage: nil
    )

    static func error(_ message: String) -> CommuteEntry {
        CommuteEntry(
            date: Date(),
            options: [],
            weather: nil,
            isLoading: false,
            errorMessage: message
        )
    }
}

#Preview(as: .systemMedium) {
    CommuteWidget()
} timeline: {
    CommuteEntry.placeholder
    CommuteEntry.loading
    CommuteEntry.error("Not configured")
}
