# Contributing to NEXO LOCK

Thanks for taking the time to improve NEXO LOCK. This project is a local-first
Android privacy tool, so changes are judged first on correctness, security, and
data compatibility, then on presentation.

By participating you agree to follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Before you start

- Read [README.md](README.md), [SECURITY.md](SECURITY.md), and
  [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).
- Search existing issues and pull requests before opening a new one.
- For a security vulnerability, **do not open a public issue**. Follow the
  private reporting instructions in [SECURITY.md](SECURITY.md).
- For a large feature or refactor, open an issue first so the design can be
  discussed before code is written.

## Project layout

```text
source/                     Android Gradle project
source/app/src/main/        Production Kotlin, Compose UI, resources, manifest
source/app/src/test/        JVM unit tests
source/app/src/androidTest/ Instrumented tests (device/emulator)
screenshots/                Runtime screenshots
reference/                  Logo source asset
*.md                        Documentation at the repository root
```

Architecture and package responsibilities are described in
[PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md).

## Build and test

### Requirements

- JDK 17+ (JDK 21 recommended).
- Android SDK platform 37 and build-tools.
- `source/local.properties` with `sdk.dir=...`, or `ANDROID_HOME` set.

### Windows

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd source
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

### Linux / macOS

```bash
export JAVA_HOME=/path/to/jdk-21
cd source
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Useful checks:

```bash
./gradlew :app:testDebugUnitTest --no-daemon --console=plain
./gradlew :app:lintDebug --no-daemon --console=plain
./gradlew :app:assembleRelease --no-daemon --console=plain
```

Instrumented tests require a running emulator or device:

```bash
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
```

Report the exact commands and results in your pull request. Do not claim a
device, OEM, or Android version was tested when it was not.

## Coding guidelines

- Keep Kotlin readable and consistent with the surrounding code.
- Prefer small, pure, testable functions for policy and formatting logic.
- Do not add a dependency unless the platform or existing libraries cannot do the
  job cleanly.
- Do not add permissions or exported components without a security review.
- Never hardcode secrets, PINs, tokens, keystores, or signing passwords.
- Preserve the application ID `com.vinay.nexolock`, the DataStore schema, the
  Keystore aliases, the `NXLF` container version, and the decoy source freeze
  unless the change explicitly includes a migration and owner approval.
- Do not change the signing identity as part of a normal pull request.
- Keep user data compatible. A vault index or credential migration must be
  forward-tested against an existing vault.
- Update documentation when behaviour, permissions, or limitations change.
- Do not remove working functionality to make the code look cleaner.

## Tests

Add or update tests when you change:

- authentication sequencing, lockout thresholds, or credential validation;
- Self-Protection policy, gate decisions, step-up binding, or revocation;
- vault framing, import policy, export naming, or integrity checks;
- dashboard status derivation or incident parsing;
- any pure policy used by the UI.

JVM tests live in `source/app/src/test/java/com/vinay/nexolock/`. Device-only
behaviour belongs in `source/app/src/androidTest/` and must be described as
instrumented, not unit-tested.

## Pull request checklist

- [ ] The change has a clear technical reason and does not rewrite working
      modules unnecessarily.
- [ ] `:app:testDebugUnitTest` passes.
- [ ] `:app:assembleDebug` passes.
- [ ] `:app:lintDebug` was run, or the reason it could not run is stated.
- [ ] New behaviour has tests, or the gap is explained.
- [ ] Documentation and screenshots are updated where needed.
- [ ] No secrets, keystores, signing passwords, or personal data are included.
- [ ] The package name, signing identity, and user-data compatibility are
      preserved.
- [ ] The pull request states what was tested and what was not.

## Commit messages

Use a short imperative subject and a body that explains the why:

```text
Fix vault entry write race

Use a single mutex-guarded read/transform/write so a background capture
cannot overwrite a foreground delete.
```

## License

No open-source license is declared in this repository. By contributing, you
agree that the owner may use, relicense, and distribute your contribution as
part of the project. Do not contribute code you cannot license this way.
