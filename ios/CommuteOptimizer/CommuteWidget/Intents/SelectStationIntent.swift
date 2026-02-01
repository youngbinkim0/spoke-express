import AppIntents
import WidgetKit

/// Configuration intent for the Live Trains widget
struct LiveTrainsConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Select Station"
    static var description = IntentDescription("Choose a station to display arrivals for.")

    @Parameter(title: "Station")
    var station: StationEntity?

    init() {}

    init(station: StationEntity?) {
        self.station = station
    }
}
