package com.vinay.nexolock.protection

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vinay.nexolock.NexoApplication
import com.vinay.nexolock.domain.ThemeMode
import com.vinay.nexolock.ui.lock.LockPhase
import com.vinay.nexolock.ui.lock.LockScreen
import com.vinay.nexolock.ui.lock.LockViewModel
import com.vinay.nexolock.ui.theme.NexoTheme
import java.lang.ref.WeakReference

/**
 * Full-screen, separate-task lock surface.
 *
 * It is launched by [ProtectionService] and never exported. The activity is
 * flagged FLAG_SECURE so the protected app's content cannot leak through the
 * recents thumbnail or a screenshot of the authentication screen.
 */
class LockActivity : FragmentActivity() {

    private lateinit var container: com.vinay.nexolock.AppContainer
    private var targetPackage: String = ""
    private var sessionToken: String = ""
    private lateinit var viewModel: LockViewModel
    private var biometricPrompt: BiometricPrompt? = null

    /** Token that owns the shared volume capture while this Activity is alive. */
    private val volumeOwner = Any()

    /** Incremented for every prompt; stale callbacks are ignored. */
    private var promptGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val app = application as? NexoApplication
        if (app == null) {
            finish()
            return
        }
        container = app.container
        targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        sessionToken = intent?.getStringExtra(EXTRA_SESSION_TOKEN).orEmpty()
        if (targetPackage.isEmpty() || sessionToken.isEmpty()) {
            finish()
            return
        }
        current = WeakReference(this)
        viewModel = ViewModelProviderFactory.create(this, container, targetPackage, sessionToken)
        // The volume capture is owned by this Activity for its whole focused
        // lifetime. dispatchKeyEvent() is only delivered to a focused window,
        // and the ViewModel's phase guard decides whether a press is relevant.
        // Arming it here removes the composition race that used to drop the
        // first presses after the gesture step appeared.
        container.volumeCapture.begin(volumeOwner, viewModel::onVolumePress)
        // The activity window exists from this point. Report it as visible
        // before the first Compose frame: on a cold or loaded device the first
        // frame can take seconds, and that must not be mistaken for a missing
        // lock surface.
        container.runtime.onLockUiVisible(targetPackage, sessionToken)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Back is always "Close app": it cancels the session and
                    // denies access. This applies to the decoy as well as the
                    // authentication and PIN surfaces, so Back can never
                    // dismiss the lock and reveal the protected app.
                    viewModel.onCloseApp()
                }
            },
        )

        setContent {
            NexoTheme(mode = ThemeMode.DARK) {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    // The first frame has been produced: the decoy (or direct
                    // authentication surface) is now visible. Each stage's own
                    // fixed window is allowed to start from this point.
                    viewModel.onSurfaceVisible()
                    container.runtime.onLockUiVisible(targetPackage, sessionToken)
                    container.runtime.onLockUiReady(targetPackage, sessionToken)
                }

                // The genuine system prompt is shown only while the session is
                // in its biometric stage. The system prompt takes input focus
                // and would swallow volume-key events, so the volume pattern is
                // only reachable after the prompt has been dismissed.
                LaunchedEffect(
                    state.phase,
                    state.biometricRequest,
                    state.biometricEnabled,
                    state.biometricReady,
                    state.unlocked,
                    state.denied,
                ) {
                    if (state.phase is LockPhase.Biometric &&
                        state.biometricEnabled && state.biometricReady &&
                        state.biometricRequest > 0 && !state.unlocked && !state.denied
                    ) {
                        showBiometricPrompt()
                    }
                }

                LaunchedEffect(state.closeRequested) {
                    if (state.closeRequested) closeAppAndGoHome()
                }

                // Terminal denial: show the honest message briefly, then leave
                // the lock and go home. Nothing is carried into a new session.
                LaunchedEffect(state.deniedMessage) {
                    if (state.deniedMessage != null) {
                        kotlinx.coroutines.delay(1_600L)
                        closeAppAndGoHome()
                    }
                }

                LockScreen(
                    state = state,
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::onBackspace,
                    onBiometric = { showBiometricPrompt(force = true) },
                    onDecoyContinue = viewModel::onDecoyContinue,
                    onCloseApp = viewModel::onCloseApp,
                    onAppInfo = {
                        runCatching {
                            startActivity(PermissionManager.appDetailsIntent(this))
                        }
                    },
                    onRecovery = viewModel::openRecovery,
                    onCancelRecovery = viewModel::closeRecovery,
                    verifyRecoveryPin = viewModel::verifyRecoveryPin,
                    verifyRecoveryPattern = viewModel::verifyRecoveryPattern,
                    onRecoverySuccess = viewModel::onRecoverySuccess,
                    onResetGesture = viewModel::resetGesture,
                    onUnlocked = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) {
            container.runtime.onLockUiVisible(targetPackage, sessionToken)
        }
    }

    override fun onPause() {
        // The volume listener is intentionally kept while the activity is not
        // focused; dispatchKeyEvent is only called when this window has focus,
        // so listening cannot capture global volume presses. The service still
        // learns that the surface is no longer in front for status purposes.
        // Configuration changes (rotation) must not look like an abandoned lock.
        if (!isChangingConfigurations && ::container.isInitialized) {
            container.runtime.onLockUiHidden(targetPackage, sessionToken)
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (::container.isInitialized) {
            container.volumeCapture.end(volumeOwner)
            if (biometricPrompt != null) {
                reportPromptActive(false)
            }
        }
        promptGeneration += 1
        runCatching { biometricPrompt?.cancelAuthentication() }
        biometricPrompt = null
        if (::container.isInitialized) {
            if (isChangingConfigurations) {
                // The same session is re-created with the same token; keep the
                // service's lock state intact.
                container.runtime.onLockUiVisible(targetPackage, sessionToken)
            } else {
                container.runtime.onLockSurfaceGone(targetPackage, sessionToken)
            }
        }
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    // Lint flags ComponentActivity.dispatchKeyEvent as restricted to the
    // androidx group, but this override is the public framework Activity hook.
    // It is required to swallow volume keys before any view can consume them.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::container.isInitialized && container.volumeCapture.onKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showBiometricPrompt(force: Boolean = false) {
        if (isFinishing || isDestroyed) return
        val state = viewModel.state.value
        if (state.phase !is LockPhase.Biometric || state.unlocked || state.denied) {
            return
        }
        if (biometricPrompt != null) {
            if (!force) return
            // The owner explicitly asked to retry. Invalidate the old callbacks
            // before cancelling, then create exactly one fresh prompt.
            promptGeneration += 1
            runCatching { biometricPrompt?.cancelAuthentication() }
            biometricPrompt = null
        }
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            viewModel.onBiometricError(
                BiometricPrompt.ERROR_NO_BIOMETRICS,
                "Biometric authentication is not available",
            )
            return
        }
        val generation = ++promptGeneration
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (generation != promptGeneration) return
                    biometricPrompt = null
                    reportPromptActive(false)
                    viewModel.onBiometricSucceeded()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (generation != promptGeneration) return
                    biometricPrompt = null
                    reportPromptActive(false)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        // Documented policy: cancelling the system prompt is
                        // terminal. The session denies and a new lock launch is
                        // required; nothing is silently skipped.
                        viewModel.onBiometricCancelled()
                    } else {
                        viewModel.onBiometricError(errorCode, errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    if (generation != promptGeneration) return
                    // Documented policy: a failed biometric result is terminal.
                    // Invalidate this prompt's callbacks, close it, and deny.
                    val activePrompt = biometricPrompt
                    promptGeneration += 1
                    biometricPrompt = null
                    reportPromptActive(false)
                    viewModel.onBiometricFailed()
                    runCatching { activePrompt?.cancelAuthentication() }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify it's you")
            .setSubtitle("Step 1 of ${viewModel.state.value.requiredFactors.size + 1} · NEXO LOCK")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(false)
            .build()
        biometricPrompt = prompt
        reportPromptActive(true)
        runCatching { prompt.authenticate(info) }.onFailure { error ->
            if (generation == promptGeneration) {
                biometricPrompt = null
                reportPromptActive(false)
                viewModel.onBiometricError(
                    BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
                    error.javaClass.simpleName,
                )
            }
        }
    }

    private fun reportPromptActive(active: Boolean) {
        if (!::container.isInitialized) return
        if (active) {
            container.runtime.onLockPromptShown(targetPackage, sessionToken)
        } else {
            container.runtime.onLockPromptDismissed(targetPackage, sessionToken)
        }
    }

    /**
     * "Close app" on the decoy: cancel the flow, leave the lock and return
     * home. Android does not allow one app to force-stop another app's
     * foreground process; NEXO does not pretend otherwise.
     */
    private fun closeAppAndGoHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "com.vinay.nexolock.extra.TARGET_PACKAGE"
        const val EXTRA_SESSION_TOKEN = "com.vinay.nexolock.extra.SESSION_TOKEN"

        @Volatile
        private var current: WeakReference<LockActivity>? = null

        /**
         * Finish the visible lock screen, if any. When [sessionToken] is given,
         * only the activity that owns that exact session is finished, so a stale
         * timeout can never tear down a newer lock for the same package.
         */
        fun finishActive(sessionToken: String? = null) {
            val activity = current?.get() ?: return
            if (sessionToken != null && activity.sessionToken != sessionToken) return
            activity.runOnUiThread {
                runCatching { activity.finish() }
            }
        }
    }
}

private object ViewModelProviderFactory {
    fun create(
        activity: FragmentActivity,
        container: com.vinay.nexolock.AppContainer,
        targetPackage: String,
        sessionToken: String,
    ): LockViewModel = androidx.lifecycle.ViewModelProvider(
        activity,
        LockViewModel.factory(container, targetPackage, sessionToken),
    )[LockViewModel::class.java]
}
