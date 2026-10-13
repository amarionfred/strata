# Preview validation — 2026-10-13

Built `dev.omalauncher.systempreview` version `0.2.0-preview` (version code 2) using the repository Gradle 9.8.0 wrapper, AGP 9.4.1, JDK 25 and SDK 36. Minimum Android API 29; target API 36.

Local artifact: `.local/companion/oma-system-preview-0.2.0.apk` relative to the repository root. Identical copy: `/home/park/Downloads/Oma-System-Preview-0.2.0.apk`.

- APK size: 2,910,769 bytes.
- SHA256: `43d9cf2542e4bd0d9508d3df9d0c2910294a74f167c9f3dcdbf68e54d27dbc41`.
- APK v2 signature verified with the Android SDK apksigner; signed with a local debug key.
- Build, all 16 host tests, and lint completed successfully. Lint reports 0 errors and 14 warnings.
- Remaining lint categories: AndroidGradlePluginVersion, DataExtractionRules, GradleDependency, NewerVersionAvailable, OldTargetApi, SdCardPath, SetTextI18n, UseTomlInstead.
- Resolved runtime dependencies: Kotlin stdlib 2.2.10, JetBrains annotations 13.0, Shizuku api/provider/aidl/shared 13.1.5 and AndroidX Annotation 1.3.0. Their notices are bundled. No native ABI libraries are packaged.
- Verified all 17 bundled config files against their recorded SHA256 values at Omarchy revision `8b4eae66da2938ba9559f103b18dbf85cdf28a70`. The fetch tool previously verified upstream Git blob hashes. Verified 22 packaged palettes byte-for-byte against the launcher's assets, shared Kotlin source copies against the originals, and shared theme classes in the final APK's DEX files.
- Eight boundary tests execute shell quoting/injection cases, configuration writes/backups/syntax rejection, process exit/error handling, output bounds and a timeout on the host.
- Eight Omarchy tests cover all bundled palettes and rendered configs; real Bash install and theme switching with backups; existing config preservation; interactive startup/override order; write failure and false-success prevention; hostile imported data; validation; shell syntax; and matching successful result acknowledgement.
- Host tests do not exercise Android IPC, permissions, UI or OEM lifecycle. The Tecno briefly appeared as an authorized ADB device, then disconnected before its package inventory could be read. The final normal APK installation attempt failed with `device not found`. The APK has not been installed, visually inspected or exercised on the Tecno. Termux and Shizuku integration remain unqualified on the phone.
- No phone settings, packages, bootloader, partitions or user files were modified during this preview implementation.

Follow [INSTALL.md](INSTALL.md), then the device checks in [README.md](README.md), before treating the preview as qualified on a particular phone.
