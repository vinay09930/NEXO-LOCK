package com.vinay.nexolock

import com.vinay.nexolock.domain.SecurityOperation
import com.vinay.nexolock.domain.SelfProtectionGate
import com.vinay.nexolock.domain.SelfProtectionGate.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Self-Protection authorization rules.
 *
 * The gate is the only place that decides whether NEXO LOCK's own interface and
 * its security settings may be changed, so these tests cover the cases the UI
 * cannot prove by itself: expiration, freshness, revoked grants and repeated
 * requests.
 */
class SelfProtectionGateTest {

    private fun gate(
        ttl: Long = 300_000L,
        fresh: Long = 60_000L,
    ) = SelfProtectionGate(sessionTtlMs = ttl, freshnessWindowMs = fresh)

    private val sensitive = listOf(
        SecurityOperation.DISABLE_PROTECTION,
        SecurityOperation.CHANGE_AUTHENTICATION,
        SecurityOperation.CHANGE_RECOVERY,
    )

    private val others = SecurityOperation.entries.filter { it !in sensitive }

    // ---------------------------------------------------------------- protection off

    @Test
    fun protectionOffNeverAsksForAuthentication() {
        val gate = gate()
        SecurityOperation.entries.forEach { operation ->
            assertEquals(
                "Protection off must behave exactly as before",
                Decision.ALLOWED,
                gate.decide(operation, required = false, nowMs = 1_000L),
            )
        }
    }

    // ---------------------------------------------------------------- no grant yet

    @Test
    fun protectionOnDeniesEveryOperationBeforeAuthentication() {
        val gate = gate()
        SecurityOperation.entries.forEach { operation ->
            assertEquals(
                "${operation.name} must be refused without a session",
                Decision.AUTHENTICATION_REQUIRED,
                gate.decide(operation, required = true, nowMs = 1_000L),
            )
        }
        assertFalse(gate.isAuthorized(1_000L))
    }

    @Test
    fun repeatedRequestsCannotGrantAccess() {
        val gate = gate()
        repeat(100) {
            assertEquals(
                Decision.AUTHENTICATION_REQUIRED,
                gate.decide(SecurityOperation.CHANGE_SECURITY_SETTINGS, required = true, nowMs = 1_000L + it),
            )
        }
        assertFalse(gate.isAuthorized(2_000L))
    }

    // ---------------------------------------------------------------- granted session

    @Test
    fun grantAllowsTheSessionUntilItExpires() {
        val gate = gate()
        gate.grant(10_000L)
        SecurityOperation.entries.forEach { operation ->
            assertEquals(
                Decision.ALLOWED,
                gate.decide(operation, required = true, nowMs = 10_500L),
            )
        }
        // 300 s later the session is gone, not renewed.
        assertFalse(gate.isAuthorized(10_000L + 300_000L))
        assertEquals(
            Decision.AUTHENTICATION_REQUIRED,
            gate.decide(SecurityOperation.DISABLE_PROTECTION, required = true, nowMs = 310_000L),
        )
    }

    @Test
    fun sensitiveOperationNeedsAFreshGrant() {
        val gate = gate(ttl = 300_000L, fresh = 60_000L)
        gate.grant(0L)
        assertTrue(gate.isFresh(59_000L))
        sensitive.forEach { operation ->
            assertEquals(
                "$operation must be allowed inside the freshness window",
                Decision.ALLOWED,
                gate.decide(operation, required = true, nowMs = 59_000L),
            )
        }
        // Still inside the session, but no longer a fresh verification.
        assertTrue(gate.isAuthorized(61_000L))
        sensitive.forEach { operation ->
            assertEquals(
                "$operation must be refused once the verification is stale",
                Decision.FRESH_AUTHENTICATION_REQUIRED,
                gate.decide(operation, required = true, nowMs = 61_000L),
            )
        }
        others.forEach { operation ->
            assertEquals(
                "$operation only needs a live session",
                Decision.ALLOWED,
                gate.decide(operation, required = true, nowMs = 61_000L),
            )
        }
    }

    @Test
    fun revokedGrantCannotAuthorizeAnything() {
        val gate = gate()
        gate.grant(1_000L)
        assertTrue(gate.isAuthorized(1_100L))
        gate.revoke()
        assertFalse(gate.isAuthorized(1_100L))
        assertEquals(
            Decision.AUTHENTICATION_REQUIRED,
            gate.decide(SecurityOperation.DISABLE_PROTECTION, required = true, nowMs = 1_100L),
        )
    }

    // ---------------------------------------------------------------- repeated grants

    @Test
    fun aNewGrantRestartsTheFreshnessWindow() {
        val gate = gate(ttl = 300_000L, fresh = 60_000L)
        gate.grant(0L)
        assertTrue(gate.isAuthorized(61_000L))
        assertEquals(
            Decision.FRESH_AUTHENTICATION_REQUIRED,
            gate.decide(SecurityOperation.DISABLE_PROTECTION, required = true, nowMs = 61_000L),
        )

        gate.grant(61_000L)
        assertEquals(
            Decision.ALLOWED,
            gate.decide(SecurityOperation.DISABLE_PROTECTION, required = true, nowMs = 61_000L),
        )
        assertTrue(gate.isFresh(121_000L))
        assertFalse(gate.isFresh(122_000L))
    }

    @Test
    fun aGrantCannotSurviveItsOwnDeadline() {
        val gate = gate(ttl = 1_000L, fresh = 1_000L)
        gate.grant(0L)
        assertTrue(gate.isAuthorized(999L))
        assertFalse(gate.isAuthorized(1_000L))
        assertEquals(
            Decision.AUTHENTICATION_REQUIRED,
            gate.decide(SecurityOperation.DISABLE_PROTECTION, required = true, nowMs = 1_000L),
        )
    }

    @Test
    fun remainingTimeCountsDownAndNeverGoesNegative() {
        val gate = gate(ttl = 2_000L, fresh = 2_000L)
        gate.grant(1_000L)
        assertEquals(2_000L, gate.remainingMs(1_000L))
        assertEquals(500L, gate.remainingMs(2_500L))
        assertEquals(0L, gate.remainingMs(9_000L))
    }
}
