# Strata releases

The public APK is built from `system/companion/`.

1. Bump `versionCode` and `versionName` in `system/companion/build.gradle.kts`.
2. Run the checks in [CONTRIBUTING.md](../CONTRIBUTING.md).
3. Build `assembleDebug` using the existing preview signing key. Keep this key private and backed up; changing it prevents updates over older builds.
4. Copy the APK to `strata.apk` and run `sha256sum strata.apk > SHA256SUMS`.
5. Tag the tested commit and attach both files to a GitHub release. Record build results and any device checks in the release notes.

Current releases are debug-signed preview builds. A production signing migration has not been configured.

The legacy launcher workflow only runs for `launcher-v*` tags and uses its own signing secrets. It does not publish the Strata APK.
