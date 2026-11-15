package com.vinay.nexolock.domain

/** Appearance preference. Persisted as the enum name. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromKey(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** Every credential NEXO LOCK can hold. Pattern credentials are volume gestures. */
enum class CredentialKind(
    val prefix: String,
    val recovery: Boolean,
    val pattern: Boolean,
) {
    MAIN_PIN("main_pin", recovery = false, pattern = false),
    RECOVERY_PIN("recovery_pin", recovery = true, pattern = false),
    MAIN_GESTURE("main_gesture", recovery = false, pattern = true),
    RECOVERY_PATTERN("recovery_pattern", recovery = true, pattern = true),
    PERSONAL_VAULT_PIN("vault_personal_pin", recovery = false, pattern = false),
    INTRUDER_VAULT_PIN("vault_intruder_pin", recovery = false, pattern = false),
}

/** How a protected app was unlocked. Stored in the audit log, never inferred. */
enum class UnlockMethod(val label: String) {
    PIN("Main PIN"),
    BIOMETRIC("Biometric"),
    GESTURE("Volume pattern"),
    RECOVERY_PIN("Recovery PIN"),
    RECOVERY_GESTURE("Recovery pattern"),
    BIOMETRIC_THEN_PIN("Biometric + PIN"),
    GESTURE_THEN_PIN("Volume pattern + PIN"),
    THREE_FACTOR("Biometric + volume pattern + PIN"),
    OPTIONAL_FACTOR("Single factor"),
}

/** Ordered factors used by the mandatory authentication engine. */
enum class AuthFactor(val label: String, val shortLabel: String) {
    BIOMETRIC("Biometric", "Fingerprint"),
    GESTURE("Volume pattern", "Volume pattern"),
    PIN("Main PIN", "PIN"),
}
