# Oma System Preview

A separate Android APK implementing the accepted **native terminal + Android
management + shared phone interface** direction. It installs as
`dev.omalauncher.systempreview`, alongside `dev.omalauncher`.

Version 0.3 adds profile-aware apps, browser pin shortcuts, wallpaper application
and notification fixes to the Omarchy/native-terminal preview. Follow [INSTALL.md](INSTALL.md) for installation and
[VALIDATION.md](VALIDATION.md) for build checks and untested device scope.

The preview uses the existing Android installation. It contains no flashing,
bootloader, rooting, VM, PRoot, package stripping, or device-wipe workflow.
Termux is a separate native runtime dependency; Shizuku is an optional Android
administration dependency. Neither component is silently installed or granted
access. The original launcher's PRoot backend has not yet been migrated.

## Implemented surfaces

- Home and accent-insensitive Android app search, with personal/work tabs and clone badges. Labels load off the main thread and refresh after app/profile/locale changes. Actions revalidate stable profile serials.
- Android 15+ private-space tab with system lock/unlock, default-home role checks and immediate hiding during locks/pause. Private apps stay out of pinned shortcuts and notification feeds. This needs physical Android 15+ testing; it is unavailable on Android 14.
- Browser/app pin-shortcut confirmation and pinned shortcut launches when Oma is the default home app. Shortcuts appear in Applications; private-space requests are rejected.
- Themes can apply a chosen image or current palette background color to Android home/lock wallpaper. An active live wallpaper is preserved; results distinguish both screens, home only and failure. Wallpaper changes are explicit; palette selection alone does not overwrite Android wallpaper.
- Native command jobs via Termux's documented `RUN_COMMAND` API, with private
  persistent output, actual exit codes, missing-result and truncation reporting.
  Each job starts in the native home. Interactive shells/editors open a real
  Termux terminal session; an embedded interactive terminal is not implemented.
- Native package installation, repository refresh, installed-package listing.
  Package names are validated separately from the intentionally free-form shell.
- Native home listing and Android document-picker integration for opening files.
- Editable `.config/oma/session.sh`, loaded after Omarchy defaults by command jobs
  and interactive sessions opened from Oma. Save validates shell syntax, writes
  atomically and preserves the previous file. Existing `.bashrc` is preserved.
- The original launcher's 22 bundled Omarchy palettes and actual shared Kotlin
  color parser/template renderer. Themes color all preview screens and the live
  floating panel. Import supports validated `colors.toml` data, without scripts.
- **Themes → Apply to terminal** installs Termux colors and adapted Omarchy Bash
  defaults, with Starship/tmux/btop config seeds. A completed runtime callback
  confirms the apply; merely sending a command does not report success.
- Shizuku 13+ UserService with typed read-state, Wi-Fi and rotation controls.
  It authorizes the caller UID, reports its actual UID, bounds process execution,
  reports errors and reads back rotation state. It accepts no arbitrary privileged
  command string. Wi-Fi status after a request may still reflect a transition.
- Media-volume control and Android settings entry points.
- Notification center using explicit notification access; opening and dismissing
  permitted notifications. The preview reads notifications when this screen is
  refreshed and does not persist or send their content off the device.
- User-enabled floating navigation panel for opening these surfaces from other
  apps, with a foreground notification and stop actions. It hides on screen-off
  and returns after unlock. It does not start automatically after a reboot.

The secure lock screen, Android shade, recents, protected settings interfaces,
and third-party app interiors are **not replaced** by this build. Shell UID 2000
does not provide root/sudo or arbitrary kernel control. Android/OEM background
process limits still apply. No native daemon supervisor has been integrated into
this APK. This is the first integration milestone, not the completed phone OS.

## Omarchy config mapping

The config snapshot matches the upstream launcher's pinned Omarchy revision:
`8b4eae66da2938ba9559f103b18dbf85cdf28a70`. Assets are included in the APK; applying
a theme needs no config download. `tools/fetch-omarchy.py` refreshes this explicit
snapshot, verifies Git blob hashes and records file SHA256 values in its manifest.
Gradle copies the launcher's existing palette and Kotlin files for compilation;
it does not compile or use the launcher's PRoot runtime.

| Config | Native behavior |
| --- | --- |
| `colors.toml` | Same palette resolution; preview UI, floating panel, Termux ANSI colors |
| Bash shell/aliases/inputrc | History, readline and portable aliases; Android file opening and native prefix paths |
| Starship | Original prompt config, seeded only when no user config exists |
| tmux | Original portable bindings and status config; desktop help command replaced by tmux's own key list |
| btop | Original config seed and generated/theme-specific colors; package not auto-installed because Termux provides it in its root repository |

**Install terminal tools** installs Starship, tmux, eza, fzf, bat, zoxide, git and
nano through Termux. Helpers that require other tools still require those tools:
for example, `n` requires Neovim and `sff` requires SSH. The original desktop/agent
launcher aliases are omitted. Hyprland/Waybar, desktop service controls, wallpapers
and launcher widgets are not connected by this change. Selecting a palette does
not restyle Android's protected UI or other apps.

Each apply backs up replaced files under `~/.local/state/oma/config-backup.*`,
writes individual files atomically and records real errors. It is not a whole-pack
transaction: a failed apply can leave some updated files, with backups for files
it replaced. Existing Starship, tmux and btop app configs are kept. `.bashrc` is
never overwritten; Oma opens Bash with its own rc wrapper, sourcing `.bashrc`,
Omarchy defaults, then the user's `session.sh`. Ordinary sessions opened directly
from Termux keep their existing startup behavior.

UI selection and native application are separate actions. **Apply to terminal**
synchronizes the selected palette; it is not a filesystem watcher or a two-way
sync with the original launcher's private app data. Export/import `colors.toml`
to share a custom palette.

