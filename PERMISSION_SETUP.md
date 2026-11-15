# Permission Setup

NEXO LOCK needs several Android permissions for app locking and optional camera
capture. This guide explains what each one does and how to grant it.

## Permission summary

The merged V1.2.0 manifest contains 13 permissions:

| Permission | Type | Required for |
| --- | --- | --- |
| `SYSTEM_ALERT_WINDOW` | Special | Drawing the lock cover and shield |
| `PACKAGE_USAGE_STATS` | Special | Fallback foreground detection |
| `FOREGROUND_SERVICE` | Normal | Protection monitor |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Normal | Protection monitor on Android 14+ |
| `POST_NOTIFICATIONS` | Runtime (Android 13+) | Ongoing protection notification |
| `RECEIVE_BOOT_COMPLETED` | Normal | Restart after reboot/update |
| `USE_BIOMETRIC` | Normal | Optional biometric unlock |
| `USE_FINGERPRINT` | Normal | Legacy biometric support |
| `VIBRATE` | Normal | Pattern feedback |
| `CAMERA` | Runtime | Optional Intruder Vault capture |
| `ACCESS_NETWORK_STATE` | Normal | Declared by an AndroidX/Media3 dependency; no internet access |
| `WAKE_LOCK` | Normal | Keep the service alive briefly |
| `com.vinay.nexolock.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Signature | AndroidX receiver protection |

## 1. Display over other apps (required for locking)

This is the only way to cover a protected app with the NEXO lock surface.

**Path (stock Android):**

1. Settings → Apps → Special app access → Display over other apps.
2. Find **NEXO LOCK**.
3. Enable **Allow display over other apps**.

Some vendors use: Settings → Apps → NEXO LOCK → **Display over other apps**.

Without this permission the service cannot draw the lock. It will report a hard
failure and send the protected app home instead of pretending it is covered.

## 2. Foreground-app detection (at least one channel required)

### Option A — Accessibility (preferred, event-driven)

1. Settings → Accessibility → Downloaded apps (or Installed services).
2. Open **NEXO LOCK app detection**.
3. Enable the service and accept the Android warning.

Android 13+ sideload note: if the service is greyed out or hidden, open
Settings → Apps → NEXO LOCK → three-dot menu → **Allow restricted settings**, then
return to Accessibility.

The service description states that it watches only the foreground app and does
not read screen content, text, passwords, or window contents.

### Option B — Usage access (fallback)

1. Settings → Apps → Special app access → Usage access.
2. Find **NEXO LOCK**.
3. Enable **Permit usage access**.

This channel polls every 2 seconds while the screen is on. It is slower than
Accessibility but works when Accessibility is unavailable.

At least one channel must be usable for protection. Enabling both is recommended:
Accessibility is fast, and Usage access covers reconnection gaps.

## 3. Notifications (Android 13+)

1. When prompted, allow notifications.
2. Or: Settings → Apps → NEXO LOCK → Notifications → allow.

The ongoing notification is required for the foreground service and shows
protection status. Without it, protection may still run, but the status is
hidden and Android may be more aggressive about stopping the service.

## 4. Camera (optional, Intruder Vault only)

Grant only if you want failed-unlock captures.

1. Enable the Intruder Vault capture toggle in **Settings → Vaults**.
2. When prompted, allow camera access.
3. Or: Settings → Apps → NEXO LOCK → Permissions → Camera → Allow.

Capture uses the front camera only and is skipped when:

- the Intruder Vault PIN does not exist;
- the toggle is off;
- the permission is missing;
- no front camera is available;
- the camera is busy.

Every skipped or failed attempt is recorded in the Intruder Vault with a status.

## 5. Battery optimisation (recommended)

Android may stop background protection to save power.

1. Settings → Apps → NEXO LOCK → Battery → **Unrestricted**.
2. Or: Settings → Battery → Battery optimisation → find NEXO LOCK → **Don't
   optimise**.

The dashboard shows an advisory while battery optimisation is on.

## 6. Biometric enrolment (optional)

Biometric unlock requires a biometric enrolled in Android, not just the
permission.

1. Settings → Security → Fingerprint / Face unlock.
2. Enrol a biometric.
3. In NEXO LOCK, enable **Biometric unlock** in **Settings → Authentication & Recovery**.

If no biometric is enrolled, sessions fall back to the volume pattern and master
PIN. The dashboard reports this honestly.

## 7. Boot and update restart

`RECEIVE_BOOT_COMPLETED` is a normal permission and is granted at install. The
`BootReceiver` restarts protection after:

- `BOOT_COMPLETED`;
- `MY_PACKAGE_REPLACED`;
- `USER_UNLOCKED`.

No user action is required. Some vendors still require the app to be allowed to
auto-start; check the vendor's battery/autostart settings if protection does not
return after a reboot.

## Verification checklist

After setup, confirm:

- [ ] Dashboard says protection is running.
- [ ] Display over other apps is granted.
- [ ] Accessibility is enabled and connected, or Usage access is granted.
- [ ] Notifications are allowed.
- [ ] Battery optimisation is off (recommended).
- [ ] A protected app shows the lock screen when opened.
- [ ] The correct PIN/pattern unlocks it.
- [ ] Turning the screen off and reopening the app requires authentication again.

## Common permission problems

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| "The lock screen could not be displayed" | Overlay permission missing | Grant Display over other apps |
| Protected app opens without a lock | No detection channel | Enable Accessibility or Usage access |
| Accessibility greyed out after sideload | Android 13+ restricted settings | App info → Allow restricted settings |
| Protection stops after a while | Battery optimisation | Set battery usage to Unrestricted |
| No protection after reboot | Vendor autostart restriction | Allow autostart for NEXO LOCK |
| Camera capture always "permission missing" | Camera permission denied | Grant Camera in app settings |
| No notification | Notification permission denied | Allow notifications |
