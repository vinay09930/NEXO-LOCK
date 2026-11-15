# Known Limitations

This document lists what V1.2.0 does **not** verify or guarantee. It is part of
the delivery, not an afterthought.

## Verification gaps

| Gap | Status | Impact |
| --- | --- | --- |
| Runtime UI testing | **PARTIAL** | Home, Settings hub, all nine category pages, About, back navigation, the floating pill, one warning action, and one vault shortcut were exercised on an API 37 emulator |
| First-run setup on device | **PASS** | Volume pattern and six-digit master PIN were configured on the emulator |
| Full authentication on device | **NOT TESTED** | Unlock, biometric, recovery, lockout, and decoy timing were not run end to end |
| Vault import/export on device | **NOT TESTED** | SAF pickers, streaming I/O, and large files were not run |
| Intruder capture on device | **NOT TESTED** | Camera2 capture and permission flows were not run |
| Upgrade migration | **PARTIAL** | The emulator upgrade over the previous same-key release was exercised; a physical-device upgrade from an earlier build was not |
| Accessibility/overlay protection | **NOT TESTED** | Foreground detection and lock coverage against a real target app were not observed |
| Android Lint | **NOT RUN** | Lint was unavailable on the 8 GB build host |
| Instrumented tests | **NOT RUN** | No `connectedAndroidTest` execution |
| Multiple Android versions/vendors | **NOT TESTED** | One API 37 x86_64 emulator was used; no real device or OEM skin |
| Documentation screenshots | **INCLUDED** | See `screenshots/v1.2.0/` and [V1.2.0_SCREENSHOTS.md](V1.2.0_SCREENSHOTS.md) |

## Release engineering limitations

- **Debug-key signing.** The APK is signed with the standard Android debug
  certificate (`CN=Android Debug`). This is not a production keystore and must not
  be used for store distribution.
- **R8 optimization disabled.** The release build intentionally disables R8
  optimization because the V1.1.2 optimized release crashed on launch and no
  device was available to capture the stack trace. The release APK is therefore
  larger and not optimized.
- **No reproducible-build guarantee.** Build timestamps and toolchain versions can
  change the APK bytes.
- **No CI pipeline.** Verification is manual and local.
- **No Git history.** The workspace is not a Git repository; there is no commit
  provenance for the source tree.

## Product limitations

### App lock is best-effort

Android has no public API that lets a third-party app intercept another app's
launch. NEXO LOCK relies on Accessibility events or UsageStats polling, so a
short exposure window is unavoidable. A malicious or unusually fast app switch
may appear briefly before the lock surface draws.

### Detection channels can be disabled

The user or the system can revoke Accessibility, Usage access, overlay, or
notification permission at any time. The dashboard reports the degraded state,
but protection cannot continue without the required access.

### Vendor background restrictions

Some manufacturers stop foreground services or restrict autostart even when
Android APIs allow them. Battery optimisation and vendor autostart settings may
need manual changes.

### Credential recovery

There is no cloud recovery, escrow, or reset backdoor. Forgetting the master PIN,
recovery credentials, and vault PIN means the encrypted data cannot be recovered.

### Weak secrets remain weak

PBKDF2 with 600,000 iterations slows guessing, but a 4-digit PIN has only 10,000
combinations. An attacker with the encrypted data and sufficient compute can
still brute-force a weak PIN. Use a longer PIN where possible.

### Vault export is plaintext by design

Export restores a decrypted copy to the destination the user chooses. NEXO LOCK
cannot protect that copy after export.

### Thumbnail limits

Thumbnails are generated only for supported image/video sources below the
internal 96 MiB source limit. Larger files can still be imported, opened, and
verified, but may not show a thumbnail.

### Viewers depend on platform codecs

Video/audio playback depends on Media3 and device codecs. PDF rendering depends on
Android `PdfRenderer`. Unsupported formats may fall back to an error state rather
than a preview.

### Camera capture is optional and limited

- Front camera only.
- Requires camera permission and a working sensor.
- Throttled to prevent camera storms.
- A failed capture is recorded as an incident, not hidden.
- Capture does not guarantee identifying a person; it is a deterrent/audit aid.

### Decoy is not plausible deniability

The decoy is a fixed fake lock surface. It is not a hidden second vault and does
not provide cryptographic deniability. Its source is frozen and hash-pinned.

### No anti-forensics

The app does not wipe free space, defeat root, resist a compromised OS, or protect
against a malicious accessibility service.

### Localisation

The UI is English-only in this delivery.

### Accessibility of the app itself

The Signature UI uses custom components. Basic content descriptions and roles are
present, but a full accessibility audit (TalkBack, large text, contrast) was not
performed.

## What was verified

See [TEST_REPORT.md](TEST_REPORT.md) and
[V1.2.0_TEST_REPORT.md](V1.2.0_TEST_REPORT.md) for the exact commands and results.
Use [V1.2.0_SCREENSHOTS.md](V1.2.0_SCREENSHOTS.md) for the runtime evidence.
