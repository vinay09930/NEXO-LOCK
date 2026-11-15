package com.vinay.nexolock

import com.vinay.nexolock.vault.CaptureStatus
import com.vinay.nexolock.vault.IntruderIncidents
import com.vinay.nexolock.vault.VaultEntry
import com.vinay.nexolock.vault.VaultEntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntruderIncidentsTest {

    private fun entry(
        id: String,
        type: VaultEntryType = VaultEntryType.CAPTURE,
        title: String = "com.android.settings",
        body: String = "Wrong main PIN",
        createdAt: Long = 1_000L,
        attachmentId: String? = null,
        status: CaptureStatus? = CaptureStatus.CAPTURED,
        stage: String? = null,
        target: String? = null,
    ) = VaultEntry(
        id = id,
        type = type,
        title = title,
        body = body,
        createdAt = createdAt,
        attachmentId = attachmentId,
        mimeType = attachmentId?.let { "image/jpeg" },
        captureStatus = status,
        failureStage = stage,
        targetPackage = target,
    )

    @Test
    fun statsOnlyCountRealIncidentRecords() {
        val entries = listOf(
            entry("1", attachmentId = "a", createdAt = 10L),
            entry("2", attachmentId = null, status = CaptureStatus.PERMISSION_MISSING, createdAt = 20L),
            entry("3", attachmentId = "c", status = CaptureStatus.CAPTURED, createdAt = 30L),
            entry("4", type = VaultEntryType.NOTE, status = null, createdAt = 40L),
        )
        val stats = IntruderIncidents.stats(entries)
        assertEquals(3, stats.incidentCount)
        assertEquals(2, stats.capturedCount)
        assertEquals(1, stats.captureUnavailableCount)
        assertEquals(3, stats.failedAuthCount)
        assertEquals(30L, stats.latestIncidentAt)
        assertEquals(1, stats.noteCount)
    }

    @Test
    fun capturedCountRequiresBothStatusAndAttachment() {
        val entries = listOf(
            entry("1", status = CaptureStatus.CAPTURED, attachmentId = null),
            entry("2", status = CaptureStatus.FAILED, attachmentId = "image"),
        )
        val stats = IntruderIncidents.stats(entries)
        assertEquals(0, stats.capturedCount)
        assertEquals(2, stats.captureUnavailableCount)
    }

    @Test
    fun emptyHistoryHasHonestZeroes() {
        val stats = IntruderIncidents.stats(emptyList())
        assertEquals(0, stats.incidentCount)
        assertEquals(0, stats.capturedCount)
        assertEquals(0, stats.failedAuthCount)
        assertEquals(0L, stats.latestIncidentAt)
    }

    @Test
    fun legacyBodyPackageIsExtractedOnlyWhenItLooksLikeAPackage() {
        val body = "Wrong main PIN · com.android.settings · Camera permission is not granted."
        assertEquals("com.android.settings", IntruderIncidents.targetPackage(body))
        assertEquals(
            "Wrong main PIN · Camera permission is not granted.",
            IntruderIncidents.reasonText(body),
        )
        assertNull(
            IntruderIncidents.targetPackage(
                "Wrong main PIN · Camera permission is not granted.",
            ),
        )
    }

    @Test
    fun structuredFieldsTakePriorityOverLegacyText() {
        val incident = entry(
            id = "1",
            body = "Wrong main PIN · legacy.package · old reason",
            stage = "VOLUME_PATTERN",
            target = "new.package",
        )
        assertEquals("VOLUME_PATTERN", IntruderIncidents.displayStage(incident))
        assertEquals("new.package", IntruderIncidents.displayPackage(incident))
    }

    @Test
    fun failureStagesAreDerivedFromTheRecordedReason() {
        assertEquals("MAIN_PIN", IntruderIncidents.failureStage("Wrong main PIN"))
        assertEquals(
            "VOLUME_PATTERN",
            IntruderIncidents.failureStage("Wrong volume pattern"),
        )
        assertEquals("BIOMETRIC", IntruderIncidents.failureStage("Biometric denied: lockout"))
        assertEquals("STAGE_TIMEOUT", IntruderIncidents.failureStage("PIN stage timeout"))
        assertEquals("RECOVERY", IntruderIncidents.failureStage("Wrong recovery pattern"))
        assertEquals("AUTHENTICATION", IntruderIncidents.failureStage("Unexpected denial"))
    }

    @Test
    fun labelsAreHumanReadableAndNeverInventACapture() {
        assertEquals("Main PIN", IntruderIncidents.failureStageLabel("MAIN_PIN"))
        assertEquals("Volume pattern", IntruderIncidents.failureStageLabel("VOLUME_PATTERN"))
        assertEquals("Authentication", IntruderIncidents.failureStageLabel(null))
        assertEquals("Captured", IntruderIncidents.captureStatusLabel(CaptureStatus.CAPTURED))
        assertEquals(
            "Camera permission missing",
            IntruderIncidents.captureStatusLabel(CaptureStatus.PERMISSION_MISSING),
        )
        assertEquals(
            "Capture unavailable",
            IntruderIncidents.captureStatusLabel(null),
        )
    }
}
