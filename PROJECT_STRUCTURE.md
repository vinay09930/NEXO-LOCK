# Project Structure

## Top-level layout

```text
D:\NEXO-LOCK\
├── .github\
│   ├── workflows\android-ci.yml      Build, unit tests, lint, artifact upload
│   ├── ISSUE_TEMPLATE\               Bug and feature issue forms
│   ├── dependabot.yml                Gradle and Actions update PRs
│   └── pull_request_template.md
├── README.md                       Landing page, download link, screenshots
├── CHANGELOG.md
├── CODE_OF_CONDUCT.md
├── CONTRIBUTING.md
├── DECOY_POLICY.md
├── FEATURES.md
├── INSTALL.md
├── KNOWN_LIMITATIONS.md
├── PERMISSION_SETUP.md
├── PRIVACY.md
├── PROJECT_STRUCTURE.md
├── RELEASING.md
├── ROADMAP.md
├── SECURITY.md
├── TEST_REPORT.md
├── TROUBLESHOOTING.md
├── V1.2.0_*.md                     Release-specific reports
├── reference\                      Logo source asset only
│   └── logo_source.jpg
├── screenshots\v1.2.0\             Runtime screenshots
└── source\                         Android Gradle project
    ├── app\
    │   ├── build.gradle.kts
    │   └── src\
    │       ├── main\               Production source and resources
    │       └── test\               JVM unit tests
    ├── gradle\libs.versions.toml   Version catalog
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── gradlew / gradlew.bat
    └── local.properties            Local SDK path (not portable)
```

The release APK and checksum are distributed through GitHub Releases and are not
stored in the repository. A local `builds/` folder is ignored by Git.

The workspace is a Git repository. The `main` branch tracks `origin/main`.

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
| `SelfProtection.kt` | Sensitive-operation policy and pure Self-Protection gate |
| `PackageCatalog.kt` | Installed-app labels/icons and launchable filtering |
| `VolumeKeyFilter.kt` | Debounces volume key events for pattern capture |
| `VolumePattern.kt` | Volume-pattern encoding and validation |

### `security`

| File | Responsibility |
| --- | --- |
| `SecurityAuthorization.kt` | Single authorization checkpoint: step-up requests, grant binding, revocation epoch, guarded writes |

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
| `selflock\SelfLockScreen.kt` | Self-Protection gate UI with `FLAG_SECURE` |
| `selflock\SelfLockViewModel.kt` | Self-Protection session, recovery binding, and pending-change gate |
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
| `res/xml/data_extraction_rules.xml` | Data-extraction exclusion rules (`allowBackup` is false) |
| `res/drawable*`, `res/mipmap*` | Launcher icon, notification icon, logo mark |

## Tests

`source/app/src/test/java/com/vinay/nexolock/` contains JVM tests covering pure
domain logic, Self-Protection policy and gate decisions, vault framing, import
policy, export, session timing, dashboard status, lockout wording, and the decoy
hash freeze. They do **not** exercise Android framework code or UI rendering. The
exact suite and test counts are recorded in [TEST_REPORT.md](TEST_REPORT.md) after
each verification run.

## Continuous integration

`.github/workflows/android-ci.yml` runs on pushes and pull requests to `main`:

1. JDK 21 and Android SDK platform 37 setup.
2. `:app:testDebugUnitTest`.
3. `:app:assembleDebug`.
4. `:app:lintDebug`.
5. Upload of test, lint, and debug-APK artifacts.

The workflow uses GitHub-hosted `ubuntu-latest` runners and does not sign or
publish releases. Dependabot checks Gradle and GitHub Actions dependencies weekly.

## Build configuration

- `source/app/build.gradle.kts` — application ID, SDK levels, version, signing.
- `source/gradle/libs.versions.toml` — dependency and plugin versions.
- `source/gradle.properties` — JVM and AndroidX settings.
- `source/local.properties` — local SDK path; machine-specific.

Release builds use the debug signing config and disable R8 optimization. This is
deliberate for the local delivery; see [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).
