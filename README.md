# Strata

An Android home and native working environment, based on
[Omalauncher](https://github.com/SimonSchubert/omalauncher).

Strata includes interactive terminals, reconnectable sessions, a file editor,
package installation, user services, and an optional graphical desktop inside
the app. Programs run natively through Termux, sharing Android's kernel. Android
apps remain available on Home. No VM, PRoot, flashing or device root is needed
for this workspace.

This is a preview. Android's protected lock screen, system shade, settings and
other apps retain their own interfaces; phone-wide interface replacement remains
unfinished. The new workspace has passed host integration checks and still needs
validation on the phone.

## Install

Install [Termux](https://f-droid.org/packages/com.termux/), open it, and paste this whole line:

```sh
pkg install -y curl && curl -fL https://raw.githubusercontent.com/amarionfred/strata/main/install.sh -o ~/strata-install.sh && bash ~/strata-install.sh
```

It works from any folder, including Downloads. No Java, Gradle, or source download is needed.
The script installs native workspace tools, checks the APK, configures Termux, saves `strata.apk` in the phone's
Downloads folder, and tries to open Android's installer.

1. Allow Termux file access if asked, so the script can save the APK in Downloads.
2. If no installation window appears, open **File Manager → Internal storage → Download → strata.apk**. Allow installation from the app opening the APK if asked, then tap **Install** or **Update**, followed by **Open**. Choose **Package installer** if prompted.
3. In **Strata → Setup**, tap **Grant command permission** and allow it.
4. Tap **Choose default home** and select **Strata**.

Open **Terminal** to start the workspace. Existing APK-only installations can use
**Setup → Prepare workspace tools** first. Terminals stay inside Strata; sessions
keep their current directory and running programs when you change screens.

**Files** browses the native home, edits UTF-8 text and imports/exports through
Android's picker. **Software** installs real Termux packages in interactive
terminal sessions. **Services** saves, starts, stops and reads logs from native
foreground programs.

For graphical programs, open **Desktop → Install graphical tools** once, finish
the package prompts, then **Desktop → Start / reconnect**. This adds native X11,
Openbox and a terminal; graphical packages run inside Strata's embedded display.
Graphics is optional, especially on phones with limited RAM.

Run the same command to install, update, or retry. A verified download is reused
when it matches the latest release. If a **Save file in ~/downloads/** or
**termux-file-editor** dialog is still open, close it, return to Termux, and run
the command above. The APK does not need an editor.
[Download the APK directly](https://github.com/amarionfred/strata/releases/latest/download/strata.apk) if you only want the app.

Requires Android 10+. APK installation has been reported on the Tecno KL4;
the new runtime, permissions, IME, graphics and background behavior still need device validation.

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
stay in Termux. Use the workspace's **Stop** button first to end its native sessions
and services. Android/OEM process killing can also end sessions; files remain.
If you installed the optional Linux module, stop Linux and remove
the module in Magisk separately; uninstalling the APK does not stop it.

[Build from source — developers](CONTRIBUTING.md#build-on-a-computer) · [Checks](system/companion/VALIDATION.md) · [Upstream fixes](docs/UPSTREAM_AUDIT.md) · [License](LICENSE)
