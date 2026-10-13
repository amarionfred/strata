# Strata 0.4.0-preview checks

Checked on 2026-10-13.

| Check | Result |
| --- | --- |
| Original launcher tests | 119 passed |
| OWL tests | 82 passed |
| Native app tests | 23 passed |
| Native APK build | Passed |
| Native lint | 0 errors, 14 warnings |
| APK signature | v2 verified; same key as previews 0.2 and 0.3 |

The original launcher compiled and passed its JVM tests. Its full native APK was
not built. The downloadable `strata.apk` is the native app in this directory.

Application ID: `dev.omalauncher.systempreview`. Version code: 4. Android 10+.

SHA256:

```text
0375adc43a20fdf893e8a916fd15542e182ba1950d05000af772462391fde9b0
```

No phone installation or UI testing was performed. Device checks still include
Termux permissions, the default-home flow, floating controls, notifications,
wallpapers, browser shortcuts, work profiles, and Android 15+ Private Space.
The recent-app row removal needs a portrait and landscape check on the original launcher.

[Machine-readable results](../../docs/validation-strata-0.4.0.json) · [Upstream audit](../../docs/UPSTREAM_AUDIT.md)
