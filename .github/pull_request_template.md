## Summary

<!-- What problem does this change solve? Keep it focused. -->

## Type of change

- [ ] Bug fix
- [ ] Security fix
- [ ] Documentation
- [ ] Build or CI
- [ ] Refactor with no behaviour change
- [ ] New feature (link the approved issue)

## Related issue

<!-- e.g. Closes #12. Use "Security advisory" instead for private reports. -->

## What changed

<!-- List the important code paths, not every file. -->

## Security and data compatibility

- [ ] No change to the application ID `com.vinay.nexolock`.
- [ ] No change to the signing identity.
- [ ] No new permission, exported component, or network access (or it is explained below).
- [ ] No change to DataStore keys, Keystore aliases, vault formats, or the decoy freeze (or a migration is included and explained below).
- [ ] No secrets, keystores, signing passwords, or personal data are included.

Notes:

## Verification

<!-- Paste exact commands and results. State what was NOT tested. -->

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest` | |
| `:app:assembleDebug` | |
| `:app:lintDebug` | |
| Instrumented tests | |
| Manual device/emulator testing | |

## Screenshots

<!-- For UI changes only. Redact credentials and private data. -->

## Checklist

- [ ] Tests were added or updated for changed behaviour, or the gap is explained.
- [ ] Documentation was updated where behaviour, permissions, or limitations changed.
- [ ] The change does not rewrite unrelated working code.
- [ ] I have read [CONTRIBUTING.md](../CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md).
