# Release Engineering

This document describes how NEXO LOCK is versioned, built, verified, and signed.
It intentionally contains no passwords, keystore files, or private keys.

## Current delivery

| Item | Value |
| --- | --- |
| Version | `1.2.0` |
| versionCode | `14` |
| Package | `com.vinay.nexolock` |
| Signing | Android debug certificate (`CN=Android Debug`) |
| R8 optimization | Disabled |
| Distribution | GitHub Releases sideload |

The current APK is **not store-ready**. The debug key is public and shared by
debug builds; it must not be used for a production or store release.

## Versioning

1. Bump `versionCode` by one for every published APK.
2. Bump `versionName` using semantic versioning (`MAJOR.MINOR.PATCH`).
3. Update `CHANGELOG.md`.
4. Update the version table and download links in `README.md` and `INSTALL.md`.
5. Update the APK name and checksum references in release notes.
6. Keep the package ID `com.vinay.nexolock` unchanged. Changing it creates a new
   app and loses access to existing data.

Version fields live in `source/app/build.gradle.kts`.

## Pre-release checks

Run from `source/`:

```bash
./gradlew :app:testDebugUnitTest --no-daemon --console=plain
./gradlew :app:lintDebug --no-daemon --console=plain
./gradlew :app:assembleRelease --no-daemon --console=plain
```

On Windows use `gradlew.bat` and set `JAVA_HOME` first.

Do not publish when:

- tests fail or are skipped without explanation;
- lint has unresolved errors;
- the APK cannot be installed over the previous same-key build;
- a new permission, exported component, or signing identity change is not
  documented and reviewed;
- the release notes claim device coverage that was not executed.

## Build the release APK

```bash
./gradlew :app:assembleRelease --no-daemon --console=plain
```

Output:

```text
source/app/build/outputs/apk/release/app-release.apk
```

Stage it with a stable, versioned name:

```powershell
Copy-Item .\app\build\outputs\apk\release\app-release.apk `
  .\NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk
```

The staged APK must not be committed. `builds/` is Git-ignored; release assets
belong in GitHub Releases.

## Checksums

Generate and publish a SHA-256 file next to the APK:

```powershell
Get-FileHash .\NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk -Algorithm SHA256 |
  ForEach-Object { "$($_.Hash)  NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk" } |
  Set-Content .\NEXO_LOCK_V1.2.0_BUILD14_SHA256.txt -Encoding ascii
```

```bash
sha256sum NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk > NEXO_LOCK_V1.2.0_BUILD14_SHA256.txt
```

Recompute the hash **after** copying or uploading. A checksum generated before a
copy does not prove the delivered bytes.

## Verify the APK

```powershell
$aapt = "$env:ANDROID_HOME\build-tools\36.0.0\aapt.exe"
& $aapt dump badging .\NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk |
  Select-String "package:|sdkVersion|targetSdkVersion"

$apksigner = "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat"
& $apksigner verify --print-certs .\NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk
```

Expected package/version:

```text
package: name='com.vinay.nexolock' versionCode='14' versionName='1.2.0'
sdkVersion:'26'
targetSdkVersion:'37'
```

Verify the downloaded asset, not only the local build:

```powershell
curl.exe -L --fail -o downloaded.apk `
  https://github.com/vinay09930/NEXO-LOCK/releases/latest/download/NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk
Get-FileHash .\downloaded.apk -Algorithm SHA256
```

## Production signing

A production key is owner-held and must never be committed, uploaded, or pasted
into an issue. Losing it prevents updates to an installed app; leaking it lets
someone else sign a malicious update.

### 1. Create the keystore outside the repository

```powershell
keytool -genkeypair -v `
  -keystore C:\secure\nexo-release.jks `
  -alias nexo `
  -keyalg RSA -keysize 4096 -validity 10000
```

Store at least two offline backups in separate secure locations.

### 2. Create a local, Git-ignored properties file

`source/keystore.properties`:

```properties
storeFile=C:\\secure\\nexo-release.jks
storePassword=REPLACE_LOCALLY
keyAlias=nexo
keyPassword=REPLACE_LOCALLY
```

Add it to `.gitignore` and verify it is not tracked:

```powershell
git check-ignore -v source/keystore.properties
git ls-files source/keystore.properties
```

The second command must print nothing.

### 3. Wire it into `source/app/build.gradle.kts`

Load the file only when it exists, so normal debug builds still work:

```kotlin
import java.util.Properties

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = if (keystoreProperties.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}
```

Do not commit the keystore, the properties file, or the passwords. Do not print
them in CI logs.

### 4. Verify the signer

```powershell
& $apksigner verify --print-certs .\NEXO_LOCK_V1.2.0_BUILD14_RELEASE.apk
```

Record the SHA-256 certificate digest in the release notes. A signing change
means existing installations cannot be updated in place; users must uninstall,
which deletes all app data.

## Publishing a release

Only the repository owner publishes releases. Do not create or replace a release
without explicit approval.

1. Build, test, lint, and verify the APK.
2. Confirm the version and package identity.
3. Compute the SHA-256 after staging.
4. Draft release notes that state:
   - version and versionCode;
   - APK name, size, and SHA-256;
   - signing certificate;
   - minimum Android version;
   - what was tested and what was not.
5. Attach the APK and checksum to a **new** release tag.
6. Never overwrite or delete an existing release asset.
7. Verify the public download URL and hash after publishing.

## Reproducibility

Android builds are not fully reproducible by default because of timestamps,
dependency resolution, and toolchain differences. To improve consistency:

- record the JDK, Gradle, AGP, Kotlin, and SDK versions;
- build from a clean checkout with `--no-daemon`;
- avoid changing `local.properties` between builds;
- compare SHA-256 values across two machines before claiming reproducibility;
- document any remaining nondeterminism instead of hiding it.

## Rollback

Keep the previous verified APK and checksum available. Do not delete the previous
release. If a new build is broken, publish a new version with a higher
`versionCode`; never replace the old asset silently.
