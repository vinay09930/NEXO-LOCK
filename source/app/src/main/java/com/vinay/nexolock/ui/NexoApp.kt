package com.vinay.nexolock.ui

import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vinay.nexolock.ui.components.NexoFloatingNav
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.NexoNavItem
import com.vinay.nexolock.ui.screens.AboutScreen
import com.vinay.nexolock.ui.screens.AppSelectionScreen
import com.vinay.nexolock.ui.screens.BiometricSetupScreen
import com.vinay.nexolock.ui.screens.DashboardScreen
import com.vinay.nexolock.ui.screens.GestureSetupScreen
import com.vinay.nexolock.ui.screens.IntruderVaultScreen
import com.vinay.nexolock.ui.screens.LogsScreen
import com.vinay.nexolock.ui.screens.PersonalVaultScreen
import com.vinay.nexolock.ui.screens.PinSetupScreen
import com.vinay.nexolock.ui.screens.ProtectedAppsScreen
import com.vinay.nexolock.ui.screens.RecoveryAuthScreen
import com.vinay.nexolock.ui.screens.RecoveryPatternScreen
import com.vinay.nexolock.ui.screens.RecoverySettingsScreen
import com.vinay.nexolock.ui.screens.SettingsCategory
import com.vinay.nexolock.ui.screens.SettingsCategoryScreen
import com.vinay.nexolock.ui.screens.SettingsScreen
import com.vinay.nexolock.ui.screens.SetupScreen
import com.vinay.nexolock.ui.screens.SplashScreen
import com.vinay.nexolock.ui.theme.LocalNexoBottomPadding
import com.vinay.nexolock.ui.theme.NexoSignatureTheme
import com.vinay.nexolock.ui.theme.NexoSpace
import com.vinay.nexolock.ui.viewer.VaultViewerScreen
import com.vinay.nexolock.vault.VaultKind
import kotlinx.coroutines.delay

object Routes {
    const val SETUP = "setup"
    const val DASHBOARD = "dashboard"
    const val APPS = "apps"
    const val LOCKED = "locked"
    const val PIN = "pin"
    const val PATTERN = "pattern"
    const val GESTURE = "gesture"
    const val BIOMETRIC = "biometric"
    const val RECOVERY = "recovery"
    const val RECOVERY_AUTH = "recovery-auth"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
    const val SETTINGS_CATEGORY = "settings/{category}"
    const val ABOUT = "about"
    const val VAULT_PERSONAL = "vault-personal"
    const val VAULT_INTRUDER = "vault-intruder"
    const val VIEWER = "viewer/{kind}/{fileId}"
    const val VIEWER_ENTRY = "viewer-entry/{kind}/{entryId}"

    fun settingsCategory(category: SettingsCategory) = "settings/${category.route}"
    fun viewer(kind: VaultKind, fileId: String) = "viewer/${kind.id}/$fileId"
    fun viewerEntry(kind: VaultKind, entryId: String) = "viewer-entry/${kind.id}/$entryId"
}

/**
 * Destinations of the five-item floating navigation pill. These are the only
 * top-level screens; setup, authentication, viewers and subpages hide the pill
 * so a half-completed flow cannot be abandoned by a stray tap.
 */
private val TopLevelRoutes = setOf(
    Routes.DASHBOARD,
    Routes.LOCKED,
    Routes.VAULT_PERSONAL,
    Routes.VAULT_INTRUDER,
    Routes.SETTINGS,
)

private val FloatingNavItems = listOf(
    NexoNavItem(
        route = Routes.DASHBOARD,
        label = "Home",
        icon = Icons.Filled.Home,
    ),
    NexoNavItem(
        route = Routes.LOCKED,
        label = "App Lock",
        icon = NexoIcons.Shield,
        selectedIcon = NexoIcons.ShieldCheck,
    ),
    NexoNavItem(
        route = Routes.VAULT_PERSONAL,
        label = "Personal Vault",
        icon = NexoIcons.Vault,
    ),
    NexoNavItem(
        route = Routes.VAULT_INTRUDER,
        label = "Intruder Vault",
        icon = NexoIcons.Camera,
    ),
    NexoNavItem(
        route = Routes.SETTINGS,
        label = "Settings",
        icon = Icons.Filled.Settings,
    ),
)

