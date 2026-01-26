export interface Station {
  id: string;
  name: string;
  transiterId: string;
  lines: string[];
  lat: number;
  lng: number;
  borough: string;
}

export interface Location {
  lat: number;
  lng: number;
  address?: string;
}

export interface Settings {
  home: Location;
  work: Location;
  bikeToStations: string[];
  walkToStations: string[];
  destinationStation: string;
}

export interface StationsData {
  stations: Station[];
}

export interface Leg {
  mode: 'bike' | 'walk' | 'subway';
  duration: number;
  to: string;
  route?: string;
}

export interface CommuteOption {
  id: string;
  rank: number;
  type: 'bike_to_transit' | 'transit_only';
  duration_minutes: number;
  summary: string;
  legs: Leg[];
  nextTrain: string;
  arrival_time: string;
  station: Station;
}

export interface Weather {
  temp_f: number;
  conditions: string;
  precipitation_type: 'none' | 'rain' | 'snow' | 'mix';
  precipitation_probability: number;
  is_bad: boolean;
}

export interface Alert {
  route: string;
  message: string;
}

export interface CommuteResponse {
  options: CommuteOption[];
  weather: Weather;
  alerts: Alert[];
  generated_at: string;
}

export interface TransiterArrival {
  arrival: {
    time: string;
  };
  trip: {
    route: {
      id: string;
    };
    direction: string;
  };
}

export interface TransiterStopResponse {
  id: string;
  name: string;
  stopTimes: TransiterArrival[];
}
