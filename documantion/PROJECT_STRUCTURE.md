# Project Structure

## Top-level layout

```text
D:\NEXO-LOCK\
├── builds\                         Single release APK and checksum
│   ├── NEXO_LOCK_V1.2.0_RELEASE.apk
│   └── NEXO_LOCK_V1.2.0_SHA256.txt
├── documantion\                    This documentation set
├── reference\                      Logo source asset only
│   └── logo_source.jpg
├── screenshots\v1.2.0\             Runtime screenshots
└── source\                         Android Gradle project
    ├── app\
    │   ├── build.gradle.kts
    │   └── src\
    │       ├── main\               Production source and resources
    │       └── test\               149 JVM unit tests
    ├── gradle\libs.versions.toml   Version catalog
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── gradlew / gradlew.bat
    └── local.properties            Local SDK path (not portable)
```

The workspace is **not a Git repository**. Changes are not tracked by commits.

## Android source packages

All production code is under
`source/app/src/main/java/com/vinay/nexolock/`.

### Root

| File | Responsibility |
| --- | --- |
| `NexoApplication.kt` | Application entry point; owns the app container |
| `AppContainer.kt` | Manual dependency container: preferences, credentials, auth, vaults, logs, capture |
| `MainActivity.kt` | Single Compose activity; hosts `NexoRoot` |

### `core.crypto`

| File | Responsibility |
| --- | --- |
| `KeystoreCipher.kt` | AES-256-GCM with a non-exportable Android Keystore key; 12-byte IV, 128-bit tag |
| `PinHasher.kt` | Salted PBKDF2-HMAC-SHA256 verifier; constant-time comparison |

### `data`

| File | Responsibility |
| --- | --- |
| `NexoPreferences.kt` | DataStore configuration, credential verifiers, lockout state, schema migration |
| `CredentialRepository.kt` | Creates/verifies/clears credentials for each credential family |
| `ProtectedAppsRepository.kt` | Encrypted protected-package set |
| `SecurityLogRepository.kt` | Encrypted, bounded security log with typed events |

### `domain`

| File | Responsibility |
| --- | --- |
| `AuthPolicy.kt` | Pure PIN/pattern validation, recovery ordering, factor rules |
| `AuthenticationService.kt` | Credential verification, lockout, recovery sessions |
| `AuthenticationSession.kt` | Per-lock-launch factor sequence and monotonic deadlines |
| `CredentialKind.kt` | Main PIN, main gesture, recovery PIN/pattern, vault PINs |
| `LockoutPolicy.kt` | Progressive delay thresholds (5/8/12 failures) |
| `PackageCatalog.kt` | Installed-app labels/icons and launchable filtering |
| `VolumeKeyFilter.kt` | Debounces volume key events for pattern capture |
| `VolumePattern.kt` | Volume-pattern encoding and validation |

### `protection`

| File | Responsibility |
| --- | --- |
| `ProtectionService.kt` | Foreground monitor; accessibility events plus UsageStats fallback |
| `NexoAccessibilityService.kt` | Reports foreground package changes |
| `LockActivity.kt` | Hosts the lock surface over a protected app (decoy-frozen) |
| `ShieldOverlay.kt` | Immediate overlay cover before the lock activity draws |
| `ProtectionRuntime.kt` | Start/stop protection and service state |
| `BootReceiver.kt` | Restarts protection after boot, package replace, or user unlock |
| `PermissionChecks.kt` | Lightweight permission probes |
| `PermissionManager.kt` | Full permission health, warnings, and settings intents |
| `VolumeCaptureController.kt` | Captures volume presses while a pattern is entered |

### `ui`

| File | Responsibility |
| --- | --- |
| `NexoApp.kt` | Navigation graph, five-tab pill, SAF effects, FLAG_SECURE ownership |
| `NexoViewModel.kt` | Management state machine for setup, apps, vaults, logs, settings |
| `DashboardStatuses.kt` | Derived dashboard status models |
| `PermissionRequests.kt` | Activity-result permission requests |
| `VaultFilePicker.kt` | `OpenMultipleDocuments` import picker |
| `VaultExportPicker.kt` | `CreateDocument` / `OpenDocumentTree` export picker |
| `Formatters.kt` | Byte, date, and duration formatting |
| `lock\LockScreen.kt` | Frozen decoy and lock UI |
| `lock\LockViewModel.kt` | Frozen lock state machine and capture trigger |
| `lock\RecoveryFlow.kt` | Recovery credential UI |
| `screens\*` | Dashboard, setup, apps, credentials, vaults, logs, settings, recovery |
| `viewer\*` | Image, video, audio, PDF, and text viewers |
| `theme\SignatureTheme.kt` | V1.2.0 Signature tokens and always-dark theme |
| `components\SignatureComponents.kt` | Signature component library |
| `components\SignatureIcons.kt` | Custom `NexoIcons` vector icons |
| `components\NexoComponents.kt` | Legacy shared components still used by frozen lock UI |

### `vault`

| File | Responsibility |
| --- | --- |
| `VaultManager.kt` | Credential/session orchestration, import, export, verify, delete |
| `VaultStore.kt` | Encrypted index and chunked attachment storage per vault |
| `VaultFileFormat.kt` | `NXLF` v1 streaming container framing |
| `VaultModels.kt` | Vault kinds, entries, files, snapshots, storage stats |
| `VaultFileTypes.kt` | MIME classification, previewability, name sanitisation |
| `VaultImportPolicy.kt` | Import completion, duplicate detection, legacy repair |
| `VaultImportErrors.kt` | Safe user-facing import error messages |
| `VaultImportDiagnostics.kt` | Bounded import diagnostics |
| `VaultExport.kt` | SAF export helpers |
| `VaultActivity.kt` | Vault session activity/timeout rules |
| `IntruderCaptureManager.kt` | Throttled front-camera capture on failed unlock |
| `IntruderIncidents.kt` | Incident reason/stage/package parsing |
| `CameraCapture.kt` | Camera2 front-camera JPEG capture |
| `OriginalFileDeletion.kt` | Opt-in original deletion after import |

## Resources

| Path | Contents |
| --- | --- |
| `res/values/strings.xml` | App name, accessibility label/description, notification strings |
| `res/values/themes.xml` | Launch and lock activity themes |
| `res/xml/nexo_accessibility_service.xml` | Accessibility service capabilities |
| `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Backup exclusion rules |
| `res/drawable*`, `res/mipmap*` | Launcher icon, notification icon, logo mark |

## Tests

`source/app/src/test/java/com/vinay/nexolock/` contains 18 suites and 149 JVM
tests. They cover pure domain logic, vault framing, import policy, export,
session timing, dashboard status, and the decoy hash freeze. They do **not**
exercise Android framework code or UI rendering.

## Build configuration

- `source/app/build.gradle.kts` — application ID, SDK levels, version, signing.
- `source/gradle/libs.versions.toml` — dependency and plugin versions.
- `source/gradle.properties` — JVM and AndroidX settings.
- `source/local.properties` — local SDK path; machine-specific.

Release builds use the debug signing config and disable R8 optimization. This is
deliberate for the local delivery; see [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).
