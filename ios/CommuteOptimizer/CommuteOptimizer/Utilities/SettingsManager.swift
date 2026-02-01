import Foundation
import SwiftUI

class SettingsManager: ObservableObject {
    private let defaults: UserDefaults
    private let suiteName = "group.com.commuteoptimizer"

    // Keys
    private enum Keys {
        static let openWeatherApiKey = "openweather_api_key"
        static let googleApiKey = "google_api_key"
        static let workerUrl = "worker_url"
        static let homeLat = "home_lat"
        static let homeLng = "home_lng"
        static let homeAddress = "home_address"
        static let workLat = "work_lat"
        static let workLng = "work_lng"
        static let workAddress = "work_address"
        static let bikeStations = "bike_stations"
        static let liveStations = "live_stations"
        static let destinationStation = "destination_station"
        static let showBikeOptions = "show_bike_options"
    }

    init() {
        if let sharedDefaults = UserDefaults(suiteName: suiteName) {
            self.defaults = sharedDefaults
        } else {
            self.defaults = UserDefaults.standard
        }

        // Initialize with default values
        _openWeatherApiKey = ""
        _googleApiKey = ""
        _workerUrl = ""
        _homeLat = 0
        _homeLng = 0
        _homeAddress = ""
        _workLat = 0
        _workLng = 0
        _workAddress = ""
        _bikeStations = []
        _liveStations = []
        _destinationStation = "court-sq"
        _showBikeOptions = true
    }

    // MARK: - API Keys

    @Published private var _openWeatherApiKey: String
    var openWeatherApiKey: String {
        get { _openWeatherApiKey }
        set {
            _openWeatherApiKey = newValue
            defaults.set(newValue, forKey: Keys.openWeatherApiKey)
        }
    }

    @Published private var _googleApiKey: String
    var googleApiKey: String {
        get { _googleApiKey }
        set {
            _googleApiKey = newValue
            defaults.set(newValue, forKey: Keys.googleApiKey)
        }
    }

    @Published private var _workerUrl: String
    var workerUrl: String {
        get { _workerUrl }
        set {
            _workerUrl = newValue
            defaults.set(newValue, forKey: Keys.workerUrl)
        }
    }

    // MARK: - Home Location

    @Published private var _homeLat: Double
    var homeLat: Double {
        get { _homeLat }
        set {
            _homeLat = newValue
            defaults.set(newValue, forKey: Keys.homeLat)
        }
    }

    @Published private var _homeLng: Double
    var homeLng: Double {
        get { _homeLng }
        set {
            _homeLng = newValue
            defaults.set(newValue, forKey: Keys.homeLng)
        }
    }

    @Published private var _homeAddress: String
    var homeAddress: String {
        get { _homeAddress }
        set {
            _homeAddress = newValue
            defaults.set(newValue, forKey: Keys.homeAddress)
        }
    }

    // MARK: - Work Location

    @Published private var _workLat: Double
    var workLat: Double {
        get { _workLat }
        set {
            _workLat = newValue
            defaults.set(newValue, forKey: Keys.workLat)
        }
    }

    @Published private var _workLng: Double
    var workLng: Double {
        get { _workLng }
        set {
            _workLng = newValue
            defaults.set(newValue, forKey: Keys.workLng)
        }
    }

    @Published private var _workAddress: String
    var workAddress: String {
        get { _workAddress }
        set {
            _workAddress = newValue
            defaults.set(newValue, forKey: Keys.workAddress)
        }
    }

    // MARK: - Stations

    @Published private var _bikeStations: [String]
    var bikeStations: [String] {
        get { _bikeStations }
        set {
            _bikeStations = newValue
            defaults.set(newValue, forKey: Keys.bikeStations)
        }
    }

    @Published private var _liveStations: [String]
    var liveStations: [String] {
        get { _liveStations }
        set {
            // Enforce max 3 stations
            _liveStations = Array(newValue.prefix(3))
            defaults.set(_liveStations, forKey: Keys.liveStations)
        }
    }

    @Published private var _destinationStation: String
    var destinationStation: String {
        get { _destinationStation }
        set {
            _destinationStation = newValue
            defaults.set(newValue, forKey: Keys.destinationStation)
        }
    }

    // MARK: - Preferences

    @Published private var _showBikeOptions: Bool
    var showBikeOptions: Bool {
        get { _showBikeOptions }
        set {
            _showBikeOptions = newValue
            defaults.set(newValue, forKey: Keys.showBikeOptions)
        }
    }

    // MARK: - Initialization

