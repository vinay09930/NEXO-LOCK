# Decoy Policy

## What the decoy is

The decoy is the first screen shown when a protected app opens. It is an
app-owned, native Compose surface that looks like a generic system/app lock card.
It is **not** a second vault and it does not unlock anything by itself.

Its purposes:

- avoid revealing NEXO LOCK's real authentication UI to a casual observer;
- give the owner a short, fixed window to continue to the real factors;
- provide an honest "Close app" exit that denies access.

## Fixed sequence

```text
decoy (default 4 s) → biometric → volume pattern → master PIN
```

- The decoy is shown first when enabled (default: enabled).
- Tapping the card or pressing a volume key continues the **same** window.
- The decoy deadline never restarts.
- When the decoy window expires, access is denied.
- "Close app" cancels the session and returns to the home screen.
- The decoy timeout is stored clamped to 2–10 seconds. The V1.2.0 Settings UI
  offers 3, 4, or 5 seconds (default 4).

## Freeze policy

The decoy experience is frozen. Any change to its colours, text, layout, icons,
animation, navigation, or timing is a **decoy-policy change**, not a routine UI
change.

Three source files are pinned byte-for-byte by `DecoyIntegrityTest`:

| File | SHA-256 |
| --- | --- |
| `ui/lock/LockScreen.kt` | `AC595425F04490AC127B12A28FDA140EF116F4319DB2E5B0DB76CD64F2B374B5` |
| `ui/lock/LockViewModel.kt` | `0F0339C61A3373DF63368DE8BEB20E6AF8F24AE05004238DADDD03D38DB25194` |
| `protection/LockActivity.kt` | `C4E71A5CAE29AE03E7A9FB05CC54207D59D596F192C791A0A95F15A890B5B759` |

The test fails if any byte changes. This is intentional: it prevents a UI rebuild
or vault change from silently altering the decoy.

## Adjacent files

These files participate in the lock/decoy experience but are **not** hash-pinned
by the test. Treat changes here as security-sensitive and review them manually:

| File | SHA-256 (V1.2.0) |
| --- | --- |
| `ui/lock/RecoveryFlow.kt` | `C76C4426209D1FDCC9C0DFA94BFC1C8669FBB69B80578EF427693477B319E35F` |
| `ui/components/NexoComponents.kt` | `60AFD43AB92945114D284D95A3818B32EEAD1024034C5C2875DAD0B62673E125` |
| `ui/theme/Color.kt` | `C3EF66D9D9EC845A1B36973E0C0F0827A783485F8819A2A22BF8492D6C813E8D` |
| `ui/theme/Theme.kt` | `B82AABAADC3861904736788CC20B3A3CB3B06E1851DC99F308600AE186CF4B9E` |
| `ui/theme/Type.kt` | `D9B409BAE9E831F326FA098BC042C003FB0FF527D73FD16AE59E4618C76FB0F6` |

## Why the management UI can change while the decoy does not

V1.2.0 uses a separate `NexoSignatureTheme` for all management screens. The lock
activity still wraps the frozen decoy/lock surface in the legacy theme
(`Color.kt` / `Theme.kt` / `Type.kt`), so the decoy keeps its exact earlier
appearance.

## Rules for future changes

1. Do not edit the three frozen files for a normal feature or UI release.
2. If a decoy change is genuinely required:
   - treat it as a security review;
   - update the expected hashes in `DecoyIntegrityTest`;
   - document the visual and behavioural change;
   - re-run the full test suite;
   - test the lock flow on a device.
3. Never weaken the decoy deadline or allow it to restart on input.
4. Never make the decoy reveal whether a PIN, pattern, or biometric exists.

## Verification

Run:

```powershell
cd D:\NEXO-LOCK\source
.\gradlew.bat :app:testDebugUnitTest --tests "com.vinay.nexolock.DecoyIntegrityTest" --no-daemon
```

Or recompute the hashes directly:

```powershell
Get-FileHash .\app\src\main\java\com\vinay\nexolock\ui\lock\LockScreen.kt -Algorithm SHA256
Get-FileHash .\app\src\main\java\com\vinay\nexolock\ui\lock\LockViewModel.kt -Algorithm SHA256
Get-FileHash .\app\src\main\java\com\vinay\nexolock\protection\LockActivity.kt -Algorithm SHA256
```

The values must match the table above exactly.
