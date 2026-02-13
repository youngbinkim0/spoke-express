/**
 * MTA GTFS-Realtime API Client
 * Fetches and parses subway arrival data directly from MTA feeds
 * No API key required as of 2024
 */

// MTA feed URLs by line group
const MTA_FEEDS = {
  '1': 'gtfs',
  '2': 'gtfs',
  '3': 'gtfs',
  '4': 'gtfs',
  '5': 'gtfs',
  '6': 'gtfs',
  '7': 'gtfs',
  'S': 'gtfs',
  'A': 'gtfs-ace',
  'C': 'gtfs-ace',
  'E': 'gtfs-ace',
  'B': 'gtfs-bdfm',
  'D': 'gtfs-bdfm',
  'F': 'gtfs-bdfm',
  'M': 'gtfs-bdfm',
  'G': 'gtfs-g',
  'J': 'gtfs-jz',
  'Z': 'gtfs-jz',
  'L': 'gtfs-l',
  'N': 'gtfs-nqrw',
  'Q': 'gtfs-nqrw',
  'R': 'gtfs-nqrw',
  'W': 'gtfs-nqrw',
};

const MTA_BASE_URL = 'https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2F';

// GTFS-Realtime protobuf field numbers and wire types
// Simplified decoder for the specific fields we need
class ProtobufReader {
  constructor(buffer) {
    this.buffer = new Uint8Array(buffer);
    this.pos = 0;
  }

  readVarint() {
    let result = 0;
    let shift = 0;
    while (this.pos < this.buffer.length) {
      const byte = this.buffer[this.pos++];
      result |= (byte & 0x7f) << shift;
      if ((byte & 0x80) === 0) break;
      shift += 7;
    }
    return result;
  }

  readFixed64() {
    // Read 8 bytes as a number (simplified - only uses lower 52 bits)
    let val = 0;
    for (let i = 0; i < 8; i++) {
      val += this.buffer[this.pos++] * Math.pow(256, i);
    }
    return val;
  }

  readFixed32() {
    let val = 0;
    for (let i = 0; i < 4; i++) {
      val += this.buffer[this.pos++] * Math.pow(256, i);
    }
    return val;
  }

  readString(length) {
    const bytes = this.buffer.slice(this.pos, this.pos + length);
    this.pos += length;
    return new TextDecoder().decode(bytes);
  }

  readBytes(length) {
    const bytes = this.buffer.slice(this.pos, this.pos + length);
    this.pos += length;
    return bytes;
  }

  skip(wireType) {
    if (wireType === 0) {
      this.readVarint();
    } else if (wireType === 1) {
      this.pos += 8;
    } else if (wireType === 2) {
      const len = this.readVarint();
      this.pos += len;
    } else if (wireType === 5) {
      this.pos += 4;
    }
  }

  hasMore() {
    return this.pos < this.buffer.length;
  }
}

// Parse GTFS-Realtime FeedMessage
function parseFeedMessage(buffer) {
  const reader = new ProtobufReader(buffer);
  const entities = [];

  while (reader.hasMore()) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;

    if (fieldNumber === 2 && wireType === 2) {
      // FeedEntity (repeated, field 2)
      const length = reader.readVarint();
      const endPos = reader.pos + length;
      const entity = parseFeedEntity(reader, endPos);
      if (entity) entities.push(entity);
      reader.pos = endPos;
    } else {
      reader.skip(wireType);
    }
  }

  return entities;
}

function parseFeedEntity(reader, endPos) {
  let tripUpdate = null;
  let id = null;

  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;

    if (fieldNumber === 1 && wireType === 2) {
      // id (string, field 1)
      const length = reader.readVarint();
      id = reader.readString(length);
    } else if (fieldNumber === 3 && wireType === 2) {
      // trip_update (TripUpdate, field 3)
      const length = reader.readVarint();
      const tuEnd = reader.pos + length;
      tripUpdate = parseTripUpdate(reader, tuEnd);
      reader.pos = tuEnd;
    } else {
      reader.skip(wireType);
    }
  }

  return tripUpdate ? { id, tripUpdate } : null;
}

