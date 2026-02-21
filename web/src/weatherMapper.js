const BAD_CONDITION_KEYWORDS = [
  'RAIN',
  'SNOW',
  'STORM',
  'SLEET',
  'HAIL',
  'DRIZZLE',
  'THUNDERSTORM',
  'WINDY',
  'FOG',
];

function normalizeString(value) {
  if (typeof value !== 'string') {
    return '';
  }
  return value.trim().toUpperCase();
}

function conditionContainsBadWeather(condition) {
  return BAD_CONDITION_KEYWORDS.some((keyword) => condition.includes(keyword));
}

function conditionContainsRainOrSnow(condition) {
  return condition.includes('RAIN') || condition.includes('SNOW');
}

export function mapGoogleWeatherToIsBad(condition, precipType, precipProbability) {
  const normalizedCondition = normalizeString(condition);
  const normalizedPrecipType = normalizeString(precipType);

  if (normalizedPrecipType === 'RAIN' || normalizedPrecipType === 'SNOW' || normalizedPrecipType === 'MIX' || normalizedPrecipType === 'SLEET') {
    return true;
  }

  if (normalizedPrecipType === 'NONE') {
    return conditionContainsRainOrSnow(normalizedCondition);
  }

  if (conditionContainsBadWeather(normalizedCondition)) {
    return true;
  }

  if (typeof precipProbability === 'number' && precipProbability >= 50) {
    return true;
  }

  return false;
}
