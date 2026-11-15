package com.vinay.nexolock.vault

/** Aggregated, honest Intruder Vault counters derived only from stored records. */
data class IntruderIncidentStats(
    val incidentCount: Int = 0,
    val capturedCount: Int = 0,
    val captureUnavailableCount: Int = 0,
    val failedAuthCount: Int = 0,
    val latestIncidentAt: Long = 0L,
    val noteCount: Int = 0,
)

/**
 * Pure incident helpers. V1.1.2 stored the target package inside the free-text
 * body ("Wrong main PIN · com.android.settings · Camera permission is not
 * granted."). V1.1.3 writes structured fields, but old records are read
 * without inventing data: the package is only extracted when it is genuinely
 * present and looks like a package name.
 */
object IntruderIncidents {

    private val PACKAGE = Regex(
        """(?:^| · )([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)(?: · |$)""",
    )

    fun stats(entries: List<VaultEntry>): IntruderIncidentStats {
        val incidents = entries.filter { it.type == VaultEntryType.CAPTURE }
        val captured = incidents.count {
            it.captureStatus == CaptureStatus.CAPTURED && it.attachmentId != null
        }
        return IntruderIncidentStats(
            incidentCount = incidents.size,
            capturedCount = captured,
            captureUnavailableCount = incidents.size - captured,
            failedAuthCount = incidents.size,
            latestIncidentAt = incidents.maxOfOrNull { it.createdAt } ?: 0L,
            noteCount = entries.count { it.type == VaultEntryType.NOTE },
        )
    }

    /** Extracts a package name only when the body actually contains one. */
    fun targetPackage(body: String): String? =
        PACKAGE.find(body)?.groupValues?.getOrNull(1)

    /** Removes the package segment from the human-readable reason. */
    fun reasonText(body: String): String {
        val packageName = targetPackage(body) ?: return body
        return body.split(" · ")
            .filterNot { it == packageName }
            .joinToString(" · ")
            .ifBlank { body }
    }

    /** Stage recorded for new incidents, or derived for legacy free-text rows. */
    fun failureStage(body: String): String {
        val reason = reasonText(body)
        return when {
            reason.contains("Wrong main PIN", ignoreCase = true) -> "MAIN_PIN"
            reason.contains("volume pattern", ignoreCase = true) -> "VOLUME_PATTERN"
            reason.contains("biometric", ignoreCase = true) -> "BIOMETRIC"
            reason.contains("timeout", ignoreCase = true) -> "STAGE_TIMEOUT"
            reason.contains("recovery", ignoreCase = true) -> "RECOVERY"
            else -> "AUTHENTICATION"
        }
    }

    fun failureStageLabel(stage: String?): String = when (stage) {
        "MAIN_PIN" -> "Main PIN"
        "VOLUME_PATTERN" -> "Volume pattern"
        "BIOMETRIC" -> "Biometric"
        "STAGE_TIMEOUT" -> "Stage timeout"
        "RECOVERY" -> "Recovery"
        "AUTHENTICATION", null, "" -> "Authentication"
        else -> stage
    }

    fun captureStatusLabel(status: CaptureStatus?): String = when (status) {
        CaptureStatus.CAPTURED -> "Captured"
        CaptureStatus.PERMISSION_MISSING -> "Camera permission missing"
        CaptureStatus.CAMERA_UNAVAILABLE -> "Camera unavailable"
        CaptureStatus.CAMERA_BUSY -> "Camera busy"
        CaptureStatus.FAILED -> "Capture failed"
        CaptureStatus.DISABLED -> "Capture disabled"
        null -> "Capture unavailable"
    }

    fun displayStage(entry: VaultEntry): String =
        entry.failureStage?.takeIf { it.isNotBlank() } ?: failureStage(entry.body)

    fun displayPackage(entry: VaultEntry): String? =
        entry.targetPackage?.takeIf { it.isNotBlank() } ?: targetPackage(entry.body)
}
