import SwiftUI

struct ModeIcon: View {
    let mode: LegMode
    var size: CGFloat = 20

    var body: some View {
        Image(systemName: iconName)
            .font(.system(size: size))
            .foregroundColor(iconColor)
    }

    private var iconName: String {
        switch mode {
        case .bike: return "bicycle"
        case .walk: return "figure.walk"
        case .subway: return "tram.fill"
        }
    }

    private var iconColor: Color {
        switch mode {
        case .bike: return .green
        case .walk: return .orange
        case .subway: return .blue
        }
    }
}

#Preview {
    HStack(spacing: 16) {
        ModeIcon(mode: .bike)
        ModeIcon(mode: .walk)
        ModeIcon(mode: .subway)
    }
}
