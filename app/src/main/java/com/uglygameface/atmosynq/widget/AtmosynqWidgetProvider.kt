package com.uglygameface.atmosynq.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver.PendingResult
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.uglygameface.atmosynq.MainActivity
import com.uglygameface.atmosynq.R
import com.uglygameface.atmosynq.location.LocationStore
import com.uglygameface.atmosynq.preferences.MotionPreferenceStore
import com.uglygameface.atmosynq.weather.OpenMeteoClient
import com.uglygameface.atmosynq.weather.WeatherCode
import com.uglygameface.atmosynq.weather.WeatherSnapshot
import java.util.Locale
import kotlin.math.roundToInt

class AtmosynqWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context, manager, appWidgetIds, goAsync())
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AtmosynqWidgetProvider::class.java))
            refreshAsync(context, manager, ids, goAsync())
            return
        }
        super.onReceive(context, intent)
    }

    private fun refreshAsync(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        pending: PendingResult
    ) {
        Thread {
            try {
                if (ids.isEmpty()) return@Thread
                val location = LocationStore(context).load()
                if (location == null) {
                    ids.forEach { manager.updateAppWidget(it, emptyViews(context, it)) }
                    return@Thread
                }

                val report = runCatching {
                    OpenMeteoClient().fetchReport(location.latitude, location.longitude)
                }.getOrNull()

                report?.current?.let { cacheSnapshot(context, it) }
                val snapshot = report?.current ?: cachedSnapshot(context)
                if (snapshot == null) {
                    ids.forEach { manager.updateAppWidget(it, errorViews(context, it)) }
                    return@Thread
                }

                val today = report?.daily?.firstOrNull()
                ids.forEach { id ->
                    manager.updateAppWidget(
                        id,
                        weatherViews(
                            context = context,
                            widgetId = id,
                            snapshot = snapshot,
                            highC = today?.highC,
                            lowC = today?.lowC,
                            precipitationPct = today?.precipitationProbabilityPct
                        )
                    )
                }
            } finally {
                pending.finish()
            }
        }.apply {
            name = "AtmosynqWidgetRefresh"
            isDaemon = true
        }.start()
    }

    private fun weatherViews(
        context: Context,
        widgetId: Int,
        snapshot: WeatherSnapshot,
        highC: Double?,
        lowC: Double?,
        precipitationPct: Int?
    ): RemoteViews {
        val animated = MotionPreferenceStore(context).isAnimated()
        val views = baseViews(context, widgetId, animated)
        val firstFrame = if (!animated && snapshot.weatherCode in setOf(95, 96, 97, 99)) 1 else 0
        views.setImageViewBitmap(R.id.widget_scene_0, WeatherWidgetSceneRenderer.render(snapshot, firstFrame))
        if (animated) {
            views.setImageViewBitmap(R.id.widget_scene_1, WeatherWidgetSceneRenderer.render(snapshot, 1))
            views.setImageViewBitmap(R.id.widget_scene_2, WeatherWidgetSceneRenderer.render(snapshot, 2))
        }
        views.setTextViewText(R.id.widget_temperature, formatTemperature(snapshot.temperatureC))
        views.setTextViewText(R.id.widget_condition, WeatherCode.description(snapshot.weatherCode))

        val details = buildString {
            if (highC != null && lowC != null) {
                append("H ${formatTemperature(highC)}  •  L ${formatTemperature(lowC)}")
            }
            if (precipitationPct != null) {
                if (isNotEmpty()) append("  •  ")
                append("${precipitationPct}% precip")
            }
        }
        views.setTextViewText(
            R.id.widget_details,
            details.ifEmpty { "Feels ${formatTemperature(snapshot.apparentTemperatureC)}" }
        )
        views.setViewVisibility(R.id.widget_refresh, View.VISIBLE)
        return views
    }

    private fun emptyViews(context: Context, widgetId: Int): RemoteViews =
        baseViews(context, widgetId, MotionPreferenceStore(context).isAnimated()).apply {
            setTextViewText(R.id.widget_temperature, "--°")
            setTextViewText(R.id.widget_condition, "Set your weather location")
            setTextViewText(R.id.widget_details, "Open Atmosynq to sync local weather")
            setViewVisibility(R.id.widget_refresh, View.GONE)
        }

    private fun errorViews(context: Context, widgetId: Int): RemoteViews =
        baseViews(context, widgetId, MotionPreferenceStore(context).isAnimated()).apply {
            setTextViewText(R.id.widget_temperature, "--°")
            setTextViewText(R.id.widget_condition, "Weather unavailable")
            setTextViewText(R.id.widget_details, "Tap refresh to try again")
            setViewVisibility(R.id.widget_refresh, View.VISIBLE)
        }

    private fun baseViews(context: Context, widgetId: Int, animated: Boolean): RemoteViews =
        RemoteViews(
            context.packageName,
            if (animated) R.layout.widget_atmosynq else R.layout.widget_atmosynq_static
        ).apply {
            val openApp = PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val refresh = PendingIntent.getBroadcast(
                context,
                widgetId + 10_000,
                Intent(context, AtmosynqWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_root, openApp)
            setOnClickPendingIntent(R.id.widget_refresh, refresh)
        }

    private fun cacheSnapshot(context: Context, snapshot: WeatherSnapshot) {
        context.getSharedPreferences("atmosynq_weather_cache", Context.MODE_PRIVATE)
            .edit()
            .putString("snapshot_json", snapshot.toJson())
            .apply()
    }

    private fun cachedSnapshot(context: Context): WeatherSnapshot? =
        context.getSharedPreferences("atmosynq_weather_cache", Context.MODE_PRIVATE)
            .getString("snapshot_json", null)
            ?.let(WeatherSnapshot::fromJson)

    companion object {
        const val ACTION_REFRESH = "com.uglygameface.atmosynq.action.REFRESH_WIDGET"

        private fun formatTemperature(celsius: Double): String {
            val usesUs = Locale.getDefault().country.equals("US", ignoreCase = true)
            val value = if (usesUs) celsius * 9.0 / 5.0 + 32.0 else celsius
            return "${value.roundToInt()}°"
        }
    }
}