function parseTripUpdate(reader, endPos) {
  let trip = null;
  const stopTimeUpdates = [];

  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;

    if (fieldNumber === 1 && wireType === 2) {
      // trip (TripDescriptor, field 1)
      const length = reader.readVarint();
      const tripEnd = reader.pos + length;
      trip = parseTripDescriptor(reader, tripEnd);
      reader.pos = tripEnd;
    } else if (fieldNumber === 2 && wireType === 2) {
      // stop_time_update (repeated, field 2)
      const length = reader.readVarint();
      const stuEnd = reader.pos + length;
      const stu = parseStopTimeUpdate(reader, stuEnd);
      if (stu) stopTimeUpdates.push(stu);
      reader.pos = stuEnd;
    } else {
      reader.skip(wireType);
    }
  }

  return { trip, stopTimeUpdates };
}

function parseTripDescriptor(reader, endPos) {
  let tripId = null;
  let routeId = null;
  let directionId = null;

  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;

    if (fieldNumber === 1 && wireType === 2) {
      // trip_id (string, field 1)
      const length = reader.readVarint();
      tripId = reader.readString(length);
    } else if (fieldNumber === 5 && wireType === 2) {
      // route_id (string, field 5)
      const length = reader.readVarint();
      routeId = reader.readString(length);
    } else if (fieldNumber === 6 && wireType === 0) {
      // direction_id (uint32, field 6)
      directionId = reader.readVarint();
    } else {
      reader.skip(wireType);
    }
  }

  return { tripId, routeId, directionId };
}

function parseStopTimeUpdate(reader, endPos) {
  let stopId = null;
  let arrival = null;
  let departure = null;

  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;

    if (fieldNumber === 4 && wireType === 2) {
      // stop_id (string, field 4)
      const length = reader.readVarint();
      stopId = reader.readString(length);
    } else if (fieldNumber === 2 && wireType === 2) {
      // arrival (StopTimeEvent, field 2)
      const length = reader.readVarint();
      const arrEnd = reader.pos + length;
      arrival = parseStopTimeEvent(reader, arrEnd);
      reader.pos = arrEnd;
    } else if (fieldNumber === 3 && wireType === 2) {
      // departure (StopTimeEvent, field 3)
      const length = reader.readVarint();
      const depEnd = reader.pos + length;
      departure = parseStopTimeEvent(reader, depEnd);
      reader.pos = depEnd;
    } else {
      reader.skip(wireType);
    }
  }

  return { stopId, arrival, departure };
}

function parseStopTimeEvent(reader, endPos) {
  let time = null;

  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;

    if (fieldNumber === 2 && wireType === 0) {
      // time (int64, field 2) - stored as varint
      time = reader.readVarint();
    } else {
      reader.skip(wireType);
    }
  }

  return time ? { time } : null;
}

// Get feeds needed for a list of lines
function getFeedsForLines(lines) {
  const feeds = new Set();
  for (const line of lines) {
    const feed = MTA_FEEDS[line];
    if (feed) feeds.add(feed);
  }
  return Array.from(feeds);
}

// Fetch and parse a single MTA feed
async function fetchMtaFeed(feedName) {
  try {
    const url = MTA_BASE_URL + feedName;
    const response = await fetch(url, {
      headers: {
        'User-Agent': 'NYC-Commute-Optimizer/1.0'
      }
    });

    if (!response.ok) {
      console.error(`MTA feed ${feedName} returned ${response.status}`);
      return [];
    }

    const buffer = await response.arrayBuffer();
    return parseFeedMessage(buffer);
  } catch (error) {
    console.error(`Error fetching MTA feed ${feedName}:`, error);
    return [];
  }
}

