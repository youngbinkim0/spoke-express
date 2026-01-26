import { config } from '../config.js';
import type { Weather } from '../types/index.js';
import NodeCache from 'node-cache';

const cache = new NodeCache({ stdTTL: config.cacheTtl.weather });

interface OpenWeatherResponse {
  current: {
    temp: number;
    weather: Array<{
      id: number;
      main: string;
      description: string;
    }>;
  };
  hourly: Array<{
    pop: number; // Probability of precipitation
    rain?: { '1h': number };
    snow?: { '1h': number };
  }>;
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

  const url = `https://api.openweathermap.org/data/3.0/onecall?lat=${lat}&lon=${lng}&units=imperial&exclude=minutely,daily,alerts&appid=${config.openWeatherApiKey}`;

  try {
    const response = await fetch(url);
    if (!response.ok) {
      console.error(`OpenWeather error: ${response.status}`);
      return getDefaultWeather();
    }

    const data: OpenWeatherResponse = await response.json();
    const weather = parseWeatherResponse(data);

    cache.set(cacheKey, weather);
    return weather;
  } catch (error) {
    console.error('Failed to fetch weather:', error);
    return getDefaultWeather();
  }
}

function parseWeatherResponse(data: OpenWeatherResponse): Weather {
  const current = data.current;
  const weatherId = current.weather[0]?.id || 800;
  const weatherMain = current.weather[0]?.main || 'Clear';

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

  // Get precipitation probability from next hour
  const precipProbability = data.hourly[0]?.pop || 0;

  // Determine if weather is bad for biking
  const isBad = precipitationType !== 'none' || precipProbability > 0.5;

  return {
    temp_f: Math.round(current.temp),
    conditions: weatherMain,
    precipitation_type: precipitationType,
    precipitation_probability: precipProbability,
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
