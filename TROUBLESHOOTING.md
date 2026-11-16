# Troubleshooting

## Protection

### A protected app opens without the lock screen

Check, in order:

1. **Protection is enabled** in App Lock.
2. **Display over other apps** is granted.
3. **Accessibility** is enabled and connected, or **Usage access** is granted.
4. The app is actually selected in the protected list.
5. Battery optimisation is off.

The dashboard names the missing capability. If the lock surface cannot be drawn,
the service sends the app home and logs a service error.

### "The lock screen could not be displayed"

The overlay permission is missing or the lock activity could not produce a
surface. Grant **Display over other apps** and try again. Check the security log
for a `SERVICE_ERROR` entry.

### Accessibility is greyed out after sideloading

Android 13+ hides Accessibility for some sideloaded apps behind restricted
settings:

1. Settings → Apps → NEXO LOCK.
2. Three-dot menu → **Allow restricted settings**.
3. Return to Accessibility and enable **NEXO LOCK app detection**.

### Protection stops after a while

Android may stop background services to save power:

1. Set NEXO LOCK battery usage to **Unrestricted**.
2. On vendor ROMs, allow autostart/background activity.
3. Keep the ongoing notification enabled.

### Protection does not return after reboot

`RECEIVE_BOOT_COMPLETED` is granted at install, but some vendors block autostart.
Allow NEXO LOCK to auto-start in the vendor's security/battery settings.

## Authentication

### Wrong PIN or pattern

After 5 failures: 30-second delay. After 8: 5 minutes. After 12: 30 minutes.
The delay is local and cannot be skipped by restarting the app.

### Biometric is unavailable

- Enrol a biometric in Android settings.
- Enable biometric unlock in NEXO LOCK.
- If the device has no usable sensor, use the volume pattern and master PIN.

### Recovery does not work

Recovery always starts with the recovery PIN. The recovery pattern, if
configured, is a second step. If no recovery PIN exists, recovery is unavailable.

### Decoy closes before I can enter anything

The decoy window is fixed (default 4 seconds) and never restarts. Tap the card or
press a volume key to continue immediately. Increase the decoy timeout in
**Settings → Authentication & Recovery** if needed (3–5 seconds in the Settings UI).

### The app locks immediately after unlocking

- **Strict mode** (a persisted setting) requires the master PIN after
  biometric/pattern success. This is
  expected.
- Turning the screen off clears the unlocked set, so the next visit requires
  authentication again.
- Leaving the app for 10 seconds or more locks vault sessions; the three-minute
  in-memory timeout also applies.

## Vaults

### "The vault is locked"

Vault sessions last three minutes and are cleared after 10+ seconds in the
background, on process death, or on timeout.
Unlock the vault again.

### Import fails

The UI reports a stage:

| Stage | Meaning |
| --- | --- |
| `PICKER` | The file picker or vault session failed |
| `METADATA` | The provider did not return usable metadata |
| `OPEN_STREAM` | The provider stream could not be opened |
| `STORAGE` | Not enough space or a write failed |
| `ENCRYPT` | Encryption failed |
| `FINALIZE` | The container could not be finalized |
| `DUPLICATE` | The file already exists in the vault |
| `COMMIT` | The index update failed |
| `UNKNOWN` | An unexpected error; see the log |

Check free storage and try a different provider/file. Large files stream, so RAM
is not the limit; free disk space is.

### Import stayed "failed" after success (legacy rows)

V1.1.2 could leave a successful import marked `IMPORTING`/`FAILED`. Later versions
repair a row only when the ciphertext exists and a whole-file digest is present.
Genuinely interrupted imports remain failed with an explanation.

### "Vault integrity check failed"

A chunk tag or the footer SHA-256 did not match. The file is reported as
corrupted; NEXO LOCK does not silently replace it. Restore the file from another
copy if available.

### Export fails

- Choose a destination with enough free space.
- Some providers do not support the requested MIME type; choose a local folder.
- A partial destination is deleted on failure or cancellation.
- The vault copy is never modified by export.

### I forgot the vault PIN

There is no backdoor. Deleting the vault removes the credential and its data. If
you need the data, it cannot be recovered without the PIN.

### Camera capture is not ready

Capture requires all of:

- an Intruder Vault PIN;
- camera permission;
- a front camera;
- the capture toggle on.

The Intruder Vault shows the exact missing condition. Failed attempts are recorded
as incidents.

## Installation and updates

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

The installed build uses a different signing key. Uninstall it first; this deletes
all app data.

### `INSTALL_FAILED_VERSION_DOWNGRADE`

The installed version code is higher than 14. Install V1.2.0 build 14 or
uninstall the newer build.

### Play Protect warns about the APK

The release is signed with the Android debug key and sideloaded. Confirm you
trust the source. Do not install a build from an unknown party.

## Logs and diagnostics

Open **Settings → Security & Monitoring** (or the Logs screen) and filter:

- **Failures** for rejected credentials and lockouts;
- **System** for permission and service errors;
- **Unlocks** for session and vault events.

The log is encrypted at rest. Clearing it removes the events and writes a
`LOG_CLEARED` entry.
