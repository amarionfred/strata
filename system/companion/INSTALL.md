# Install Strata on your phone

Open Termux and paste this whole line. It works from any folder:

```sh
pkg install -y curl && curl -fL https://raw.githubusercontent.com/amarionfred/strata/main/install.sh -o ~/strata-install.sh && bash ~/strata-install.sh
```

The script downloads the published APK, verifies it, enables the Termux connection,
and opens the app chooser. Existing Termux settings are kept; changed settings
are backed up beside `termux.properties` as `termux.properties.strata-backup`.

1. In **Open with**, choose **Package installer**, not Termux. Allow installs from Termux if asked, then tap **Install** or **Update**, followed by **Open**.
2. In **Strata → Setup**, tap **Grant command permission → Allow**.
3. Tap **Choose default home** and select **Strata**.

No Java, Gradle, Git, or source compilation is required. Android 10+ is required.
Run the same command to install, update, or retry. It reuses the downloaded APK
when its checksum matches the latest release. Existing app data is kept.

## If installation stops

- No installer appeared, or you closed it: return to Termux and run the same installation command above. Choose **Package installer** in **Open with**.
- A **Save file in ~/downloads/** dialog appeared: tap **Cancel**, return to Termux, and run the same installation command above. Choose **Package installer** when prompted.
- Download or checksum failed: check your connection and run the installation command again.
- Android blocked installation: allow **Install unknown apps** for Termux, return to Termux, and run the same installation command above.
- Terminal commands fail: in Strata's Setup tab, tap **Grant command permission**, then **Verify Termux connection**. Reopen Termux if needed.

The script cannot accept Android's installation or permission dialogs for you.

## Root and Linux

Skip this section on a stock phone. Your phone must already be rooted; installing
Strata, Termux, or a sudo package does not grant device root.

1. Open **System → Request root and check identity**. Allow Strata in your root manager.
2. Open the job result. It must report `uid=0`. **Terminal → Run as Android root** runs device root commands.
3. For Linux packages and services, download the matching `strata-runtime-0.5.0-*.zip` from the [release](https://github.com/amarionfred/strata/releases/latest). Use `arm64` for `aarch64`, `arm` for `armv7l`/`armv8l`, or `x64` for `x86_64`. The identity check shows the kernel architecture.
4. In an existing **Magisk** installation, open **Modules → Install from storage**, select the ZIP, and reboot.
5. Open **System → Status Linux**. If stopped, tap **Start Linux**. Use **Software** to install Linux packages and **Terminal → Run as Linux root** to run them.

Linux uses a persistent filesystem on `/data` and Android's existing kernel.
There is no VM or PRoot. The module currently supports Magisk; other root managers
and phone builds have not been tested. No boot image flashing, bootloader unlocking,
factory reset, or Android package removal is part of installation.

Save foreground service scripts in System, then stop and start Linux to load them.
**Start Linux after reboot** enables startup at boot. For startup errors, open
**System → Startup log**. Interactive root terminals also need a root grant for Termux.

If a root command times out or Strata is killed, check running processes before
repeating it. Stopping job tracking does not terminate the process.

## Uninstall

To remove the Linux module, first stop Linux in System, then remove the module
through Magisk and reboot. Its files remain under `/data/adb/oma-system`.
Uninstalling Strata alone leaves the module running if it was started.

To remove Strata, choose another home in Android settings and uninstall the app.
Termux files remain in Termux. Theme backups are under
`~/.local/state/oma/config-backup.*`.

[Build and device testing status](VALIDATION.md).
