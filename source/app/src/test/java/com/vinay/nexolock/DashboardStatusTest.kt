package com.vinay.nexolock

import com.vinay.nexolock.data.NexoConfig
import com.vinay.nexolock.protection.PermissionManager
import com.vinay.nexolock.ui.BiometricState
import com.vinay.nexolock.ui.DashboardAction
import com.vinay.nexolock.ui.DashboardStatus
import com.vinay.nexolock.ui.DashboardStatuses
import com.vinay.nexolock.ui.StatusTone
import com.vinay.nexolock.ui.VaultOverview
import com.vinay.nexolock.vault.CaptureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the truthfulness rules of the six dashboard statuses: a healthy value
 * may only appear when the matching observable state is healthy, and a missing
 * permission or stopped monitor must never be reported as active protection.
 */
class DashboardStatusTest {

    private fun healthyConfig() = NexoConfig(
        hasMainPin = true,
        hasMainGesture = true,
        biometricEnabled = true,
        protectionEnabled = true,
        hasPersonalVaultPin = true,
        hasIntruderVaultPin = true,
    )

    private fun healthyPermissions() = PermissionManager.Health(
        overlay = PermissionManager.State.GRANTED,
        accessibility = PermissionManager.State.GRANTED,
        usage = PermissionManager.State.GRANTED,
        notifications = PermissionManager.State.GRANTED,
        batteryOptimized = false,
        serviceRunning = true,
        accessibilityConnected = true,
    )

    private fun build(
        config: NexoConfig = healthyConfig(),
        health: PermissionManager.Health = healthyPermissions(),
        protectionRunning: Boolean = true,
        biometric: BiometricState = BiometricState.READY,
        vaults: VaultOverview = VaultOverview(personalCount = 2, intruderCount = 1),
        capture: CaptureState = CaptureState(),
    ): List<DashboardStatus> = DashboardStatuses.build(
        config = config,
        health = health,
        protectionRunning = protectionRunning,
        biometric = biometric,
        vaults = vaults,
        capture = capture,
    )

    private fun List<DashboardStatus>.byTitle(title: String): DashboardStatus =
        first { it.title == title }

    @Test
    fun allSixStatusesAreAlwaysPresentInOrder() {
        val statuses = build()
        assertEquals(6, statuses.size)
        assertEquals(
            listOf(
                "Protection Active",
                "Permission Required",
                "Monitoring Unavailable",
                "Vault Secured",
                "Mandatory Factors",
                "Device Feature Unsupported",
            ),
            statuses.map { it.title },
        )
    }

    @Test
    fun healthyStateReportsEveryStatusAsHealthy() {
        val statuses = build()
        assertEquals("ACTIVE", statuses.byTitle("Protection Active").value)
        assertEquals("GRANTED", statuses.byTitle("Permission Required").value)
        assertEquals("NO", statuses.byTitle("Monitoring Unavailable").value)
        assertEquals("SECURED", statuses.byTitle("Vault Secured").value)
        assertEquals("COMPLETE", statuses.byTitle("Mandatory Factors").value)
        assertEquals("NONE", statuses.byTitle("Device Feature Unsupported").value)
        assertTrue(statuses.all { it.tone == StatusTone.OK })
    }

    @Test
    fun missingOverlayNeverReportsProtectionActive() {
        val statuses = build(
            health = healthyPermissions().copy(overlay = PermissionManager.State.MISSING),
        )
        val protection = statuses.byTitle("Protection Active")
        assertEquals("PERMISSION REQUIRED", protection.value)
        assertEquals(StatusTone.WARN, protection.tone)
        assertEquals(
            listOf("Display over other apps"),
            DashboardStatuses.missingRequiredPermissions(
                healthyPermissions().copy(overlay = PermissionManager.State.MISSING),
            ),
        )
        assertNotEquals("ACTIVE", protection.value)
        assertEquals("1 MISSING", statuses.byTitle("Permission Required").value)
    }

    @Test
    fun noDetectorReportsMonitoringUnavailableEvenWhenServiceRuns() {
        val health = healthyPermissions().copy(
            accessibility = PermissionManager.State.MISSING,
            usage = PermissionManager.State.MISSING,
        )
        val statuses = build(health = health)
        assertEquals("PERMISSION REQUIRED", statuses.byTitle("Protection Active").value)
        assertEquals("YES", statuses.byTitle("Monitoring Unavailable").value)
        assertEquals(StatusTone.BAD, statuses.byTitle("Monitoring Unavailable").tone)
    }

    @Test
    fun stoppedMonitorIsReportedAsUnavailableAndNotActive() {
        val statuses = build(protectionRunning = false)
        assertEquals("NOT RUNNING", statuses.byTitle("Protection Active").value)
        assertEquals(StatusTone.BAD, statuses.byTitle("Protection Active").tone)
        assertEquals("YES", statuses.byTitle("Monitoring Unavailable").value)
    }

