# Roadmap

NEXO LOCK is verification-first. The existing feature set is broad enough that the
next work should make it measurable and trustworthy before adding more surface.

Priority labels:

- **P0** — required before calling the build reliable.
- **P1** — required before a production release.
- **P2** — valuable product work after P0/P1.
- **P3** — optional or exploratory.

## P0 — Verification and CI

- [x] Run Android Lint locally and resolve real findings (0 errors, 39
      non-blocking warnings).
- [ ] Confirm the first green CI run, including lint.
- [ ] Run `connectedDebugAndroidTest` on an emulator in CI.
- [ ] Verify the Self-Protection gate end to end on a physical device: enable
      App Protection, open a guarded setting, complete and deny the gate, and
      confirm the pending change is applied only after a fresh grant.
- [ ] Execute full unlock, wrong-PIN lockout, wrong-pattern, biometric
      cancellation, recovery, and decoy-expiry flows on a physical device.
- [ ] Verify vault import, export, integrity verification, and deletion with
      large files and low-storage conditions.
- [ ] Verify Intruder Vault capture with granted, denied, and revoked camera
      permission.
- [ ] Verify overlay and accessibility protection against real protected apps.
- [ ] Test upgrade-in-place from the previous same-key build on a physical
      device.
- [ ] Test on Android 8, 10, 12, 14, 15, 16, and 17 where hardware is available.
- [ ] Test at least Samsung, Pixel, and one other OEM skin.
- [ ] Add a release checklist that records the exact device matrix and results.

## P1 — Release engineering

- [ ] Create an owner-held production keystore outside the repository.
- [ ] Move signing configuration to a local, Git-ignored properties file or
      environment variables; never commit secrets.
- [ ] Produce a production-signed APK and verify upgrade compatibility from the
      debug-signed build is documented (a signing change requires uninstall).
- [ ] Re-enable R8 with a tested ProGuard/R8 configuration, or document why
      optimization remains disabled.
- [ ] Make release builds reproducible enough to compare SHA-256 values across
      machines, or document the remaining nondeterminism.
- [ ] Add build provenance and checksum verification to the release process.
- [ ] Publish release notes that distinguish emulator, physical-device, and
      not-tested coverage.

## P2 — Product quality

- [ ] Add a guided permission-health flow that explains exactly which channel is
      degraded and why.
- [ ] Improve TalkBack labels, large-text layout, contrast, and focus order.
- [ ] Add English string externalisation and at least one additional locale.
- [ ] Add a vault storage report that separates index, ciphertext, previews, and
      free space.
- [ ] Add an explicit "verify all files" maintenance action with progress and
      cancellation.
- [ ] Improve incident timelines and capture metadata without storing new
      sensitive data.
- [ ] Evaluate a per-app unlock session policy (currently the unlocked set is
      cleared on screen-off and app switches).

## P3 — Optional

- [ ] Evaluate biometric-backed Keystore keys for vault unlocking, with a clear
      fallback and migration plan.
- [ ] Evaluate a second decoy profile without weakening the frozen decoy
      guarantees.
- [ ] Evaluate encrypted backup/export bundles with an explicit user-held
      passphrase (no cloud service).
- [ ] Evaluate foldable and tablet layouts.

## Non-goals

- Cloud accounts, sync, telemetry, analytics, or advertising.
- A claim of complete security or guaranteed app-launch interception.
- Anti-forensics, root hiding, or defeating a compromised operating system.
- Hidden vaults or plausible deniability beyond the documented decoy.
- Store distribution before production signing and device verification.

## How to propose a roadmap change

Open an issue describing the user problem, the security impact, and the
verification plan. Roadmap items are prioritised by reliability and data safety,
not by feature count.
