package com.vinay.nexolock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vinay.nexolock.core.crypto.PinHasher
import com.vinay.nexolock.domain.CredentialKind
import com.vinay.nexolock.domain.LockoutPolicy
import com.vinay.nexolock.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.nexoDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexo_prefs")

/** Persisted Personal Vault presentation. Purely visual; never affects data. */
enum class VaultLayout {
    GRID,
    LIST,
    ;

    companion object {
        fun fromKey(value: String?): VaultLayout =
            entries.firstOrNull { it.name == value } ?: GRID
    }
}

data class NexoConfig(
    val onboarded: Boolean = false,
    val setupComplete: Boolean = false,
    val setupStep: Int = 0,
    val pinLength: Int = 6,
    val mainGestureLength: Int = 0,
    val biometricEnabled: Boolean = false,
    val gestureEnabled: Boolean = false,
    val strictMode: Boolean = false,
    val protectionEnabled: Boolean = false,
    val privacyCover: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val mainFailures: Int = 0,
    val mainLockoutUntil: Long = 0L,
    val recoveryFailures: Int = 0,
    val recoveryLockoutUntil: Long = 0L,
    val hasMainPin: Boolean = false,
    val hasRecoveryPin: Boolean = false,
    val hasMainGesture: Boolean = false,
    val hasRecoveryPattern: Boolean = false,
    // --- V3.1: vaults, decoy and capture settings; V5 removes the
    // single-factor toggle — all factors are always mandatory ---
    val decoyEnabled: Boolean = true,
    /** How long the decoy stays before the session is denied. */
    val decoyTimeoutSeconds: Int = 4,
    /** Fixed window for the volume-pattern stage. */
    val gestureTimeoutSeconds: Int = 15,
    /** Fixed window for the master-PIN stage. */
    val pinTimeoutSeconds: Int = 15,
    val personalVaultPinLength: Int = 6,
    val intruderVaultPinLength: Int = 6,
    val hasPersonalVaultPin: Boolean = false,
    val hasIntruderVaultPin: Boolean = false,
    val intruderCaptureEnabled: Boolean = false,
    // --- V1.1.4: Personal Vault presentation and opt-in original deletion ---
    val vaultLayout: VaultLayout = VaultLayout.GRID,
    val deleteOriginalsAfterImport: Boolean = false,
    val schemaVersion: Int = CURRENT_SCHEMA,
) {
    companion object {
        /** Bumped when a DataStore key changes meaning. Vault files carry their own version. */
        const val CURRENT_SCHEMA = 2
    }
}

/**
 * Single source of truth for all non-secret state and for credential verifiers.
 * Verifiers are one-way PBKDF2 values; the original PIN/pattern is never stored.
 */
class NexoPreferences(context: Context) {

    private val store = context.nexoDataStore

