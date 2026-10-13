# Install Strata

1. Download [strata.apk](https://github.com/amarionfred/strata/releases/latest/download/strata.apk) and install it.
2. Install [Termux](https://f-droid.org/packages/com.termux/) and open it once.
3. In Strata's **Setup** tab, tap **Copy setup command**. Paste it into Termux and run it.
4. Return to Strata. Tap **Grant command permission**, then **Verify native runtime**.
5. Tap **Choose default home** and select Strata.

Requires Android 10+. No flashing, reset, root, or VM is needed.

## Optional

- **Themes → Apply to terminal**: apply the selected Omarchy palette and shell configs.
- **Themes → Install terminal tools**: install the listed Termux packages.
- **Setup → Grant notification access**: show notifications in Inbox.
- **Setup → Allow floating controls**, then **Start floating controls**: open Strata from other apps.
- [Set up Shizuku](https://shizuku.rikka.app/guide/setup/), then tap **Authorize controls** for Android controls. Restart Shizuku after reboot.

If commands do not run, open Termux, repeat the setup command, and check Strata's command permission in Android settings.

Install updates over the existing app to keep its data. Older Oma System Preview installations update to Strata using the same application ID and signing key.

To uninstall, choose another home app in Android settings and remove Strata. Termux files and packages remain in Termux. Theme backups are stored under `~/.local/state/oma/config-backup.*`.

See [validation](VALIDATION.md) for build results and device testing status.
