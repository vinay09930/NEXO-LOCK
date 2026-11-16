# NEXO LOCK

[![Android CI](https://github.com/vinay09930/NEXO-LOCK/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/vinay09930/NEXO-LOCK/actions/workflows/android-ci.yml)
[![Latest release](https://img.shields.io/github/v/release/vinay09930/NEXO-LOCK?display_name=tag&sort=semver)](https://github.com/vinay09930/NEXO-LOCK/releases/latest)
![Minimum Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)

NEXO LOCK is a local-first Android privacy app that combines an app lock, two
independent encrypted vaults, and a tamper-evident security journal. It is built
with Kotlin, Jetpack Compose, Android Keystore, and DataStore.

Everything runs on the device. The app declares no `INTERNET` permission, has no
account system, and sends no telemetry.

> **Delivery status:** V1.2.0 build 14 is a sideloaded build signed with the
> Android debug certificate. Its self-protection logic is covered by JVM tests
> and the APK was built and verified locally; full device flows still need
> physical-device testing. It is not published on Google Play and is not
> production-signing ready. Read [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md)
> before relying on it.

## Download

[**Download NEXO LOCK v1.2.0 build 14 APK**](https://github.com/vinay09930/NEXO-LOCK/releases/latest/download/NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk)

The APK and checksum are GitHub Release assets. They are not stored in the source
tree, so no repository clone is required. The original code-13 build remains
available under the `v1.2.0` tag.

| Item | Value |
| --- | --- |
| Version | `1.2.0` (versionCode `14`) |
| Package | `com.vinay.nexolock` |
| Minimum Android | 8.0 (API 26) |
| Target / compile SDK | API 37 |
| APK size | 13,971,330 bytes |
| SHA-256 | `E31ACB83A8727F4E533E7532F97868A7D481FCB6BABE88C48150C6F2B40053EA` |
| Checksum file | [NEXO_LOCK_V1.2.0_BUILD14_SHA256.txt](https://github.com/vinay09930/NEXO-LOCK/releases/latest/download/NEXO_LOCK_V1.2.0_BUILD14_SHA256.txt) |
| Signing certificate | `fe1d89ccfac4fb031cc3e13d79d311f88421afe69b9bdc69fecff7aedda1978c` |

Verify the download before installing:

```powershell
Get-FileHash .\NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk -Algorithm SHA256
```

```bash
sha256sum NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk
```

Install and upgrade instructions: [INSTALL.md](INSTALL.md).

## Features

| Area | What it does |
| --- | --- |
| **App lock** | Covers selected apps with a full-screen lock surface. Detection uses Accessibility events, with UsageStats polling as a fallback. Best-effort by Android design. |
| **Self-Protection** | While App Protection is on, sensitive settings changes require a fresh self-authentication gate. There is no separate toggle to disable it. |
| **Personal Vault** | A separate PIN, Keystore key, AAD domain, and directory. Stores secure notes plus streamed, chunk-encrypted files with no size cap. |
| **Intruder Vault** | A second, independent vault for incident records. Optional front-camera capture after failed unlocks, throttled and written as an auditable record. |
| **Authentication** | Decoy (optional) → biometric (optional) → volume pattern (optional) → master PIN. Each stage has its own fixed deadline. |
| **Encryption** | AES-256-GCM with non-exportable Android Keystore keys. Credentials are stored only as salted PBKDF2-HMAC-SHA256 verifiers. |
| **Security journal** | Encrypted, append-only records chained with SHA-256 so deleted or reordered entries are detectable. Bounded to 2,000 records. |
| **Privacy** | No account, cloud sync, analytics, ads, crash reporting, or network permission. Android backup and device transfer are disabled. |
| **Export/import** | Streaming import from the system picker and verified export to a user-selected SAF destination. The vault copy is never modified by export. |

Full feature guide: [FEATURES.md](FEATURES.md).

## Screenshots

Captured from the V1.2.0 release build on an API 37 (Android 17) emulator.

| Home | Home with recent activity | Settings hub |
| --- | --- | --- |
| ![Home](screenshots/v1.2.0/01_home.png) | ![Home with recent activity](screenshots/v1.2.0/01b_home_recent_activity.png) | ![Settings hub](screenshots/v1.2.0/02_settings_hub.png) |

| Protection | Authentication & Recovery | About |
| --- | --- | --- |
| ![Protection](screenshots/v1.2.0/03_settings_protection.png) | ![Authentication](screenshots/v1.2.0/04_settings_authentication.png) | ![About](screenshots/v1.2.0/11_settings_about.png) |

See the full set and runtime notes in
[V1.2.0_SCREENSHOTS.md](V1.2.0_SCREENSHOTS.md).

## Compatibility

| Item | Value |
| --- | --- |
| Android | 8.0 (API 26) and newer |
| Target / compile SDK | 37 |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (universal APK) |
| Form factors | Phones and tablets; foldables and TV are not tested |
| Biometrics | Optional; requires an enrolled sensor supported by AndroidX Biometric |
| Camera | Optional front camera for Intruder Vault capture |
| Root | Not required; root/compromised devices are outside the threat model |
| Distribution | GitHub Releases sideload only; not on Google Play |

**Runtime evidence:** one headless API 37 x86_64 emulator. Physical devices,
other Android versions, and OEM skins were not tested. See
[TEST_REPORT.md](TEST_REPORT.md).

## Permissions

NEXO LOCK requests only permissions used by visible features:

| Permission | Purpose |
| --- | --- |
| `SYSTEM_ALERT_WINDOW` | Draw the lock cover over a protected app |
| `PACKAGE_USAGE_STATS` | Optional fallback foreground-app detection |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the protection monitor running |
| `POST_NOTIFICATIONS` | Show the ongoing protection status notification |
| `RECEIVE_BOOT_COMPLETED` | Restart protection after reboot or update |
| `USE_BIOMETRIC`, `USE_FINGERPRINT` | Optional biometric unlock |
| `VIBRATE` | Pattern-entry feedback |
| `CAMERA` | Optional Intruder Vault capture |

The accessibility service requests only window-state events. It cannot retrieve
window content or filter keys. Step-by-step setup:
[PERMISSION_SETUP.md](PERMISSION_SETUP.md).

## Security and privacy

- Secrets are never stored; only salted PBKDF2 verifiers are persisted.
- Vault indexes and files are AES-256-GCM encrypted with per-vault Keystore keys
  and authenticated associated data.
- Every vault file chunk is bound to its vault, file ID, and index; the footer
  carries a whole-file SHA-256 digest.
- A corrupted index or failed authentication is reported, never silently
  replaced.
- The app fails closed when a lock surface cannot be displayed.
- Sensitive settings changes are guarded by Self-Protection while App Protection
  is on; a denied or expired gate cannot complete a pending change.
- Android backup, device transfer, and cleartext traffic are disabled.

NEXO LOCK is not "unhackable." A rooted or compromised device, a malicious
accessibility service, weak PINs, and shoulder surfing remain outside its
guarantees. Read [SECURITY.md](SECURITY.md) and [PRIVACY.md](PRIVACY.md).

## Known limitations

- App-lock detection is best-effort; Android provides no launch-interception API.
- Accessibility, Usage access, overlay, or notification permission can be
  revoked at any time; protection then degrades visibly.
- OEM battery managers may stop the foreground monitor.
- There is no cloud recovery. Forgotten credentials mean unrecoverable data.
- The release APK is debug-signed and R8 optimization is disabled.
- Full unlock/lockout, decoy timing, vault I/O, camera capture, overlay coverage,
  and physical-device upgrades still need real-device verification.

The complete list is in [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md). Common
setup issues are covered in [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Quick start

1. Download `NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk` from the latest GitHub Release.
2. Open the app and complete the two mandatory setup steps: volume pattern and
   master PIN.
3. Grant **Display over other apps** and at least one detection channel
   (Accessibility or Usage access).
4. Choose protected apps and enable protection.
5. Optionally create the Personal Vault and Intruder Vault PINs.
6. Open **Settings** to reach every control from the category hub.

## Build from source

### Requirements

- JDK 17 or newer (JDK 21 recommended; Android Studio's bundled JBR works).
- Android SDK with platform 37 and matching build-tools.
- `source/local.properties` with `sdk.dir=...`, or `ANDROID_HOME` /
  `ANDROID_SDK_ROOT` set.
- About 4 GB free disk and 2 GB RAM for Gradle. The verification host used a
  1,408 MB Gradle heap on 8 GB RAM.

### Windows

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd source
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Release build:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease --no-daemon --console=plain
```

### Linux / macOS

```bash
export JAVA_HOME=/path/to/jdk-21
cd source
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Release build:

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease --no-daemon --console=plain
```

### Lint

```bash
./gradlew :app:lintDebug --no-daemon --console=plain
```

The debug APK is written to `source/app/build/outputs/apk/debug/app-debug.apk`.
The release APK is written to
`source/app/build/outputs/apk/release/app-release.apk`.

The release build deliberately uses the debug signing config and disables R8
optimization. It is installable for local testing but **not store-ready**. Secure
production signing is documented in [RELEASING.md](RELEASING.md).

## Verification status

Last verified on 2026-11-16:

- `:app:compileDebugKotlin` — **BUILD SUCCESSFUL**.
- 171 JVM unit tests across 21 suites — **0 failures, 0 errors, 0 skipped**.
- `:app:assembleRelease` — **BUILD SUCCESSFUL**.
- `:app:lintDebug` — **0 errors, 39 warnings** (dependency updates,
  deprecations, and resource-shape advisories; no blocking issues).
- Release APK package, version, and signer certificate verified with
  `aapt2`/`apksigner`.
- Release APK SHA-256 recorded for build 14.
- The three frozen decoy source files still match their pinned SHA-256 hashes.
- The code-13 build's first-run setup, Home, Settings hub, all nine category
  pages, About, back navigation, floating pill, and a vault shortcut were
  exercised on one API 37 emulator.
- Build 14 installed over code 13 with data preserved (`firstInstallTime`
  unchanged); its activity started and created a window with no app crash. The
  host-starved emulator's System UI then raised ANR dialogs, so a visual build-14
  UI check was not possible.

**Not fully verified:** the build-14 Self-Protection gate and full
unlock/lockout flows on a device, decoy timing, vault import/export, camera
capture, overlay/accessibility protection against a real protected app,
physical-device upgrade migration, and multiple Android versions/vendors.
Instrumented tests were not run. Details are reported honestly in
[TEST_REPORT.md](TEST_REPORT.md).

## Repository layout

```text
.
├── .github/            CI, Dependabot, issue/PR templates
├── source/             Android Gradle project
├── screenshots/        Runtime screenshots
├── reference/          Logo source asset
├── README.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── RELEASING.md
├── ROADMAP.md
├── CHANGELOG.md
├── FEATURES.md
├── INSTALL.md
├── SECURITY.md
├── PRIVACY.md
├── PERMISSION_SETUP.md
├── DECOY_POLICY.md
├── KNOWN_LIMITATIONS.md
├── TEST_REPORT.md
├── TROUBLESHOOTING.md
├── PROJECT_STRUCTURE.md
└── V1.2.0_*.md         Release-specific reports
```

The release APK and checksum are GitHub Release assets, not repository files. A
local `builds/` folder is ignored by Git.

## Documentation

| Document | Purpose |
| --- | --- |
| [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) | Repository layout and source packages |
| [FEATURES.md](FEATURES.md) | Complete user-facing feature guide |
| [SECURITY.md](SECURITY.md) | Cryptography, authentication, threat model |
| [PRIVACY.md](PRIVACY.md) | Data handling and network behaviour |
| [PERMISSION_SETUP.md](PERMISSION_SETUP.md) | Every permission and how to grant it |
| [INSTALL.md](INSTALL.md) | Install, upgrade, and uninstall |
| [RELEASING.md](RELEASING.md) | Versioning, checksums, and production signing |
| [DECOY_POLICY.md](DECOY_POLICY.md) | Frozen decoy rules and hashes |
| [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) | Honest gaps and non-goals |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | Common setup and protection issues |
| [TEST_REPORT.md](TEST_REPORT.md) | Current verification evidence |
| [CHANGELOG.md](CHANGELOG.md) | Version history |
| [ROADMAP.md](ROADMAP.md) | Planned work and priorities |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Build, test, and contribution workflow |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Community expectations |
| [V1.2.0_IMPLEMENTATION_REPORT.md](V1.2.0_IMPLEMENTATION_REPORT.md) | What changed in this release |
| [V1.2.0_MODIFIED_FILES.md](V1.2.0_MODIFIED_FILES.md) | Files that carry the V1.2.0 UI |
| [V1.2.0_TEST_REPORT.md](V1.2.0_TEST_REPORT.md) | Release-specific build and runtime evidence |
| [V1.2.0_RELEASE_REPORT.md](V1.2.0_RELEASE_REPORT.md) | Original code-13 release report |
| [V1.2.0_BUILD14_RELEASE_REPORT.md](V1.2.0_BUILD14_RELEASE_REPORT.md) | Build-14 artifact, signing, and verification report |
| [V1.2.0_SCREENSHOTS.md](V1.2.0_SCREENSHOTS.md) | Screenshot index and runtime notes |

## Roadmap

The next work is verification-first, not feature-first:

1. Confirm the first green CI run and add instrumented tests.
2. Verify the Self-Protection gate, unlock, lockout, decoy timing, vault
   import/export, and camera capture on physical devices across Android 8–17 and
   multiple OEMs.
3. Replace debug signing with an owner-held production keystore and publish a
   signed release with reproducible build notes.
4. Improve accessibility (TalkBack, large text, contrast) and localisation.

Details and non-goals: [ROADMAP.md](ROADMAP.md).

## Contributing

Bug reports, documentation fixes, and focused pull requests are welcome. Read
[CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
first. Security issues should follow [SECURITY.md](SECURITY.md), not a public
issue.

## License and ownership

No open-source license is declared in this repository. The source, APKs,
documentation, and assets remain owner-controlled; no rights are granted beyond
what GitHub's terms and applicable law provide. A license must be chosen by the
owner before external reuse is permitted.
