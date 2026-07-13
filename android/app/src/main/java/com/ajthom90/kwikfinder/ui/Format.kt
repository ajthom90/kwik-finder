package com.ajthom90.kwikfinder.ui

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Shared UI formatters (mirrors iOS `Format`). */
object Format {
    /**
     * Distance for list/detail rows.
     * Under ~0.2 mi → feet; under 10 mi → one decimal mile; else whole miles.
     */
    fun distance(meters: Float): String {
        val miles = meters / 1609.344f
        return when {
            miles < 0.2f -> {
                val feet = (meters * 3.28084f).roundToInt()
                "$feet ft"
            }
            miles < 10f -> String.format(Locale.US, "%.1f mi", miles)
            else -> String.format(Locale.US, "%.0f mi", miles)
        }
    }

    fun distance(meters: Double): String = distance(meters.toFloat())

    /**
     * Relative "as of" phrasing for catalog banners / price footers.
     * Uses simple relative units (no ICU dependency).
     */
    fun asOf(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val delta = (nowMs - epochMs).coerceAtLeast(0L)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(delta)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        return when {
            seconds < 45L -> "just now"
            minutes < 2L -> "1 minute ago"
            minutes < 60L -> "$minutes minutes ago"
            hours < 2L -> "1 hour ago"
            hours < 24L -> "$hours hours ago"
            days < 2L -> "1 day ago"
            days < 30L -> "$days days ago"
            else -> {
                val months = (days / 30L).coerceAtLeast(1L)
                if (months < 2L) "1 month ago" else "$months months ago"
            }
        }
    }
}
