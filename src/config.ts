import 'dotenv/config';

export const config = {
  port: parseInt(process.env.PORT || '8888', 10),
  hostname: process.env.HOST || '0.0.0.0',
  transiterUrl: process.env.TRANSITER_URL || 'http://localhost:8080',
  googleMapsApiKey: process.env.GOOGLE_MAPS_API_KEY || '',
  openWeatherApiKey: process.env.OPENWEATHER_API_KEY || '',

  // NYC subway system ID in Transiter
  transitSystem: 'us-ny-subway',

  // Walking speed in mph (average walking pace)
  walkingSpeedMph: 3.0,

  // Biking speed in mph (average city biking)
  bikingSpeedMph: 10.0,

  // Cache TTL in seconds
  cacheTtl: {
    weather: 600,      // 10 minutes
    arrivals: 30,      // 30 seconds
    bikeTime: 3600,    // 1 hour (bike routes don't change often)
  },
};
