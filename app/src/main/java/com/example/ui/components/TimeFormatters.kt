package com.example.ui.components

import java.util.Locale

fun formatDurationMs(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val tenths = (safeMs % 1000L) / 100L
    return if (minutes > 0L) {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    } else {
        String.format(Locale.US, "0:%02d.%d", seconds, tenths)
    }
}

fun formatClockMs(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
