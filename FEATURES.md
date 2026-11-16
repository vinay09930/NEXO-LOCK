# Features

This guide describes the behaviour implemented in the V1.2.0 source. Runtime
behaviour was smoke-tested on an API 37 emulator, not on a physical device; see
[TEST_REPORT.md](TEST_REPORT.md) and
[KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).

## 1. First-run setup

Setup has exactly two mandatory steps. Optional capabilities are configured later
and are never forced.

1. **Volume pattern** — press the volume keys in a sequence, then confirm it.
   The pattern must contain 4–8 UP/DOWN presses.
2. **Master PIN** — choose 4–8 digits, then confirm.

After setup the app records a `SETUP_COMPLETED` event and opens the dashboard.
Recovery credentials, biometrics, vaults, protected apps, and monitoring
permissions can all be configured afterwards from Settings.

The setup flow is an explicit state machine: entry and confirmation are separate
states, so a confirmation cannot exist without a first value.

## 2. Navigation

Five top-level destinations share a quiet floating navigation pill:

| Tab | Screen |
| --- | --- |
| Home | Minimal protection overview |
| App Lock | Protected-app picker and protection controls |
| Personal Vault | Private encrypted notes and files |
| Intruder Vault | Encrypted failed-unlock captures |
| Settings | Category hub for security, vault, appearance, and permissions |

Setup, authentication, recovery, viewers, and subpages hide the pill so a
half-completed security flow cannot be abandoned with a stray tap. Settings
categories are subpages and therefore use back navigation instead of the pill.

## 3. Dashboard

Home is a compact overview, not a diagnostics page. It shows:

- a compact header;
- one primary protection card with the real monitor state and up to three
  accurate stats;
- at most one prioritized actionable warning;
- shortcuts to App Lock, Personal Vault, and Intruder Vault;
- optional compact recent activity when the journal has entries.

The protection card derives its state from the running service, overlay access,
detection availability, notification permission, battery optimization, biometric
availability, vault setup, and recent events. The single warning is chosen by a
fixed priority and links to the page that can resolve it. Detailed diagnostics
live in Settings, not on Home.

## 4. App Lock

### Selecting apps

The app picker lists launchable installed apps with icons and labels. Apps can be
searched, filtered, and protected individually or in bulk. The protected set is
stored encrypted with an Android Keystore key.

### Detection

Android has no public API that lets a third-party app intercept another app's
launch. NEXO LOCK therefore uses two best-effort channels:

1. **Accessibility service** — event-driven foreground-app detection (preferred).
2. **UsageStats polling** — documented fallback, polled every 2 seconds while the
   screen is on.

While Accessibility is connected, the service only re-checks permission health
every 30 seconds; it does not poll app usage.

### Covering the app

When a protected app comes to the foreground, NEXO LOCK:

1. Shows an immediate shield overlay if possible.
2. Launches `LockActivity` over the app.
3. Requires authentication before revealing the app.

A short exposure window is unavoidable and is documented in the UI. If the lock
surface cannot be drawn, the service reports the failure and sends the protected
app home rather than pretending the app is locked.

### Session behaviour

- A successfully unlocked app stays unlocked for that session.
- Turning the screen off clears the unlocked set, so the next visit requires
  authentication again.
- Disabling protection stops the monitor and removes the ongoing notification.

## 5. Lock-screen authentication

Each lock launch creates one authentication session with a fixed sequence:

```text
decoy (default 4 s)
  → biometric (if enabled, Android-managed)
  → volume pattern (if enabled, default 15 s)
  → master PIN (default 15 s)
```

Rules:

- The decoy stage is app-owned and always shown first when enabled.
- Tapping the decoy card or pressing a volume key continues the same decoy
  window; it does not restart it.
- The volume-pattern deadline is fixed when the stage opens. A key press never
  extends it.
- The PIN deadline is fixed when the stage opens. A digit or retry never extends
  it.
