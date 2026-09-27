package com.uglygameface.atmosynq.weather

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object WeatherVisualMapper {
    private val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
    private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
    private val THUNDER_CODES = setOf(95, 96, 97, 99)
    fun map(snapshot: WeatherSnapshot, nowEpochMs: Long = System.currentTimeMillis()): WeatherVisualState {
        val daylight = computeDaylight(snapshot, nowEpochMs)
        val sunsetWarmth = computeSunsetWarmth(snapshot, nowEpochMs)
        val snowCode = snapshot.weatherCode in SNOW_CODES
        val rainCode = snapshot.weatherCode in RAIN_CODES || snapshot.weatherCode in THUNDER_CODES

        // `precipitation` includes rain, showers, and snowfall water-equivalent. Never use
        // that generic field to manufacture rain during a snow event. Prefer the liquid-only
        // fields and use the WMO code as a low-intensity fallback when the latest accumulation
        // bucket is zero but the reported condition is still active.
        val liquidRate = (snapshot.rainMm + snapshot.showersMm).coerceAtLeast(0.0)
        val rainRate = when {
            liquidRate > 0.0 -> liquidRate
            !snowCode && rainCode -> snapshot.precipitationMm
            else -> 0.0
        }
        val measuredRain = normalizedRate(rainRate, 8.0)
        val measuredSnow = normalizedRate(snapshot.snowfallCm, 2.5)
        val rainIntensity = max(measuredRain, minimumRainForCode(snapshot.weatherCode))
        val snowIntensity = max(measuredSnow, minimumSnowForCode(snapshot.weatherCode))
        val cloudiness = (snapshot.cloudCoverPct / 100.0).coerceIn(0.0, 1.0).toFloat()

        val codeFog = snapshot.weatherCode == 45 || snapshot.weatherCode == 48
        val visibilityFog = when {
            snapshot.visibilityM <= 500 -> 1.0
            snapshot.visibilityM >= 10_000 -> 0.0
            else -> 1.0 - ((snapshot.visibilityM - 500.0) / 9_500.0)
        }
        val fogIntensity = max(if (codeFog) 0.65 else 0.0, visibilityFog).coerceIn(0.0, 1.0).toFloat()

        val thunderIntensity = when (snapshot.weatherCode) {
            95 -> 0.55f
            96 -> 0.72f
            97 -> 0.86f
            99 -> 1.0f
            else -> 0.0f
        }
        val windStrength = ((max(snapshot.windSpeedKmh, snapshot.windGustKmh * 0.7)) / 65.0)
            .coerceIn(0.0, 1.0).toFloat()

        return WeatherVisualState(
            daylight = daylight,
            sunsetWarmth = sunsetWarmth,
            cloudiness = cloudiness,
            rainIntensity = if (snowCode && liquidRate <= 0.0) 0.0f else rainIntensity,
            snowIntensity = snowIntensity,
            fogIntensity = fogIntensity,
            thunderIntensity = thunderIntensity,
            windStrength = windStrength,
            windDirectionDeg = snapshot.windDirectionDeg.toFloat()
        )
    }

    private fun minimumRainForCode(code: Int): Float = when (code) {
        51, 56, 61, 80 -> 0.10f
        53, 63, 66, 81 -> 0.28f
        55, 57, 65, 67, 82 -> 0.68f
        95, 96, 97, 99 -> 0.42f
        else -> 0.0f
    }

    private fun minimumSnowForCode(code: Int): Float = when (code) {
        71, 77, 85 -> 0.12f
        73 -> 0.36f
        75, 86 -> 0.72f
        else -> 0.0f
    }

    private fun normalizedRate(value: Double, heavyRate: Double): Float {
        if (value <= 0.0) return 0.0f
        val ratio = (value / heavyRate).coerceIn(0.0, 1.0)
        return ratio.pow(0.58).toFloat().coerceIn(0.04f, 1.0f)
    }

    private fun computeDaylight(snapshot: WeatherSnapshot, nowEpochMs: Long): Float {
        val zone = runCatching { ZoneId.of(snapshot.timezone) }.getOrDefault(ZoneId.of("UTC"))
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
        val sunrise = parseLocalForCurrentDate(snapshot.sunriseIsoLocal, zone, now.toLocalDate())
            ?: return if (snapshot.isDay) 1f else 0f
        val sunset = parseLocalForCurrentDate(snapshot.sunsetIsoLocal, zone, now.toLocalDate())
            ?: return if (snapshot.isDay) 1f else 0f
        val t = now.toEpochSecond().toDouble()
        val rise = sunrise.toEpochSecond().toDouble()
        val set = sunset.toEpochSecond().toDouble()
        val fadeSeconds = 45.0 * 60.0
        return when {
            t < rise - fadeSeconds -> 0f
            t < rise + fadeSeconds -> smoothstep((t - (rise - fadeSeconds)) / (fadeSeconds * 2.0)).toFloat()
            t < set - fadeSeconds -> 1f
            t < set + fadeSeconds -> (1.0 - smoothstep((t - (set - fadeSeconds)) / (fadeSeconds * 2.0))).toFloat()
            else -> 0f
        }
    }

    private fun computeSunsetWarmth(snapshot: WeatherSnapshot, nowEpochMs: Long): Float {
        val zone = runCatching { ZoneId.of(snapshot.timezone) }.getOrDefault(ZoneId.of("UTC"))
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
        val sunrise = parseLocalForCurrentDate(snapshot.sunriseIsoLocal, zone, now.toLocalDate())
        val sunset = parseLocalForCurrentDate(snapshot.sunsetIsoLocal, zone, now.toLocalDate())
        val nowSec = now.toEpochSecond().toDouble()
        val window = 70.0 * 60.0
        fun peak(instantSec: Double): Double {
            val d = kotlin.math.abs(nowSec - instantSec)
            return (1.0 - d / window).coerceIn(0.0, 1.0)
        }
        return max(
            sunrise?.let { peak(it.toEpochSecond().toDouble()) } ?: 0.0,
            sunset?.let { peak(it.toEpochSecond().toDouble()) } ?: 0.0
        ).toFloat()
    }

    private fun parseLocalForCurrentDate(value: String?, zone: ZoneId, currentDate: java.time.LocalDate) = value?.let {
        runCatching {
            val parsed = LocalDateTime.parse(it)
            // Cached weather may survive an offline midnight. Reuse the last known local
            // sunrise/sunset clock time for the current date instead of leaving the wallpaper
            // stuck in yesterday's night state until networking returns.
            LocalDateTime.of(currentDate, parsed.toLocalTime()).atZone(zone)
        }.getOrNull()
    }

    private fun smoothstep(x: Double): Double {
        val t = min(1.0, max(0.0, x))
        return t * t * (3.0 - 2.0 * t)
    }
}
