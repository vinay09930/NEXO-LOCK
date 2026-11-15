package com.vinay.nexolock.ui

import java.util.Locale

/** Human-readable byte count with binary (1024-based) units. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    val pattern = when {
        value >= 100.0 -> "%.0f %s"
        value >= 10.0 -> "%.1f %s"
        else -> "%.2f %s"
    }
    return String.format(Locale.getDefault(), pattern, value, units[index])
}