    func loadFromDefaults() {
        _openWeatherApiKey = defaults.string(forKey: Keys.openWeatherApiKey) ?? ""
        _googleApiKey = defaults.string(forKey: Keys.googleApiKey) ?? ""
        _workerUrl = defaults.string(forKey: Keys.workerUrl) ?? ""
        _homeLat = defaults.double(forKey: Keys.homeLat)
        _homeLng = defaults.double(forKey: Keys.homeLng)
        _homeAddress = defaults.string(forKey: Keys.homeAddress) ?? ""
        _workLat = defaults.double(forKey: Keys.workLat)
        _workLng = defaults.double(forKey: Keys.workLng)
        _workAddress = defaults.string(forKey: Keys.workAddress) ?? ""
        _bikeStations = defaults.stringArray(forKey: Keys.bikeStations) ?? []
        _liveStations = defaults.stringArray(forKey: Keys.liveStations) ?? []
        _destinationStation = defaults.string(forKey: Keys.destinationStation) ?? "court-sq"
        _showBikeOptions = defaults.object(forKey: Keys.showBikeOptions) == nil ? true : defaults.bool(forKey: Keys.showBikeOptions)
    }

    // MARK: - Validation

    var isConfigured: Bool {
        !googleApiKey.isEmpty &&
        homeLat != 0 && homeLng != 0 &&
        workLat != 0 && workLng != 0 &&
        !bikeStations.isEmpty
    }

    // MARK: - Per-Widget Settings

    func getWidgetOriginName(_ widgetId: String) -> String {
        defaults.string(forKey: "widget_origin_name_\(widgetId)")
            ?? (homeAddress.isEmpty ? "Home" : homeAddress)
    }

    func getWidgetOriginLat(_ widgetId: String) -> Double {
        let lat = defaults.object(forKey: "widget_origin_lat_\(widgetId)") as? Double
        return lat ?? homeLat
    }

    func getWidgetOriginLng(_ widgetId: String) -> Double {
        let lng = defaults.object(forKey: "widget_origin_lng_\(widgetId)") as? Double
        return lng ?? homeLng
    }

    func setWidgetOrigin(_ widgetId: String, name: String, lat: Double, lng: Double) {
        defaults.set(name, forKey: "widget_origin_name_\(widgetId)")
        defaults.set(lat, forKey: "widget_origin_lat_\(widgetId)")
        defaults.set(lng, forKey: "widget_origin_lng_\(widgetId)")
    }

    func hasWidgetOrigin(_ widgetId: String) -> Bool {
        defaults.object(forKey: "widget_origin_lat_\(widgetId)") != nil
    }

    func getWidgetDestinationName(_ widgetId: String) -> String {
        defaults.string(forKey: "widget_dest_name_\(widgetId)")
            ?? (workAddress.isEmpty ? "Work" : workAddress)
    }

    func getWidgetDestinationLat(_ widgetId: String) -> Double {
        let lat = defaults.object(forKey: "widget_dest_lat_\(widgetId)") as? Double
        return lat ?? workLat
    }

    func getWidgetDestinationLng(_ widgetId: String) -> Double {
        let lng = defaults.object(forKey: "widget_dest_lng_\(widgetId)") as? Double
        return lng ?? workLng
    }

    func setWidgetDestination(_ widgetId: String, name: String, lat: Double, lng: Double) {
        defaults.set(name, forKey: "widget_dest_name_\(widgetId)")
        defaults.set(lat, forKey: "widget_dest_lat_\(widgetId)")
        defaults.set(lng, forKey: "widget_dest_lng_\(widgetId)")
    }

    func hasWidgetDestination(_ widgetId: String) -> Bool {
        defaults.object(forKey: "widget_dest_lat_\(widgetId)") != nil
    }

    func getLiveTrainsWidgetStation(_ widgetId: String) -> String? {
        defaults.string(forKey: "live_trains_station_\(widgetId)")
    }

    func setLiveTrainsWidgetStation(_ widgetId: String, stationId: String) {
        defaults.set(stationId, forKey: "live_trains_station_\(widgetId)")
    }

    func clearWidgetData(_ widgetId: String) {
        let keys = [
            "widget_origin_name_\(widgetId)",
            "widget_origin_lat_\(widgetId)",
            "widget_origin_lng_\(widgetId)",
            "widget_dest_name_\(widgetId)",
            "widget_dest_lat_\(widgetId)",
            "widget_dest_lng_\(widgetId)",
            "live_trains_station_\(widgetId)"
        ]
        keys.forEach { defaults.removeObject(forKey: $0) }
    }
}
