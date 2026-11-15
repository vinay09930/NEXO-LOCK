package com.vinay.nexolock.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** One safe, structured event in an import attempt. */
data class ImportStageEvent(
    val stage: String,
    val at: Long,
    val detail: String? = null,
)

/**
 * Safe import diagnostics. Only metadata is recorded: no file content, no
 * encryption keys, no PINs, no full content:// URIs and no query parameters.
 *
 * The history is bounded and in-memory. It is intentionally never written to
 * the encrypted vault index, so it cannot grow the user's vault or leak into a
 * backup of vault metadata.
 */
data class ImportDiagnostic(
    val id: String,
    val startedAt: Long,
    val scheme: String?,
    val authority: String?,
    val mimeType: String,
    val displayName: String,
    val reportedSize: Long?,
    val permissionGranted: Boolean?,
    val stages: List<ImportStageEvent> = emptyList(),
    val status: String = VaultImportDiagnostics.RUNNING,
    val errorClass: String? = null,
    val errorMessage: String? = null,
)

object VaultImportDiagnostics {

    const val RUNNING = "RUNNING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"

    private const val MAX_ENTRIES = 25
    private const val MAX_MESSAGE = 200

    private val lock = Any()
    private val _history = MutableStateFlow<List<ImportDiagnostic>>(emptyList())
    val history: StateFlow<List<ImportDiagnostic>> = _history.asStateFlow()

    fun begin(
        scheme: String?,
        authority: String?,
        mimeType: String,
        displayName: String,
        reportedSize: Long?,
        permissionGranted: Boolean?,
        now: Long = System.currentTimeMillis(),
    ): ImportDiagnostic {
        val diagnostic = ImportDiagnostic(
            id = UUID.randomUUID().toString(),
            startedAt = now,
            scheme = scheme,
            authority = authority,
            mimeType = mimeType,
            displayName = sanitizeName(displayName),
            reportedSize = reportedSize,
            permissionGranted = permissionGranted,
            stages = listOf(ImportStageEvent("PICKER_RESULT", now)),
        )
        synchronized(lock) {
            _history.value = (listOf(diagnostic) + _history.value).take(MAX_ENTRIES)
        }
        return diagnostic
    }

    fun stage(id: String, stage: String, detail: String? = null, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            _history.value = _history.value.map { current ->
                if (current.id != id) {
                    current
                } else {
                    current.copy(
                        stages = current.stages + ImportStageEvent(
                            stage = stage,
                            at = now,
                            detail = detail?.let(::sanitizeMessage),
                        ),
                    )
                }
            }
        }
    }

    fun finish(
        id: String,
        status: String,
        errorClass: String? = null,
        errorMessage: String? = null,
    ) {
        synchronized(lock) {
            _history.value = _history.value.map { current ->
                if (current.id != id) {
                    current
                } else {
                    current.copy(
                        status = status,
                        errorClass = errorClass,
                        errorMessage = errorMessage?.let(::sanitizeMessage),
                    )
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) { _history.value = emptyList() }
    }

    fun snapshot(): List<ImportDiagnostic> = synchronized(lock) { _history.value }

    /** Safe file name for diagnostics and logcat. */
    fun sanitizeName(raw: String?): String {
        if (raw.isNullOrBlank()) return "(unnamed)"
        return sanitizeDisplayName(raw).take(80).ifBlank { "(unnamed)" }
    }

    /**
     * Removes control characters, query strings, local file paths and common
     * secret-bearing key/value pairs from an exception message.
     */
    fun sanitizeMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var value = raw.filterNot { it.isISOControl() }
        value = value.replace(
            Regex("(?i)((?:key|pin|token|secret|password|passphrase)\\s*=\\s*)[^\\s&;]+"),
            "$1[redacted]",
        )
        value = value.replace(Regex("(content://[^\\s?]+)\\?[^\\s]*"), "$1?[redacted]")
        value = value.replace(Regex("file://[^\\s]+"), "[local path]")
        return value.take(MAX_MESSAGE)
    }
}
