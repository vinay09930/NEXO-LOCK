# Privacy and Data Handling

## Summary

NEXO LOCK is designed to keep data on the device. It has no account system, no
cloud sync, no analytics, no advertising SDK, and no `INTERNET` permission.

## What the app stores

| Data | Where | Protection |
| --- | --- | --- |
| Main PIN verifier | DataStore | PBKDF2-HMAC-SHA256, salted |
| Volume-pattern verifier | DataStore | PBKDF2-HMAC-SHA256, salted |
| Recovery PIN/pattern verifiers | DataStore | PBKDF2-HMAC-SHA256, salted |
| Vault PIN verifiers | DataStore | PBKDF2-HMAC-SHA256, salted |
| Protected-app package list | DataStore | AES-256-GCM, Android Keystore |
| Security log | App files | AES-256-GCM, Android Keystore |
| Vault notes and file index | `nexo_vault/<kind>/index.v1.enc` | AES-256-GCM, per-vault Keystore alias |
| Vault files | `nexo_vault/<kind>/<id>.nxf.enc` | AES-256-GCM, chunked `NXLF` v1 |
| Decrypted previews/thumbnails | App cache/memory | Deleted when the vault locks |
| Lockout counters and settings | DataStore | Plain configuration values |

Secrets themselves are never written to disk. Only salted verifiers are stored.

## What the app does not collect

- No name, email, phone number, or account.
- No contacts, calendar, SMS, call log, or location.
- No screen content, text, passwords, or window contents.
- No keystroke or hardware-key capture by the accessibility service.
- No analytics, crash reporting, advertising ID, or telemetry.
- No network requests; the manifest declares no internet permission.

## Permissions and their privacy impact

| Permission | Why | Privacy impact |
| --- | --- | --- |
| `SYSTEM_ALERT_WINDOW` | Draw the lock cover over a protected app | The app can draw over other apps; it uses this only for the lock surface and shield |
| `PACKAGE_USAGE_STATS` | Fallback foreground-app detection | The app can read usage events; it uses only the current foreground package |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep protection running | Shows an ongoing notification |
| `POST_NOTIFICATIONS` | Protection status notification | Android 13+ runtime permission |
| `RECEIVE_BOOT_COMPLETED` | Restart protection after reboot | No data access |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | Optional biometric unlock | Biometric matching is handled by Android |
| `VIBRATE` | Subtle pattern feedback | No data access |
| `CAMERA` | Optional Intruder Vault capture | Front camera only, only after failed unlocks, only when enabled |
| `ACCESS_NETWORK_STATE` | Declared by a dependency | No internet permission; no network requests |
| `WAKE_LOCK` | Keep the protection service alive briefly | No data access |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX internal receiver protection | Signature-level, automatic |

The accessibility service description shown to the user is explicit:

> NEXO LOCK watches only which app comes to the foreground so it can show a lock
> screen for the apps you selected. It does not read screen content, text,
> passwords or window contents, and it does not capture hardware keys.

## Backup

- `android:allowBackup="false"`.
- `android:fullBackupContent="false"`.
- Data-extraction rules exclude app data.
- Vault data does not leave the device through Android backup.

## Recent-apps privacy

- `FLAG_SECURE` is applied to vault, viewer, and entry routes.
- An optional **privacy cover** hides content in the recent-apps preview for the
  rest of the app.
- The lock surface uses its own secure window handling.

## Data deletion

- Deleting a vault removes its credential, index, attachments, and preview cache.
- Deleting a vault file removes its ciphertext and index row.
- Clearing the security log removes stored events and writes a `LOG_CLEARED`
  event.
- Uninstalling the app removes all app-private data. Because backup is disabled,
  this data is not recoverable.

## Third-party libraries

The app uses AndroidX/Compose, Media3, and Kotlin coroutines. These libraries run
locally. No third-party analytics or network SDK is included.

## Children and sensitive data

The app is a local privacy tool, not a regulated data controller. The owner is
responsible for the lawfulness of the data stored in the vaults.
