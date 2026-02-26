// render-utils.js — Shared rendering functions for Spoke Express
// Depends on: commute-engine.js (lineColors)

function getModeIcon(mode) {
  switch (mode) {
    case 'bike': return '🚲';
    case 'walk': return '🚶';
    case 'subway': return '🚇';
    default: return '•';
  }
}

function cleanLineName(line) {
  if (!line) return '';
  // Remove "Line", "Train", "Express" suffixes and clean up
  let cleaned = line.replace(/\s*(Line|Train|Express|Local)$/i, '').trim();
  // Handle express train suffixes (6X -> 6, 7X -> 7, FX -> F, etc.)
  cleaned = cleaned.replace(/^([A-Z0-9])X$/i, '$1').toUpperCase();
  return cleaned;
}

function renderLineBadge(line) {
  const cleanLine = cleanLineName(line);
  const color = lineColors[cleanLine] || '#888';
  const textColor = ['N', 'Q', 'R', 'W'].includes(cleanLine) ? '#000' : '#fff';
  return `<span class="line-badge" style="background:${color};color:${textColor}">${cleanLine}</span>`;
}

function renderOption(option) {
  const rankClass = option.rank === 1 ? 'rank-1' : '';

  const legsHtml = option.legs.map((leg, i) => {
    let legContent = `<span class="leg-icon">${getModeIcon(leg.mode)}</span>`;
    if (leg.route) {
      legContent += renderLineBadge(leg.route);
    }
    legContent += ` ${leg.duration}m`;

    const arrow = i < option.legs.length - 1 ? '<span class="leg-arrow">→</span>' : '';
    return `<span class="leg">${legContent}</span>${arrow}`;
  }).join('');

  // Build expanded details with transfer stops
  const expandedHtml = option.legs.map(leg => {
    const icon = getModeIcon(leg.mode);
    let lineInfo = '';
    let stopsInfo = '';

    if (leg.route) {
      lineInfo = renderLineBadge(leg.route);
      stopsInfo = `${leg.from || ''} → ${leg.to}`;
      if (leg.numStops) {
        stopsInfo += ` (${leg.numStops} stops)`;
      }
    } else {
      stopsInfo = `${leg.from || 'Home'} → ${leg.to}`;
    }

    return `
      <div class="transfer-step">
        <div class="transfer-step-icon">${icon} ${lineInfo}</div>
        <div class="transfer-step-info">
          <div class="transfer-step-stops">${stopsInfo}</div>
        </div>
        <div class="transfer-step-time">${leg.duration}m</div>
      </div>
    `;
  }).join('');

  return `
    <div class="option-card ${rankClass}" onclick="this.classList.toggle('expanded')">
      <div class="option-header">
        <span class="option-rank">${option.rank}</span>
        <span class="option-summary">${option.summary}</span>
        <span class="option-duration">${option.duration}m <span class="expand-icon">▼</span></span>
      </div>
      <div class="option-details">
        <span>Next train: ${option.nextTrain}</span>
        <span>Arrive: ${option.arrivalTime}</span>
      </div>
      <div class="option-legs">${legsHtml}</div>
      <div class="option-details-expanded">${expandedHtml}</div>
    </div>
  `;
}

function renderWeather(weather) {
  const warningHtml = weather.isBad
    ? '<span class="weather-warning">Bad for biking</span>'
    : '';

  return `
    <div class="weather-bar">
      <div class="weather-info">
         <span class="weather-temp">${weather.tempF != null && !isNaN(weather.tempF) ? weather.tempF + '°F' : '--°F'}</span>
        <span class="weather-conditions">${weather.conditions}</span>
        ${warningHtml}
      </div>
      <span class="time">${new Date().toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}</span>
    </div>
  `;
}

function renderAlerts(alerts) {
  if (!alerts || alerts.length === 0) return '';

  return `
    <div class="alerts-section">
      ${alerts.map(a => `
        <div class="alert-card ${a.isElevator ? 'elevator' : ''}">
          ⚠️ ${a.text}
        </div>
      `).join('')}
    </div>
  `;
}
