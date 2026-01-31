import SwiftUI

struct MtaColors {
    // Official MTA line colors
    static let lineColors: [String: Color] = [
        // Red lines (1, 2, 3)
        "1": Color(hex: "#EE352E"),
        "2": Color(hex: "#EE352E"),
        "3": Color(hex: "#EE352E"),

        // Green lines (4, 5, 6)
        "4": Color(hex: "#00933C"),
        "5": Color(hex: "#00933C"),
        "6": Color(hex: "#00933C"),

        // Purple line (7)
        "7": Color(hex: "#B933AD"),

        // Blue lines (A, C, E)
        "A": Color(hex: "#0039A6"),
        "C": Color(hex: "#0039A6"),
        "E": Color(hex: "#0039A6"),

        // Orange lines (B, D, F, M)
        "B": Color(hex: "#FF6319"),
        "D": Color(hex: "#FF6319"),
        "F": Color(hex: "#FF6319"),
        "M": Color(hex: "#FF6319"),

        // Lime green line (G)
        "G": Color(hex: "#6CBE45"),

        // Brown lines (J, Z)
        "J": Color(hex: "#996633"),
        "Z": Color(hex: "#996633"),

        // Gray line (L)
        "L": Color(hex: "#A7A9AC"),

        // Yellow lines (N, Q, R, W)
        "N": Color(hex: "#FCCC0A"),
        "Q": Color(hex: "#FCCC0A"),
        "R": Color(hex: "#FCCC0A"),
        "W": Color(hex: "#FCCC0A"),

        // Shuttle (S)
        "S": Color(hex: "#808183")
    ]

    static func color(for line: String) -> Color {
        // Normalize express variants (6X -> 6, 7X -> 7, FX -> F)
        let normalizedLine = cleanExpressLine(line)
        return lineColors[normalizedLine.uppercased()] ?? Color(hex: "#808183")
    }

    static func textColor(for line: String) -> Color {
        // Yellow lines need dark text
        let normalizedLine = cleanExpressLine(line)
        switch normalizedLine.uppercased() {
        case "N", "Q", "R", "W": return .black
        default: return .white
        }
    }

    /// Normalize express train variants to base line
    static func cleanExpressLine(_ line: String) -> String {
        // Express variants: 6X->6, 7X->7, FX->F, etc.
        if line.count == 2 && line.hasSuffix("X") {
            return String(line.dropLast())
        }
        return line
    }

    static func weatherEmoji(_ conditions: String, precipType: PrecipitationType) -> String {
        switch precipType {
        case .rain: return "rain"
        case .snow: return "snow"
        case .mix: return "cloud.sleet"
        case .none:
            if conditions.lowercased().contains("cloud") { return "cloud" }
            if conditions.lowercased().contains("sun") || conditions.lowercased().contains("clear") { return "sun.max" }
            return "questionmark"
        }
    }

    // Rank badge colors
    static let goldBadge = Color(hex: "#FFD700")
    static let silverBadge = Color(hex: "#C0C0C0")
    static let bronzeBadge = Color(hex: "#CD7F32")

    static func rankColor(for rank: Int) -> Color {
        switch rank {
        case 1: return goldBadge
        case 2: return silverBadge
        case 3: return bronzeBadge
        default: return Color.gray
        }
    }

    /// Badge color for arrivals - green for <=2 min, line color otherwise
    static func arrivalBadgeColor(minutesAway: Int, line: String) -> Color {
        minutesAway <= 2 ? .green : color(for: line)
    }
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r, g, b: Double
        r = Double((int >> 16) & 0xFF) / 255.0
        g = Double((int >> 8) & 0xFF) / 255.0
        b = Double(int & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}
