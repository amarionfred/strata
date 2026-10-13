# Install Oma System Preview 0.3

This is an ordinary Android APK. It preserves Android, installs alongside the
original Omalauncher and uses native Termux packages. Installation requires no
flashing, bootloader unlock, root, VM or factory reset. It updates an existing
0.1/0.2 preview signed with this workspace's same debug key.

## 1. Install the APK

The built APK on this computer is:

```text
/home/park/Downloads/Oma-System-Preview-0.3.0.apk
```

Copy it to the phone's Downloads folder using USB file transfer or your usual
file-transfer method. Open it in the phone's Files app, allow that app to install
unknown apps if Android asks, and tap **Install**. Open **Oma System Preview**.
USB debugging is not needed for this file-based installation.

Alternatively, with an authorized USB-debugging connection, run from the repository:

```sh
.local/toolchains/platform-tools/adb install -r .local/companion/oma-system-preview-0.3.0.apk
```

The package is `dev.omalauncher.systempreview`; the original `dev.omalauncher`
stays installed. Installing does not change your default home app.

## 2. Connect the terminal

Install [Termux from F-Droid](https://f-droid.org/en/packages/com.termux/) or an
[official supported download](https://github.com/termux/termux-app#installation).
If Termux is already installed, use that installation. Its app and add-ons must
come from compatible signing sources. Open Termux once and let setup finish.

In **Oma → Setup**, tap **Copy setup command**, switch to Termux and paste/run it:

```sh
mkdir -p ~/.termux && printf '\nallow-external-apps = true\n' >> ~/.termux/termux.properties && termux-reload-settings
```

Return to Oma's **Setup**, tap **Grant command permission** and allow it. Then
tap **Verify native runtime**. Open the completed job and check for exit 0 and
the reported native home/UID. This permission allows Oma to run commands as your
Termux app; it does not give the phone root.

If Android no longer shows the permission prompt, use **Runtime permission settings**
in Setup. If the job stays awaiting result, open Termux and inspect its setup and
error notifications; waiting is not a confirmed connection. Termux documents
both required permissions in its [command API guide](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).

## 3. Apply Omarchy

1. Open **Themes** and select a palette, for example **Tokyo Night**.
2. Tap **Apply to terminal → Apply**. Wait for a completed successful job. The
   result gives the backup directory for any replaced files.
3. Tap **Install terminal tools → Install**. Wait for exit 0; downloads require
   internet access. If a repository is unavailable, open Termux, run
   `termux-change-repo`, choose a working mirror and retry the failed install.
4. Tap **Open themed terminal**. A new real Termux session loads the Omarchy
   shell defaults and installed prompt tools. Run `tmux` for its configured UI.

There are 22 bundled palettes. Import your own **colors.toml** using the Themes
file picker. Selecting a palette updates Oma immediately; **Apply to terminal**
also updates the native configs. Existing Starship, tmux and btop configs are
preserved, so your own config may intentionally override the supplied defaults.

Oma preserves `.bashrc`. Sessions opened from Oma load `.bashrc`, Omarchy defaults,
then `~/.config/oma/session.sh`. Sessions opened directly from Termux retain their
own startup behavior. Termux colors are shared by its terminal sessions.

The full desktop Hyprland/Waybar configuration does not run Android's UI. This
build connects colors and portable native tool configs; it does not replace the
secure lock screen, Android shade, recents or other apps' interiors.

## 4. Optional phone controls and home

The terminal and themes work without Shizuku. For the additional Android controls,
install/open [Shizuku](https://shizuku.rikka.app/download/). With the phone connected
by authorized USB debugging, run on this computer from the repository:

```sh
.local/toolchains/platform-tools/adb devices -l
.local/toolchains/platform-tools/adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
```

Proceed with the second command only when the phone is listed as `device`.
Return to **Oma → Setup**, authorize Shizuku access and use **Controls → Read device
state**. An ADB-started service reports shell UID 2000 and needs restarting after
a reboot. See [Shizuku's USB setup guide](https://shizuku.rikka.app/guide/setup/).

In **Setup**, enable the optional default home, notification access and floating
controls individually. Android shows the relevant permission screens. Start the
floating panel after allowing display over other apps; stop it from Setup or its
notification. It hides while the screen is locked.

## Remove or undo

Choose another default home in Android settings, stop floating controls and
uninstall Oma normally. Your existing Android apps remain. Termux packages and
native configs persist in Termux until you remove or restore them there. Each
apply's job output identifies `~/.local/state/oma/config-backup.*`; restore the
specific file you want from its matching relative path. User application config
seeds are left alone on later applies. A failed apply may need a retry or file
restoration; it does not report the theme as synchronized.

This is a locally signed preview for testing. Build checks are recorded in
[VALIDATION.md](VALIDATION.md); the Tecno installation and permission flows still
need a real device check before broader distribution.

## Check the 0.3 additions

In **Themes → Android wallpaper**, choose a photo or apply the palette background color, then read the result. Live wallpapers are kept. In **Home**, verify search and any work/clone profile. Set Oma as default home before trying a browser’s **Add to home screen**; accept the pin request, then open the shortcut from Applications. **Inbox** should update when notifications arrive or disappear.

Private Space requires Android 15 or later and a configured private profile. An Android 14 Tecno cannot validate that feature. This APK requires Android 10 or later; model-independent source code does not guarantee every OEM’s behavior.
