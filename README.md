# Strata

An Android home app and control panel connected to Termux, based on
[Omalauncher](https://github.com/SimonSchubert/omalauncher).

The current release does not deliver the intended integrated phone environment.
Interactive terminals open in Termux. The separate native Linux runtime requires
existing root and Magisk. Android's lock screen, system shade, settings, and other
apps retain their own interfaces. Installing this APK or selecting it as the
default home does not complete that integration.

## Install

Install [Termux](https://f-droid.org/packages/com.termux/), open it, and paste this whole line:

```sh
pkg install -y curl && curl -fL https://raw.githubusercontent.com/amarionfred/strata/main/install.sh -o ~/strata-install.sh && bash ~/strata-install.sh
```

It works from any folder, including Downloads. No Java, Gradle, or source download is needed.
The script checks the APK, configures Termux, saves `strata.apk` in the phone's
Downloads folder, and tries to open Android's installer.

1. Allow Termux file access if asked, so the script can save the APK in Downloads.
2. If no installation window appears, open **File Manager → Internal storage → Download → strata.apk**. Allow installation from the app opening the APK if asked, then tap **Install** or **Update**, followed by **Open**. Choose **Package installer** if prompted.
3. In **Strata → Setup**, tap **Grant command permission** and allow it.
4. Tap **Choose default home** and select **Strata**.

Run the same command to install, update, or retry. A verified download is reused
when it matches the latest release. If a **Save file in ~/downloads/** or
**termux-file-editor** dialog is still open, close it, return to Termux, and run
the command above. The APK does not need an editor.
[Download the APK directly](https://github.com/amarionfred/strata/releases/latest/download/strata.apk) if you only want the app.

Requires Android 10+. APK installation has been reported on the Tecno KL4;
runtime, permissions, and interface behavior still need device validation.

## Themes and controls

In **Themes**, pick a palette and tap **Apply to terminal**.
**Install terminal tools** adds the listed Termux packages.

[Shizuku](https://shizuku.rikka.app/guide/setup/) is optional for Android controls.

## Root and Linux

Optional: these features require a phone that is already rooted. Skip them on a
stock phone. The installer does not root or flash the phone.

[Root and Linux setup](system/companion/INSTALL.md#root-and-linux).

## Uninstall

Choose another home app in Android settings, then uninstall Strata. Termux files
stay in Termux. If you installed the optional Linux module, stop Linux and remove
the module in Magisk separately; uninstalling the APK does not stop it.

[Build from source — developers](CONTRIBUTING.md#build-on-a-computer) · [Checks](system/companion/VALIDATION.md) · [Upstream fixes](docs/UPSTREAM_AUDIT.md) · [License](LICENSE)
