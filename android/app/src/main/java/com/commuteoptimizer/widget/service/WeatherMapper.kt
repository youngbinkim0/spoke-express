package com.commuteoptimizer.widget

private val BAD_CONDITION_KEYWORDS = listOf(
    "RAIN",
    "SNOW",
    "STORM",
    "SLEET",
    "HAIL",
    "DRIZZLE",
    "THUNDERSTORM",
)

private fun normalizeWeatherValue(value: String?): String {
    return value?.trim()?.uppercase() ?: ""
}

private fun containsBadConditionKeyword(condition: String): Boolean {
    return BAD_CONDITION_KEYWORDS.any { keyword -> condition.contains(keyword) }
}

fun mapGoogleWeatherToIsBad(
    condition: String?,
    precipitationProbability: Int? = null,
    precipitationType: String? = null
): Boolean {
    val normalizedCondition = normalizeWeatherValue(condition)
    val normalizedPrecipitationType = normalizeWeatherValue(precipitationType)

    if (normalizedPrecipitationType == "RAIN" || normalizedPrecipitationType == "SNOW" || normalizedPrecipitationType == "MIX" || normalizedPrecipitationType == "SLEET") {
        return true
    }

    if (normalizedPrecipitationType == "NONE") {
        return false
    }

    if (containsBadConditionKeyword(normalizedCondition)) {
        return true
    }

    if ((precipitationProbability ?: -1) >= 50) {
        return true
    }

    return false
}