    val raw: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
        else throw error
    }

    val config: Flow<NexoConfig> = raw.map { it.toConfig() }.distinctUntilChanged()

    suspend fun snapshot(): NexoConfig = config.first()

    val protectedBlob: Flow<String?> = raw.map { it[K.PROTECTED_BLOB] }.distinctUntilChanged()

    suspend fun protectedBlobSnapshot(): String? = raw.map { it[K.PROTECTED_BLOB] }.first()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { prefs -> block(prefs) }
    }

    suspend fun setOnboarded(value: Boolean) = edit { it[K.ONBOARDED] = value }

    suspend fun setSetupComplete(value: Boolean) = edit { it[K.SETUP_COMPLETE] = value }

    suspend fun setSetupStep(step: Int) = edit { it[K.SETUP_STEP] = step.coerceIn(0, SETUP_STEPS - 1) }

    suspend fun setPinLength(length: Int) = edit { it[K.PIN_LENGTH] = length.coerceIn(4, 8) }

    suspend fun setGestureLength(length: Int) = edit {
        it[K.MAIN_GESTURE_LENGTH] = length.coerceIn(0, 8)
    }

    suspend fun setBiometricEnabled(value: Boolean) = edit { it[K.BIOMETRIC] = value }

    suspend fun setGestureEnabled(value: Boolean) = edit { it[K.GESTURE_ENABLED] = value }

    suspend fun setStrictMode(value: Boolean) = edit { it[K.STRICT] = value }

    suspend fun setProtectionEnabled(value: Boolean) = edit { it[K.PROTECTION] = value }

    suspend fun setPrivacyCover(value: Boolean) = edit { it[K.PRIVACY_COVER] = value }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[K.THEME] = mode.name }

    suspend fun setProtectedBlob(value: String) = edit { it[K.PROTECTED_BLOB] = value }

    suspend fun setDecoyEnabled(value: Boolean) = edit { it[K.DECOY_ENABLED] = value }

    suspend fun setDecoyTimeoutSeconds(value: Int) = edit {
        it[K.DECOY_TIMEOUT_SECONDS] = value.coerceIn(2, 10)
    }

    suspend fun setGestureTimeoutSeconds(value: Int) = edit {
        it[K.GESTURE_TIMEOUT_SECONDS] = value.coerceIn(5, 60)
    }

    suspend fun setPinTimeoutSeconds(value: Int) = edit {
        it[K.PIN_TIMEOUT_SECONDS] = value.coerceIn(5, 60)
    }

    suspend fun setPersonalVaultPinLength(length: Int) = edit {
        it[K.PERSONAL_VAULT_PIN_LENGTH] = length.coerceIn(4, 8)
    }

    suspend fun setIntruderVaultPinLength(length: Int) = edit {
        it[K.INTRUDER_VAULT_PIN_LENGTH] = length.coerceIn(4, 8)
    }

    suspend fun setIntruderCaptureEnabled(value: Boolean) = edit {
        it[K.INTRUDER_CAPTURE] = value
    }

    suspend fun setVaultLayout(layout: VaultLayout) = edit {
        it[K.VAULT_LAYOUT] = layout.name
    }

    suspend fun setDeleteOriginalsAfterImport(value: Boolean) = edit {
        it[K.DELETE_ORIGINALS] = value
    }

    /** Marks the DataStore schema version after the idempotent upgrade hooks ran. */
    suspend fun stampSchemaVersion() = edit { it[K.SCHEMA] = NexoConfig.CURRENT_SCHEMA }

    suspend fun saveVerifier(kind: CredentialKind, verifier: PinHasher.Verifier) = edit {
        it[verifierKey(kind, "salt")] = verifier.salt
        it[verifierKey(kind, "hash")] = verifier.hash
        it[verifierKey(kind, "iterations")] = verifier.iterations.toString()
    }

    suspend fun verifierSnapshot(kind: CredentialKind): PinHasher.Verifier? = raw.map { it.verifier(kind) }.first()

    suspend fun clearVerifier(kind: CredentialKind) = edit {
        it.remove(verifierKey(kind, "salt"))
        it.remove(verifierKey(kind, "hash"))
        it.remove(verifierKey(kind, "iterations"))
    }

    suspend fun registerFailure(recovery: Boolean, now: Long): FailureState = editWithResult { prefs ->
        val failures = (prefs[if (recovery) K.RECOVERY_FAILURES else K.MAIN_FAILURES] ?: 0) + 1
        val delay = LockoutPolicy.delayForFailures(failures)
        val until = if (delay > 0) now + delay else 0L
        if (recovery) {
            prefs[K.RECOVERY_FAILURES] = failures
            prefs[K.RECOVERY_LOCKOUT_UNTIL] = until
        } else {
            prefs[K.MAIN_FAILURES] = failures
            prefs[K.MAIN_LOCKOUT_UNTIL] = until
        }
        FailureState(
            failures = failures,
            lockoutUntil = until,
            attemptsUntilLockout = LockoutPolicy.attemptsUntilFailures(failures),
        )
    }

    suspend fun clearFailures(recovery: Boolean) = edit {
        if (recovery) {
            it[K.RECOVERY_FAILURES] = 0
            it[K.RECOVERY_LOCKOUT_UNTIL] = 0L
        } else {
            it[K.MAIN_FAILURES] = 0
            it[K.MAIN_LOCKOUT_UNTIL] = 0L
        }
    }

    // ------------------------------------------------------------------ generic per-credential lockout

    /**
     * Every credential family gets its own failure budget. "main" and "recovery"
     * keep the historical keys; vault credentials use their own prefix so a
     * wrong vault PIN can never consume the app-unlock budget.
     */
    suspend fun failureState(scope: String): CredentialFailureState {
        val prefs = raw.first()
        return CredentialFailureState(
            failures = prefs[failuresKey(scope)] ?: 0,
            lockoutUntil = prefs[lockoutKey(scope)] ?: 0L,
        )
    }

    suspend fun registerFailure(scope: String, now: Long): FailureState = editWithResult { prefs ->
        val failures = (prefs[failuresKey(scope)] ?: 0) + 1
        val delay = LockoutPolicy.delayForFailures(failures)
        val until = if (delay > 0) now + delay else 0L
        prefs[failuresKey(scope)] = failures
        prefs[lockoutKey(scope)] = until
        FailureState(
            failures = failures,
            lockoutUntil = until,
            attemptsUntilLockout = LockoutPolicy.attemptsUntilFailures(failures),
        )
    }

    suspend fun clearFailures(scope: String) = edit {
        it[failuresKey(scope)] = 0
        it[lockoutKey(scope)] = 0L
    }

    suspend fun clearLockout(scope: String, now: Long): Long = editWithResult { prefs ->
        val until = prefs[lockoutKey(scope)] ?: 0L
        if (until in 1L..now) prefs[lockoutKey(scope)] = 0L
        (until - now).coerceAtLeast(0L)
    }

    data class CredentialFailureState(
        val failures: Int,
        val lockoutUntil: Long,
    )

    suspend fun refreshLockoutState(now: Long): LockoutSnapshot = editWithResult { prefs ->
        val mainUntil = prefs[K.MAIN_LOCKOUT_UNTIL] ?: 0L
        val recUntil = prefs[K.RECOVERY_LOCKOUT_UNTIL] ?: 0L
        // Expired lockouts are cleared, the failure counter is kept so the next
        // wrong attempt escalates instead of restarting from zero.
        if (mainUntil in 1L..now) prefs[K.MAIN_LOCKOUT_UNTIL] = 0L
        if (recUntil in 1L..now) prefs[K.RECOVERY_LOCKOUT_UNTIL] = 0L
        LockoutSnapshot(
            mainRemaining = (mainUntil - now).coerceAtLeast(0L),
            recoveryRemaining = (recUntil - now).coerceAtLeast(0L),
        )
    }

    data class FailureState(
        val failures: Int,
        val lockoutUntil: Long,
        val attemptsUntilLockout: Int,
    )

    data class LockoutSnapshot(
        val mainRemaining: Long,
        val recoveryRemaining: Long,
    )

    private suspend fun <T> editWithResult(block: (MutablePreferences) -> T): T {
        var result: T? = null
        store.edit { prefs -> result = block(prefs) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun Preferences.toConfig(): NexoConfig = NexoConfig(
        onboarded = this[K.ONBOARDED] ?: false,
        setupComplete = this[K.SETUP_COMPLETE] ?: false,
        setupStep = this[K.SETUP_STEP] ?: 0,
        pinLength = this[K.PIN_LENGTH] ?: 6,
        mainGestureLength = this[K.MAIN_GESTURE_LENGTH] ?: 0,
        biometricEnabled = this[K.BIOMETRIC] ?: false,
        gestureEnabled = this[K.GESTURE_ENABLED] ?: false,
        strictMode = this[K.STRICT] ?: false,
        protectionEnabled = this[K.PROTECTION] ?: false,
        privacyCover = this[K.PRIVACY_COVER] ?: false,
        themeMode = ThemeMode.fromKey(this[K.THEME]),
        mainFailures = this[K.MAIN_FAILURES] ?: 0,
        mainLockoutUntil = this[K.MAIN_LOCKOUT_UNTIL] ?: 0L,
        recoveryFailures = this[K.RECOVERY_FAILURES] ?: 0,
        recoveryLockoutUntil = this[K.RECOVERY_LOCKOUT_UNTIL] ?: 0L,
        hasMainPin = this.verifier(CredentialKind.MAIN_PIN) != null,
        hasRecoveryPin = this.verifier(CredentialKind.RECOVERY_PIN) != null,
        hasMainGesture = this.verifier(CredentialKind.MAIN_GESTURE) != null,
        hasRecoveryPattern = this.verifier(CredentialKind.RECOVERY_PATTERN) != null,
        decoyEnabled = this[K.DECOY_ENABLED] ?: true,
        // The pre-1.1.2 shared deadline is only read as a migration hint for
        // the decoy window; the per-stage keys are authoritative.
        decoyTimeoutSeconds = (
            this[K.DECOY_TIMEOUT_SECONDS] ?: this[K.AUTH_DEADLINE_SECONDS] ?: 4
            ).coerceIn(2, 10),
        gestureTimeoutSeconds = (this[K.GESTURE_TIMEOUT_SECONDS] ?: 15).coerceIn(5, 60),
        pinTimeoutSeconds = (this[K.PIN_TIMEOUT_SECONDS] ?: 15).coerceIn(5, 60),
        personalVaultPinLength = this[K.PERSONAL_VAULT_PIN_LENGTH] ?: 6,
        intruderVaultPinLength = this[K.INTRUDER_VAULT_PIN_LENGTH] ?: 6,
        hasPersonalVaultPin = this.verifier(CredentialKind.PERSONAL_VAULT_PIN) != null,
        hasIntruderVaultPin = this.verifier(CredentialKind.INTRUDER_VAULT_PIN) != null,
        intruderCaptureEnabled = this[K.INTRUDER_CAPTURE] ?: false,
        vaultLayout = VaultLayout.fromKey(this[K.VAULT_LAYOUT]),
        deleteOriginalsAfterImport = this[K.DELETE_ORIGINALS] ?: false,
        schemaVersion = this[K.SCHEMA] ?: 1,
    )

    private fun Preferences.verifier(kind: CredentialKind): PinHasher.Verifier? {
        val salt = this[verifierKey(kind, "salt")] ?: return null
        val hash = this[verifierKey(kind, "hash")] ?: return null
        val iterations = this[verifierKey(kind, "iterations")]?.toIntOrNull() ?: return null
        return PinHasher.Verifier(salt, hash, iterations)
    }

    private fun verifierKey(kind: CredentialKind, field: String) =
        stringPreferencesKey("${kind.prefix}_$field")

    private fun failuresKey(scope: String) = intPreferencesKey("${scope}_failures")

    private fun lockoutKey(scope: String) = longPreferencesKey("${scope}_lockout_until")

    private object K {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val SETUP_STEP = intPreferencesKey("setup_step")
        val PIN_LENGTH = intPreferencesKey("pin_length")
        val MAIN_GESTURE_LENGTH = intPreferencesKey("main_gesture_length")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val GESTURE_ENABLED = booleanPreferencesKey("gesture_enabled")
        val STRICT = booleanPreferencesKey("strict_pin")
        val PROTECTION = booleanPreferencesKey("protection_enabled")
        val PRIVACY_COVER = booleanPreferencesKey("privacy_cover")
        val THEME = stringPreferencesKey("theme_mode")
        val MAIN_FAILURES = intPreferencesKey("main_failures")
        val MAIN_LOCKOUT_UNTIL = longPreferencesKey("main_lockout_until")
        val RECOVERY_FAILURES = intPreferencesKey("recovery_failures")
        val RECOVERY_LOCKOUT_UNTIL = longPreferencesKey("recovery_lockout_until")
        val PROTECTED_BLOB = stringPreferencesKey("protected_blob")
        val DECOY_ENABLED = booleanPreferencesKey("decoy_enabled")
        val DECOY_TIMEOUT_SECONDS = intPreferencesKey("decoy_timeout_seconds")
        val GESTURE_TIMEOUT_SECONDS = intPreferencesKey("gesture_timeout_seconds")
        val PIN_TIMEOUT_SECONDS = intPreferencesKey("pin_timeout_seconds")
        /** Legacy shared-deadline key, read-only for migration. */
        val AUTH_DEADLINE_SECONDS = intPreferencesKey("auth_deadline_seconds")
        val PERSONAL_VAULT_PIN_LENGTH = intPreferencesKey("personal_vault_pin_length")
        val INTRUDER_VAULT_PIN_LENGTH = intPreferencesKey("intruder_vault_pin_length")
        val INTRUDER_CAPTURE = booleanPreferencesKey("intruder_capture_enabled")
        val VAULT_LAYOUT = stringPreferencesKey("vault_layout")
        val DELETE_ORIGINALS = booleanPreferencesKey("delete_originals_after_import")
        val SCHEMA = intPreferencesKey("schema_version")
    }

    companion object {
        /** First-run setup has two steps: volume pattern (1) and master PIN (2). */
        const val SETUP_STEPS = 2
    }
}
