package com.uglygameface.atmosynq.preferences

import android.content.Context

class MotionPreferenceStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAnimated(): Boolean = prefs.getBoolean(KEY_ANIMATED, true)

    fun setAnimated(animated: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATED, animated).apply()
    }

    companion object {
        private const val PREFS = "atmosynq_visual_preferences"
        private const val KEY_ANIMATED = "animated_visuals"
    }
}
