# Strata

Android home, root shell, native Linux packages and services, files, notifications,
and Omarchy themes. Based on [Omalauncher](https://github.com/SimonSchubert/omalauncher).

Android 10+. Preview build; phone testing is pending. Android stays installed.
The full phone interface is unfinished: the lock screen, system shade, and other
apps still use their own UI.

## Install

1. [Download strata.apk](https://github.com/amarionfred/strata/releases/latest/download/strata.apk) and install it.
2. Open **Setup → Choose default home** and select Strata.

Install updates over the existing app to keep your settings.

## Root and Linux

**Your phone must already be rooted. Strata cannot root a stock phone.**
Installing `sudo`, enabling USB debugging, or using Shizuku does not grant device root.

1. Open **System → Request root and check identity**. Allow Strata in your root manager.
2. Open the job result. It must report `uid=0`. **Terminal → Run as Android root** now runs real root commands.
3. For Linux packages and services, download the matching `strata-runtime-0.5.0-*.zip` from the [release](https://github.com/amarionfred/strata/releases/latest). Use `arm64` for an `aarch64` kernel, `arm` for `armv7l`/`armv8l`, or `x64` for `x86_64`. The identity check shows your kernel architecture.
4. In an existing **Magisk** installation, open **Modules → Install from storage**, select the ZIP, and reboot.
5. Open **System → Status Linux**. If stopped, tap **Start Linux**. Use **Software** to install Linux packages and **Terminal → Run as Linux root** to run them.

Linux uses a persistent filesystem on `/data` and the phone's existing kernel.
There is no VM or PRoot. The runtime module currently supports Magisk; other root
managers and phone builds have not been tested. Installation does not flash a
boot image, unlock the bootloader, reset the phone, or remove Android packages.

In **System**, save a foreground service script, then stop and start Linux to
load it. Services can run after the app closes. **Start Linux after reboot**
enables startup at boot. Stop Linux before removing its module in Magisk;
Linux files are retained. [Runtime details](system/README.md).

## Terminal and themes

Termux supplies interactive terminals and packages that also work without root.

1. Install [Termux](https://f-droid.org/packages/com.termux/) and open it once.
2. In **Strata → Setup**, tap **Copy setup command**, paste it into Termux, and run it.
3. Return to Strata. Tap **Grant command permission**, then **Verify Termux connection**.

**Terminal** opens ordinary, Android root, or Linux root sessions. Root sessions
require a separate root grant for Termux. **Themes → Apply to terminal** applies
the selected Omarchy colors and shell configs to Termux.

[Shizuku](https://shizuku.rikka.app/guide/setup/) is optional for Android controls.
Notification access and floating controls are enabled in Setup.

To uninstall, choose another home app in Android settings, then remove Strata.
Removing the APK does not stop the Linux runtime or delete its files.

## Build

Use JDK 25 and Android SDK 36. Set `ANDROID_HOME` to your SDK folder.

```sh
./gradlew -p system/companion assembleDebug testDebugUnitTest lintDebug
```

APK: `system/companion/build/outputs/apk/debug/Strata-debug.apk`.

The downloadable app is built from `system/companion/`. The original Compose
launcher is in `app/`; its recent-app row is removed. Its legacy Linux backend
is separate from the native Strata runtime.

[Development](CONTRIBUTING.md) · [Checks](system/companion/VALIDATION.md) · [Upstream fixes](docs/UPSTREAM_AUDIT.md) · [License](LICENSE)
