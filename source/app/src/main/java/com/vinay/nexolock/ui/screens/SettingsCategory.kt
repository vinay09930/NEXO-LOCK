package com.vinay.nexolock.ui.screens

/**
 * Settings categories are a presentation-only map of the controls that already
 * exist. Each detail route receives one of these values; no preference key,
 * credential, service or permission flow is introduced here.
 */
enum class SettingsCategory(
    val route: String,
    val title: String,
    val summary: String,
) {
    PROTECTION(
        route = "protection",
        title = "Protection",
        summary = "Monitor, privacy cover and protected apps",
    ),
    AUTHENTICATION(
        route = "authentication",
        title = "Authentication & Recovery",
        summary = "Factors, decoy timing and recovery",
    ),
    VAULTS(
        route = "vaults",
        title = "Vaults",
        summary = "Personal, Intruder and capture",
    ),
    NOTIFICATIONS(
        route = "notifications",
        title = "Notifications",
        summary = "Protection status and warnings",
    ),
    APPEARANCE(
        route = "appearance",
        title = "Appearance",
        summary = "Always-dark management interface",
    ),
    PERMISSIONS(
        route = "permissions",
        title = "Permissions",
        summary = "System access required for protection",
    ),
    SECURITY(
        route = "security",
        title = "Security & Monitoring",
        summary = "Journal, monitoring and diagnostics",
    ),
    BATTERY(
        route = "battery",
        title = "Battery & Background Operation",
        summary = "Keep protection running",
    ),
    ABOUT(
        route = "about",
        title = "About NEXO LOCK",
        summary = "Version, security model and limits",
    ),
    ;

    companion object {
        fun fromRoute(route: String?): SettingsCategory? =
            entries.firstOrNull { it.route == route }
    }
}
