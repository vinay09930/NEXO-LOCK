# Test Report

Date: 2026-11-16
Host: Windows, 8 GB RAM
Project: `D:\NEXO-LOCK\source`
Runtime: headless API 37 (Android 17) x86_64 emulator

## Summary

| Check | Result |
| --- | --- |
| Kotlin compilation (`:app:compileDebugKotlin`) | **PASS** - BUILD SUCCESSFUL |
| JVM unit tests | **PASS** - 171 tests, 21 suites, 0 failures, 0 errors, 0 skipped |
| Release APK assembly (`:app:assembleRelease`) | **PASS** - BUILD SUCCESSFUL |
| Android Lint (`:app:lintDebug`) | **PASS** - 0 errors, 39 warnings |
| APK signature | **PASS** - certificate `fe1d89cc...978c` |
| APK metadata | **PASS** - package `com.vinay.nexolock`, versionCode `14`, versionName `1.2.0` |
| Runtime first-run setup (code-13 build) | **PASS** - pattern and master PIN configured on emulator |
| Code-13 release install and launch | **PASS** - installed over the previous same-key release; data preserved |
| Code-13 runtime UI/navigation | **PASS** - Home, Settings hub, nine categories, About, back, pill, warning action, vault shortcut |
| Build-14 install / upgrade | **PASS** - installed over the code-13 release; versionCode `14`; data preserved |
| Build-14 launch | **PARTIAL** - activity started, window created, and no app crash; emulator System UI ANRs prevented a visual UI check |
| Build-14 Self-Protection gate | **PARTIAL** - JVM logic covered; full device gate flow not exercised |
| Instrumented tests | **NOT RUN** - no `connectedAndroidTest` execution |
| Full unlock / lockout / decoy timing | **NOT TESTED** |
| Vault import/export / camera capture | **NOT TESTED** |
| Protected-app overlay coverage | **NOT TESTED** |

## Commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd D:\NEXO-LOCK\source
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:lintDebug `
  --no-daemon --console=plain `
  "-Dorg.gradle.jvmargs=-Xmx1408m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8"
```

## Unit-test suites

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `AuthenticationSessionTest` | 32 | 0 | 0 | 0 |
| `AuthPolicyTest` | 10 | 0 | 0 | 0 |
| `DashboardStatusTest` | 12 | 0 | 0 | 0 |
| `DecoyIntegrityTest` | 1 | 0 | 0 | 0 |
| `IntruderIncidentsTest` | 7 | 0 | 0 | 0 |
| `LockoutPolicyTest` | 4 | 0 | 0 | 0 |
| `MonitoringStateTest` | 9 | 0 | 0 | 0 |
| `OriginalFileDeletionPolicyTest` | 5 | 0 | 0 | 0 |
| `PinHasherTest` | 7 | 0 | 0 | 0 |
| `SelfProtectionGateTest` | 9 | 0 | 0 | 0 |
| `SelfProtectionPolicyTest` | 7 | 0 | 0 | 0 |
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
| **Total** | **171** | **0** | **0** | **0** |

## What the tests cover

- Authentication session sequencing, deadlines, factor completion, recovery
  ordering, and denial paths.
- PIN/pattern validation and lockout thresholds, including delay wording.
- Self-Protection policy derivation and the pure gate decisions for every
  sensitive operation, including protection-off, no-grant, fresh-auth, expiry,
  and revocation states.
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
- the Self-Protection gate UI, step-up prompt, and pending-change lifecycle on a
  device;
- decoy timing and lock surface rendering;
- protected-app detection and overlay coverage;
- vault import/export through the system pickers;
- large-file streaming and storage exhaustion;
- camera capture;
- upgrade from an earlier installed build;
- TalkBack/large-text accessibility;
- vendor-specific background restrictions.

## Runtime smoke test

The code-13 RELEASE APK was installed with `adb install -r` on
`emulator-5554` (`NexoTest`, Android 17 / API 37, x86_64), over the previous
same-key release. First-run setup was completed with a four-press volume pattern
and a six-digit master PIN. The actual test values are not published; use your
own credentials when reproducing the setup.

Build 14 was then installed over the code-13 release with `adb install -r`.
`dumpsys package` confirmed versionCode `14`, the original `firstInstallTime` was
preserved, and the activity started with no `com.vinay.nexolock` crash in the
crash buffer. However, the 8 GB host could not sustain the API 37 emulator: the
system process, Pixel Launcher, and System UI repeatedly raised ANR dialogs, so
the build-14 management UI could not be visually confirmed in this run. That is
an emulator/host limitation, not evidence of an app defect, but it is also not a
UI pass.

The Self-Protection gate's state machine and authorization rules are covered by
JVM tests, but the full on-device gate flow (enable protection → open a guarded
setting → complete or deny the gate) was not driven end to end in this run.

The code-13 install succeeded, the app launched, and setup state plus journal
entries were preserved. The second Home screenshot shows the optional
recent-activity section.

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
File:    NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk (GitHub Release asset)
Size:    13,971,330 bytes
SHA-256: E31ACB83A8727F4E533E7532F97868A7D481FCB6BABE88C48150C6F2B40053EA
Cert:    fe1d89ccfac4fb031cc3e13d79d311f88421afe69b9bdc69fecff7aedda1978c
```

`aapt2 dump badging` confirms:

```text
package: name='com.vinay.nexolock' versionCode='14' versionName='1.2.0'
minSdkVersion:'26'
targetSdkVersion:'37'
```

## Honest conclusion

The source compiles, the pure logic is covered by 171 passing JVM tests, Android
Lint reports no errors, and the release APK is signed and hash-verified. The
code-13 UI was smoke-tested on one API 37 emulator. Build 14 was installed over
it with data preserved, and its activity started without an app crash, but the
host-starved emulator's System UI raised ANR dialogs before a visual build-14 UI
check. The release is **not** a full device acceptance test: the build-14
Self-Protection gate, full unlock, lockout, decoy timing, vault I/O, capture,
overlay protection, and physical-device upgrade migration still need real-device
verification.
