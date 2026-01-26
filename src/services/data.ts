import { readFileSync, writeFileSync, existsSync } from 'fs';
import { join } from 'path';
import type { Settings, Station } from '../types/index.js';
import { getAllStations } from './transiter.js';

const DATA_DIR = join(process.cwd(), 'data');
const SETTINGS_PATH = join(DATA_DIR, 'settings.json');

// Default settings if file doesn't exist
const DEFAULT_SETTINGS: Settings = {
  home: { lat: 40.6892, lng: -73.9442, address: '' },
  work: { lat: 40.7471, lng: -73.9456, address: '' },
  bikeToStations: [],
  walkToStations: [],
  destinationStation: 'G22', // Court Sq
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

export async function getStationById(id: string): Promise<Station | undefined> {
  const stations = await getAllStations();
  return stations.find((s) => s.id === id || s.transiterId === id);
}

export async function getStationByTransiterId(transiterId: string): Promise<Station | undefined> {
  const stations = await getAllStations();
  return stations.find((s) => s.transiterId === transiterId);
}
