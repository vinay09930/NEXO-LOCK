package com.vinay.nexolock.ui

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vinay.nexolock.AppContainer
import com.vinay.nexolock.data.LogReadResult
import com.vinay.nexolock.data.LogType
import com.vinay.nexolock.data.NexoConfig
import com.vinay.nexolock.data.VaultLayout
import com.vinay.nexolock.domain.AuthPolicy
import com.vinay.nexolock.domain.CredentialKind
import com.vinay.nexolock.domain.InstalledApp
import com.vinay.nexolock.domain.LockoutPolicy
import com.vinay.nexolock.domain.ThemeMode
import com.vinay.nexolock.domain.UnlockMethod
import com.vinay.nexolock.domain.VolumePattern
import com.vinay.nexolock.domain.VolumePress
import com.vinay.nexolock.protection.PermissionChecks
import com.vinay.nexolock.protection.PermissionManager
import com.vinay.nexolock.protection.ProtectionService
import com.vinay.nexolock.vault.CaptureState
import com.vinay.nexolock.vault.ImportDiagnostic
import com.vinay.nexolock.vault.OriginalDeletionResult
import com.vinay.nexolock.vault.OriginalFileDeletionPolicy
import com.vinay.nexolock.vault.VaultDeletionReport
import com.vinay.nexolock.vault.VaultEntry
import com.vinay.nexolock.vault.VaultExport
import com.vinay.nexolock.vault.VaultExportFileResult
import com.vinay.nexolock.vault.VaultExportResult
import com.vinay.nexolock.vault.VaultFile
import com.vinay.nexolock.vault.VaultFileStatus
import com.vinay.nexolock.vault.VaultImportDiagnostics
import com.vinay.nexolock.vault.VaultImportErrors
import com.vinay.nexolock.vault.VaultImportOutcome
import com.vinay.nexolock.vault.VaultImportStage
import com.vinay.nexolock.vault.VaultKind
import com.vinay.nexolock.vault.VaultSnapshot
import com.vinay.nexolock.vault.VaultStorageStats
import com.vinay.nexolock.vault.sanitizeDisplayName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.util.ArrayDeque

enum class BiometricState { UNSUPPORTED, NOT_ENROLLED, READY }

data class Notice(val message: String, val isError: Boolean = false)

data class VaultOverview(
    val personalCount: Int = 0,
    val intruderCount: Int = 0,
    val personalCorrupted: Boolean = false,
    val intruderCorrupted: Boolean = false,
)

/**
 * One explicit vault phase. "Unlocked with no PIN" and "locked but no PIN"
 * were previously representable by independent booleans; the phase makes them
 * impossible.
 */
sealed interface VaultStatus {
    /** The selected vault is being read from preferences. */
    data object Opening : VaultStatus
    /** No independent vault PIN exists yet; the create form is shown. */
    data object NeedsPin : VaultStatus
    data object Locked : VaultStatus
    data object Unlocked : VaultStatus
}

/** Destination shape chosen for a verified restore. */
enum class ExportMode {
    /** ACTION_CREATE_DOCUMENT: one file, user picks the name. */
    SINGLE_DOCUMENT,

    /** ACTION_OPEN_DOCUMENT_TREE: one or more files with collision-free names. */
    MULTIPLE_TO_TREE,
}

/** Pending SAF launch requested by the UI; consumed exactly once. */
data class ExportRequest(
    val mode: ExportMode,
    val fileIds: List<String>,
    val suggestedName: String,
)

/** Picker result waiting for the explicit delete-originals confirmation. */
data class PendingImportRequest(
    val uris: List<Uri>,
    val deleteOriginals: Boolean,
)

/** One system delete confirmation that must be launched from an Activity. */
data class DeleteConsentRequest(
    val displayName: String,
    val uri: Uri,
    val intentSender: IntentSender,
)

data class VaultUiState(
    val kind: VaultKind? = null,
    val status: VaultStatus = VaultStatus.Opening,
    val entries: List<VaultEntry> = emptyList(),
    val files: List<VaultFile> = emptyList(),
    val pinLength: Int = 6,
    val busy: Boolean = false,
    val importingName: String? = null,
    val importedBytes: Long = 0L,
    val importTotalBytes: Long? = null,
    /** 1-based position in a multi-file picker result; 0 when idle. */
    val importIndex: Int = 0,
    val importCount: Int = 0,
    /** Human-readable current import stage (metadata, encrypting, committing). */
    val importStage: String? = null,
    /** Per-file failures from the last multi-file import. */
    val importFailures: List<String> = emptyList(),
    /** Safe in-memory import diagnostics for the latest attempts. */
    val diagnostics: List<ImportDiagnostic> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val capture: CaptureState = CaptureState(),
    /** Measured storage footprint while unlocked; null while the vault is locked. */
    val storage: VaultStorageStats? = null,
    /** Free bytes on the volume that hosts the vault; null when unavailable. */
    val availableBytes: Long? = null,
    // --- V1.1.4: import confirmation, verified export and original deletion ---
    /** Set when a picker result waits for the delete-originals confirmation. */
    val pendingImport: PendingImportRequest? = null,
    val exporting: Boolean = false,
    val exportingName: String? = null,
    val exportIndex: Int = 0,
    val exportCount: Int = 0,
    val exportedBytes: Long = 0L,
    val exportTotalBytes: Long = 0L,
    val exportReport: VaultExportResult? = null,
    val exportError: String? = null,
    /** Non-null once; the UI consumes it and launches the SAF picker. */
    val exportRequest: ExportRequest? = null,
    /** Honest per-file outcomes for the latest delete-originals batch. */
    val deletionReport: VaultDeletionReport? = null,
    /** Non-null once; the UI consumes it and launches the system consent. */
    val deleteConsent: DeleteConsentRequest? = null,
) {
    val hasPin: Boolean
        get() = status is VaultStatus.Locked || status is VaultStatus.Unlocked
    val unlocked: Boolean
        get() = status is VaultStatus.Unlocked
}

/**
 * Explicit viewer preparation state. A decrypted cache path can only exist in
 * [Ready]; an error can only exist in [Failed]. The old independent
 * `loading`/`cachePath`/`error` fields could claim "loading" while a path was
 * already available.
 */
sealed interface ViewerLoad {
    data object Idle : ViewerLoad
    data class Preparing(val progressBytes: Long = 0L) : ViewerLoad
    data class Ready(val cachePath: String) : ViewerLoad
    data class Failed(val message: String) : ViewerLoad
}

/** Decrypted viewer preparation for one vault file. */
data class ViewerUiState(
    val kind: VaultKind? = null,
    val file: VaultFile? = null,
    val entryId: String? = null,
    val displayName: String? = null,
    val mimeType: String? = null,
    val load: ViewerLoad = ViewerLoad.Idle,
    val totalBytes: Long = 0L,
) {
    val cachePath: String? get() = (load as? ViewerLoad.Ready)?.cachePath
    val error: String? get() = (load as? ViewerLoad.Failed)?.message
    val loading: Boolean get() = load is ViewerLoad.Preparing
    val progressBytes: Long
        get() = when (val current = load) {
            is ViewerLoad.Preparing -> current.progressBytes
            is ViewerLoad.Ready -> totalBytes
            ViewerLoad.Idle, is ViewerLoad.Failed -> 0L
        }
}

data class NexoUiState(
    val loading: Boolean = true,
    val config: NexoConfig = NexoConfig(),
    val protectedPackages: Set<String> = emptySet(),
    val apps: List<InstalledApp> = emptyList(),
    val appsLoading: Boolean = true,
    val logs: LogReadResult = LogReadResult(emptyList()),
    val biometric: BiometricState = BiometricState.UNSUPPORTED,
    val overlayAllowed: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val usageAllowed: Boolean = false,
    val notificationsAllowed: Boolean = true,
    val protectionRunning: Boolean = false,
    val permissionHealth: PermissionManager.Health = PermissionManager.Health(),
    val degradedReasons: List<String> = emptyList(),
    val vaults: VaultOverview = VaultOverview(),
    val vault: VaultUiState = VaultUiState(),
    val logWriteError: String? = null,
    val notice: Notice? = null,
    val viewer: ViewerUiState = ViewerUiState(),
)

/**
 * Single ViewModel for the management app. It exposes immutable UI state and
 * performs every mutation in viewModelScope, so the UI stays declarative.
 */
class NexoViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(NexoUiState())
    val state: StateFlow<NexoUiState> = _state.asStateFlow()

    /** Invalidates in-flight viewer preparation when the route is left. */
    @Volatile
    private var viewerRequest = 0L

    /** Active multi-file import; cancelled explicitly by the UI or a lock. */
    private var vaultImportJob: Job? = null

    /** Active verified export; cancelled explicitly by the UI or a lock. */
    private var vaultExportJob: Job? = null

    /** Request already handed to a SAF picker; the result arrives later. */
    private var pendingExport: ExportRequest? = null

    /** Delete confirmations waiting behind the currently active system dialog. */
    private val deleteConsentQueue = ArrayDeque<DeleteConsentRequest>()
    private var activeDeleteConsent: DeleteConsentRequest? = null

    init {
        viewModelScope.launch {
            combine(
                container.prefs.config,
                container.protectedApps.packages,
            ) { config, packages -> config to packages }
                .collect { (config, packages) ->
                    _state.update {
                        it.copy(
                            loading = false,
                            config = config,
                            protectedPackages = packages,
                        )
                    }
                }
        }
        viewModelScope.launch {
            container.runtime.status.collect { runtime ->
                _state.update {
                    it.copy(
                        protectionRunning = runtime.running,
                        degradedReasons = runtime.degradedReasons,
                        permissionHealth = container.permissionHealth(),
                    )
                }
            }
        }
        viewModelScope.launch {
            container.logs.writeError.collect { error ->
                _state.update { it.copy(logWriteError = error) }
            }
        }
        viewModelScope.launch {
            container.intruderCapture.state.collect { capture ->
                _state.update {
                    it.copy(vault = it.vault.copy(capture = capture))
                }
            }
        }
        viewModelScope.launch {
            VaultImportDiagnostics.history.collect { history ->
                _state.update {
                    it.copy(vault = it.vault.copy(diagnostics = history))
                }
            }
        }
        refreshPermissions()
        refreshBiometric()
        loadApps()
        refreshLogs()
        refreshVaults()
        viewModelScope.launch { container.intruderCapture.refreshReadiness() }
    }

    // ------------------------------------------------------------------ app data

    fun loadApps() {
        _state.update { it.copy(appsLoading = true) }
        viewModelScope.launch {
            val apps = runCatching { container.catalog.loadLaunchable() }.getOrDefault(emptyList())
            _state.update { it.copy(apps = apps, appsLoading = false) }
        }
    }

    fun refreshLogs() {
        viewModelScope.launch {
            val result = container.logs.read()
            _state.update { it.copy(logs = result) }
        }
    }

    fun refreshPermissions() {
        val health = container.permissionHealth()
        _state.update {
            it.copy(
                overlayAllowed = health.canDrawLock,
                accessibilityEnabled = health.accessibility == PermissionManager.State.GRANTED,
                usageAllowed = health.usage == PermissionManager.State.GRANTED,
                notificationsAllowed = health.notifications == PermissionManager.State.GRANTED,
                permissionHealth = health,
            )
        }
    }

    fun refreshBiometric() {
        val status = when (PermissionManager.biometricState(container.appContext)) {
            PermissionManager.BiometricState.READY -> BiometricState.READY
            PermissionManager.BiometricState.NOT_ENROLLED -> BiometricState.NOT_ENROLLED
            PermissionManager.BiometricState.UNSUPPORTED -> BiometricState.UNSUPPORTED
        }
        _state.update { it.copy(biometric = status) }
    }

    fun refreshVaults() {
        viewModelScope.launch {
            val personal = runCatching {
                container.vaults.count(VaultKind.PERSONAL)
            }.getOrDefault(0)
            val intruder = runCatching {
                container.vaults.count(VaultKind.INTRUDER)
            }.getOrDefault(0)
            _state.update {
                it.copy(vaults = VaultOverview(personalCount = personal, intruderCount = intruder))
            }
        }
    }

    fun openSystemSettings(intent: Intent) {
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            container.appContext.startActivity(intent)
        }.onFailure {
            notify("This settings screen is unavailable on this device.", error = true)
        }
    }

    // ------------------------------------------------------------------ notices

    fun notify(message: String, error: Boolean = false) {
        _state.update { it.copy(notice = Notice(message, error)) }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    /** Needed by screens that open documented Android settings screens. */
    fun containerContext(): Context = container.appContext

    // ------------------------------------------------------------------ onboarding / setup

    fun completeOnboarding() {
        viewModelScope.launch {
            container.prefs.setOnboarded(true)
            container.prefs.setSetupStep(0)
        }
    }

    fun createMainPin(pin: String, onResult: (String?) -> Unit) {
        if (!AuthPolicy.validPin(pin)) {
            onResult("Use 4 to 8 digits.")
            return
        }
        viewModelScope.launch {
            runCatching {
                val recoveryExisting = container.credentials.exists(CredentialKind.RECOVERY_PIN)
                if (recoveryExisting && container.credentials.verify(CredentialKind.RECOVERY_PIN, pin)) {
                    onResult("The main PIN must be different from the recovery PIN.")
                    return@runCatching
                }
                container.credentials.store(CredentialKind.MAIN_PIN, pin)
                container.prefs.setPinLength(pin.length)
                container.logs.log(LogType.PIN_CHANGED, "Main PIN configured")
                onResult(null)
            }.onFailure {
                onResult("The PIN could not be saved.")
            }
        }
    }

    fun createRecoveryPin(pin: String, onResult: (String?) -> Unit) {
        if (!AuthPolicy.validPin(pin)) {
            onResult("Use 4 to 8 digits.")
            return
        }
        viewModelScope.launch {
            runCatching {
                if (container.credentials.verify(CredentialKind.MAIN_PIN, pin)) {
                    onResult("The recovery PIN must be different from the main PIN.")
                    return@runCatching
                }
                container.credentials.store(CredentialKind.RECOVERY_PIN, pin)
                container.logs.log(LogType.RECOVERY_CHANGED, "Recovery PIN configured")
                onResult(null)
            }.onFailure {
                onResult("The recovery PIN could not be saved.")
            }
        }
    }

    fun createRecoveryPattern(pattern: String, onResult: (String?) -> Unit) {
        if (!AuthPolicy.validPattern(pattern)) {
            onResult("Connect at least 4 different dots.")
            return
        }
        viewModelScope.launch {
            runCatching {
                container.credentials.store(CredentialKind.RECOVERY_PATTERN, pattern)
                container.logs.log(LogType.RECOVERY_CHANGED, "Recovery pattern configured")
                onResult(null)
            }.onFailure {
                onResult("The recovery pattern could not be saved.")
            }
        }
    }

    fun finishSetup(onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val config = container.prefs.snapshot()
            if (!config.hasMainPin) {
                onResult("Create a main PIN before finishing setup.")
                return@launch
            }
            if (!config.hasMainGesture) {
                onResult("Record the mandatory volume pattern before finishing setup.")
                return@launch
            }
            container.prefs.setOnboarded(true)
            container.prefs.setSetupComplete(true)
            container.logs.log(LogType.SETUP_COMPLETED, "Security setup completed")

            // Honest completion: record exactly what is still open instead of
            // implying full protection. Biometric, vaults, protected apps and
            // monitoring permission are optional first-run items; they are
            // configured later from Settings / Protection Settings.
            val health = container.permissionHealth()
            val after = container.prefs.snapshot()
            val open = buildList {
                if (!health.detectionAvailable || !health.canDrawLock) {
                    add("monitoring permission")
                }
                if (container.protectedApps.snapshot().isEmpty()) {
                    add("protected apps")
                }
                if (!after.hasPersonalVaultPin) add("Personal Vault")
                if (!after.hasIntruderVaultPin) add("Intruder Vault")
                if (!(PermissionManager.biometricState(container.appContext) ==
                    PermissionManager.BiometricState.READY && after.biometricEnabled)
                ) {
                    add("biometric")
                }
            }
            if (open.isNotEmpty()) {
                container.logs.log(
                    LogType.DEGRADED_PROTECTION,
                    "Setup finished with optional items open: ${open.joinToString()}",
                )
            }
            refreshBiometric()
            if (!config.protectionEnabled) {
                setProtectionEnabled(true)
            }
            refreshPermissions()
            refreshVaults()
            onResult(null)
        }
    }

    fun setDecoyEnabled(value: Boolean) {
        viewModelScope.launch {
            container.prefs.setDecoyEnabled(value)
            container.logs.log(
                LogType.PIN_CHANGED,
                if (value) "Protection decoy enabled" else "Protection decoy disabled",
            )
        }
    }

    fun setDecoyTimeoutSeconds(value: Int) {
        viewModelScope.launch {
            container.prefs.setDecoyTimeoutSeconds(value)
        }
    }

    fun setGestureTimeoutSeconds(value: Int) {
        viewModelScope.launch {
            container.prefs.setGestureTimeoutSeconds(value)
        }
    }

    fun setPinTimeoutSeconds(value: Int) {
        viewModelScope.launch {
            container.prefs.setPinTimeoutSeconds(value)
        }
    }

    fun setSetupStep(step: Int) {
        viewModelScope.launch { container.prefs.setSetupStep(step) }
    }

    // ------------------------------------------------------------------ protected apps

    fun setAppProtected(packageName: String, protect: Boolean) {
        viewModelScope.launch {
            val current = container.protectedApps.snapshot().toMutableSet()
            if (protect) current += packageName else current -= packageName
            container.protectedApps.setPackages(current)
        }
    }

    fun setAllVisibleAppsProtected(apps: List<InstalledApp>, protect: Boolean) {
        viewModelScope.launch {
            val current = container.protectedApps.snapshot().toMutableSet()
            if (protect) current += apps.map { it.packageName } else current -= apps.map { it.packageName }.toSet()
            container.protectedApps.setPackages(current)
        }
    }

    // ------------------------------------------------------------------ protection service

    fun setProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.prefs.setProtectionEnabled(enabled)
            if (enabled) {
                if (!container.prefs.snapshot().hasMainPin) {
                    notify("Create a main PIN before starting protection.", error = true)
                    container.prefs.setProtectionEnabled(false)
                    return@launch
                }
                val started = withContext(Dispatchers.Main) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            container.appContext,
                            Intent(container.appContext, ProtectionService::class.java),
                        )
                    }.isSuccess
                }
                if (!started) {
                    notify("Android blocked the protection service. Open the app and try again.", error = true)
                    container.prefs.setProtectionEnabled(false)
                } else {
                    val health = container.permissionHealth()
                    val problems = health.warnings
                    if (problems.isNotEmpty()) {
                        notify(problems.first(), error = true)
                    }
                }
            } else {
                runCatching {
                    container.appContext.stopService(
                        Intent(container.appContext, ProtectionService::class.java),
                    )
                }
                container.logs.log(LogType.PROTECTION_STOPPED, "Protection stopped")
            }
            refreshPermissions()
        }
    }

    // ------------------------------------------------------------------ vaults

    private suspend fun storageFor(kind: VaultKind): VaultStorageStats? =
        runCatching { container.vaults.storageStats(kind) }.getOrNull()

    fun openVault(kind: VaultKind) {
        viewModelScope.launch {
            // Switching vaults abandons any operation started for the previous
            // vault. A stale export destination or delete consent must never be
            // applied to the other vault.
            if (_state.value.vault.kind != null && _state.value.vault.kind != kind) {
                vaultImportJob?.cancel()
                vaultImportJob = null
                vaultExportJob?.cancel()
                vaultExportJob = null
                pendingExport = null
                deleteConsentQueue.clear()
                activeDeleteConsent = null
            }
            val config = container.prefs.snapshot()
            val hasPin = when (kind) {
                VaultKind.PERSONAL -> config.hasPersonalVaultPin
                VaultKind.INTRUDER -> config.hasIntruderVaultPin
            }
            val pinLength = when (kind) {
                VaultKind.PERSONAL -> config.personalVaultPinLength
                VaultKind.INTRUDER -> config.intruderVaultPinLength
            }
            val unlocked = container.vaults.isUnlocked(kind)
            if (kind == VaultKind.INTRUDER) {
                container.intruderCapture.refreshReadiness()
            }
            val snapshot = if (unlocked) {
                runCatching { container.vaults.entries(kind) }.getOrNull()
            } else {
                null
            }
            val storage = if (unlocked && snapshot != null) storageFor(kind) else null
            val available = container.vaults.availableStorageBytes()
            _state.update {
                it.copy(
                    vault = VaultUiState(
                        kind = kind,
                        status = when {
                            !hasPin -> VaultStatus.NeedsPin
                            unlocked && snapshot != null -> VaultStatus.Unlocked
                            else -> VaultStatus.Locked
                        },
                        pinLength = pinLength,
                        entries = snapshot?.entries ?: emptyList(),
                        files = snapshot?.files ?: emptyList(),
                        storage = storage,
                        availableBytes = available,
                        error = if (unlocked && snapshot == null) {
                            "The vault could not be opened. Its integrity check failed."
                        } else {
                            null
                        },
                        capture = it.vault.capture,
                        diagnostics = it.vault.diagnostics,
                    ),
                )
            }
        }
    }

    fun createVaultPin(pin: String, onResult: (String?) -> Unit) {
        val kind = _state.value.vault.kind ?: run {
            onResult("Open a vault first.")
            return
        }
        if (!AuthPolicy.validPin(pin)) {
            onResult("Use 4 to 8 digits.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(vault = it.vault.copy(busy = true, error = null)) }
            val failure = container.vaults.createPin(kind, pin)
            if (failure == null) {
                val unlocked = container.vaults.unlock(kind, pin)
                val config = container.prefs.snapshot()
                val storage = if (unlocked.unlocked) storageFor(kind) else null
                val available = if (unlocked.unlocked) container.vaults.availableStorageBytes() else null
                _state.update {
                    it.copy(
                        vault = it.vault.copy(
                            busy = false,
                            status = if (unlocked.unlocked) {
                                VaultStatus.Unlocked
                            } else {
                                VaultStatus.Locked
                            },
                            entries = unlocked.snapshot?.entries ?: emptyList(),
                            files = unlocked.snapshot?.files ?: emptyList(),
                            storage = storage,
                            availableBytes = available,
                            pinLength = when (kind) {
                                VaultKind.PERSONAL -> config.personalVaultPinLength
                                VaultKind.INTRUDER -> config.intruderVaultPinLength
                            },
                            message = "${kind.title} created and encrypted.",
                            error = unlocked.message,
                        ),
                    )
                }
                refreshVaults()
                onResult(null)
            } else {
                _state.update { it.copy(vault = it.vault.copy(busy = false, error = failure)) }
                onResult(failure)
            }
        }
    }

    fun unlockVault(pin: String, onResult: (String?) -> Unit = {}) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            _state.update { it.copy(vault = it.vault.copy(busy = true, error = null, message = null)) }
            val result = container.vaults.unlock(kind, pin)
            val storage = if (result.unlocked) storageFor(kind) else null
            val available = if (result.unlocked) container.vaults.availableStorageBytes() else null
            _state.update {
                it.copy(
                    vault = it.vault.copy(
                        busy = false,
                        status = if (result.unlocked) {
                            VaultStatus.Unlocked
                        } else {
                            VaultStatus.Locked
                        },
                        entries = result.snapshot?.entries ?: if (result.unlocked) emptyList() else it.vault.entries,
                        files = result.snapshot?.files ?: if (result.unlocked) emptyList() else it.vault.files,
                        storage = storage,
                        availableBytes = available,
                        importingName = null,
                        importedBytes = 0L,
                        importTotalBytes = null,
                        error = result.message,
                    ),
                )
            }
            onResult(result.message)
            refreshVaults()
        }
    }

    fun lockVault() {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            vaultImportJob?.cancel()
            vaultImportJob = null
            vaultExportJob?.cancel()
            vaultExportJob = null
            pendingExport = null
            deleteConsentQueue.clear()
            activeDeleteConsent = null
            container.vaults.lockFromUi(kind)
            releaseViewer()
            _state.update {
                it.copy(
                    vault = it.vault.copy(
                        status = VaultStatus.Locked,
                        busy = false,
                        entries = emptyList(),
                        files = emptyList(),
                        storage = null,
                        importingName = null,
                        importedBytes = 0L,
                        importTotalBytes = null,
                        importStage = null,
                        importIndex = 0,
                        importCount = 0,
                        importFailures = emptyList(),
                        pendingImport = null,
                        exporting = false,
                        exportingName = null,
                        exportIndex = 0,
                        exportCount = 0,
                        exportedBytes = 0L,
                        exportTotalBytes = 0L,
                        exportReport = null,
                        exportError = null,
                        exportRequest = null,
                        deletionReport = null,
                        deleteConsent = null,
                        error = null,
                        message = "${kind.title} locked.",
                    ),
                )
            }
        }
    }

    /**
     * Locks every unlocked vault after the management UI spent time in the
     * background. MainActivity calls this only when a system file picker is not
     * pending, so an import in progress is not interrupted by a picker trip.
     */
    fun lockVaultsOnBackground() {
        viewModelScope.launch {
            val unlocked = VaultKind.entries.filter { container.vaults.isUnlocked(it) }
            if (unlocked.isEmpty()) return@launch
            vaultImportJob?.cancel()
            vaultImportJob = null
            vaultExportJob?.cancel()
            vaultExportJob = null
            pendingExport = null
            deleteConsentQueue.clear()
            activeDeleteConsent = null
            container.vaults.lockAll()
            releaseViewer()
            unlocked.forEach { kind ->
                container.logs.log(
                    if (kind == VaultKind.PERSONAL) {
                        LogType.VAULT_LOCKED
                    } else {
                        LogType.INTRUDER_VAULT_LOCKED
                    },
                    "${kind.title} locked after the app spent time in the background",
                )
            }
            _state.update { state ->
                state.copy(
                    vault = state.vault.copy(
                        status = VaultStatus.Locked,
                        busy = false,
                        entries = emptyList(),
                        files = emptyList(),
                        storage = null,
                        importingName = null,
                        importedBytes = 0L,
                        importTotalBytes = null,
                        importStage = null,
                        importIndex = 0,
                        importCount = 0,
                        importFailures = emptyList(),
                        pendingImport = null,
                        exporting = false,
                        exportingName = null,
                        exportIndex = 0,
                        exportCount = 0,
                        exportedBytes = 0L,
                        exportTotalBytes = 0L,
                        exportReport = null,
                        exportError = null,
                        exportRequest = null,
                        deletionReport = null,
                        deleteConsent = null,
                        error = null,
                        message = "Vault locked after the app was in the background.",
                    ),
                )
            }
        }
    }

    fun addVaultNote(title: String, body: String, onResult: (String?) -> Unit) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            _state.update { it.copy(vault = it.vault.copy(busy = true, error = null)) }
            val failure = container.vaults.addNote(kind, title, body)
            val snapshot = if (failure == null) {
                runCatching { container.vaults.entries(kind) }.getOrNull()
            } else {
                null
            }
            val storage = if (failure == null) storageFor(kind) else null
            _state.update {
                it.copy(
                    vault = it.vault.copy(
                        busy = false,
                        entries = snapshot?.entries ?: it.vault.entries,
                        files = snapshot?.files ?: it.vault.files,
                        storage = storage ?: it.vault.storage,
                        error = failure,
                        message = if (failure == null) "Note encrypted into ${kind.title}." else null,
                    ),
                )
            }
            onResult(failure)
            refreshVaults()
        }
    }

    /**
     * Streams one or more picker URIs into the unlocked vault.
     *
     * Every URI is imported independently: a failure on one file does not stop
     * the rest, and the summary names each failed file. Files are streamed, so
     * memory stays bounded and no size cap is applied. Cancellation stops the
     * current file and deletes its temporary container before returning.
     */
    fun importVaultFiles(uris: List<Uri>) {
        val kind = _state.value.vault.kind ?: return
        if (uris.isEmpty()) return
        if (!_state.value.vault.unlocked) {
            notify("Unlock ${kind.title} before importing files.", error = true)
            return
        }
        if (_state.value.vault.busy || _state.value.vault.exporting) {
            notify("Wait for the current vault operation to finish.", error = true)
            return
        }
        if (_state.value.config.deleteOriginalsAfterImport) {
            _state.update { current ->
                if (current.vault.kind != kind) {
                    current
                } else {
                    current.copy(
                        vault = current.vault.copy(
                            pendingImport = PendingImportRequest(uris, deleteOriginals = true),
                            error = null,
                            message = null,
                        ),
                    )
                }
            }
            return
        }
        startVaultImport(uris, deleteOriginals = false)
    }

    /**
     * Applies the explicit confirmation shown after the picker returns. The
     * persisted setting alone never deletes anything: this call is the user's
     * confirmation for this batch.
     */
    fun confirmPendingImport(deleteOriginals: Boolean) {
        val pending = _state.value.vault.pendingImport ?: return
        _state.update { it.copy(vault = it.vault.copy(pendingImport = null)) }
        startVaultImport(pending.uris, deleteOriginals && pending.deleteOriginals)
    }

    fun cancelPendingImport() {
        _state.update { it.copy(vault = it.vault.copy(pendingImport = null)) }
    }

    private fun startVaultImport(uris: List<Uri>, deleteOriginals: Boolean) {
        val kind = _state.value.vault.kind ?: return
        if (!_state.value.vault.unlocked) {
            notify("Unlock ${kind.title} before importing files.", error = true)
            return
        }
        vaultImportJob?.cancel()
        vaultImportJob = viewModelScope.launch {
            val thisJob = currentCoroutineContext()[Job]
            val context = container.appContext
            var imported = 0
            var cancelled = false
            var deletionReport = VaultDeletionReport()
            val failures = mutableListOf<String>()
            _state.update { current ->
                if (current.vault.kind != kind) {
                    current
                } else {
                    current.copy(
                        vault = current.vault.copy(
                            busy = true,
                            error = null,
                            message = null,
                            importingName = null,
                            importedBytes = 0L,
                            importTotalBytes = null,
                            importIndex = 0,
                            importCount = uris.size,
                            importStage = "Preparing import",
                            importFailures = emptyList(),
                            deletionReport = if (deleteOriginals) {
                                VaultDeletionReport()
                            } else {
                                current.vault.deletionReport
                            },
                        ),
                    )
                }
            }
            try {
                uris.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    _state.update { current ->
                        if (current.vault.kind != kind) {
                            current
                        } else {
                            current.copy(
                                vault = current.vault.copy(
                                    importIndex = index + 1,
                                    importingName = null,
                                    importedBytes = 0L,
                                    importTotalBytes = null,
                                    importStage = "Reading file details",
                                ),
                            )
                        }
                    }
                    val metadata = withContext(Dispatchers.IO) { readImportMetadata(context, uri) }
                    val diagnostic = VaultImportDiagnostics.begin(
                        scheme = uri.scheme,
                        authority = uri.authority,
                        mimeType = metadata.mimeType,
                        displayName = metadata.displayName,
                        reportedSize = metadata.sizeBytes,
                        permissionGranted = readPermissionGranted(context, uri),
                    )
                    VaultImportDiagnostics.stage(
                        diagnostic.id,
                        "METADATA",
                        "${metadata.mimeType} · ${metadata.sizeBytes?.let { "$it bytes" } ?: "size unknown"}",
                    )
                    _state.update { current ->
                        if (current.vault.kind != kind) {
                            current
                        } else {
                            current.copy(
                                vault = current.vault.copy(
                                    importingName = metadata.displayName,
                                    importedBytes = 0L,
                                    importTotalBytes = metadata.sizeBytes,
                                    importStage = "Encrypting",
                                ),
                            )
                        }
                    }
                    val outcome = try {
                        container.vaults.importFile(
                            kind = kind,
                            displayName = metadata.displayName,
                            mimeType = metadata.mimeType,
                            expectedBytes = metadata.sizeBytes,
                            openStream = {
                                context.contentResolver.openInputStream(uri)
                                    ?: throw IOException("The selected file could not be opened.")
                            },
                            onProgress = { bytes ->
                                _state.update { current ->
                                    if (current.vault.kind != kind) {
                                        current
                                    } else {
                                        current.copy(
                                            vault = current.vault.copy(
                                                importedBytes = bytes,
                                                importTotalBytes = current.vault.importTotalBytes
                                                    ?: metadata.sizeBytes,
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    } catch (error: CancellationException) {
                        VaultImportDiagnostics.finish(
                            diagnostic.id,
                            VaultImportDiagnostics.CANCELLED,
                            errorClass = error.javaClass.simpleName,
                            errorMessage = "Import cancelled by the user.",
                        )
                        throw error
                    } catch (error: Exception) {
                        VaultImportDiagnostics.finish(
                            diagnostic.id,
                            VaultImportDiagnostics.FAILED,
                            errorClass = error.javaClass.simpleName,
                            errorMessage = VaultImportErrors.messageFor(error),
                        )
                        VaultImportOutcome(
                            failure = VaultImportErrors.messageFor(error),
                            stage = VaultImportStage.UNKNOWN,
                        )
                    }
                    val failure = outcome.failure
                    if (failure == null) {
                        imported += 1
                        VaultImportDiagnostics.stage(
                            diagnostic.id,
                            "COMMITTED",
                            "${outcome.file?.sizeBytes ?: 0L} bytes · ${outcome.file?.chunkCount ?: 0} chunks",
                        )
                        VaultImportDiagnostics.finish(diagnostic.id, VaultImportDiagnostics.SUCCESS)
                        if (deleteOriginals) {
                            deletionReport = deleteOriginalAfterImport(
                                kind = kind,
                                uri = uri,
                                file = outcome.file,
                                displayName = metadata.displayName,
                                report = deletionReport,
                            )
                        }
                    } else {
                        failures += "${metadata.displayName}: $failure"
                        VaultImportDiagnostics.finish(
                            diagnostic.id,
                            VaultImportDiagnostics.FAILED,
                            errorClass = outcome.stage?.name,
                            errorMessage = failure,
                        )
                    }
                }
            } catch (error: CancellationException) {
                cancelled = true
            } finally {
                val failureText = when {
                    cancelled -> "Import cancelled. The unfinished file was removed."
                    failures.isEmpty() -> null
                    else -> failures.joinToString(" ").take(MAX_IMPORT_ERROR_CHARS)
                }
                _state.update { current ->
                    if (current.vault.kind != kind) {
                        current
                    } else {
                        current.copy(
                            vault = current.vault.copy(
                                busy = false,
                                importingName = null,
                                importedBytes = 0L,
                                importTotalBytes = null,
                                importStage = null,
                                importIndex = 0,
                                importCount = 0,
                                importFailures = failures.toList(),
                                pendingImport = null,
                                deletionReport = if (deleteOriginals && deletionReport.requested > 0) {
                                    deletionReport
                                } else {
                                    current.vault.deletionReport
                                },
                                error = failureText,
                                message = when {
                                    cancelled || imported == 0 -> null
                                    failures.isEmpty() && imported == 1 ->
                                        "File encrypted into ${kind.title}."
                                    failures.isEmpty() ->
                                        "$imported files encrypted into ${kind.title}."
                                    else ->
                                        "$imported of ${uris.size} files encrypted; ${failures.size} failed."
                                },
                            ),
                        )
                    }
                }
                if (vaultImportJob === thisJob) vaultImportJob = null
                if (imported > 0) refreshVaultContents(kind)
                refreshVaults()
                refreshLogs()
            }
        }
    }

    /** Clears the in-memory import diagnostics shown in the vault UI. */
    fun clearImportDiagnostics() {
        VaultImportDiagnostics.clear()
    }

    /** Cancels the active import; the store removes its temporary container. */
    fun cancelVaultImport() {
        val job = vaultImportJob ?: return
        _state.update { current ->
            current.copy(
                vault = current.vault.copy(importStage = "Cancelling import"),
            )
        }
        job.cancel()
    }

    /**
     * Deletes one provider-owned original only after the freshly committed
     * encrypted copy has been fully decrypted and matched against its SHA-256.
     * Provider refusals and system-consent requirements are reported honestly.
     */
    private suspend fun deleteOriginalAfterImport(
        kind: VaultKind,
        uri: Uri,
        file: VaultFile?,
        displayName: String,
        report: VaultDeletionReport,
    ): VaultDeletionReport {
        if (file == null) return report
        val verified = runCatching { container.vaults.verifyFile(kind, file.id).ok }
            .getOrDefault(false)
        val hasContentUri = uri.scheme == ContentResolver.SCHEME_CONTENT
        if (!OriginalFileDeletionPolicy.shouldDelete(
                enabled = true,
                importSucceeded = true,
                verified = verified,
                hasContentUri = hasContentUri,
            )
        ) {
            val result = OriginalDeletionResult.Failed(
                when {
                    !verified ->
                        "The encrypted copy failed verification, so the original was kept."

                    !hasContentUri ->
                        "Only content:// documents can be deleted automatically; the original was kept."

                    else -> "The original was kept."
                },
            )
            container.vaults.recordOriginalDeletion(displayName, result)
            return report.copy(requested = report.requested + 1).record(result, displayName)
        }
        val result = container.vaults.deleteOriginal(uri)
        container.vaults.recordOriginalDeletion(displayName, result)
        val updated = report.copy(requested = report.requested + 1).record(result, displayName)
        if (result is OriginalDeletionResult.NeedsConsent) {
            enqueueDeleteConsent(
                DeleteConsentRequest(
                    displayName = displayName,
                    uri = uri,
                    intentSender = result.intentSender,
                ),
            )
        }
        return updated
    }

    // ------------------------------------------------------------------ verified export

    fun requestExportFile(fileId: String) {
        val vault = _state.value.vault
        val kind = vault.kind ?: return
        if (!vault.unlocked) {
            notify("Unlock ${kind.title} before exporting files.", error = true)
            return
        }
        if (vault.exporting) {
            notify("Wait for the current export to finish.", error = true)
            return
        }
        val file = vault.files.firstOrNull { it.id == fileId } ?: run {
            notify("The file is no longer in the vault index.", error = true)
            return
        }
        _state.update { current ->
            if (current.vault.kind != kind) {
                current
            } else {
                current.copy(
                    vault = current.vault.copy(
                        exportRequest = ExportRequest(
                            mode = ExportMode.SINGLE_DOCUMENT,
                            fileIds = listOf(fileId),
                            suggestedName = VaultExport.safeName(file.displayName),
                        ),
                        exportError = null,
                    ),
                )
            }
        }
    }

    fun requestExportFiles(fileIds: Collection<String>) {
        val vault = _state.value.vault
        val kind = vault.kind ?: return
        if (!vault.unlocked) {
            notify("Unlock ${kind.title} before exporting files.", error = true)
            return
        }
        if (vault.exporting) {
            notify("Wait for the current export to finish.", error = true)
            return
        }
        val ids = fileIds.filter { id ->
            vault.files.any { it.id == id && it.status == VaultFileStatus.COMPLETE }
        }
        if (ids.isEmpty()) {
            notify("Select at least one completed file to export.", error = true)
            return
        }
        val first = vault.files.firstOrNull { it.id == ids.first() }
        _state.update { current ->
            if (current.vault.kind != kind) {
                current
            } else {
                current.copy(
                    vault = current.vault.copy(
                        exportRequest = ExportRequest(
                            mode = if (ids.size == 1) {
                                ExportMode.SINGLE_DOCUMENT
                            } else {
                                ExportMode.MULTIPLE_TO_TREE
                            },
                            fileIds = ids,
                            suggestedName = VaultExport.safeName(
                                first?.displayName ?: "Nexo Lock export",
                            ),
                        ),
                        exportError = null,
                    ),
                )
            }
        }
    }

    /** Atomically takes the pending SAF request so it is launched once. */
    fun consumeExportRequest(): ExportRequest? {
        val request = _state.value.vault.exportRequest ?: return null
        // The picker result arrives in a later Activity callback, so the
        // request must survive this state clear. Without this hand-off the
        // destination handler would see no request and silently drop it.
        pendingExport = request
        _state.update { it.copy(vault = it.vault.copy(exportRequest = null)) }
        return request
    }

    fun onExportPickerCancelled() {
        pendingExport = null
        _state.update { it.copy(vault = it.vault.copy(exportRequest = null)) }
    }

    fun onExportPickerFailed(message: String) {
        pendingExport = null
        _state.update {
            it.copy(vault = it.vault.copy(exportRequest = null, exportError = message))
        }
        notify(message, error = true)
    }

    /** Destination returned by ACTION_CREATE_DOCUMENT or ACTION_OPEN_DOCUMENT_TREE. */
    fun onExportDestination(uri: Uri, tree: Boolean) {
        val kind = _state.value.vault.kind ?: return
        val request = pendingExport ?: return
        pendingExport = null
        _state.update { it.copy(vault = it.vault.copy(exportRequest = null)) }
        if (!_state.value.vault.unlocked) {
            onExportPickerFailed("The ${kind.title} locked before the export could start.")
            return
        }
        val expectedTree = request.mode == ExportMode.MULTIPLE_TO_TREE
        if (tree != expectedTree) {
            onExportPickerFailed("The selected destination does not match the export request.")
            return
        }
        vaultExportJob?.cancel()
        vaultExportJob = viewModelScope.launch {
            val thisJob = currentCoroutineContext()[Job]
            val files = request.fileIds.mapNotNull { id ->
                _state.value.vault.files.firstOrNull {
                    it.id == id && it.status == VaultFileStatus.COMPLETE
                }
            }
            if (files.isEmpty()) {
                onExportPickerFailed("None of the selected files are available for export.")
                if (vaultExportJob === thisJob) vaultExportJob = null
                return@launch
            }
            val totalBytes = files.sumOf { it.sizeBytes.coerceAtLeast(0L) }
            _state.update { current ->
                if (current.vault.kind != kind) {
                    current
                } else {
                    current.copy(
                        vault = current.vault.copy(
                            exporting = true,
                            exportingName = null,
                            exportIndex = 0,
                            exportCount = files.size,
                            exportedBytes = 0L,
                            exportTotalBytes = totalBytes,
                            exportReport = null,
                            exportError = null,
                            message = null,
                        ),
                    )
                }
            }
            val results = mutableListOf<VaultExportFileResult>()
            var cancelled = false
            try {
                files.forEachIndexed { index, file ->
                    currentCoroutineContext().ensureActive()
                    _state.update { current ->
                        if (current.vault.kind != kind) {
                            current
                        } else {
                            current.copy(
                                vault = current.vault.copy(
                                    exportingName = file.displayName,
                                    exportIndex = index + 1,
                                    exportedBytes = 0L,
                                    exportTotalBytes = file.sizeBytes.coerceAtLeast(0L),
                                ),
                            )
                        }
                    }
                    val result = if (tree) {
                        container.vaults.exportFileToTree(kind, file.id, uri) { bytes ->
                            updateExportProgress(kind, bytes)
                        }
                    } else {
                        container.vaults.exportFileToUri(kind, file.id, uri) { bytes ->
                            updateExportProgress(kind, bytes)
                        }
                    }
                    results += result
                }
            } catch (error: CancellationException) {
                cancelled = true
            } finally {
                val report = VaultExportResult(results.toList(), cancelled)
                _state.update { current ->
                    if (current.vault.kind != kind) {
                        current
                    } else {
                        current.copy(
                            vault = current.vault.copy(
                                exporting = false,
                                exportingName = null,
                                exportIndex = 0,
                                exportCount = 0,
                                exportedBytes = 0L,
                                exportTotalBytes = 0L,
                                exportReport = if (results.isEmpty()) {
                                    current.vault.exportReport
                                } else {
                                    report
                                },
                                exportError = if (cancelled) {
                                    "Export cancelled. Any partial destination was removed."
                                } else {
                                    null
                                },
                                message = when {
                                    cancelled ->
                                        "Export cancelled. Any partial destination was removed."

                                    results.isEmpty() -> null
                                    report.failed == 0 && report.exported == 1 ->
                                        "Verified copy restored to the selected destination."

                                    report.failed == 0 ->
                                        "${report.exported} verified copies restored."

                                    else ->
                                        "${report.exported} restored; ${report.failed} failed."
                                },
                            ),
                        )
                    }
                }
                if (vaultExportJob === thisJob) vaultExportJob = null
                refreshLogs()
                refreshVaults()
            }
        }
    }

    private fun updateExportProgress(kind: VaultKind, bytes: Long) {
        _state.update { current ->
            if (current.vault.kind != kind) {
                current
            } else {
                current.copy(vault = current.vault.copy(exportedBytes = bytes))
            }
        }
    }

    fun cancelVaultExport() {
        vaultExportJob?.cancel()
    }

    fun clearExportReport() {
        _state.update {
            it.copy(vault = it.vault.copy(exportReport = null, exportError = null))
        }
    }

    // ------------------------------------------------------------------ delete-originals consent

    private fun enqueueDeleteConsent(request: DeleteConsentRequest) {
        if (activeDeleteConsent == null) {
            activeDeleteConsent = request
            _state.update { it.copy(vault = it.vault.copy(deleteConsent = request)) }
        } else {
            deleteConsentQueue.addLast(request)
        }
    }

    /** Atomically takes the consent request so the Activity launches it once. */
    fun consumeDeleteConsent(): DeleteConsentRequest? {
        val request = _state.value.vault.deleteConsent ?: return null
        _state.update { it.copy(vault = it.vault.copy(deleteConsent = null)) }
        return request
    }

    fun onDeleteConsentResult(approved: Boolean) {
        val current = activeDeleteConsent ?: return
        activeDeleteConsent = null
        viewModelScope.launch {
            val result = if (!approved) {
                OriginalDeletionResult.Failed(
                    "The system confirmation was declined. The original was kept.",
                )
            } else {
                val retry = container.vaults.deleteOriginal(current.uri)
                if (retry is OriginalDeletionResult.NeedsConsent) {
                    OriginalDeletionResult.Failed(
                        "The provider still refused to delete the original after confirmation. " +
                            "It was kept.",
                    )
                } else {
                    retry
                }
            }
            container.vaults.recordOriginalDeletion(current.displayName, result)
            _state.update { state ->
                state.copy(
                    vault = state.vault.copy(
                        deletionReport = state.vault.deletionReport
                            ?.resolveConsent(result, current.displayName),
                        deleteConsent = null,
                    ),
                )
            }
            promoteNextDeleteConsent()
        }
    }

    fun onDeleteConsentLaunchFailed(message: String) {
        val current = activeDeleteConsent ?: return
        activeDeleteConsent = null
        viewModelScope.launch {
            val result = OriginalDeletionResult.Failed(message)
            container.vaults.recordOriginalDeletion(current.displayName, result)
            _state.update { state ->
                state.copy(
                    vault = state.vault.copy(
                        deletionReport = state.vault.deletionReport
                            ?.resolveConsent(result, current.displayName),
                        deleteConsent = null,
                    ),
                )
            }
            promoteNextDeleteConsent()
        }
    }

    private fun promoteNextDeleteConsent() {
        if (deleteConsentQueue.isEmpty()) return
        val next = deleteConsentQueue.removeFirst()
        activeDeleteConsent = next
        _state.update { it.copy(vault = it.vault.copy(deleteConsent = next)) }
    }

    // ------------------------------------------------------------------ vault preferences

    fun setVaultLayout(layout: VaultLayout) {
        viewModelScope.launch { container.prefs.setVaultLayout(layout) }
    }

    fun setDeleteOriginalsAfterImport(value: Boolean) {
        viewModelScope.launch {
            container.prefs.setDeleteOriginalsAfterImport(value)
            container.logs.log(
                LogType.VAULT_AUTO_DELETE_CHANGED,
                if (value) {
                    "Delete originals after import enabled; every batch still requires confirmation"
                } else {
                    "Delete originals after import disabled"
                },
            )
        }
    }

    /**
     * Reloads entries, files and measured storage after an import. This runs in
     * a fresh coroutine because the importing coroutine may be cancelled.
     */
    private fun refreshVaultContents(kind: VaultKind) {
        viewModelScope.launch {
            val snapshot = runCatching { container.vaults.entries(kind) }.getOrNull()
            val storage = storageFor(kind)
            val available = container.vaults.availableStorageBytes()
            _state.update { current ->
                if (current.vault.kind != kind) {
                    current
                } else {
                    current.copy(
                        vault = current.vault.copy(
                            entries = snapshot?.entries ?: current.vault.entries,
                            files = snapshot?.files ?: current.vault.files,
                            storage = storage ?: current.vault.storage,
                            availableBytes = available ?: current.vault.availableBytes,
                        ),
                    )
                }
            }
        }
    }

    fun verifyVaultFile(fileId: String) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(vault = it.vault.copy(busy = true, error = null, message = null))
            }
            val result = container.vaults.verifyFile(kind, fileId)
            _state.update {
                if (it.vault.kind != kind) {
                    it
                } else {
                    it.copy(
                        vault = it.vault.copy(
                            busy = false,
                            error = if (result.ok) null else result.message,
                            message = if (result.ok) result.message else null,
                        ),
                    )
                }
            }
        }
    }

    fun deleteVaultFile(fileId: String, onResult: (String?) -> Unit = {}) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            val failure = container.vaults.deleteFile(kind, fileId)
            val snapshot = if (failure == null) {
                runCatching { container.vaults.entries(kind) }.getOrNull()
            } else {
                null
            }
            val storage = if (failure == null) storageFor(kind) else null
            _state.update {
                if (it.vault.kind != kind) {
                    it
                } else {
                    it.copy(
                        vault = it.vault.copy(
                            entries = snapshot?.entries ?: it.vault.entries,
                            files = snapshot?.files ?: it.vault.files,
                            storage = storage ?: it.vault.storage,
                            error = failure,
                            message = if (failure == null) "Encrypted file deleted." else null,
                        ),
                    )
                }
            }
            onResult(failure)
            refreshVaults()
        }
    }

    /** Deletes several selected files and refreshes the vault once. */
    fun deleteVaultFiles(fileIds: Collection<String>, onResult: (String?) -> Unit = {}) {
        val kind = _state.value.vault.kind ?: return
        if (fileIds.isEmpty()) return
        viewModelScope.launch {
            val failures = mutableListOf<String>()
            fileIds.forEach { id ->
                container.vaults.deleteFile(kind, id)?.let { failures += it }
            }
            val snapshot = runCatching { container.vaults.entries(kind) }.getOrNull()
            val storage = storageFor(kind)
            val failureText = if (failures.isEmpty()) {
                null
            } else {
                failures.joinToString(" ").take(MAX_IMPORT_ERROR_CHARS)
            }
            _state.update {
                if (it.vault.kind != kind) {
                    it
                } else {
                    it.copy(
                        vault = it.vault.copy(
                            entries = snapshot?.entries ?: it.vault.entries,
                            files = snapshot?.files ?: it.vault.files,
                            storage = storage ?: it.vault.storage,
                            availableBytes = container.vaults.availableStorageBytes()
                                ?: it.vault.availableBytes,
                            error = failureText,
                            message = if (failures.isEmpty()) {
                                if (fileIds.size == 1) {
                                    "Encrypted file deleted."
                                } else {
                                    "${fileIds.size} encrypted files deleted."
                                }
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
            onResult(failureText)
            refreshVaults()
        }
    }

    /**
     * Clears every Intruder Vault incident and its encrypted capture while
     * keeping secure notes. Requires the unlocked Intruder Vault.
     */
    fun clearIntruderHistory(onResult: (String?) -> Unit = {}) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            val result = container.vaults.clearIntruderHistory()
            val snapshot = if (result.failure == null) {
                runCatching { container.vaults.entries(kind) }.getOrNull()
            } else {
                null
            }
            val storage = if (result.failure == null) storageFor(kind) else null
            _state.update {
                if (it.vault.kind != kind) {
                    it
                } else {
                    it.copy(
                        vault = it.vault.copy(
                            entries = snapshot?.entries ?: it.vault.entries,
                            files = snapshot?.files ?: it.vault.files,
                            storage = storage ?: it.vault.storage,
                            availableBytes = container.vaults.availableStorageBytes()
                                ?: it.vault.availableBytes,
                            error = result.failure,
                            message = if (result.failure == null) {
                                if (result.deleted == 1) {
                                    "1 incident cleared."
                                } else {
                                    "${result.deleted} incidents cleared."
                                }
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
            onResult(result.failure)
            refreshVaults()
        }
    }

    /** Renames a streaming vault file. Requires the unlocked vault session. */
    fun renameVaultFile(fileId: String, newName: String, onResult: (String?) -> Unit = {}) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            val failure = container.vaults.renameFile(kind, fileId, newName)
            val snapshot = if (failure == null) {
                runCatching { container.vaults.entries(kind) }.getOrNull()
            } else {
                null
            }
            _state.update {
                if (it.vault.kind != kind) {
                    it
                } else {
                    it.copy(
                        vault = it.vault.copy(
                            entries = snapshot?.entries ?: it.vault.entries,
                            files = snapshot?.files ?: it.vault.files,
                            error = failure,
                            message = if (failure == null) "File renamed." else null,
                        ),
                    )
                }
            }
            onResult(failure)
        }
    }

    /** Renames a note or capture entry; the attachment and evidence are untouched. */
    fun renameVaultEntry(entryId: String, newTitle: String, onResult: (String?) -> Unit = {}) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            val failure = container.vaults.renameEntry(kind, entryId, newTitle)
            val snapshot = if (failure == null) {
                runCatching { container.vaults.entries(kind) }.getOrNull()
            } else {
                null
            }
            _state.update {
                if (it.vault.kind != kind) {
                    it
                } else {
                    it.copy(
                        vault = it.vault.copy(
                            entries = snapshot?.entries ?: it.vault.entries,
                            files = snapshot?.files ?: it.vault.files,
                            error = failure,
                            message = if (failure == null) "Entry renamed." else null,
                        ),
                    )
                }
            }
            onResult(failure)
        }
    }

    // ------------------------------------------------------------------ viewers

    /**
     * Decrypts one stored file to the app-private cache and exposes its path to
     * the viewer. The whole container is verified while it is decrypted, so the
     * viewer only ever receives authenticated plaintext.
     */
    fun prepareVaultFile(kind: VaultKind, fileId: String) {
        val request = ++viewerRequest
        viewModelScope.launch {
            val existing = _state.value.viewer
            val existingPath = existing.cachePath
            if (existing.kind == kind && existing.file?.id == fileId &&
                existingPath != null && File(existingPath).exists()
            ) {
                return@launch
            }
            // Never leave a previously decrypted preview behind when switching.
            existing.cachePath?.let { old -> runCatching { File(old).delete() } }
            _state.update {
                it.copy(viewer = ViewerUiState(kind = kind, load = ViewerLoad.Preparing()))
            }
            val snapshot = runCatching { container.vaults.entries(kind) }.getOrNull()
            val file = snapshot?.files?.firstOrNull { it.id == fileId }
            if (file == null) {
                if (viewerRequest == request) {
                    _state.update {
                        it.copy(
                            viewer = ViewerUiState(
                                kind = kind,
                                load = ViewerLoad.Failed(
                                    "The file is not available. Unlock ${kind.title} and try again.",
                                ),
                            ),
                        )
                    }
                }
                return@launch
            }
            if (file.status != VaultFileStatus.COMPLETE) {
                if (viewerRequest == request) {
                    _state.update {
                        it.copy(
                            viewer = ViewerUiState(
                                kind = kind,
                                file = file,
                                load = ViewerLoad.Failed(
                                    "${file.displayName} did not finish importing, so it cannot be opened.",
                                ),
                            ),
                        )
                    }
                }
                return@launch
            }
            _state.update {
                it.copy(
                    viewer = it.viewer.copy(
                        file = file,
                        totalBytes = file.sizeBytes,
                        load = ViewerLoad.Preparing(),
                    ),
                )
            }
            val decrypted = runCatching {
                container.vaults.openFile(kind, fileId) { bytes ->
                    if (viewerRequest == request) {
                        _state.update { current ->
                            if (current.viewer.load is ViewerLoad.Preparing) {
                                current.copy(
                                    viewer = current.viewer.copy(
                                        load = ViewerLoad.Preparing(bytes),
                                    ),
                                )
                            } else {
                                current
                            }
                        }
                    }
                }
            }
            if (viewerRequest != request) {
                decrypted.getOrNull()?.let { runCatching { it.delete() } }
                return@launch
            }
            decrypted.onSuccess { path ->
                _state.update {
                    it.copy(
                        viewer = it.viewer.copy(
                            load = ViewerLoad.Ready(path.absolutePath),
                        ),
                    )
                }
                // The vault session is an in-memory timestamp, so a viewer must
                // not outlive it. Poll truthfully and delete the preview.
                watchViewerSession(kind, request)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        viewer = it.viewer.copy(
                            load = ViewerLoad.Failed(
                                error.message ?: "The file could not be decrypted.",
                            ),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Decrypts a legacy entry attachment (Intruder capture or V4 note file)
     * into the app-private cache for the real viewer.
     */
    fun prepareVaultEntry(kind: VaultKind, entryId: String) {
        val request = ++viewerRequest
        viewModelScope.launch {
            val existing = _state.value.viewer
            val existingPath = existing.cachePath
            if (existing.kind == kind && existing.entryId == entryId &&
                existingPath != null && File(existingPath).exists()
            ) {
                return@launch
            }
            // Never leave a previously decrypted preview behind when switching.
            existing.cachePath?.let { old -> runCatching { File(old).delete() } }
            _state.update {
                it.copy(
                    viewer = ViewerUiState(
                        kind = kind,
                        entryId = entryId,
                        load = ViewerLoad.Preparing(),
                    ),
                )
            }
            val snapshot = runCatching { container.vaults.entries(kind) }.getOrNull()
            val entry = snapshot?.entries?.firstOrNull { it.id == entryId }
            if (entry == null) {
                if (viewerRequest == request) {
                    _state.update {
                        it.copy(
                            viewer = ViewerUiState(
                                kind = kind,
                                entryId = entryId,
                                load = ViewerLoad.Failed(
                                    "The entry is not available. Unlock ${kind.title} and try again.",
                                ),
                            ),
                        )
                    }
                }
                return@launch
            }
            _state.update {
                it.copy(
                    viewer = it.viewer.copy(
                        displayName = entry.title.ifBlank { "Vault attachment" },
                        mimeType = entry.mimeType ?: "image/jpeg",
                    ),
                )
            }
            val decrypted = runCatching { container.vaults.openAttachment(kind, entryId) }
            if (viewerRequest != request) {
                decrypted.getOrNull()?.let { runCatching { it.delete() } }
                return@launch
            }
            decrypted.onSuccess { path ->
                _state.update {
                    it.copy(
                        viewer = it.viewer.copy(
                            load = ViewerLoad.Ready(path.absolutePath),
                            totalBytes = path.length(),
                        ),
                    )
                }
                watchViewerSession(kind, request)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        viewer = it.viewer.copy(
                            load = ViewerLoad.Failed(
                                error.message ?: "The attachment could not be decrypted.",
                            ),
                        ),
                    )
                }
            }
        }
    }

    /** Deletes the decrypted preview and invalidates any in-flight preparation. */
    fun releaseViewer() {
        viewerRequest += 1
        val path = _state.value.viewer.cachePath
        if (path != null) runCatching { File(path).delete() }
        _state.update { it.copy(viewer = ViewerUiState()) }
    }

    /**
     * Ends the viewer when the vault session expires. The manager's session is
     * a timestamp checked on access, so the viewer polls it instead of assuming
     * that an open screen keeps the vault unlocked.
     */
    private fun watchViewerSession(kind: VaultKind, request: Long) {
        viewModelScope.launch {
            while (viewerRequest == request) {
                delay(VIEWER_SESSION_POLL_MS)
                if (viewerRequest != request) return@launch
                if (!container.vaults.isUnlocked(kind)) {
                    val path = _state.value.viewer.cachePath
                    if (path != null) runCatching { File(path).delete() }
                    _state.update {
                        it.copy(
                            viewer = ViewerUiState(
                                kind = kind,
                                load = ViewerLoad.Failed(
                                    "The ${kind.title} session ended. The decrypted preview " +
                                        "was deleted and the vault is locked.",
                                ),
                            ),
                        )
                    }
                    return@launch
                }
            }
        }
    }

    fun readVaultAttachment(entryId: String, onResult: (ByteArray?) -> Unit) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            onResult(runCatching { container.vaults.readAttachment(kind, entryId) }.getOrNull())
        }
    }

    /**
     * Decrypts a bounded image thumbnail into memory. The manager caches the
     * decoded bitmap and drops it when the vault locks. Returns null for
     * non-images, locked vaults and images too large to decode safely.
     */
    suspend fun loadVaultThumbnail(kind: VaultKind, fileId: String): Bitmap? =
        runCatching { container.vaults.loadThumbnail(kind, fileId) }.getOrNull()

    /** Decrypts a bounded thumbnail for a legacy V4 capture attachment. */
    suspend fun loadVaultAttachmentThumbnail(kind: VaultKind, attachmentId: String): Bitmap? =
        runCatching {
            container.vaults.loadAttachmentThumbnail(kind, attachmentId)
        }.getOrNull()

    fun deleteVaultEntry(entryId: String) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            val failure = container.vaults.deleteEntry(kind, entryId)
            val snapshot = if (failure == null) {
                runCatching { container.vaults.entries(kind) }.getOrNull()
            } else {
                null
            }
            val storage = if (failure == null) storageFor(kind) else null
            _state.update {
                it.copy(
                    vault = it.vault.copy(
                        entries = snapshot?.entries ?: it.vault.entries,
                        files = snapshot?.files ?: it.vault.files,
                        storage = storage ?: it.vault.storage,
                        error = failure,
                    ),
                )
            }
            refreshVaults()
        }
    }

    fun changeVaultPin(currentPin: String, newPin: String, onResult: (String?) -> Unit) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            val failure = container.vaults.changePin(kind, currentPin, newPin)
            _state.update { it.copy(vault = it.vault.copy(error = failure, message = if (failure == null) "Vault PIN changed." else null)) }
            onResult(failure)
        }
    }

    fun deleteVault(onResult: (String?) -> Unit) {
        val kind = _state.value.vault.kind ?: return
        viewModelScope.launch {
            vaultImportJob?.cancel()
            vaultImportJob = null
            vaultExportJob?.cancel()
            vaultExportJob = null
            pendingExport = null
            deleteConsentQueue.clear()
            activeDeleteConsent = null
            val failure = container.vaults.deleteVault(kind)
            releaseViewer()
            _state.update {
                it.copy(
                    vault = it.vault.copy(
                        status = if (failure == null) {
                            VaultStatus.NeedsPin
                        } else {
                            it.vault.status
                        },
                        busy = false,
                        entries = emptyList(),
                        files = emptyList(),
                        storage = null,
                        availableBytes = null,
                        importingName = null,
                        importedBytes = 0L,
                        importTotalBytes = null,
                        importStage = null,
                        importIndex = 0,
                        importCount = 0,
                        importFailures = emptyList(),
                        pendingImport = null,
                        exporting = false,
                        exportingName = null,
                        exportIndex = 0,
                        exportCount = 0,
                        exportedBytes = 0L,
                        exportTotalBytes = 0L,
                        exportReport = null,
                        exportError = null,
                        exportRequest = null,
                        deletionReport = null,
                        deleteConsent = null,
                        error = failure,
                        message = if (failure == null) "${kind.title} deleted." else null,
                    ),
                )
            }
            onResult(failure)
            refreshVaults()
        }
    }

    fun setIntruderCaptureEnabled(value: Boolean, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val config = container.prefs.snapshot()
            if (value && !config.hasIntruderVaultPin) {
                onResult("Create the Intruder Vault PIN first.")
                return@launch
            }
            container.prefs.setIntruderCaptureEnabled(value)
            container.intruderCapture.refreshReadiness()
            container.logs.log(
                LogType.CAPTURE_SETTING_CHANGED,
                if (value) "Intruder capture enabled" else "Intruder capture disabled",
            )
            onResult(null)
        }
    }

    // ------------------------------------------------------------------ settings

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.prefs.setThemeMode(mode) }
    }

    fun setStrictMode(value: Boolean) {
        viewModelScope.launch { container.prefs.setStrictMode(value) }
    }

    fun setPrivacyCover(value: Boolean) {
        viewModelScope.launch { container.prefs.setPrivacyCover(value) }
    }

    fun setBiometricEnabled(value: Boolean, onResult: (String?) -> Unit = {}) {
        if (value && _state.value.biometric != BiometricState.READY) {
            onResult("Biometric authentication is not available or not enrolled.")
            return
        }
        viewModelScope.launch {
            container.prefs.setBiometricEnabled(value)
            container.logs.log(
                LogType.PIN_CHANGED,
                if (value) "Biometric unlock enabled" else "Biometric unlock disabled",
            )
            onResult(null)
        }
    }

    fun verifyMainPin(pin: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val attempt = container.auth.verify(CredentialKind.MAIN_PIN, pin)
            if (attempt.accepted) {
                onResult(null)
            } else {
                container.logs.log(LogType.MAIN_PIN_FAILED, "Wrong PIN while authorising a settings change")
                onResult(
                    if (attempt.lockedOutMs > 0) {
                        "Too many attempts. Try again in ${LockoutPolicy.describe(attempt.lockedOutMs)}."
                    } else {
                        "Incorrect PIN. ${attempt.attemptsUntilLockout} attempts left before a delay."
                    },
                )
            }
        }
    }

    fun changeMainPin(currentPin: String, newPin: String, onResult: (String?) -> Unit) {
        if (!AuthPolicy.validPin(newPin)) {
            onResult("Use 4 to 8 digits.")
            return
        }
        viewModelScope.launch {
            val attempt = container.auth.verify(CredentialKind.MAIN_PIN, currentPin)
            if (!attempt.accepted) {
                if (attempt.lockedOutMs > 0) {
                    onResult("Too many attempts. Try again in ${LockoutPolicy.describe(attempt.lockedOutMs)}.")
                } else {
                    onResult("Current PIN is incorrect. ${attempt.attemptsUntilLockout} attempts left before a delay.")
                }
                container.logs.log(LogType.MAIN_PIN_FAILED, "Wrong PIN while changing credentials")
                return@launch
            }
            runCatching {
                if (container.credentials.exists(CredentialKind.RECOVERY_PIN) &&
                    container.credentials.verify(CredentialKind.RECOVERY_PIN, newPin)
                ) {
                    onResult("The main PIN must be different from the recovery PIN.")
                    return@runCatching
                }
                container.credentials.store(CredentialKind.MAIN_PIN, newPin)
                container.prefs.setPinLength(newPin.length)
                container.logs.log(LogType.PIN_CHANGED, "Main PIN changed")
                onResult(null)
            }.onFailure { onResult("The PIN could not be saved.") }
        }
    }

    fun changeRecoveryPin(mainPin: String, newPin: String, onResult: (String?) -> Unit) {
        if (!AuthPolicy.validPin(newPin)) {
            onResult("Use 4 to 8 digits.")
            return
        }
        viewModelScope.launch {
            val attempt = container.auth.verify(CredentialKind.MAIN_PIN, mainPin)
            if (!attempt.accepted) {
                onResult(
                    if (attempt.lockedOutMs > 0) {
                        "Too many attempts. Try again in ${LockoutPolicy.describe(attempt.lockedOutMs)}."
                    } else {
                        "Main PIN is incorrect."
                    },
                )
                return@launch
            }
            runCatching {
                if (container.credentials.verify(CredentialKind.MAIN_PIN, newPin)) {
                    onResult("The recovery PIN must be different from the main PIN.")
                    return@runCatching
                }
                container.credentials.store(CredentialKind.RECOVERY_PIN, newPin)
                container.logs.log(LogType.RECOVERY_CHANGED, "Recovery PIN changed")
                onResult(null)
            }.onFailure { onResult("The recovery PIN could not be saved.") }
        }
    }

    fun changeRecoveryPattern(mainPin: String, pattern: String, onResult: (String?) -> Unit) {
        if (!AuthPolicy.validPattern(pattern)) {
            onResult("Connect at least 4 different dots.")
            return
        }
        viewModelScope.launch {
            val attempt = container.auth.verify(CredentialKind.MAIN_PIN, mainPin)
            if (!attempt.accepted) {
                onResult(
                    if (attempt.lockedOutMs > 0) {
                        "Too many attempts. Try again in ${LockoutPolicy.describe(attempt.lockedOutMs)}."
                    } else {
                        "Main PIN is incorrect."
                    },
                )
                return@launch
            }
            runCatching {
                container.credentials.store(CredentialKind.RECOVERY_PATTERN, pattern)
                container.logs.log(LogType.RECOVERY_CHANGED, "Recovery pattern changed")
                onResult(null)
            }.onFailure { onResult("The recovery pattern could not be saved.") }
        }
    }

    fun resetRecoveryCredentials(mainPin: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val attempt = container.auth.verify(CredentialKind.MAIN_PIN, mainPin)
            if (!attempt.accepted) {
                onResult(
                    if (attempt.lockedOutMs > 0) {
                        "Too many attempts. Try again in ${LockoutPolicy.describe(attempt.lockedOutMs)}."
                    } else {
                        "Main PIN is incorrect."
                    },
                )
                return@launch
            }
            runCatching {
                container.credentials.clear(CredentialKind.RECOVERY_PIN)
                container.credentials.clear(CredentialKind.RECOVERY_PATTERN)
                container.logs.log(LogType.RECOVERY_CHANGED, "Recovery credentials reset")
                onResult(null)
            }.onFailure { onResult("Recovery credentials could not be reset.") }
        }
    }

    fun saveGesture(pattern: VolumePattern, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                container.credentials.store(CredentialKind.MAIN_GESTURE, pattern.code)
                container.prefs.setGestureLength(pattern.presses.size)
                container.prefs.setGestureEnabled(true)
                container.logs.log(LogType.GESTURE_CONFIGURED, "Volume pattern configured")
                onResult(null)
            }.onFailure { onResult("The volume pattern could not be saved.") }
        }
    }

    fun clearGesture() {
        viewModelScope.launch {
            container.credentials.clear(CredentialKind.MAIN_GESTURE)
            container.prefs.setGestureLength(0)
            container.prefs.setGestureEnabled(false)
            container.logs.log(LogType.GESTURE_CONFIGURED, "Volume pattern removed")
        }
    }

    fun setGestureEnabled(value: Boolean) {
        viewModelScope.launch {
            if (value && !container.credentials.exists(CredentialKind.MAIN_GESTURE)) {
                notify("Record a volume pattern first.", error = true)
                return@launch
            }
            container.prefs.setGestureEnabled(value)
        }
    }

    /** Token-scoped capture: a disposing screen cannot clear a newer capture. */
    fun beginGestureCapture(owner: Any, listener: (VolumePress) -> Unit) {
        container.volumeCapture.begin(owner, listener)
    }

    fun endGestureCapture(owner: Any) {
        container.volumeCapture.end(owner)
    }

    @Deprecated("Pass an owner token so a stale screen cannot clear the capture.")
    fun beginGestureCapture(listener: (VolumePress) -> Unit) {
        container.volumeCapture.setListener(listener)
    }

    @Deprecated("Pass an owner token so a stale screen cannot clear the capture.")
    fun endGestureCapture() {
        container.volumeCapture.clear()
    }

    fun recordRecoveryVerification(method: UnlockMethod) {
        viewModelScope.launch {
            container.logs.log(
                LogType.RECOVERY_UNLOCKED,
                "Recovery authentication verified",
                method.label,
            )
            refreshLogs()
        }
    }

    fun testGesture(pattern: VolumePattern, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                container.credentials.verify(CredentialKind.MAIN_GESTURE, pattern.code)
            }.getOrDefault(false)
            onResult(ok)
        }
    }

    // ------------------------------------------------------------------ recovery (standalone screen)

    fun verifyRecoveryPin(pin: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val attempt = container.auth.verify(CredentialKind.RECOVERY_PIN, pin)
            if (attempt.accepted) {
                onResult(null)
            } else {
                container.logs.log(LogType.RECOVERY_FAILED, "Recovery PIN rejected")
                onResult(
                    if (attempt.lockedOutMs > 0) {
                        "Too many attempts. Try again in ${LockoutPolicy.describe(attempt.lockedOutMs)}."
                    } else {
                        "Incorrect recovery PIN. ${attempt.attemptsUntilLockout} attempts left before a delay."
                    },
                )
            }
        }
    }

    fun verifyRecoveryPattern(pattern: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val attempt = container.auth.verify(CredentialKind.RECOVERY_PATTERN, pattern)
            if (attempt.accepted) {
                onResult(null)
            } else {
                container.logs.log(LogType.RECOVERY_FAILED, "Recovery pattern rejected")
                onResult(
                    if (attempt.lockedOutMs > 0) {
                        "Too many attempts. Try again in ${LockoutPolicy.describe(attempt.lockedOutMs)}."
                    } else {
                        "Incorrect recovery pattern. ${attempt.attemptsUntilLockout} attempts left before a delay."
                    },
                )
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            container.logs.clear()
            container.logs.log(LogType.LOG_CLEARED, "Security log cleared by the owner")
            refreshLogs()
        }
    }

    companion object {
        private const val VIEWER_SESSION_POLL_MS = 2_000L
        private const val MAX_IMPORT_ERROR_CHARS = 900

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NexoViewModel(container) as T
            }
    }
}

