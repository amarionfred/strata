# Strata Android app

Native Termux commands and packages, Android app search, files, notifications,
floating controls, and Omarchy themes. [Install](INSTALL.md).

Android 10+. Shizuku is optional for Android controls. This app keeps Android
installed and uses native processes. It does not replace SystemUI or grant root.

## Build

Use JDK 25, Android SDK 36, and build-tools 36.0.0. From the repository root:

```sh
./gradlew -p system/companion assembleDebug testDebugUnitTest lintDebug
```

APK: `system/companion/build/outputs/apk/debug/Strata-debug.apk`.
Application ID: `dev.omalauncher.systempreview`, retained for existing installations.

## Runtime

Commands use Termux's `RUN_COMMAND` API. Jobs store output and exit codes locally;
interactive shells open in Termux. Package installation uses Termux repositories.
User overrides live in `~/.config/oma/session.sh`.

Shizuku controls use a typed service for Wi-Fi, rotation, and device state.
Notification access and floating controls are enabled separately in Setup.
Private Space needs Android 15+ and Strata as the default home app.

## Themes

The app shares the original launcher's 22 palettes and color parser. Configs are
pinned to Omarchy revision `8b4eae66da2938ba9559f103b18dbf85cdf28a70`.
**Apply to terminal** writes Termux colors and adapted Bash defaults, with
Starship, tmux, and btop config seeds. Existing app configs and `.bashrc` are kept.
Replaced files are backed up under `~/.local/state/oma/config-backup.*`.

Wallpaper changes require an explicit apply. Android's protected UI and other
apps keep their own styling. Desktop Hyprland and Waybar configs are not used.

## Checks

[Build results](VALIDATION.md) and [upstream changes](../../docs/UPSTREAM_AUDIT.md).
The original grid and Wayland changes are in `app/`, separate from this APK.
