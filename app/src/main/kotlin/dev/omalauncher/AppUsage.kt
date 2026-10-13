package dev.omalauncher

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlin.math.pow

/**
 * App launch frequency ranks search results. Launches count less as they age: half every two weeks.
 */
class AppUsage(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("app_usage", Context.MODE_PRIVATE)

    /** Key -> (decayed score, millis it was last updated). */
    private val scores = mutableStateMapOf<String, Pair<Double, Long>>()

    init {
        for ((key, value) in prefs.all) {
            val (score, at) = (value as? String)?.split(',') ?: continue
            scores[key] = (score.toDoubleOrNull() ?: continue) to (at.toLongOrNull() ?: continue)
        }
    }

    fun record(key: String) {
        val now = System.currentTimeMillis()
        val score = score(key, now) + 1
        scores[key] = score to now
        prefs.edit().putString(key, "$score,$now").apply()
    }

    fun score(
        key: String,
        now: Long = System.currentTimeMillis(),
    ): Double {
        val (score, at) = scores[key] ?: return 0.0
        return score * 0.5.pow((now - at).coerceAtLeast(0) / HALF_LIFE)
    }

    private companion object {
        const val HALF_LIFE = 14 * 24 * 60 * 60 * 1000.0
    }
}
