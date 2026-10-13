# Preview and upstream integration validation — 2026-10-13

Built `dev.omalauncher.systempreview` version `0.3.0-preview` (version code 3), minimum Android API 29, target/compile API 36. The original application compiles against API 37. Gradle 9.8.0, AGP 9.4.1 and JDK 25 run the builds; OWL uses JDK 17.

- Preview: `assembleDebug testDebugUnitTest lintDebug` succeeded; **23 tests**, zero failures/errors/skips. Lint: zero errors, 14 warnings.
- Original app: `:app:testDebugUnitTest` succeeded; **116 tests**, zero failures/errors/skips.
- OWL: `:owl:test :owl:owlJar` succeeded; **82 tests**, zero failures/errors/skips. Asset directories are declared test inputs so widget/script changes invalidate stale test results.
- Untouched upstream `92ac947`: 85 original-app and 81 OWL tests passed in an isolated worktree before integration.
- `git diff --check` passed. Workflow action hashes were verified against official repositories and their default permission scope was checked; hosted GitHub CI was not executed or published.

## APK

`/home/park/Downloads/Oma-System-Preview-0.3.0.apk` and repository `.local/companion/oma-system-preview-0.3.0.apk` are identical.

- Size: 2,986,631 bytes.
- SHA256: `269dec11f315f1526d00667f11c95eb9ce5540840fd9e28fd370f9a550aa8f28`.
- APK v2 signature verified. Certificate SHA256: `48e6c57d0d87daf1d0bbd8bbe0372c57eb584bd525fccd95f5092938dd10b04f`, matching the prior 0.2 preview for normal APK updates.
- Debug signing is for local preview distribution, not a production signing/release process.
- No native ABI libraries, PRoot or VM are packaged in this APK.

All 17 bundled config files match their recorded SHA256 at Omarchy revision `8b4eae66da2938ba9559f103b18dbf85cdf28a70`. All 22 palette assets match the original launcher. Shared Kotlin parser/template sources match the originals. The wallpaper Java implementation and outcome tests are compiled by both applications from the same source directory.

## Coverage and limits

The existing 16 native-preview tests cover real host shell execution, quoting, config writes/backups/syntax rejection, output/exit handling and the Omarchy configs. Four added preview policy tests cover profile types, private visibility, stable serials and accent search. Three shared wallpaper-result tests cover both-screen success, system-only fallback and complete failure; they do not invoke Android's wallpaper service.

Original additions/imports cover reordering (including every source/destination pair on a full 24-cell grid), touch ID allocation, private profile policy/lock gating, stable profile serials, accent search, locale-sensitive dates, terminal first-appearance behavior, stop/resume process gates, sandbox refusal, wallpaper results and Android 17 permission gating. Bundled widget tests compile the adapted weather file. None of these replace physical gesture, Binder, IME, VPN, notification, OEM lifecycle or service tests.

Remaining lint categories are AndroidGradlePluginVersion, DataExtractionRules, GradleDependency, NewerVersionAvailable, OldTargetApi, SdCardPath, SetTextI18n and UseTomlInstead. The specific QUERY_ALL_PACKAGES warning is suppressed only on the API-33-capped manifest permission required by WallpaperManager's older live-wallpaper inspection API; API 34+ uses a service query. See the Android source link in the audit.

The native preview's build/test/lint passed after its last source changes. ADB listed no attached device at the end of this audit. **This APK has not been installed or visually tested on the Tecno.** Termux/Shizuku, work profiles, wallpaper, shortcut pinning and notification actions remain physically unqualified. Private Space needs Android 15+, so an Android 14 Tecno cannot validate it. The original app's full APK was not built because its PRoot native libraries are absent.

No phone settings, packages, bootloader, partitions or user files were modified. Follow [INSTALL.md](INSTALL.md), the device checks in [README.md](README.md), and the [upstream audit](../../docs/UPSTREAM_AUDIT.md). The remaining upstream issues are explicitly listed there; this is not an all-issues-fixed claim.