@Composable
fun NexoRoot(vm: NexoViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    // The management UI is always dark by design. The legacy ThemeMode value
    // is still persisted because the frozen lock/decoy surface reads it.
    NexoSignatureTheme {
        var splashDone by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(850)
            splashDone = true
        }

        if (state.loading || !splashDone) {
            SplashScreen()
            return@NexoSignatureTheme
        }

        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val context = LocalContext.current
        // Frozen once the first config snapshot is available: recreating the
        // navigation graph mid-session would reset the back stack.
        val startDestination = remember {
            when {
                !state.config.setupComplete -> Routes.SETUP
                else -> Routes.DASHBOARD
            }
        }

        // FLAG_SECURE is owned by the route, not by individual screens, so the
        // dashboard, lists, details, previews and every sensitive dialog are
        // covered for the whole time they are on screen.
        val backStackEntry by nav.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route
        val secureRoute = route == Routes.VAULT_PERSONAL ||
            route == Routes.VAULT_INTRUDER ||
            route == Routes.VIEWER ||
            route == Routes.VIEWER_ENTRY
        SecureWindow(enabled = secureRoute || state.config.privacyCover)

        val navVisible = route in TopLevelRoutes
        val bottomReserve = if (navVisible) NexoSpace.navReserve else 0.dp

        LaunchedEffect(state.notice) {
            val notice = state.notice ?: return@LaunchedEffect
            snackbar.showSnackbar(notice.message)
            vm.consumeNotice()
        }

        // The ViewModel cannot launch an Activity result itself. These effects
        // consume each request exactly once and hand it to the fixed-code SAF
        // launchers; a failure is reported back so no export hangs forever.
        LaunchedEffect(state.vault.exportRequest) {
            val request = vm.consumeExportRequest() ?: return@LaunchedEffect
            val launched = when (request.mode) {
                ExportMode.SINGLE_DOCUMENT ->
                    VaultExportPicker.launchCreateDocument(context, request.suggestedName)

                ExportMode.MULTIPLE_TO_TREE ->
                    VaultExportPicker.launchOpenTree(context)
            }
            if (!launched) {
                vm.onExportPickerFailed("No system file picker is available on this device.")
            }
        }

        LaunchedEffect(state.vault.deleteConsent) {
            val request = vm.consumeDeleteConsent() ?: return@LaunchedEffect
            val launched = VaultExportPicker.launchDeleteConsent(context, request.intentSender)
            if (!launched) {
                vm.onDeleteConsentLaunchFailed(
                    "The system delete confirmation could not be opened. The original was kept.",
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalNexoBottomPadding provides bottomReserve) {
                NavHost(
                    navController = nav,
                    startDestination = startDestination,
                ) {
                    composable(Routes.SETUP) {
                        SetupScreen(
                            state = state,
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onFinished = {
                                nav.navigate(Routes.DASHBOARD) {
                                    popUpTo(Routes.SETUP) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(Routes.DASHBOARD) {
                        DashboardScreen(
                            state = state,
                            vm = vm,
                            onSettings = { nav.navigate(Routes.SETTINGS) },
                            onProtection = {
                                nav.navigate(
                                    Routes.settingsCategory(SettingsCategory.PROTECTION),
                                )
                            },
                            onAuthentication = {
                                nav.navigate(
                                    Routes.settingsCategory(SettingsCategory.AUTHENTICATION),
                                )
                            },
                            onPermissions = {
                                nav.navigate(
                                    Routes.settingsCategory(SettingsCategory.PERMISSIONS),
                                )
                            },
                            onGesture = { nav.navigate(Routes.GESTURE) },
                            onLogs = { nav.navigate(Routes.LOGS) },
                            onProtectedApps = { nav.navigate(Routes.LOCKED) },
                            onPersonalVault = { nav.navigate(Routes.VAULT_PERSONAL) },
                            onIntruderVault = { nav.navigate(Routes.VAULT_INTRUDER) },
                        )
                    }
                    composable(Routes.APPS) {
                        AppSelectionScreen(state, vm) { nav.popBackStack() }
                    }
                    composable(Routes.LOCKED) {
                        ProtectedAppsScreen(
                            state = state,
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onAddApps = { nav.navigate(Routes.APPS) },
                        )
                    }
                    composable(Routes.PIN) {
                        PinSetupScreen(state, vm) { nav.popBackStack() }
                    }
                    composable(Routes.PATTERN) {
                        RecoveryPatternScreen(state, vm) { nav.popBackStack() }
                    }
                    composable(Routes.GESTURE) {
                        GestureSetupScreen(state, vm) { nav.popBackStack() }
                    }
                    composable(Routes.BIOMETRIC) {
                        BiometricSetupScreen(state, vm) { nav.popBackStack() }
                    }
                    composable(Routes.RECOVERY) {
                        RecoverySettingsScreen(
                            state = state,
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onChangePattern = { nav.navigate(Routes.PATTERN) },
                            onTestRecovery = { nav.navigate(Routes.RECOVERY_AUTH) },
                        )
                    }
                    composable(Routes.RECOVERY_AUTH) {
                        RecoveryAuthScreen(state, vm) { nav.popBackStack() }
                    }
                    composable(Routes.LOGS) {
                        LogsScreen(state, vm) { nav.popBackStack() }
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onCategory = { category ->
                                nav.navigate(Routes.settingsCategory(category))
                            },
                            onAbout = { nav.navigate(Routes.ABOUT) },
                        )
                    }
                    composable(
                        route = Routes.SETTINGS_CATEGORY,
                        arguments = listOf(
                            navArgument("category") { type = NavType.StringType },
                        ),
                    ) { entry ->
                        when (
                            val category = SettingsCategory.fromRoute(
                                entry.arguments?.getString("category"),
                            )
                        ) {
                            null -> LaunchedEffect(Unit) { nav.popBackStack() }

                            SettingsCategory.ABOUT -> AboutScreen(
                                onBack = { nav.popBackStack() },
                                state = state,
                            )

                            else -> SettingsCategoryScreen(
                                state = state,
                                vm = vm,
                                category = category,
                                onBack = { nav.popBackStack() },
                                onChangePin = { nav.navigate(Routes.PIN) },
                                onChangeRecovery = { nav.navigate(Routes.RECOVERY) },
                                onChangePattern = { nav.navigate(Routes.PATTERN) },
                                onGesture = { nav.navigate(Routes.GESTURE) },
                                onBiometric = { nav.navigate(Routes.BIOMETRIC) },
                                onLogs = { nav.navigate(Routes.LOGS) },
                                onPersonalVault = { nav.navigate(Routes.VAULT_PERSONAL) },
                                onIntruderVault = { nav.navigate(Routes.VAULT_INTRUDER) },
                                onProtectedApps = { nav.navigate(Routes.LOCKED) },
                                onSelectApps = { nav.navigate(Routes.APPS) },
                            )
                        }
                    }
                    composable(Routes.VAULT_PERSONAL) {
                        PersonalVaultScreen(
                            state = state,
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onOpenFile = { kind, fileId -> nav.navigate(Routes.viewer(kind, fileId)) },
                            onOpenEntry = { kind, entryId -> nav.navigate(Routes.viewerEntry(kind, entryId)) },
                        )
                    }
                    composable(Routes.VAULT_INTRUDER) {
                        IntruderVaultScreen(
                            state = state,
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onOpenFile = { kind, fileId -> nav.navigate(Routes.viewer(kind, fileId)) },
                            onOpenEntry = { kind, entryId -> nav.navigate(Routes.viewerEntry(kind, entryId)) },
                        )
                    }
                    composable(
                        route = Routes.VIEWER,
                        arguments = listOf(
                            navArgument("kind") { type = NavType.StringType },
                            navArgument("fileId") { type = NavType.StringType },
                        ),
                    ) { entry ->
                        val kindId = entry.arguments?.getString("kind")
                        val fileId = entry.arguments?.getString("fileId")
                        val kind = VaultKind.entries.firstOrNull { it.id == kindId }
                        VaultViewerScreen(
                            state = state,
                            vm = vm,
                            kind = kind,
                            fileId = fileId,
                            entryId = null,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(
                        route = Routes.VIEWER_ENTRY,
                        arguments = listOf(
                            navArgument("kind") { type = NavType.StringType },
                            navArgument("entryId") { type = NavType.StringType },
                        ),
                    ) { entry ->
                        val kindId = entry.arguments?.getString("kind")
                        val entryId = entry.arguments?.getString("entryId")
                        VaultViewerScreen(
                            state = state,
                            vm = vm,
                            kind = VaultKind.entries.firstOrNull { it.id == kindId },
                            fileId = null,
                            entryId = entryId,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.ABOUT) {
                        AboutScreen(
                            onBack = { nav.popBackStack() },
                            state = state,
                        )
                    }
                }
            }

            if (navVisible) {
                NexoFloatingNav(
                    items = FloatingNavItems,
                    currentRoute = route,
                    onSelect = { item ->
                        if (route != item.route) {
                            nav.navigate(item.route) {
                                // Keep each top-level destination's scroll and
                                // selection state across tab switches, and never
                                // stack duplicate copies of a tab.
                                popUpTo(Routes.DASHBOARD) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                )
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (navVisible) 88.dp else 16.dp,
                    ),
            )
        }
    }
}

/**
 * Applies FLAG_SECURE to the hosting window while [enabled] is true. The flag
 * blocks ordinary screenshots and task-switcher thumbnails on Android, but it
 * cannot stop a rooted device, a camera pointed at the screen, or an OEM screen
 * recorder that ignores the secure flag. Those limits are documented, not hidden.
 */
@Composable
private fun SecureWindow(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val activity = context.findActivity()
        if (activity != null) {
            if (enabled) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        onDispose { }
    }
}
