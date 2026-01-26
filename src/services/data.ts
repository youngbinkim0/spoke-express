import { readFileSync, writeFileSync, existsSync } from 'fs';
import { join } from 'path';
import type { Settings, StationsData, Station } from '../types/index.js';

const DATA_DIR = join(process.cwd(), 'data');
const SETTINGS_PATH = join(DATA_DIR, 'settings.json');
const STATIONS_PATH = join(DATA_DIR, 'stations.json');

// Default settings if file doesn't exist
const DEFAULT_SETTINGS: Settings = {
  home: { lat: 40.6892, lng: -73.9442, address: '' },
  work: { lat: 40.7471, lng: -73.9456, address: '' },
  bikeToStations: [],
  walkToStations: [],
  destinationStation: 'court-sq',
};

export function getSettings(): Settings {
  try {
    if (!existsSync(SETTINGS_PATH)) {
      saveSettings(DEFAULT_SETTINGS);
      return DEFAULT_SETTINGS;
    }
    const data = readFileSync(SETTINGS_PATH, 'utf-8');
    return JSON.parse(data) as Settings;
  } catch (error) {
    console.error('Error reading settings:', error);
    return DEFAULT_SETTINGS;
  }
}

export function saveSettings(settings: Settings): void {
  try {
    writeFileSync(SETTINGS_PATH, JSON.stringify(settings, null, 2));
  } catch (error) {
    console.error('Error saving settings:', error);
    throw error;
  }
}

export function getStations(): Station[] {
  try {
    const data = readFileSync(STATIONS_PATH, 'utf-8');
    const parsed: StationsData = JSON.parse(data);
    return parsed.stations;
  } catch (error) {
    console.error('Error reading stations:', error);
    return [];
  }
}

export function getStationById(id: string): Station | undefined {
  const stations = getStations();
  return stations.find((s) => s.id === id);
}

export function getStationByTransiterId(transiterId: string): Station | undefined {
  const stations = getStations();
  return stations.find((s) => s.transiterId === transiterId);
}