## Build

Use the repository wrapper, JDK 25, Android SDK platform 36 and build-tools 36.0.0:

```sh
./gradlew -p system/companion assembleDebug testDebugUnitTest lintDebug
```

Set `JAVA_HOME` and `ANDROID_HOME` to your installed toolchains as usual. The
standalone project avoids configuring the upstream Compose/NDK/PRoot application.
It uses AGP 9.4.1 / Gradle 9.8.0 like the checkout, but has its own compile and
target API 36. The upstream application's API 37 target is unchanged. Minimum
API 29 is a build constraint, not a compatibility certification for every phone.
There is no ABI-specific payload in the companion; the installed Termux runtime
must support the actual phone ABI and Android release.

APK: `build/outputs/apk/debug/OmaSystemPreview-debug.apk` (see Gradle's output
metadata if the output name changes). Debug signing is for local testing, not
the release identity for distributing updates to friends.

## First setup on the phone

1. Install the preview APK alongside the existing launcher. Open **Setup**.
2. Install Termux from an upstream-supported source and launch it once to complete
   bootstrap. Existing Termux add-ons must use a compatible signing source.
3. Copy and run the displayed `allow-external-apps = true` command in Termux.
   Grant Oma the separate Termux command permission in Setup. If Android has
   stopped showing the prompt, open the preview's app permission settings.
   This authorizes Oma to execute commands and access data with Termux's identity.
4. Run **Verify native runtime**. Open its job result and inspect UID, kernel,
   native home and package-manager path. A permission grant alone is not a passed
   connectivity test. The UI records no successful command until a result arrives.
5. Open **Themes**, select a palette, **Apply to terminal** and wait for the
   completed result. Use **Install terminal tools**, wait for exit 0, then
   **Open themed terminal** to load the portable defaults.
6. For Android administration, install/open Shizuku and start it using its USB
   instructions on the computer. Authorize Oma, then **Read device state**.
   Re-activate an ADB-started Shizuku service after reboot; the KL4 does not expose
   Wireless debugging according to the user. Root is not part of this setup.
7. Enable notification access, floating controls or default home individually
   when ready. Core native commands do not depend on Shizuku being connected.

## Qualification checklist

No stable ADB connection was available for installing or testing this APK. Host
tests and an APK build cannot establish that Termux bootstrap, permission prompts, Shizuku
activation, OEM background behavior or interface rendering work on the KL4.

On the actual device, record the app and dependency versions and check:

1. Both launchers remain installed. Open every tab; test navigation with the
   keyboard, screen rotation, large font settings, and the Tecno's gesture bar.
2. With neither dependency installed/authorized, home and app launches work;
   unavailable native/admin actions explain the missing setup.
3. Native identity returns the Termux app UID and the device kernel; `false`
   returns exit 1, not success. Shell errors and stderr are visible.
4. Install `tree`, run `tree --version`, write a file in the native home, close and
   reopen both apps, and confirm the package and file remain.
5. Open an interactive session and test input, Ctrl-C, `cd`, an editor and `exit`.
   Job-console `cd` does not persist to the next process.
6. Load/save configuration with quotes, Unicode and multiple lines. An invalid
   shell program must preserve the previous file and return a nonzero exit code.
7. Read the Shizuku report. For a USB-started service, expect UID 2000. Compare
   rotation readback and actual behavior, then restore the original state. Try
   Wi-Fi changes only when disruption is acceptable. An OEM denial remains an error.
8. Grant notification access; verify open/dismiss and revocation. Enable floating
   controls; test another app, screen-off, secure unlock, stop action and revocation.
9. Stop Shizuku and repeat core runtime use. Reboot only as a separately planned
   device test, then verify explicit USB reactivation. Test process survival
   under the device's background policies; do not infer daemon support from a job.
10. Apply Tokyo Night, then Nord; confirm Termux colors and matching successful
    results. Switch a light palette while the panel is open. Open a fresh themed
    terminal, check the prompt, aliases and tmux. Preserve an existing config and
    inspect its reported backup. Import a custom palette and reject invalid data.

Pending native jobs remain **awaiting result** if the runtime never calls back.
Oma does not invent an exit status or kill unrelated Termux processes. History
retains recent finished jobs and pending jobs, with a maximum of 16 outstanding
requests. Use **Stop tracking** in a pending job's result to stop waiting for a
lost callback; this does not kill its native process. Output can contain private
data; it stays in the preview's app data, excluded from Android backup/transfer.

## Removal and source references

Stop floating controls and choose another default home in Android settings, then
uninstall the preview normally. That removes its job history and editor draft.
Native files/packages/configuration stay in Termux; remove them there if desired.
Revoke the preview's notification and Termux permissions when no longer needed.
No Android packages have been disabled or system partitions modified by setup.

- [Termux command API](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Termux native execution and package prefixes](https://github.com/termux/termux-packages/wiki/Termux-execution-environment)
- [Shizuku API / UserService](https://github.com/RikkaApps/Shizuku-API)
- [Shizuku activation](https://shizuku.rikka.app/guide/setup/)
- [Android notification listeners](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Android application overlays](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)

Shizuku, Omalauncher and Omarchy notices are bundled with the APK, together with
Apache 2.0 for Kotlin, JetBrains annotations and AndroidX Annotation. See
`assets/THIRD_PARTY.txt` and `assets/omarchy/LICENSE`.
Termux itself is installed separately and is not bundled/rebranded in this APK.

## Upstream maintenance

See [the audit](../../docs/UPSTREAM_AUDIT.md) for the original launcher fixes, exact PR dispositions and remaining work. Reordering and Wayland multitouch changes are in `app/`; this preview does not contain that grid or compositor. There is no claim that all upstream issues or every Android model are qualified.
