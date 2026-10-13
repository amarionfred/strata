# Strata

Android home, root shell, native Linux packages and services, files, notifications,
and Omarchy themes. Based on [Omalauncher](https://github.com/SimonSchubert/omalauncher).

## Install

Install [Termux](https://f-droid.org/packages/com.termux/), open it, and paste this whole line:

```sh
pkg install -y curl && curl -fL https://raw.githubusercontent.com/amarionfred/strata/main/install.sh -o ~/strata-install.sh && bash ~/strata-install.sh
```

It works from any folder, including Downloads. No Java, Gradle, or source download is needed.
The script downloads the APK, checks its checksum, configures Termux, and opens the app chooser.

1. In **Open with**, choose **Package installer**, not Termux. Allow installs from Termux if asked, then tap **Install** or **Update**, followed by **Open**.
2. In **Strata → Setup**, tap **Grant command permission** and allow it.
3. Tap **Choose default home** and select **Strata**.

That's the normal installation. Run the same command for updates.
[Download the APK directly](https://github.com/amarionfred/strata/releases/latest/download/strata.apk) if you only want the app.

Requires Android 10+. This is a preview; phone testing is pending. The complete
phone interface is unfinished. Android's lock screen, system shade, and other
apps still use their own UI.

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