// Get arrivals for a specific station
async function getStationArrivals(stationId, lines) {
  // Determine which feeds to fetch based on lines at this station
  const feeds = getFeedsForLines(lines);

  // Fetch all needed feeds in parallel
  const feedPromises = feeds.map(feed => fetchMtaFeed(feed));
  const allEntities = (await Promise.all(feedPromises)).flat();

  const now = Math.floor(Date.now() / 1000);
  const arrivals = [];

  // MTA uses different stop ID format: stationId + N/S for direction
  // e.g., "G33N" for northbound at G33
  const northStopId = stationId + 'N';
  const southStopId = stationId + 'S';

  for (const entity of allEntities) {
    if (!entity.tripUpdate) continue;

    const { trip, stopTimeUpdates } = entity.tripUpdate;
    if (!trip || !stopTimeUpdates) continue;

    for (const stu of stopTimeUpdates) {
      // Check if this stop matches our station (with N or S suffix)
      if (stu.stopId !== northStopId && stu.stopId !== southStopId) continue;

      const arrivalTime = stu.arrival?.time || stu.departure?.time;
      if (!arrivalTime || arrivalTime < now - 60) continue; // Skip past arrivals

      const direction = stu.stopId.endsWith('N') ? 'N' : 'S';

      arrivals.push({
        routeId: trip.routeId,
        direction,
        arrivalTime,
        minutesAway: Math.max(0, Math.round((arrivalTime - now) / 60))
      });
    }
  }

  // Sort by arrival time
  arrivals.sort((a, b) => a.arrivalTime - b.arrivalTime);

  return arrivals;
}

// Get next arrival for a station (used by commute optimizer)
async function getNextArrival(stationId, lines) {
  const arrivals = await getStationArrivals(stationId, lines);

  if (arrivals.length === 0) {
    return { nextTrain: '--', arrivalTime: '--', routeId: null };
  }

  const next = arrivals[0];
  const arrivalDate = new Date(next.arrivalTime * 1000);
  const timeStr = arrivalDate.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit'
  });

  const displayText = next.minutesAway <= 0 ? "Now" : `${next.minutesAway}m`;

  return {
    nextTrain: displayText,
    minutesAway: next.minutesAway,
    arrivalTime: timeStr,
    routeId: next.routeId
  };
}

// Get grouped arrivals for display (like the arrivals page)
async function getGroupedArrivals(stationId, lines) {
  const arrivals = await getStationArrivals(stationId, lines);

  // Group by route and direction
  const groups = new Map();

  for (const arrival of arrivals) {
    const key = `${arrival.routeId}-${arrival.direction}`;

    if (!groups.has(key)) {
      groups.set(key, {
        line: arrival.routeId,
        direction: arrival.direction,
        headsign: arrival.direction === 'N' ? 'Northbound' : 'Southbound',
        arrivals: []
      });
    }

    const group = groups.get(key);
    if (group.arrivals.length < 3) {
      group.arrivals.push({ minutesAway: arrival.minutesAway });
    }
  }

  return Array.from(groups.values()).sort((a, b) => (a.line || '').localeCompare(b.line || ''));
}

// Alert effects that impact service
const ALERT_EFFECTS = {
  1: 'NO_SERVICE', 2: 'REDUCED_SERVICE', 3: 'SIGNIFICANT_DELAYS',
  4: 'DETOUR', 5: 'ADDITIONAL_SERVICE', 6: 'MODIFIED_SERVICE',
  7: 'OTHER_EFFECT', 8: 'UNKNOWN_EFFECT', 9: 'STOP_MOVED'
};

function parseAlertFeed(buffer) {
  const reader = new ProtobufReader(buffer);
  const alerts = [];
  while (reader.hasMore()) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;
    if (fieldNumber === 2 && wireType === 2) {
      const length = reader.readVarint();
      const endPos = reader.pos + length;
      const alert = parseAlertEntity(reader, endPos);
      if (alert) alerts.push(alert);
      reader.pos = endPos;
    } else {
      reader.skip(wireType);
    }
  }
  return alerts;
}

