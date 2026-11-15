# Install and Upgrade Guide

## Choose an APK

Exactly one build is delivered:

| File | Size | Use |
| --- | --- | --- |
| `builds/NEXO_LOCK_V1.2.0_RELEASE.apk` | 13,905,878 bytes | Normal installation; R8 optimization disabled |

It is signed with the Android debug certificate:

```text
SHA-256: fe1d89ccfac4fb031cc3e13d79d311f88421afe69b9bdc69fecff7aedda1978c
DN: C=US, O=Android, CN=Android Debug
```

## Verify the download

Windows:

```powershell
Get-FileHash .\NEXO_LOCK_V1.2.0_RELEASE.apk -Algorithm SHA256
```

Expected:

```text
80BA3BEA9F3111E410C3B19B29C44370A2C1E0C1BC4294A607971DF5E6EB3D2D
```

Linux/macOS:

```bash
sha256sum NEXO_LOCK_V1.2.0_RELEASE.apk
```

## Requirements

- Android 8.0 (API 26) or newer.
- About 40 MB free for installation plus vault storage.
- Permission to install unknown apps if installing from a file manager.

## Install with adb

```powershell
adb install -r .\NEXO_LOCK_V1.2.0_RELEASE.apk
```

If the device already has a build signed with a different key:

```text
INSTALL_FAILED_UPDATE_INCOMPATIBLE
```

Uninstall the old build first. Uninstalling deletes all app data, including
vaults, because backup is disabled.

## Install from the device

1. Copy the APK to the device.
2. Open it with Files/Downloads.
3. Allow the file manager to install unknown apps when prompted.
4. Confirm the install.
5. Open **NEXO LOCK**.

## Upgrade from an earlier NEXO LOCK build

An in-place upgrade preserves data only when the signing certificate is the same.
The V1.2.0 APK uses the same debug certificate as the previous local builds.

1. Do not uninstall the old app.
2. Install the new APK over it (`adb install -r` or tap the APK).
3. Open the app and confirm setup state and vault contents.

The upgrade was exercised on the emulator over the previous same-key release.
Physical-device upgrade migration was **not** executed for this delivery. See
[KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).

## First run

1. Complete the volume-pattern setup and confirmation.
2. Create the master PIN and confirm it.
3. Open the dashboard.
4. Grant overlay and at least one detection channel.
5. Select protected apps and enable protection.
6. Optionally create the Personal Vault and Intruder Vault PINs.

Detailed permission steps: [PERMISSION_SETUP.md](PERMISSION_SETUP.md).

## Uninstall

```powershell
adb uninstall com.vinay.nexolock
```

Or: long-press the app icon → App info → Uninstall.

Uninstalling removes:

- credentials and settings;
- both vaults and all encrypted files;
- the security log;
- cached previews.

There is no cloud backup and `allowBackup` is false, so removed data cannot be
recovered.

## Troubleshooting installation

| Problem | Meaning | Fix |
| --- | --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Different signing key | Uninstall the old build (data loss) |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | Lower version code | Install V1.2.0 (code 13) or uninstall |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | Not enough space | Free storage |
| "App not installed" from file manager | Unknown-apps permission | Allow install from that file manager |
| Play Protect warning | Sideloaded debug-key build | Confirm the source and continue only if you trust it |
