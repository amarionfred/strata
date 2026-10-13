# Strata 0.5.0-preview checks

Checked on 2026-10-13.

The Termux installer has seven passing host tests for checksum failures, failed
downloads, release selection, and preserving existing configuration. Its live
download and checksum steps were checked against the published 0.5.0 APK.
Android's installation and permission dialogs still need phone testing.

| Check | Result |
| --- | --- |
| Native app tests | 28 passed |
| Native runtime and archive tests | 11 passed |
| APK build | Passed |
| Lint | 0 errors, 15 warnings |
| APK signature | v2 verified; same key as previews 0.2–0.4 |
| Runtime modules | arm64, arm, x64 built; archive and payload checksums verified |

Root command tests check the actual host UID before allowing the payload, reject
invalid arguments, and verify that saving a service does not execute it. Invalid
shell syntax preserves the current script and its backup.

Runtime tests use native Linux user namespaces on the host. They cover actual
namespace UID 0, mounts, persistent files, installed packages and sudo, service
restart, and process cleanup. These are host results, not device root verification.

The original launcher and OWL were unchanged in this update. Their earlier
[119 and 82 passing tests](../../docs/validation-strata-0.4.0.json) remain recorded
with the previous release; they were not rerun for this update.

Application ID: `dev.omalauncher.systempreview`. Version code: 5. Android 10+.

APK SHA256:

```text
26e959b015d0af831dc6e45cc471a375465d27013836fda387c59d4c77686b8e
```

The Tecno KL4 is still stock and unrooted. No phone installation or UI testing
was performed. Root-manager grants/denials, module installation and startup,
SELinux compatibility, interactive terminals, foreground-service lifecycle, and
normal home/theme/notification behavior still need physical device testing.
The complete phone interface is unfinished.

[Machine-readable results](../../docs/validation-strata-0.5.0.json) · [Upstream audit](../../docs/UPSTREAM_AUDIT.md)
