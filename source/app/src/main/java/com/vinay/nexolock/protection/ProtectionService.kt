package com.vinay.nexolock.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vinay.nexolock.AppContainer
import com.vinay.nexolock.BuildConfig
import com.vinay.nexolock.MainActivity
import com.vinay.nexolock.NexoApplication
import com.vinay.nexolock.R
import com.vinay.nexolock.data.LogType
import com.vinay.nexolock.data.NexoConfig
import com.vinay.nexolock.domain.LockoutPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Best-effort foreground monitor.
 *
 * Detection uses the accessibility service when connected (fast) and falls back
 * to UsageStats polling (documented, slower). Android has no API that lets a
 * third-party app intercept another app's launch, so a short exposure window is
 * unavoidable and is documented in the UI.
 */
class ProtectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var container: AppContainer
    private lateinit var shield: ShieldOverlay

    private var config = NexoConfig()
    private var protectedPackages: Set<String> = emptySet()
    private val unlockedPackages = LinkedHashSet<String>()

    private var lockPackage: String? = null
    private var lockSessionToken: String? = null
    private var lockReady = false
    private var lockVisible = false
    private var lockSurfaceAlive = false
    private var lockPromptActive = false
    private var lockStartedAt = 0L
    private var lastForeground: String? = null

    private var lastUsageEventTime = System.currentTimeMillis() - INITIAL_LOOKBACK_MS
    private var lastWarningAt = 0L
    private var started = false
    private var lastDegradedSignature = ""

    /**
     * Accessibility stays fully event-driven: while it is connected this loop
     * only re-checks permission health every 30 s. UsageStats is the documented
     * fallback and is polled at a slower 2 s cadence, never while the screen is
     * off (the poll body exits early in that case).
     */
    private val pollTick = object : Runnable {
        override fun run() {
            if (PermissionChecks.isAccessibilityConnected()) {
                checkHealth()
                handler.postDelayed(this, HEALTH_INTERVAL_MS)
            } else {
                pollOnce()
                checkHealth()
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private val lockTimeout = Runnable {
        val pkg = lockPackage ?: return@Runnable
        val token = lockSessionToken
        // A created activity window (even before its first Compose frame) is a
        // real overlay. Only a lock that never produced a surface is a failure.
        if (!lockReady && !lockSurfaceAlive && !lockVisible) {
            debugLog { "lock launch timeout: ready=$lockReady alive=$lockSurfaceAlive visible=$lockVisible" }
            LockActivity.finishActive(token)
            shield.hide()
            clearLockState()
            reportProblem(
                "The lock screen could not be displayed. Check display-over-other-apps permission.",
                LogType.SERVICE_ERROR,
                pkg,
            )
            sendProtectedAppHome()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // A screen lock ends the "already unlocked" window; the next
                    // visit to a protected app must authenticate again.
                    unlockedPackages.clear()
                    lastForeground = null
                }
                Intent.ACTION_USER_PRESENT -> {
                    handler.post {
                        checkHealth()
                        pollOnce()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = (application as NexoApplication).container
        shield = ShieldOverlay(this)
        createChannel()
        startForegroundCompat("Preparing protection")
        container.runtime.attach(this)
        registerScreenReceiver()
        started = true

        scope.launch {
            container.prefs.config.collect { updated ->
                config = updated
                if (started && !updated.protectionEnabled) {
                    stopSelf()
                } else {
                    updateNotification()
                }
            }
        }
        scope.launch {
            container.protectedApps.packages.collect { packages ->
                protectedPackages = packages
                updateNotification()
            }
        }

        handler.post(pollTick)
        checkHealth()
        scope.launch {
            container.logs.log(LogType.PROTECTION_STARTED, "Protection monitor started")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopProtection()
            else -> {
                container.runtime.attach(this)
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the foreground monitor alive; the system decides when to reclaim it.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        LockActivity.finishActive(lockSessionToken)
        shield.hide()
        container.runtime.detach(this)
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ runtime callbacks

    fun onForegroundPackage(packageName: String) {
        handler.post { handleForeground(packageName) }
    }

    fun onLockUiVisible(packageName: String, sessionToken: String) {
        handler.post {
            if (!matchesActiveLock(packageName, sessionToken)) return@post
            lockSurfaceAlive = true
            lockVisible = true
            container.runtime.setTarget(packageName)
            debugLog { "lock visible $packageName token=${sessionToken.take(8)}" }
        }
    }

    fun onLockUiReady(packageName: String, sessionToken: String) {
        handler.post {
            if (!matchesActiveLock(packageName, sessionToken)) return@post
            lockReady = true
            lockSurfaceAlive = true
            shield.hide()
            handler.removeCallbacks(lockTimeout)
            debugLog { "lock ready $packageName token=${sessionToken.take(8)}" }
        }
    }

    fun onLockUiHidden(packageName: String, sessionToken: String) {
        handler.post {
            if (!matchesActiveLock(packageName, sessionToken)) return@post
            lockVisible = false
            debugLog { "lock hidden $packageName token=${sessionToken.take(8)}" }
        }
    }

    fun onLockPromptShown(packageName: String, sessionToken: String) {
        handler.post {
            if (!matchesActiveLock(packageName, sessionToken)) return@post
            lockPromptActive = true
            debugLog { "biometric prompt shown $packageName" }
        }
    }

    fun onLockPromptDismissed(packageName: String, sessionToken: String) {
        handler.post {
            if (!matchesActiveLock(packageName, sessionToken)) return@post
            lockPromptActive = false
            debugLog { "biometric prompt dismissed $packageName" }
        }
    }

    /**
     * The lock activity was destroyed without delivering a result (process
     * reclaimed, crash in the UI, Android killed the task). Fail closed: deny
     * access and return the protected app to the home screen. A destroyed
     * surface from an older token is ignored so it can never tear down a newer
     * lock for the same package.
     */
    fun onLockSurfaceGone(packageName: String, sessionToken: String) {
        handler.post {
            if (!matchesActiveLock(packageName, sessionToken)) return@post
            val wasDisplayed = lockSurfaceAlive || lockVisible
            debugLog {
                "lock surface gone $packageName token=${sessionToken.take(8)} " +
                    "displayed=$wasDisplayed ready=$lockReady"
            }
            handler.removeCallbacks(lockTimeout)
            shield.hide()
            clearLockState()
            scope.launch {
                container.logs.log(
                    LogType.SERVICE_ERROR,
                    if (wasDisplayed) {
                        "Lock screen closed without a result; access denied"
                    } else {
                        "Lock screen never became visible; access denied"
                    },
                    container.catalog.labelFor(packageName),
                )
            }
            sendProtectedAppHome()
        }
    }

    fun onLockResult(packageName: String, sessionToken: String, unlocked: Boolean) {
        handler.post {
            if (!matchesActiveLock(packageName, sessionToken)) return@post
            handler.removeCallbacks(lockTimeout)
            clearLockState()
            shield.hide()
            if (unlocked) {
                unlockedPackages += packageName
                lastForeground = packageName
            } else {
                // "Close app" and cancelled locks must never count as unlocked.
                unlockedPackages.remove(packageName)
            }
            debugLog { "lock result $packageName unlocked=$unlocked" }
        }
    }

    /** Only callbacks carrying the exact package + token of the live lock count. */
    private fun matchesActiveLock(packageName: String, sessionToken: String): Boolean =
        lockPackage == packageName && lockSessionToken == sessionToken

    private fun clearLockState() {
        lockPackage = null
        lockSessionToken = null
        lockReady = false
        lockVisible = false
        lockSurfaceAlive = false
        lockPromptActive = false
        lockStartedAt = 0L
        container.runtime.setTarget(null)
    }

    // ------------------------------------------------------------------ detection

    private fun handleForeground(packageName: String) {
        if (!config.protectionEnabled) return
        if (packageName == this.packageName) return

        val activeLock = lockPackage
        if (packageName == activeLock) {
            if (!lockVisible && !lockPromptActive) {
                // The owner returned to the protected app while an abandoned
                // lock activity was still in its own task: bring the lock back.
                // While the system biometric prompt is on screen the activity is
                // legitimately paused, so no relaunch is attempted.
                launchLockActivity(packageName, lockSessionToken ?: return)
            }
            return
        }

        if (activeLock != null && !lockVisible) {
            // Window-state events can arrive in a burst while the lock activity
            // is starting or while the system biometric prompt is transitioning
            // focus. Do not abandon a live lock during those windows.
            if (lockPromptActive) {
                debugLog { "Ignoring foreground $packageName while biometric prompt is active" }
                return
            }
            // The launch grace only covers the gap between startActivity and the
            // first lock window. Once the lock has actually had a surface, a
            // hidden lock followed by another foreground package is a genuine
            // app switch and must be abandoned immediately — otherwise a fast
            // switch could leave the next protected app unprotected.
            if (!lockSurfaceAlive && !lockReady &&
                SystemClock.elapsedRealtime() - lockStartedAt < LOCK_START_GRACE_MS
            ) {
                debugLog { "Ignoring foreground $packageName during lock launch grace" }
                return
            }
            // The owner moved to another app without finishing the previous lock.
            val abandonedToken = lockSessionToken
            LockActivity.finishActive(abandonedToken)
            shield.hide()
            clearLockState()
            debugLog { "abandoned lock for $activeLock" }
        }

        if (packageName != lastForeground) {
            lastForeground?.let { unlockedPackages.remove(it) }
            lastForeground = packageName
        }

        if (packageName in protectedPackages && packageName !in unlockedPackages) {
            showLock(packageName)
        }
    }

    private fun showLock(packageName: String) {
        if (lockPackage != null) return
        if (!config.hasMainPin) {
            reportProblem("Set a main PIN before protecting apps.", LogType.PERMISSION_MISSING, packageName)
            sendProtectedAppHome()
            return
        }
        if (!container.canDrawOverlays()) {
            reportProblem(
                "Display over other apps is required to show the lock screen.",
                LogType.PERMISSION_MISSING,
                packageName,
            )
            // Failing open would expose the protected app. Without a lock
            // surface the safest available behaviour is to leave the app.
            sendProtectedAppHome()
            return
        }
        if (lockoutActive()) {
            reportProblem(
                "App unlocking is temporarily locked after repeated wrong credentials.",
                LogType.LOCKOUT,
                packageName,
            )
            // Never leave the protected app open just because the unlock
            // budget is exhausted.
            sendProtectedAppHome()
            return
        }
        val token = java.util.UUID.randomUUID().toString()
        lockPackage = packageName
        lockSessionToken = token
        lockReady = false
        lockVisible = false
        lockSurfaceAlive = false
        lockPromptActive = false
        lockStartedAt = SystemClock.elapsedRealtime()
        container.runtime.setTarget(packageName)
        shield.show()
        handler.removeCallbacks(lockTimeout)
        launchLockActivity(packageName, token)
        handler.postDelayed(lockTimeout, LOCK_LAUNCH_TIMEOUT_MS)
        debugLog { "showLock $packageName token=${token.take(8)}" }
        scope.launch {
            container.logs.log(LogType.APP_LOCKED, "Lock screen shown", container.catalog.labelFor(packageName))
        }
    }

    private fun launchLockActivity(packageName: String, sessionToken: String) {
        val intent = Intent(this, LockActivity::class.java)
            .putExtra(LockActivity.EXTRA_TARGET_PACKAGE, packageName)
            .putExtra(LockActivity.EXTRA_SESSION_TOKEN, sessionToken)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        runCatching { startActivity(intent) }.onFailure { error ->
            shield.hide()
            if (matchesActiveLock(packageName, sessionToken)) clearLockState()
            reportProblem(
                "Android blocked the lock screen: ${error.javaClass.simpleName}",
                LogType.SERVICE_ERROR,
                packageName,
            )
            sendProtectedAppHome()
        }
    }

    /** Last-resort safety fallback when the lock surface cannot be shown. */
    private fun sendProtectedAppHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            container.runtime.reportError("Android could not return to the home screen.")
        }
    }

    /**
     * Compares permission health with the last observation. A revoked overlay,
     * accessibility or usage permission becomes a visible degraded state and a
     * single journal warning instead of a silent failure.
     */
    private fun checkHealth() {
        val health = container.permissionHealth()
        val reasons = health.warnings
        val signature = reasons.joinToString("|")
        if (signature == lastDegradedSignature) return
        lastDegradedSignature = signature
        container.runtime.reportHealth(reasons)
        updateNotification()
        if (reasons.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastWarningAt >= WARNING_THROTTLE_MS) {
                lastWarningAt = now
                scope.launch {
                    container.logs.log(
                        LogType.DEGRADED_PROTECTION,
                        reasons.joinToString(" "),
                    )
                }
            }
        }
    }

    private fun pollOnce() {
        if (!config.protectionEnabled) return
        // The accessibility service is event-driven and faster; polling is the
        // documented fallback when it is not currently connected.
        if (PermissionChecks.isAccessibilityConnected()) return
        if (!container.hasUsageAccess()) return
        val power = getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) return
        scope.launch {
            val latest = withContext(Dispatchers.IO) { readLatestForegroundPackage() }
            if (latest != null) handleForeground(latest)
        }
    }

    private fun readLatestForegroundPackage(): String? {
        val manager = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = runCatching { manager.queryEvents(lastUsageEventTime, now) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val active = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (active) latest = event.packageName
        }
        lastUsageEventTime = now
        return latest
    }

    private fun lockoutActive(): Boolean {
        val now = System.currentTimeMillis()
        return config.mainLockoutUntil > now
    }

    // ------------------------------------------------------------------ service plumbing

    private fun stopProtection() {
        scope.launch {
            container.prefs.setProtectionEnabled(false)
            container.logs.log(LogType.PROTECTION_STOPPED, "Protection stopped by the owner")
            stopSelf()
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_protection),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows whether NEXO LOCK protection is active."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(text), type)
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val count = protectedPackages.size
        val base = when {
            count == 0 -> "No apps are protected yet."
            count == 1 -> "1 app is protected."
            else -> "$count apps are protected."
        }
        val text = if (lastDegradedSignature.isBlank()) {
            base
        } else {
            "Degraded protection. Open NEXO LOCK to fix permissions."
        }
        val current = buildNotification(text + " Tap to manage.")
        manager.notify(NOTIFICATION_ID, current)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ProtectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nexo)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Pause protection", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun reportProblem(message: String, type: LogType, packageName: String) {
        val now = System.currentTimeMillis()
        container.runtime.reportError(message)
        if (now - lastWarningAt < WARNING_THROTTLE_MS) return
        lastWarningAt = now
        scope.launch {
            container.logs.log(type, message, container.catalog.labelFor(packageName))
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(
            NOTIFICATION_ID,
            buildNotification(message),
        )
    }

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    companion object {
        const val ACTION_STOP = "com.vinay.nexolock.action.STOP_PROTECTION"
        private const val TAG = "NexoLock"
        private const val CHANNEL_ID = "nexo_protection"
        private const val NOTIFICATION_ID = 7301
        private const val POLL_INTERVAL_MS = 2_000L
        private const val HEALTH_INTERVAL_MS = 30_000L
        private const val INITIAL_LOOKBACK_MS = 15_000L

        /**
         * Time allowed for LockActivity to render its first frame. A cold start
         * on a loaded device can take several seconds; the previous 4 s budget
         * caused a false "cannot display" fallback. The lock is still failed
         * closed (protected app sent home) if no frame arrives within this
         * window.
         */
        private const val LOCK_LAUNCH_TIMEOUT_MS = 10_000L
        private const val LOCK_START_GRACE_MS = 2_000L
        private const val WARNING_THROTTLE_MS = 60_000L
    }
}
