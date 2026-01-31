import { config } from '../config.js';
import type { Weather } from '../types/index.js';
import NodeCache from 'node-cache';

const cache = new NodeCache({ stdTTL: config.cacheTtl.weather });

// Response from OpenWeatherMap Current Weather API 2.5 (Free tier)
interface OpenWeatherResponse {
  main: {
    temp: number;
  };
  weather: Array<{
    id: number;
    main: string;
    description: string;
  }>;
  rain?: { '1h'?: number; '3h'?: number };
  snow?: { '1h'?: number; '3h'?: number };
}

export async function getWeather(lat: number, lng: number): Promise<Weather> {
  const cacheKey = `weather_${lat.toFixed(2)}_${lng.toFixed(2)}`;
  const cached = cache.get<Weather>(cacheKey);
  if (cached) return cached;

  // If no API key, return default weather
  if (!config.openWeatherApiKey) {
    console.warn('No OpenWeather API key configured, using default weather');
    return getDefaultWeather();
  }

  // Using free 2.5 API instead of paid 3.0 One Call API
  const url = `https://api.openweathermap.org/data/2.5/weather?lat=${lat}&lon=${lng}&units=imperial&appid=${config.openWeatherApiKey}`;

  try {
    const response = await fetch(url);
    if (!response.ok) {
      console.error(`OpenWeather error: ${response.status}`);
      return getDefaultWeather();
    }

    const data = (await response.json()) as OpenWeatherResponse;
    const weather = parseWeatherResponse(data);

    cache.set(cacheKey, weather);
    return weather;
  } catch (error) {
    console.error('Failed to fetch weather:', error);
    return getDefaultWeather();
  }
}

function parseWeatherResponse(data: OpenWeatherResponse): Weather {
  const weatherId = data.weather[0]?.id || 800;
  const weatherMain = data.weather[0]?.main || 'Clear';

  // Determine precipitation type based on weather condition codes
  // https://openweathermap.org/weather-conditions
  let precipitationType: Weather['precipitation_type'] = 'none';
  if (weatherId >= 200 && weatherId < 600) {
    precipitationType = 'rain';
  } else if (weatherId >= 600 && weatherId < 700) {
    precipitationType = 'snow';
  } else if (weatherId >= 610 && weatherId < 620) {
    precipitationType = 'mix';
  }

  // Check if there's active rain or snow
  const hasRain = (data.rain?.['1h'] || data.rain?.['3h'] || 0) > 0;
  const hasSnow = (data.snow?.['1h'] || data.snow?.['3h'] || 0) > 0;

  // Determine if weather is bad for biking
  const isBad = precipitationType !== 'none' || hasRain || hasSnow;

  return {
    temp_f: Math.round(data.main.temp),
    conditions: weatherMain,
    precipitation_type: precipitationType,
    precipitation_probability: isBad ? 1 : 0,
    is_bad: isBad,
  };
}

function getDefaultWeather(): Weather {
  return {
    temp_f: 65,
    conditions: 'Unknown',
    precipitation_type: 'none',
    precipitation_probability: 0,
    is_bad: false,
  };
}
