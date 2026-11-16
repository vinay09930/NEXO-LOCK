package com.vinay.nexolock

import com.vinay.nexolock.domain.LockoutPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * User-facing lockout wording. The delay thresholds are covered by
 * [AuthPolicyTest]; these tests pin the rounding and singular/plural forms so a
 * one-second lockout never renders as "1 seconds".
 */
class LockoutPolicyTest {

    @Test
    fun nonPositiveDelayHasNoDescription() {
        assertEquals("", LockoutPolicy.describe(0L))
        assertEquals("", LockoutPolicy.describe(-1L))
    }

    @Test
    fun secondsRoundUpAndPluralise() {
        assertEquals("1 second", LockoutPolicy.describe(1L))
        assertEquals("1 second", LockoutPolicy.describe(1_000L))
        assertEquals("2 seconds", LockoutPolicy.describe(1_001L))
        assertEquals("30 seconds", LockoutPolicy.describe(30_000L))
    }

    @Test
    fun minutesRoundUpAndPluralise() {
        assertEquals("1 minute", LockoutPolicy.describe(60_000L))
        assertEquals("5 minutes", LockoutPolicy.describe(300_000L))
        assertEquals("59 minutes", LockoutPolicy.describe(3_540_000L))
    }

    @Test
    fun hoursRoundUpAndPluralise() {
        assertEquals("1 hour", LockoutPolicy.describe(3_600_000L))
        assertEquals("2 hours", LockoutPolicy.describe(3_600_001L))
        assertEquals("30 minutes", LockoutPolicy.describe(1_800_000L))
    }
}
