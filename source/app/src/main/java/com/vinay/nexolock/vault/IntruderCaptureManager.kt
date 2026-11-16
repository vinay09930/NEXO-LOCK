package com.vinay.nexolock.vault

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.vinay.nexolock.data.LogType
import com.vinay.nexolock.data.NexoConfig
import com.vinay.nexolock.data.NexoPreferences
import com.vinay.nexolock.data.SecurityLogRepository
import com.vinay.nexolock.protection.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CaptureState(
    val inProgress: Boolean = false,
    val enabled: Boolean = false,
    val hasVaultPin: Boolean = false,
    val cameraPermission: Boolean = false,
    val cameraAvailable: Boolean = false,
    /** False until the device capabilities were actually probed. */
    val checked: Boolean = false,
    /** True only when capture is enabled, a vault PIN exists and a front camera works. */
    val ready: Boolean = false,
    val lastStatus: CaptureStatus? = null,
    val lastMessage: String? = null,
    val lastAttemptAt: Long = 0L,
    val attemptCount: Int = 0,
)

/**
 * Captures the front camera after failed unlock attempts when the owner enabled
 * it. Every outcome (including permission missing / camera busy / no sensor) is
 * written as a visible Intruder Vault record, so a failed capture is auditable
 * rather than hidden.
 *
 * Capture is throttled and serialised; a lock screen cannot start a camera storm.
 */
class IntruderCaptureManager(
    context: Context,
    private val prefs: NexoPreferences,
    private val vault: VaultManager,
    private val logs: SecurityLogRepository,
    private val scope: CoroutineScope,
) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private var lastStartedAt = 0L

    fun requestCapture(reason: String, targetLabel: String) {
        scope.launch { capture(reason, targetLabel) }
    }

    /**
     * Recomputes what the device can actually do right now. Called when the
     * Intruder Vault opens and before every attempt, so the UI can never claim
     * capture is ready when the permission or the front camera is missing.
     */
    suspend fun refreshReadiness() {
        applyReadiness(prefs.snapshot())
    }

    suspend fun capture(reason: String, targetLabel: String) {
        val config = prefs.snapshot()
        applyReadiness(config)
        if (!config.intruderCaptureEnabled || !config.hasIntruderVaultPin) {
            _state.update {
                it.copy(
                    inProgress = false,
                    lastStatus = CaptureStatus.DISABLED,
                    lastMessage = if (!config.hasIntruderVaultPin) {
                        "Create the Intruder Vault PIN first."
                    } else {
                        "Capture is off."
                    },
                )
            }
            return
        }
        val incidentReason = IntruderIncidents.reasonText(reason)
        val incidentPackage = IntruderIncidents.targetPackage(reason)
        val incidentStage = IntruderIncidents.failureStage(incidentReason)
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastStartedAt < THROTTLE_MS) return@withLock
            lastStartedAt = now
            _state.update {
                it.copy(inProgress = true, lastAttemptAt = now, attemptCount = it.attemptCount + 1)
            }
            val result = if (!PermissionManager.hasCameraPermission(appContext)) {
                CameraCapture.CaptureResult.Failure(
                    CaptureStatus.PERMISSION_MISSING,
                    "Camera permission is not granted.",
                )
            } else {
                CameraCapture.captureFront(appContext)
            }

            when (result) {
                is CameraCapture.CaptureResult.Success -> {
                    val writeError = vault.addCapture(
                        reason = incidentReason,
                        targetLabel = targetLabel,
                        status = CaptureStatus.CAPTURED,
                        jpeg = result.jpeg,
                        failureStage = incidentStage,
                        targetPackage = incidentPackage,
                    )
                    if (writeError == null) {
                        _state.update {
                            it.copy(
                                inProgress = false,
                                lastStatus = CaptureStatus.CAPTURED,
                                lastMessage = "Encrypted capture stored (${result.jpeg.size / 1024} KB).",
                            )
                        }
                        logs.log(LogType.INTRUDER_CAPTURE, "Intruder capture stored for $targetLabel", reason)
                    } else {
                        fail(
                            CaptureStatus.FAILED,
                            writeError,
                            incidentReason,
                            targetLabel,
                            incidentStage,
                            incidentPackage,
                        )
                    }
                }

                is CameraCapture.CaptureResult.Failure -> {
                    fail(
                        result.status,
                        result.message,
                        incidentReason,
                        targetLabel,
                        incidentStage,
                        incidentPackage,
                    )
                }
            }
        }
    }

    private suspend fun fail(
        status: CaptureStatus,
        message: String,
        reason: String,
        targetLabel: String,
        failureStage: String,
        targetPackage: String?,
    ) {
        val body = if (reason.isBlank()) message else "$reason · $message"
        val writeError = vault.addCapture(
            reason = body,
            targetLabel = targetLabel,
            status = status,
            failureStage = failureStage,
            targetPackage = targetPackage,
        )
        _state.update {
            it.copy(
                inProgress = false,
                lastStatus = status,
                lastMessage = message,
            )
        }
        logs.log(
            LogType.INTRUDER_CAPTURE_FAILED,
            "Intruder capture failed: $message" + if (writeError != null) " · $writeError" else "",
            reason,
        )
    }

    private fun applyReadiness(config: NexoConfig) {
        val permission = PermissionManager.hasCameraPermission(appContext)
        val camera = permission && hasFrontCamera()
        _state.update {
            it.copy(
                enabled = config.intruderCaptureEnabled,
                hasVaultPin = config.hasIntruderVaultPin,
                cameraPermission = permission,
                cameraAvailable = camera,
                checked = true,
                ready = config.intruderCaptureEnabled &&
                    config.hasIntruderVaultPin &&
                    camera,
            )
        }
    }

    private fun hasFrontCamera(): Boolean = runCatching {
        val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return@runCatching false
        manager.cameraIdList.any { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        }
    }.getOrDefault(false)

    private companion object {
        const val THROTTLE_MS = 10_000L
    }
}