- Decoy, pattern, and PIN expiry are terminal and deny access.
- Biometric has no NEXO timer. Success advances; failure or cancellation denies.
- **Strict mode** is a legacy persisted flag and is not read by the V1.2.0 lock
  flow. The master PIN is always the final gate after any configured biometric or
  volume-pattern factor.
- **Recovery** always starts with the recovery PIN. If a recovery pattern is
  configured, it is an additional step.

### Lockout

Wrong credentials trigger progressive delays per credential family:

| Failures | Delay |
| --- | --- |
| 5 | 30 seconds |
| 8 | 5 minutes |
| 12 | 30 minutes |

Expired lockouts clear the deadline but keep the failure counter, so the next
wrong attempt escalates rather than restarting. Vault PIN failures have separate
budgets and cannot consume the app-unlock budget.

### Intruder capture trigger

When Intruder Vault capture is enabled, the lock screen requests a front-camera
capture after:

- a wrong master PIN;
- a wrong volume pattern;
- a biometric denial;
- a stage timeout.

Capture is throttled and serialised so a lock screen cannot start a camera storm.

## 6. Self-Protection

Self-Protection protects NEXO LOCK's own management interface while App
Protection is on. It is not a separate switch: it is derived from App Protection,
so it cannot drift out of sync, and turning App Protection off is the only way to
disable it.

When the owner changes a sensitive setting, NEXO LOCK replaces the management UI
with the self-authentication gate. The gate reuses the same decoy, biometric,
volume-pattern, master-PIN, and recovery flow as app unlocking. A successful gate
issues a short-lived grant bound to that specific change; the change is applied
only if the grant is still current.

Sensitive operations:

- turning App Protection off;
- changing protected applications;
- changing authentication methods;
- changing recovery settings;
- changing security settings;
- modifying protection rules.

Rules:

- With App Protection off, the gate never appears and settings behave as before.
- Turning App Protection off requires a fresh verification even inside an
  already-unlocked session.
- A denied, expired, superseded, or revoked gate cannot complete the pending
  change.
- Leaving the app for 30 seconds or more revokes the session; a denial revokes it
  immediately.
- The gate applies `FLAG_SECURE` while PIN or recovery entry is visible.
- The gate is fail-closed: if the protection requirement cannot be read, the
  change is refused rather than allowed.

## 7. Personal Vault

### Credential

- 4–8 digit PIN, separate from the main PIN, recovery PIN, and Intruder Vault PIN.
- Vault PINs are not compared with other credential families. Reusing the same
  digits does not link the vaults: each has its own verifier, failure budget,
  Keystore key, associated data, and directory. Choose distinct secrets if you do
  not want one observed PIN to open more than one surface.
- Unlocking opens a three-minute in-memory session. Leaving the app for 10
  seconds or more, process death, or timeout re-locks it. A system file picker or
  delete confirmation is treated as an intentional trip and keeps the vault
  unlocked until the result returns.

### Notes

- Create, rename, and delete encrypted notes.
- Notes are stored in the encrypted vault index.

### Files

- Import one or many files through the system document picker.
- Streaming import: files are encrypted chunk by chunk, never buffered whole in
  memory, and have no app-level size cap.
- Categories: All, Photos, Videos, Documents, Audio, Other, Notes.
- Grid and list layouts.
- Search by name.
- Thumbnails for images and videos.
- Sort newest, oldest, name, type, largest, or smallest; details include size,
  date, chunk count, and integrity digest.
- Rename and delete single or multiple files.
- Verify integrity: decrypts the file and compares the whole-file SHA-256.
- Export one or many files to a user-chosen SAF destination. The vault copy is
  never modified; a partial destination is deleted if export fails.
- Optional **delete originals after import** (off by default). Deletion requires
  a separate consent step.

### Storage reporting

The vault reports measured values, not estimates:

- encrypted bytes (index plus ciphertext);
- preview-cache bytes;
- logical plaintext bytes;
- attachment bytes;
- file count, entry count, failed-import count.

### Import lifecycle

