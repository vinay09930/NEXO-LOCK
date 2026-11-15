package com.vinay.nexolock

import com.vinay.nexolock.data.NexoConfig
import com.vinay.nexolock.protection.PermissionManager
import com.vinay.nexolock.ui.BiometricState
import com.vinay.nexolock.ui.DashboardStatuses
import com.vinay.nexolock.ui.MonitoringState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the truthful protection-setting labels used by the v1.1.2 dashboard.
 * "Enabled" must never be shown unless the feature is configured AND every
 * observable precondition is currently satisfied.
 */
class MonitoringStateTest {

    private fun config() = NexoConfig(
        hasMainPin = true,
        hasMainGesture = true,
        mainGestureLength = 4,
        gestureEnabled = true,
        biometricEnabled = true,
        protectionEnabled = true,
    )

    private fun health() = PermissionManager.Health(
        overlay = PermissionManager.State.GRANTED,
        accessibility = PermissionManager.State.GRANTED,
        usage = PermissionManager.State.GRANTED,
        notifications = PermissionManager.State.GRANTED,
        serviceRunning = true,
        accessibilityConnected = true,
    )

    @Test
    fun monitoringIsEnabledOnlyWhenConfiguredRunningAndPermitted() {
        assertEquals(
            MonitoringState.ENABLED,
            DashboardStatuses.monitoringState(config(), health(), protectionRunning = true),
        )
        assertEquals(
            "Enabled",
            DashboardStatuses.monitoringState(config(), health(), protectionRunning = true).label,
        )
    }

    @Test
    fun monitoringIsNotConfiguredWithoutTheMandatoryFactors() {
        assertEquals(
            MonitoringState.NOT_CONFIGURED,
            DashboardStatuses.monitoringState(
                config().copy(hasMainGesture = false),
                health(),
                protectionRunning = true,
            ),
        )
        assertEquals(
            MonitoringState.NOT_CONFIGURED,
            DashboardStatuses.monitoringState(
                config().copy(protectionEnabled = false),
                health(),
                protectionRunning = true,
            ),
        )
    }

    @Test
    fun missingOverlayRequiresPermissionInsteadOfClaimingEnabled() {
        val state = DashboardStatuses.monitoringState(
            config(),
            health().copy(overlay = PermissionManager.State.MISSING),
            protectionRunning = true,
        )
        assertEquals(MonitoringState.PERMISSION_REQUIRED, state)
        assertEquals("Permission required", state.label)
    }

    @Test
    fun stoppedMonitorIsTemporarilyUnavailable() {
        assertEquals(
            MonitoringState.TEMPORARILY_UNAVAILABLE,
            DashboardStatuses.monitoringState(config(), health(), protectionRunning = false),
        )
        assertEquals(
            MonitoringState.TEMPORARILY_UNAVAILABLE,
            DashboardStatuses.monitoringState(
                config(),
                health().copy(serviceRunning = false),
                protectionRunning = true,
            ),
        )
    }

    @Test
    fun disconnectedAccessibilityWithoutUsageFallbackIsTemporarilyUnavailable() {
        assertEquals(
            MonitoringState.TEMPORARILY_UNAVAILABLE,
            DashboardStatuses.monitoringState(
                config(),
                health().copy(
                    accessibilityConnected = false,
                    usage = PermissionManager.State.MISSING,
                ),
                protectionRunning = true,
            ),
        )
    }

    @Test
    fun noDetectionChannelAtAllIsUnsupported() {
        assertEquals(
            MonitoringState.UNSUPPORTED,
            DashboardStatuses.monitoringState(
                config(),
                health().copy(
                    accessibility = PermissionManager.State.UNSUPPORTED,
                    usage = PermissionManager.State.UNSUPPORTED,
                ),
                protectionRunning = true,
            ),
        )
    }

    @Test
    fun volumePatternRequiresConfiguredValidLength() {
        assertEquals(
            MonitoringState.NOT_CONFIGURED,
            DashboardStatuses.volumePatternState(config().copy(gestureEnabled = false)),
        )
        assertEquals(
            MonitoringState.TEMPORARILY_UNAVAILABLE,
            DashboardStatuses.volumePatternState(config().copy(mainGestureLength = 3)),
        )
        assertEquals(
            MonitoringState.TEMPORARILY_UNAVAILABLE,
            DashboardStatuses.volumePatternState(config().copy(mainGestureLength = 9)),
        )
        assertEquals(
            MonitoringState.ENABLED,
            DashboardStatuses.volumePatternState(config()),
        )
    }

    @Test
    fun biometricReportsUnsupportedBeforeNotConfigured() {
        assertEquals(
            MonitoringState.UNSUPPORTED,
            DashboardStatuses.biometricState(config(), BiometricState.UNSUPPORTED),
        )
        assertEquals(
            MonitoringState.NOT_CONFIGURED,
            DashboardStatuses.biometricState(config(), BiometricState.NOT_ENROLLED),
        )
        assertEquals(
            MonitoringState.NOT_CONFIGURED,
            DashboardStatuses.biometricState(
                config().copy(biometricEnabled = false),
                BiometricState.READY,
            ),
        )
        assertEquals(
            MonitoringState.ENABLED,
            DashboardStatuses.biometricState(config(), BiometricState.READY),
        )
    }

    @Test
    fun permissionRowsUseTheTruthfulLabels() {
        assertEquals(
            MonitoringState.PERMISSION_REQUIRED,
            DashboardStatuses.overlayState(
                health().copy(overlay = PermissionManager.State.RESTRICTED),
            ),
        )
        assertEquals(
            MonitoringState.UNSUPPORTED,
            DashboardStatuses.overlayState(
                health().copy(overlay = PermissionManager.State.UNSUPPORTED),
            ),
        )
        assertEquals(
            MonitoringState.ENABLED,
            DashboardStatuses.accessibilityState(health()),
        )
        assertEquals(
            MonitoringState.PERMISSION_REQUIRED,
            DashboardStatuses.accessibilityState(
                health().copy(accessibility = PermissionManager.State.MISSING),
            ),
        )
        assertEquals(
            MonitoringState.TEMPORARILY_UNAVAILABLE,
            DashboardStatuses.accessibilityState(health().copy(accessibilityConnected = false)),
        )
        assertEquals(
            MonitoringState.NOT_CONFIGURED,
            DashboardStatuses.protectedAppsState(0),
        )
        assertEquals(
            MonitoringState.ENABLED,
            DashboardStatuses.protectedAppsState(3),
        )
    }
}
