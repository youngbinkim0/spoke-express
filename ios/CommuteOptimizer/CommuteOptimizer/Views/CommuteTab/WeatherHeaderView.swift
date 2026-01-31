import SwiftUI

struct WeatherHeaderView: View {
    let weather: Weather

    var body: some View {
        HStack {
            // Weather icon
            Image(systemName: MtaColors.weatherEmoji(weather.conditions, precipType: weather.precipitationType))
                .font(.title2)
                .foregroundColor(weather.isBad ? .blue : .yellow)

            VStack(alignment: .leading, spacing: 2) {
                Text("\(weather.tempF)")
                    .font(.title)
                    .fontWeight(.bold)

                Text(weather.conditions)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }

            Spacer()

            // Bad weather indicator
            if weather.isBad {
                HStack(spacing: 4) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                    Text("Bike demoted")
                        .font(.caption)
                        .foregroundColor(.orange)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.orange.opacity(0.1))
                .cornerRadius(8)
            }
        }
        .padding()
        .background(Color(.systemBackground))
    }
}

#Preview {
    VStack {
        WeatherHeaderView(weather: Weather(
            tempF: 72,
            conditions: "Clear",
            precipitationType: .none,
            precipitationProbability: 0,
            isBad: false
        ))

        WeatherHeaderView(weather: Weather(
            tempF: 55,
            conditions: "Rain",
            precipitationType: .rain,
            precipitationProbability: 80,
            isBad: true
        ))
    }
}
