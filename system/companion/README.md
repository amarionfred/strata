# Strata Android app

Embedded native terminals and graphics, packages, services, files, Android apps,
notifications and Omarchy themes. Optional device-root administration is preserved.
[Install](INSTALL.md).

Android 10+. Shizuku is optional for Android controls. This app keeps Android
installed and uses native processes. It uses an existing root manager; it does
not root the phone or replace SystemUI.

## Build on a computer

For phone installation, use the [Termux installer](../../README.md#install).

Use JDK 25, Android SDK 36, and build-tools 36.0.0. From the repository root:

```sh
./gradlew -p system/companion assembleDebug testDebugUnitTest lintDebug
```

APK: `system/companion/build/outputs/apk/debug/Strata-debug.apk`.
Application ID: `dev.omalauncher.systempreview`, retained for existing installations.

## Runtime

Root jobs run in a non-exported foreground service through `su -c`. A UID check
must return 0 before the command executes. Output and exit status are recorded;
a failed command can still have a verified root identity. Jobs have a ten-minute
timeout. Terminating the client does not guarantee termination of every process
started by a root manager, so interrupted jobs report unknown process state.
Root jobs are initiated in the visible app and are not automatically retried.

The System tab controls the [native runtime module](../README.md). Linux packages
use Alpine's `apk`; foreground services are syntax-checked and saved atomically.
Restart Linux to apply service changes. Files and packages persist across restarts.
The runtime requires Magisk and does not supply missing kernel capabilities.

The stock-phone workspace uses Termux's `RUN_COMMAND` API to start a native Python
broker, then connects an embedded terminal/file/service/display frontend to it.
Dedicated tmux sessions preserve programs across frontend reconnections. Packages
use Termux repositories. Graphical programs use a private native Xvnc display with
Openbox; no guest OS is installed. User overrides live in `~/.config/oma/session.sh`.
Advanced root terminals keep their existing separate Termux path.
[Implementation and validation](../../docs/NATIVE_WORKSPACE.md).

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