    @Test
    fun disabledProtectionIsNotReportedAsRunning() {
        val statuses = build(config = healthyConfig().copy(protectionEnabled = false))
        assertEquals("OFF", statuses.byTitle("Protection Active").value)
        assertEquals("NOT EXPECTED", statuses.byTitle("Monitoring Unavailable").value)
        assertEquals(StatusTone.NEUTRAL, statuses.byTitle("Monitoring Unavailable").tone)
    }

    @Test
    fun enabledButRunningWithoutPermissionStillRequiresPermission() {
        val statuses = build(
            health = healthyPermissions().copy(
                overlay = PermissionManager.State.MISSING,
                serviceRunning = false,
            ),
            protectionRunning = false,
        )
        assertEquals("NOT RUNNING", statuses.byTitle("Protection Active").value)
        // Once the service stops, monitoring is unavailable before permissions.
        assertEquals("YES", statuses.byTitle("Monitoring Unavailable").value)
    }

    @Test
    fun vaultStatesAreTruthful() {
        val unconfigured = build(
            config = healthyConfig().copy(
                hasPersonalVaultPin = false,
                hasIntruderVaultPin = false,
            ),
        ).byTitle("Vault Secured")
        assertEquals("NOT CONFIGURED", unconfigured.value)
        assertEquals(StatusTone.NEUTRAL, unconfigured.tone)

        val personalOnly = build(
            config = healthyConfig().copy(hasIntruderVaultPin = false),
        ).byTitle("Vault Secured")
        assertEquals("PERSONAL ONLY", personalOnly.value)
        assertEquals(DashboardAction.INTRUDER_VAULT, personalOnly.action)

        val damaged = build(
            vaults = VaultOverview(personalCount = 1, personalCorrupted = true),
        ).byTitle("Vault Secured")
        assertEquals("DAMAGED", damaged.value)
        assertEquals(StatusTone.BAD, damaged.tone)
        assertEquals(DashboardAction.PERSONAL_VAULT, damaged.action)
    }

    @Test
    fun authenticationIsIncompleteWithoutEveryMandatoryFactor() {
        val noGesture = build(config = healthyConfig().copy(hasMainGesture = false))
            .byTitle("Mandatory Factors")
        assertEquals("INCOMPLETE", noGesture.value)
        assertEquals(DashboardAction.GESTURE, noGesture.action)

        val notEnrolled = build(biometric = BiometricState.NOT_ENROLLED)
            .byTitle("Mandatory Factors")
        assertEquals("INCOMPLETE", notEnrolled.value)

        val disabled = build(config = healthyConfig().copy(biometricEnabled = false))
            .byTitle("Mandatory Factors")
        assertEquals("INCOMPLETE", disabled.value)
    }

    @Test
    fun unsupportedBiometricIsDisclosedAsFallbackNotAsHealthy() {
        val statuses = build(biometric = BiometricState.UNSUPPORTED)
        val auth = statuses.byTitle("Mandatory Factors")
        assertEquals("FALLBACK", auth.value)
        assertNotEquals(StatusTone.OK, auth.tone)
        assertTrue(auth.detail.contains("Device Feature Unsupported"))

        val device = statuses.byTitle("Device Feature Unsupported")
        assertEquals("BIOMETRIC", device.value)
        assertEquals(StatusTone.WARN, device.tone)
    }

    @Test
    fun missingFrontCameraIsOnlyReportedAfterCapabilityProbe() {
        val config = healthyConfig().copy(
            intruderCaptureEnabled = true,
            hasIntruderVaultPin = true,
        )
        val unchecked = build(config = config, capture = CaptureState())
            .byTitle("Device Feature Unsupported")
        assertEquals("NONE", unchecked.value)

        val checked = build(
            config = config,
            capture = CaptureState(
                enabled = true,
                hasVaultPin = true,
                cameraPermission = true,
                cameraAvailable = false,
                checked = true,
            ),
        ).byTitle("Device Feature Unsupported")
        assertEquals("CAMERA", checked.value)
        assertEquals(StatusTone.WARN, checked.tone)
    }

    @Test
    fun notificationsOffIsAdvisoryAndNotClaimedAsGranted() {
        val statuses = build(
            health = healthyPermissions().copy(
                notifications = PermissionManager.State.MISSING,
            ),
        )
        val permission = statuses.byTitle("Permission Required")
        assertEquals("NOTIFICATIONS", permission.value)
        assertEquals(StatusTone.WARN, permission.tone)
        // Notifications do not gate the lock surface, so protection is still active.
        assertEquals("ACTIVE", statuses.byTitle("Protection Active").value)
    }
}