function parseAlertEntity(reader, endPos) {
  let id = null, alert = null;
  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;
    if (fieldNumber === 1 && wireType === 2) {
      id = reader.readString(reader.readVarint());
    } else if (fieldNumber === 5 && wireType === 2) {
      const length = reader.readVarint();
      const alertEnd = reader.pos + length;
      alert = parseAlert(reader, alertEnd);
      reader.pos = alertEnd;
    } else {
      reader.skip(wireType);
    }
  }
  return alert ? { id, ...alert } : null;
}

function parseAlert(reader, endPos) {
  const routeIds = [];
  let effect = null, headerText = null, activePeriods = [];
  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;
    if (fieldNumber === 1 && wireType === 2) {
      const length = reader.readVarint();
      const periodEnd = reader.pos + length;
      const period = parseActivePeriod(reader, periodEnd);
      if (period) activePeriods.push(period);
      reader.pos = periodEnd;
    } else if (fieldNumber === 5 && wireType === 2) {
      const length = reader.readVarint();
      const entityEnd = reader.pos + length;
      const routeId = parseInformedEntity(reader, entityEnd);
      if (routeId && !routeIds.includes(routeId)) routeIds.push(routeId);
      reader.pos = entityEnd;
    } else if (fieldNumber === 7 && wireType === 0) {
      effect = reader.readVarint();
    } else if (fieldNumber === 10 && wireType === 2) {
      const length = reader.readVarint();
      const textEnd = reader.pos + length;
      headerText = parseTranslatedString(reader, textEnd);
      reader.pos = textEnd;
    } else {
      reader.skip(wireType);
    }
  }
  const now = Math.floor(Date.now() / 1000);
  const isActive = activePeriods.length === 0 || activePeriods.some(p => (!p.start || p.start <= now) && (!p.end || p.end >= now));
  if (!isActive || !headerText) return null;
  return { routeIds, effect: ALERT_EFFECTS[effect] || 'UNKNOWN', headerText };
}

function parseActivePeriod(reader, endPos) {
  let start = null, end = null;
  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;
    if (fieldNumber === 1 && wireType === 0) start = reader.readVarint();
    else if (fieldNumber === 2 && wireType === 0) end = reader.readVarint();
    else reader.skip(wireType);
  }
  return { start, end };
}

function parseInformedEntity(reader, endPos) {
  let routeId = null;
  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;
    if (fieldNumber === 5 && wireType === 2) {
      routeId = reader.readString(reader.readVarint());
    } else reader.skip(wireType);
  }
  return routeId;
}

function parseTranslatedString(reader, endPos) {
  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;
    if (fieldNumber === 1 && wireType === 2) {
      const length = reader.readVarint();
      const transEnd = reader.pos + length;
      const text = parseTranslation(reader, transEnd);
      reader.pos = transEnd;
      if (text) return text;
    } else reader.skip(wireType);
  }
  return null;
}

function parseTranslation(reader, endPos) {
  let text = null;
  while (reader.pos < endPos) {
    const tag = reader.readVarint();
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;
    if (fieldNumber === 1 && wireType === 2) {
      text = reader.readString(reader.readVarint());
    } else reader.skip(wireType);
  }
  return text;
}

async function fetchServiceAlerts(routeIds = []) {
  try {
    const url = MTA_BASE_URL + 'gtfs-alerts';
    const response = await fetch(url);
    if (!response.ok) return [];
    const buffer = await response.arrayBuffer();
    const alerts = parseAlertFeed(buffer);
    if (routeIds.length > 0) {
      return alerts.filter(a => a.routeIds.length === 0 || a.routeIds.some(r => routeIds.includes(r)));
    }
    return alerts;
  } catch (e) {
    console.error('Error fetching alerts:', e);
    return [];
  }
}

// Export for use in other scripts
window.MtaApi = {
  getStationArrivals,
  getNextArrival,
  getGroupedArrivals,
  fetchServiceAlerts,
  MTA_FEEDS
};
