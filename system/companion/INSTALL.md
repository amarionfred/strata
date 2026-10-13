# Install Strata

Follow the [installation steps](../../README.md#install).

Root commands need an already rooted phone and permission from its root manager.
Linux packages and persistent services also need the Strata Magisk runtime module.
A stock phone can use home, Termux, themes, notifications, and optional Shizuku
controls; it cannot use the root features.

Install updates over the existing app. The application ID and signing key are
unchanged from earlier Strata and Oma System Preview builds.

If Termux commands fail, open Termux, repeat the setup command, and check Strata's
command permission in Android settings. For a root failure, check the root manager
and the job output. For Linux startup failures, use **System → Startup log**.
If a root command times out or Strata is killed, check running processes before
repeating it. Stopping job tracking does not terminate the process.

To remove the Linux module, first stop Linux in System, then remove the module
through Magisk and reboot. Its files remain under `/data/adb/oma-system`.
Uninstalling Strata alone leaves the module running if it was started.

To remove Strata, choose another home in Android settings and uninstall the app.
Termux files remain in Termux. Theme backups are under
`~/.local/state/oma/config-backup.*`.

[Build and device testing status](VALIDATION.md).
