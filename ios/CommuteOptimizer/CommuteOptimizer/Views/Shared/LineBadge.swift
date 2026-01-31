import SwiftUI

struct LineBadge: View {
    let line: String
    var size: CGFloat = 24

    var body: some View {
        ZStack {
            Circle()
                .fill(MtaColors.color(for: line))
                .frame(width: size, height: size)

            Text(MtaColors.cleanExpressLine(line))
                .font(.system(size: size * 0.55, weight: .bold))
                .foregroundColor(MtaColors.textColor(for: line))
        }
    }
}

struct LineBadgeRow: View {
    let lines: [String]
    var size: CGFloat = 24

    var body: some View {
        HStack(spacing: 4) {
            ForEach(lines, id: \.self) { line in
                LineBadge(line: line, size: size)
            }
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        LineBadge(line: "G")
        LineBadge(line: "A")
        LineBadge(line: "N")
        LineBadge(line: "1")
        LineBadgeRow(lines: ["G", "7", "E", "M"])
    }
}
