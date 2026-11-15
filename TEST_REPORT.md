# Test Report

Date: 2026-11-15
Host: Windows, 8 GB RAM
Project: `D:\NEXO-LOCK\source`
Runtime: headless API 37 (Android 17) x86_64 emulator

## Summary

| Check | Result |
| --- | --- |
| Kotlin compilation (`:app:compileDebugKotlin`) | **PASS** - BUILD SUCCESSFUL |
| JVM unit tests | **PASS** - 149 tests, 18 suites, 0 failures, 0 errors, 0 skipped |
| Release APK assembly (`:app:assembleRelease`) | **PASS** - BUILD SUCCESSFUL |
| APK signature | **PASS** - certificate `fe1d89cc...978c` |
| APK metadata | **PASS** - package `com.vinay.nexolock`, versionCode `13`, versionName `1.2.0` |
| Runtime first-run setup | **PASS** - pattern and master PIN configured on emulator |
| Release APK install and launch | **PASS** - installed over the previous same-key release; data preserved |
| Runtime UI/navigation | **PASS** - Home, Settings hub, nine categories, About, back, pill, warning action, vault shortcut |
| Android Lint | **NOT RUN** - unavailable on the build host |
| Instrumented tests | **NOT RUN** - no `connectedAndroidTest` execution |
| Full unlock / lockout / decoy timing | **NOT TESTED** |
| Vault import/export / camera capture | **NOT TESTED** |
| Protected-app overlay coverage | **NOT TESTED** |

## Commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd D:\NEXO-LOCK\source
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease `
  -x lintVitalRelease -x lintVitalAnalyzeRelease --no-daemon --console=plain `
  "-Dorg.gradle.jvmargs=-Xmx1408m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8"
```

## Unit-test suites

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `AuthenticationSessionTest` | 32 | 0 | 0 | 0 |
| `AuthPolicyTest` | 8 | 0 | 0 | 0 |
| `DashboardStatusTest` | 12 | 0 | 0 | 0 |
| `DecoyIntegrityTest` | 1 | 0 | 0 | 0 |
| `IntruderIncidentsTest` | 7 | 0 | 0 | 0 |
| `MonitoringStateTest` | 9 | 0 | 0 | 0 |
| `OriginalFileDeletionPolicyTest` | 5 | 0 | 0 | 0 |
| `PinHasherTest` | 7 | 0 | 0 | 0 |
| `VaultActivityTest` | 4 | 0 | 0 | 0 |
| `VaultExportTest` | 6 | 0 | 0 | 0 |
| `VaultFileFormatTest` | 13 | 0 | 0 | 0 |
| `VaultFileTypesTest` | 7 | 0 | 0 | 0 |
| `VaultImportDiagnosticsTest` | 6 | 0 | 0 | 0 |
| `VaultImportErrorsTest` | 3 | 0 | 0 | 0 |
| `VaultImportPolicyTest` | 10 | 0 | 0 | 0 |
| `VaultKindIndependenceTest` | 3 | 0 | 0 | 0 |
| `VolumeKeyFilterTest` | 11 | 0 | 0 | 0 |
| `VolumePatternTest` | 5 | 0 | 0 | 0 |
| **Total** | **149** | **0** | **0** | **0** |

## What the tests cover

- Authentication session sequencing, deadlines, factor completion, recovery
  ordering, and denial paths.
- PIN/pattern validation and lockout thresholds.
- Dashboard status derivation.
- Decoy source hash freeze.
- Intruder incident parsing.
- Monitoring state transitions.
- Original-file deletion policy.
- PBKDF2 verifier creation/verification and constant-time comparison behaviour.
- Vault session timeout.
- Vault export result aggregation.
- `NXLF` container framing, chunk AAD, footer digest, tamper detection.
- MIME classification, previewability, display-name sanitisation.
- Import diagnostics and safe error messages.
- Import completion, duplicate detection, and legacy repair.
- Vault kind independence (credential/AAD/alias separation).
- Volume key debouncing and pattern encoding.

## What the tests do not cover

The tests are JVM tests. They do not instantiate Android framework classes, draw
Compose UI, open SAF streams, access the Android Keystore, use Camera2, or run the
accessibility/overlay protection service.

Specifically **not tested**:

- full PIN/pattern/biometric/recovery unlock and lockout countdown;
- decoy timing and lock surface rendering;
- protected-app detection and overlay coverage;
- vault import/export through the system pickers;
- large-file streaming and storage exhaustion;
- camera capture;
- upgrade from an earlier installed build;
- TalkBack/large-text accessibility;
- vendor-specific background restrictions.

## Runtime smoke test

The RELEASE APK was installed with `adb install -r` on `emulator-5554`
(`NexoTest`, Android 17 / API 37, x86_64), over the previous same-key release.
First-run setup was completed with volume pattern `UP, UP, DOWN, DOWN` and master
PIN `135790`.

The install succeeded, the app launched, and setup state plus journal entries were
preserved. The second Home screenshot shows the optional recent-activity section.

Verified visually:

- Home shows the compact header, one protection overview, exactly one warning,
  one accurate stat, and three shortcuts.
- Settings shows nine categories and no detailed controls on the landing page.
- All nine category destinations open, including About.
- Back navigation returns to the hub.
- The floating pill switches between the five top-level destinations.
- The Home warning opens Permissions.
- The Personal Vault shortcut opens the secure vault route; its screenshot is
  black because `FLAG_SECURE` is applied.
- Permission labels match the emulator's real Android state.
- The decoy source files still match their pinned hashes.

Screenshots and detailed observations are in
[V1.2.0_TEST_REPORT.md](V1.2.0_TEST_REPORT.md) and
[V1.2.0_SCREENSHOTS.md](V1.2.0_SCREENSHOTS.md).

## APK verification

Release APK (the only delivered build):

```text
File:    NEXO_LOCK_V1.2.0_RELEASE.apk (GitHub Release asset)
Size:    13,905,878 bytes
SHA-256: 80BA3BEA9F3111E410C3B19B29C44370A2C1E0C1BC4294A607971DF5E6EB3D2D
Cert:    fe1d89ccfac4fb031cc3e13d79d311f88421afe69b9bdc69fecff7aedda1978c
```

`aapt2 dump badging` confirms:

```text
package: name='com.vinay.nexolock' versionCode='13' versionName='1.2.0'
minSdkVersion:'26'
targetSdkVersion:'37'
```

## Honest conclusion

The source compiles, the pure logic is covered by 149 passing JVM tests, the
release APK is signed and hash-verified, and the V1.2.0 UI was smoke-tested on one
API 37 emulator. The release is **not** a full device acceptance test: unlock,
lockout, decoy timing, vault I/O, capture, overlay protection, and physical-device
upgrade migration still need real-device verification. Android Lint was not run.