An import writes to a temporary container, fsyncs it, and atomically renames it.
Only then does the index row change from `IMPORTING` to `COMPLETE`. If the process
dies mid-import, the row is surfaced as a failed/interrupted import with an
honest explanation. Legacy rows written by the V1.1.2 status defect are repaired
only when the ciphertext exists and a whole-file digest is present.

Duplicate imports are detected by size plus SHA-256 of the plaintext.

## 8. Intruder Vault

- Separate 4–8 digit PIN and separate Keystore alias/AAD/directory.
- Stores encrypted failed-unlock captures and incident metadata.
- Capture is **off by default** and requires:
  - an Intruder Vault PIN;
  - camera permission;
  - a working front camera;
  - the capture toggle enabled.
- Every outcome is recorded, including:
  - captured;
  - permission missing;
  - camera unavailable;
  - camera busy;
  - failed;
  - disabled.
- Incident records include reason, failure stage, target package, and timestamp.
- History can be cleared without deleting the vault credential.
- Deleting the Intruder Vault also turns capture off.

## 9. Security log

The log is encrypted at rest and grouped into:

- **Unlocks** — app locked/unlocked, factor verified, recovery used, vault
  unlocked, decoy choices.
- **Failures** — wrong PIN, wrong pattern, biometric rejection, lockout, expired
  sessions, capture failures.
- **System** — setup, PIN changes, protection start/stop, permission problems,
  service errors, vault lifecycle, import/export events.

Log entries carry a severity (info, success, warning, danger). The log is bounded
and can be cleared from Settings; clearing writes a `LOG_CLEARED` event.

## 10. Viewers

Inside the vault, files can be previewed without exporting:

- **Images** — decoded preview.
- **Video and audio** — Media3 ExoPlayer.
- **PDF** — Android `PdfRenderer`.
- **Text/JSON/XML** — text document viewer.

Viewer previews are cached only while the vault is unlocked and are deleted on
lock. `FLAG_SECURE` is applied to vault and viewer routes.

## 11. Settings

Settings is a category hub. The landing page lists nine categories and contains
no detailed switches, timeout chips, permission actions, or diagnostics. While
App Protection is on, changing a sensitive setting opens the Self-Protection gate
before the change is applied.

### Protection

- Protection monitor on/off.
- Privacy cover (block screenshots and blank the recent-apps thumbnail).
- Protected-app count and navigation to the app picker.

### Authentication & Recovery

- Volume-pattern and biometric factor status and setup/change.
- Main PIN status and change flow.
- Protection decoy on/off and decoy visibility (3, 4, or 5 seconds; default 4).
- Volume-pattern timeout (10, 15, 20, or 30 seconds; default 15).
- Master PIN timeout (10, 15, 20, or 30 seconds; default 15).
- Volume-pattern setup/change.
- Recovery PIN change (requires the master PIN).
- Recovery pattern change/reset.

### Vaults

- Personal Vault status and navigation to its workspace.
- Intruder Vault status and navigation to its workspace.
- Intruder capture toggle and camera readiness.

Vault creation, PIN change, deletion, layout, and the delete-originals
preference live inside each vault workspace, not the Settings pages.

### Notifications

- Android notification permission state.
- Open-settings action.
- Explanation of the ongoing protection notification and degraded warnings.

### Appearance

- Always-dark management-interface status.
- Compatibility note for the retained legacy theme value.

### Permissions

- Overlay, Accessibility detection, and Usage access fallback.
- Restricted-settings guidance and App info action.
- Notification permission.
- Battery optimization.

### Security & Monitoring

- Monitor, detection, and service status.
- Journal record count and security-journal access.
- Existing explanation of best-effort protection.

### Battery & Background Operation

- Battery-optimization state and open-settings action.
- Background-limit guidance.

### About NEXO LOCK

- Version, core features, security model, and known limitations.

## 12. Deliberate non-features

- No cloud sync, account, or telemetry.
- No internet permission.
- No screenshot or screen-content reading.
- No hardware-key capture by the accessibility service.
- No app-launch interception guarantee; detection is best-effort.
- No store-ready signing or R8-optimised release in this delivery.
