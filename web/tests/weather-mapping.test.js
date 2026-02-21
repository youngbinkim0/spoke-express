import { describe, it, expect } from 'vitest';
import { mapGoogleWeatherToIsBad as isBadWeather } from '../src/weatherMapper.js';

describe('Weather Mapping (Google Conditions -> isBad)', () => {
  it('maps CLEAR to isBad=false', () => {
    expect(isBadWeather('CLEAR')).toBe(false);
  });

  it('maps RAIN to isBad=true', () => {
    expect(isBadWeather('RAIN')).toBe(true);
  });

  it('maps SNOW to isBad=true', () => {
    expect(isBadWeather('SNOW')).toBe(true);
  });

  it('maps STORM to isBad=true', () => {
    expect(isBadWeather('STORM')).toBe(true);
  });

  it('maps FOG to isBad=true', () => {
    expect(isBadWeather('FOG')).toBe(true);
  });

  it('maps WINDY to isBad=true', () => {
    expect(isBadWeather('WINDY')).toBe(true);
  });

  it('provides safe fallback for unknown conditions (isBad=false)', () => {
    expect(isBadWeather('UNKNOWN_CONDITION')).toBe(false);
    expect(isBadWeather('PARTLY_CLOUDY')).toBe(false);
    expect(isBadWeather('')).toBe(false);
    expect(isBadWeather(null)).toBe(false);
    expect(isBadWeather(undefined)).toBe(false);
  });
});
