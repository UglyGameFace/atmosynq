package com.uglygameface.atmosynq.weather

import android.content.Context
import com.uglygameface.atmosynq.location.LocationStore
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class WeatherController(private val context: Context) {
    private val locationStore = LocationStore(context)
    private val client = OpenMeteoClient()
    private val prefs = context.getSharedPreferences("atmosynq_weather_cache", Context.MODE_PRIVATE)
    private val stateRef = AtomicReference(loadCachedVisual())
    @Volatile private var snapshot: WeatherSnapshot? = loadCachedSnapshot()
    private var scheduler: ScheduledExecutorService? = null

    fun currentVisual(): WeatherVisualState = snapshot?.let { WeatherVisualMapper.map(it) } ?: stateRef.get()
    fun currentSnapshot(): WeatherSnapshot? = snapshot

    @Synchronized
    fun start() {
        if (scheduler != null) return
        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "AtmosynqWeather").apply { isDaemon = true }
        }.also { executor ->
            executor.scheduleWithFixedDelay({ refreshSafely() }, 0, 15, TimeUnit.MINUTES)
        }
    }

    @Synchronized
    fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    fun refreshNow() = refreshSafely()

    private fun refreshSafely() {
        val location = locationStore.load() ?: return
        runCatching { client.fetch(location.latitude, location.longitude) }
            .onSuccess { fresh ->
                snapshot = fresh
                stateRef.set(WeatherVisualMapper.map(fresh))
                prefs.edit().putString("snapshot_json", fresh.toJson()).apply()
            }
    }

    private fun loadCachedSnapshot(): WeatherSnapshot? =
        prefs.getString("snapshot_json", null)?.let(WeatherSnapshot::fromJson)

    private fun loadCachedVisual(): WeatherVisualState =
        loadCachedSnapshot()?.let { WeatherVisualMapper.map(it) } ?: WeatherVisualState.DEFAULT
}
