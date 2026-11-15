# Changelog

This changelog is reconstructed from the current source and its comments. Older
release notes were not present in the workspace when this documentation set was
recreated.

## 1.2.0 - versionCode 13

**Release consolidation.**

- Rebuilt Settings as a category hub with nine entries and no detailed controls
  on the landing page.
- Added dedicated category pages for Protection, Authentication & Recovery,
  Vaults, Notifications, Appearance, Permissions, Security & Monitoring, and
  Battery & Background Operation.
- Reduced Home to a compact header, one protection overview, one prioritized
  actionable warning, up to three accurate stats, three shortcuts, and optional
  recent activity.
- Kept the five-tab floating navigation pill with quieter elevation, surface,
  selection, and icon treatment.
- Bumped `versionName` to `1.2.0` and `versionCode` to `13`.
- Removed all previous-version APKs, debug APKs, checksum files, reference APK
  copies, and duplicate Gradle outputs; only one release APK is delivered.
- No changes to authentication, decoy, vault, storage, permission, monitoring,
  capture, import/export, logging, manifest, package, or signing behaviour.
- Runtime-smoke-tested on an API 37 emulator and captured in
  `screenshots/v1.2.0/`.

## 1.1.6 - versionCode 12

**UI/UX presentation release.**

- Rebuilt Settings as a category hub with nine entries and no detailed controls
  on the landing page:
  - Protection;
  - Authentication & Recovery;
  - Vaults;
  - Notifications;
  - Appearance;
  - Permissions;
  - Security & Monitoring;
  - Battery & Background Operation;
  - About NEXO LOCK.
- Added dedicated category pages that preserve the existing switches, timeout
  chips, vault/capture flows, permission cards, journal access, and recovery
  controls.
- Reduced Home to a compact header, one protection overview, one prioritized
  actionable warning, up to three accurate stats, three shortcuts, and optional
  recent activity.
- Kept the five-tab floating navigation pill with quieter elevation, surface,
  selection, and icon treatment.
- Bumped `versionName` to `1.1.6` and `versionCode` to `12`.
- No changes to authentication, decoy, vault, storage, permission, monitoring,
  capture, import/export, logging, manifest, package, or signing behaviour.
- Runtime-smoke-tested on an API 37 emulator.

## 1.1.5 — versionCode 11

**Management-UI release.**

- Added the NEXO Signature design system:
  - always-dark palette (`#0B0C0F` background, silver accents);
  - spacing, radius, and typography tokens;
  - component library (`SigCard`, `SigPill`, `SigPinPanel`, `SigPatternPad`,
    `SigBanner`, `SigDialog`, `SigBottomSheet`, and others);
  - custom `NexoIcons`.
- Rebuilt the dashboard, setup, app picker, credentials, logs, settings, recovery,
  Personal Vault, Intruder Vault, and viewer screens.
- Added the five-tab floating navigation pill: Home, App Lock, Personal Vault,
  Intruder Vault, Settings.
- Rebuilt the Personal Vault workspace:
  - category chips and search;
  - grid/list layouts;
  - thumbnails and file details;
  - multi-select actions;
  - integrity verification and export.
- Rebuilt the Intruder Vault workspace with incident summaries and capture
  status.
- Kept the decoy/lock surface on the legacy theme and byte-for-byte frozen.
- No changes to:
  - PBKDF2 credential verifiers;
  - Android Keystore aliases;
  - `NXLF` v1 vault container;
  - vault index schema;
  - authentication session timing;
  - protection service behaviour.

## 1.1.4

- Previous management UI based on the legacy theme in `Color.kt`, `Theme.kt`, and
  `Type.kt`.
- The frozen lock/decoy surface still uses this legacy theme in V1.1.5.

## 1.1.3

- Added structured Intruder Vault incident fields: `failureStage` and
  `targetPackage`. Older records decode with nulls and the UI derives what it can
  without inventing data.
- Decoy experience declared frozen; `DecoyIntegrityTest` pins the three decoy
  source files.
- Vault index schema unchanged, so V1.1.2 could still read a V1.1.3 index.

## 1.1.2

Source comments reference two V1.1.2 issues that shaped later releases:

- **Release crash:** the R8-optimized release build crashed on launch while the
  unminified debug build ran. R8 optimization was disabled for release rather
  than shipping another unverified artifact.
- **Import status defect:** a successful streaming import could remain
  `IMPORTING`/`FAILED` because the committed row was not flipped to `COMPLETE`.
  Later releases repair legacy rows only when the ciphertext exists and a
  whole-file SHA-256 is present.

## Earlier versions

Earlier release notes are not available in this workspace. The current source
contains the accumulated app lock, recovery, vault, capture, and logging features
described in [FEATURES.md](FEATURES.md).
