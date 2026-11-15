package com.vinay.nexolock.domain

/**
 * Progressive delay after repeated wrong credentials. Deliberately simple and
 * fully local: no lockout state ever leaves the device.
 */
object LockoutPolicy {

    private val thresholds = listOf(
        5 to 30_000L,
        8 to 300_000L,
        12 to 1_800_000L,
    )

    fun delayForFailures(failures: Int): Long =
        thresholds.lastOrNull { failures >= it.first }?.second ?: 0L

    /** Wrong attempts left before the next escalation. 0 means a lockout is active. */
    fun attemptsUntilFailures(failures: Int): Int {
        val next = thresholds.firstOrNull { it.first > failures } ?: return 0
        return next.first - failures
    }

    fun describe(delayMs: Long): String = when {
        delayMs <= 0L -> ""
        delayMs < 60_000L -> "${(delayMs + 999) / 1000} seconds"
        delayMs < 3_600_000L -> "${(delayMs + 59_999) / 60_000} minutes"
        else -> "${(delayMs + 3_599_999) / 3_600_000} hours"
    }
}
