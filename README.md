# Strata

Android home, native terminal, packages, files, notifications, and Omarchy themes.
Based on [Omalauncher](https://github.com/SimonSchubert/omalauncher).

## Install

Requires Android 10 or newer. This is an early build; phone testing is still pending.

1. [Download strata.apk](https://github.com/amarionfred/strata/releases/latest/download/strata.apk) and open it on your phone. Allow installation when Android asks.
2. Install [Termux](https://f-droid.org/packages/com.termux/) and open it once.
3. Open **Strata → Setup → Copy setup command**. Paste it into Termux and run it.
4. Return to Strata and tap **Grant command permission**, then **Verify native runtime**.
5. Tap **Choose default home** and select **Strata**.

In **Themes**, pick a palette and tap **Apply to terminal**. **Install terminal tools** adds the listed packages.

[Shizuku](https://shizuku.rikka.app/guide/setup/) is optional for Android controls.

To remove it, choose another home app in Android settings, then uninstall Strata.

## Build

Use JDK 25 and Android SDK 36. Set `ANDROID_HOME` to your SDK folder.

```sh
./gradlew -p system/companion assembleDebug
```

APK: `system/companion/build/outputs/apk/debug/Strata-debug.apk`.

The downloadable APK comes from `system/companion/`. The original Compose
launcher is in `app/`; its recent-app row has been removed. Its optional legacy
Linux backend is separate from Strata's native Termux runtime.

[Development](CONTRIBUTING.md) · [Upstream fixes](docs/UPSTREAM_AUDIT.md) · [License](LICENSE)
