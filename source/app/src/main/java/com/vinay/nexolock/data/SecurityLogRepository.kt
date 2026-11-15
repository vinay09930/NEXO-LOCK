package com.vinay.nexolock.data

import android.content.Context
import android.util.Log
import com.vinay.nexolock.core.crypto.KeystoreCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Base64

enum class LogSeverity { INFO, SUCCESS, WARNING, DANGER }

enum class LogGroup { UNLOCKS, FAILURES, SYSTEM }

enum class LogType(
    val title: String,
    val severity: LogSeverity,
    val group: LogGroup,
) {
    SETUP_COMPLETED("Security setup completed", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    PIN_CHANGED("Main PIN changed", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    RECOVERY_CHANGED("Recovery credential changed", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    GESTURE_CONFIGURED("Volume pattern configuration changed", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    PROTECTION_STARTED("Protection started", LogSeverity.INFO, LogGroup.SYSTEM),
    PROTECTION_STOPPED("Protection stopped", LogSeverity.WARNING, LogGroup.SYSTEM),
    PROTECTION_PAUSED("Protection needs attention", LogSeverity.WARNING, LogGroup.SYSTEM),
    PERMISSION_MISSING("Required permission missing", LogSeverity.WARNING, LogGroup.SYSTEM),
    SERVICE_ERROR("Protection service error", LogSeverity.DANGER, LogGroup.SYSTEM),
    APP_LOCKED("Protected app locked", LogSeverity.INFO, LogGroup.UNLOCKS),
    APP_UNLOCKED("Protected app unlocked", LogSeverity.SUCCESS, LogGroup.UNLOCKS),
    RECOVERY_UNLOCKED("Recovery unlock used", LogSeverity.WARNING, LogGroup.UNLOCKS),
    BIOMETRIC_SUCCESS("Biometric verified", LogSeverity.SUCCESS, LogGroup.UNLOCKS),
    BIOMETRIC_FAILED("Biometric rejected", LogSeverity.WARNING, LogGroup.FAILURES),
    BIOMETRIC_UNAVAILABLE("Biometric unavailable", LogSeverity.WARNING, LogGroup.FAILURES),
    MAIN_PIN_FAILED("Main PIN rejected", LogSeverity.WARNING, LogGroup.FAILURES),
    GESTURE_FAILED("Volume pattern rejected", LogSeverity.WARNING, LogGroup.FAILURES),
    RECOVERY_FAILED("Recovery credential rejected", LogSeverity.DANGER, LogGroup.FAILURES),
    LOCKOUT("Credential lockout triggered", LogSeverity.DANGER, LogGroup.FAILURES),
    LOG_CLEARED("Security log cleared", LogSeverity.WARNING, LogGroup.SYSTEM),
    // --- V3.1 decoy, mandatory factors, vaults and capture ---
    DECOY_SHOWN("Decoy lock shown", LogSeverity.INFO, LogGroup.UNLOCKS),
    DECOY_WAIT("Decoy: Wait chosen", LogSeverity.INFO, LogGroup.UNLOCKS),
    DECOY_CLOSE("Decoy: Close app chosen", LogSeverity.WARNING, LogGroup.UNLOCKS),
    AUTH_SESSION_STARTED("Factor session started", LogSeverity.INFO, LogGroup.UNLOCKS),
    AUTH_FACTOR_OK("Factor verified", LogSeverity.SUCCESS, LogGroup.UNLOCKS),
    AUTH_SESSION_EXPIRED("Authentication deadline expired", LogSeverity.WARNING, LogGroup.FAILURES),
    AUTH_SESSION_CANCELLED("Authentication session cancelled", LogSeverity.WARNING, LogGroup.FAILURES),
    AUTH_COMPLETED("All required factors verified", LogSeverity.SUCCESS, LogGroup.UNLOCKS),
    VAULT_CREATED("Personal Vault created", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    VAULT_UNLOCKED("Personal Vault unlocked", LogSeverity.SUCCESS, LogGroup.UNLOCKS),
    VAULT_LOCKED("Personal Vault locked", LogSeverity.INFO, LogGroup.SYSTEM),
    VAULT_ENTRY_ADDED("Personal Vault entry added", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    VAULT_ENTRY_DELETED("Personal Vault entry deleted", LogSeverity.INFO, LogGroup.SYSTEM),
    VAULT_PIN_CHANGED("Personal Vault PIN changed", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    VAULT_DELETED("Personal Vault deleted", LogSeverity.WARNING, LogGroup.SYSTEM),
    VAULT_CORRUPTED("Vault integrity check failed", LogSeverity.DANGER, LogGroup.SYSTEM),
    INTRUDER_VAULT_CREATED("Intruder Vault created", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    INTRUDER_VAULT_UNLOCKED("Intruder Vault unlocked", LogSeverity.WARNING, LogGroup.UNLOCKS),
    INTRUDER_VAULT_LOCKED("Intruder Vault locked", LogSeverity.INFO, LogGroup.SYSTEM),
    INTRUDER_VAULT_PIN_CHANGED("Intruder Vault PIN changed", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    INTRUDER_VAULT_DELETED("Intruder Vault deleted", LogSeverity.WARNING, LogGroup.SYSTEM),
    INTRUDER_VAULT_CORRUPTED("Intruder Vault integrity check failed", LogSeverity.DANGER, LogGroup.SYSTEM),
    INTRUDER_ENTRY_ADDED("Intruder Vault entry added", LogSeverity.WARNING, LogGroup.SYSTEM),
    INTRUDER_ENTRY_DELETED("Intruder Vault entry deleted", LogSeverity.INFO, LogGroup.SYSTEM),
    INTRUDER_ENTRY_OPENED("Intruder capture opened", LogSeverity.INFO, LogGroup.SYSTEM),
    INTRUDER_CAPTURE("Intruder capture succeeded", LogSeverity.WARNING, LogGroup.FAILURES),
    INTRUDER_CAPTURE_FAILED("Intruder capture failed", LogSeverity.DANGER, LogGroup.FAILURES),
    INTRUDER_HISTORY_CLEARED("Intruder Vault history cleared", LogSeverity.WARNING, LogGroup.SYSTEM),
    DEGRADED_PROTECTION("Protection degraded", LogSeverity.WARNING, LogGroup.SYSTEM),
    CAPTURE_SETTING_CHANGED("Intruder capture setting changed", LogSeverity.INFO, LogGroup.SYSTEM),
    // --- V5 streaming vault files ---
    VAULT_FILE_IMPORTED("Vault file encrypted", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    VAULT_FILE_IMPORT_FAILED("Vault file import failed", LogSeverity.WARNING, LogGroup.FAILURES),
    VAULT_FILE_VERIFIED("Vault file integrity verified", LogSeverity.SUCCESS, LogGroup.SYSTEM),
    VAULT_FILE_DELETED("Vault file deleted", LogSeverity.INFO, LogGroup.SYSTEM),
    VAULT_FILE_OPENED("Vault file opened for preview", LogSeverity.INFO, LogGroup.SYSTEM),
    VAULT_FILE_RENAMED("Vault item renamed", LogSeverity.INFO, LogGroup.SYSTEM),
    VAULT_FILE_EXPORTED("Vault file exported", LogSeverity.WARNING, LogGroup.SYSTEM),
    // --- V1.1.4: verified export, opt-in original deletion, layout ---
    VAULT_EXPORT_FAILED("Vault export failed", LogSeverity.WARNING, LogGroup.FAILURES),
    VAULT_EXPORT_CANCELLED("Vault export cancelled", LogSeverity.WARNING, LogGroup.SYSTEM),
    VAULT_ORIGINAL_DELETED("Original deleted after import", LogSeverity.WARNING, LogGroup.SYSTEM),
    VAULT_ORIGINAL_DELETE_FAILED("Original file could not be deleted", LogSeverity.WARNING, LogGroup.FAILURES),
    VAULT_AUTO_DELETE_CHANGED("Delete-originals setting changed", LogSeverity.INFO, LogGroup.SYSTEM),
    // --- V5 monitoring lifecycle ---
    MONITORING_CONNECTED("App-switch monitoring connected", LogSeverity.INFO, LogGroup.SYSTEM),
    MONITORING_INTERRUPTED("App-switch monitoring interrupted", LogSeverity.WARNING, LogGroup.SYSTEM),
}

data class LogEntry(
    val timestamp: Long,
    val type: LogType,
    val method: String,
    val message: String,
    val tampered: Boolean = false,
)

data class LogReadResult(
    val entries: List<LogEntry>,
    val corruptedRecords: Int = 0,
) {
    val tamperedCount: Int get() = entries.count { it.tampered }
}

/**
 * Append-only security journal, encrypted with Android Keystore AES-GCM.
 * Each record also stores the SHA-256 of the previous ciphertext line, so
 * deleted or re-ordered records are detectable on read.
 */
class SecurityLogRepository(
    context: Context,
    private val cipher: KeystoreCipher,
) {

    private val directory = File(context.filesDir, "security")
    private val file = File(directory, "events.log")
    private val mutex = Mutex()

    private val _writeError = MutableStateFlow<String?>(null)

    /** Non-null when the most recent append failed; cleared by a successful append. */
    val writeError: StateFlow<String?> = _writeError.asStateFlow()

    /**
     * Appends one encrypted record. Returns false when the write failed and
     * exposes the reason through [writeError]; a security journal must never
     * silently lose events.
     */
    suspend fun log(type: LogType, message: String, method: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching { append(type, message, method) }
                    .onSuccess { _writeError.value = null }
                    .onFailure { error ->
                        _writeError.value = error.message ?: error.javaClass.simpleName
                        Log.e("NexoLock", "Security journal write failed", error)
                    }
                    .isSuccess
            }
        }

    suspend fun read(): LogReadResult = withContext(Dispatchers.IO) {
        mutex.withLock { readInternal() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { file.delete() }
            Unit
        }
    }

    private fun append(type: LogType, message: String, method: String) {
        directory.mkdirs()
        val lines = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
        val previous = lines.lastOrNull() ?: GENESIS
        val record = buildString {
            append(System.currentTimeMillis())
            append('|')
            append(type.name)
            append('|')
            append(b64(method))
            append('|')
            append(b64(message))
            append('|')
            append(sha256(previous))
        }
        val encrypted = cipher.encrypt(record.toByteArray(Charsets.UTF_8), AAD)
        val encoded = Base64.getEncoder().encodeToString(encrypted)
        // Append and fsync so a process death right after a successful log call
        // cannot lose the record that the caller was told was written.
        FileOutputStream(file, true).use { stream ->
            stream.write((encoded + "\n").toByteArray(Charsets.UTF_8))
            stream.flush()
            stream.fd.sync()
        }
        if (lines.size + 1 > MAX_RECORDS) {
            val kept = (lines + encoded).takeLast(KEEP_RECORDS)
            val temp = File(directory, "events.tmp")
            runCatching {
                FileOutputStream(temp).use { stream ->
                    stream.write(
                        kept.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8),
                    )
                    stream.flush()
                    stream.fd.sync()
                }
                if (!temp.renameTo(file)) {
                    temp.delete()
                    error("The journal could not be replaced atomically.")
                }
            }
        }
    }

    private fun readInternal(): LogReadResult {
        if (!file.exists()) return LogReadResult(emptyList())
        val lines = file.readLines().filter { it.isNotBlank() }
        val entries = ArrayList<LogEntry>(lines.size)
        var corrupted = 0
        var previousRaw: String? = null
        for (raw in lines) {
            val record = runCatching {
                cipher.decrypt(Base64.getDecoder().decode(raw), AAD).toString(Charsets.UTF_8)
            }.getOrNull()
            if (record == null) {
                corrupted++
                previousRaw = raw
                continue
            }
            val parts = record.split('|')
            if (parts.size != 5) {
                corrupted++
                previousRaw = raw
                continue
            }
            val timestamp = parts[0].toLongOrNull() ?: 0L
            val type = LogType.entries.firstOrNull { it.name == parts[1] } ?: LogType.SERVICE_ERROR
            val method = runCatching { unb64(parts[2]) }.getOrDefault("")
            val message = runCatching { unb64(parts[3]) }.getOrDefault("")
            val chainOk = previousRaw == null || parts[4] == sha256(previousRaw)
            entries += LogEntry(
                timestamp = timestamp,
                type = type,
                method = method,
                message = message,
                tampered = !chainOk,
            )
            previousRaw = raw
        }
        return LogReadResult(entries.sortedByDescending { it.timestamp }, corrupted)
    }

    companion object {
        private const val MAX_RECORDS = 2_000
        private const val KEEP_RECORDS = 1_000
        private const val GENESIS = "nexo-lock-genesis-v1"
        private val AAD = "com.vinay.nexolock.log.v1".toByteArray(Charsets.UTF_8)

        private fun b64(value: String): String =
            Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

        private fun unb64(value: String): String =
            String(Base64.getDecoder().decode(value), Charsets.UTF_8)

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