private data class ImportMetadata(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?,
)

/**
 * Reads the display name, MIME type and (advisory) size a content provider
 * exposes. The declared size is only used for progress; the byte count written
 * by the streaming import is authoritative.
 */
private fun readImportMetadata(context: Context, uri: Uri): ImportMetadata {
    val resolver = context.contentResolver
    var providerType: String? = null
    var name: String? = null
    var size: Long? = null
    runCatching {
        providerType = resolver.getType(uri)
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    name = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    }
    val displayName = sanitizeDisplayName(name)
    val declared = providerType?.takeIf { it.isNotBlank() }
    val mimeType = when {
        declared != null && declared != "application/octet-stream" -> declared
        else -> mimeTypeFromName(displayName) ?: declared ?: "application/octet-stream"
    }
    return ImportMetadata(
        displayName = displayName,
        mimeType = mimeType,
        // A provider is allowed to report -1/unknown; 0 is a real empty file.
        sizeBytes = size?.takeIf { it >= 0L },
    )
}

private fun mimeTypeFromName(displayName: String): String? {
    val extension = displayName.substringAfterLast('.', "").lowercase()
    if (extension.isBlank()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
}

/**
 * Advisory read-grant check for diagnostics. A null result means the URI is
 * not a content URI; a denied grant is still attempted because some providers
 * grant access without the flag being visible here.
 */
private fun readPermissionGranted(context: Context, uri: Uri): Boolean? {
    if (uri.scheme != "content") return null
    return runCatching {
        context.checkCallingOrSelfUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
    }.getOrNull()
}
