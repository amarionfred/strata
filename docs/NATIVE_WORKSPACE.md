# Native workspace in 0.6.0

The shipped `system/companion` APK now embeds terminal, file, package, service and
graphical views. The original `app/` compositor and grid remain preserved. Its
Arch paths, shared-memory/display bridge and process launcher are coupled to
PRoot, so that runtime is not activated for the stock-phone workspace. Strata
continues to reuse its palette parser, palettes and shared Android integrations.

## Runtime and frontend

`system/workspace/runtime.py` runs as a Termux background task managed by
TermuxService. The APK sends the bundled code and a private pairing secret through
RUN_COMMAND stdin. There is no shell exposed by an exported Strata component.
The broker listens on IPv4 loopback and authenticates both directions using
fresh nonces and HMAC-SHA256 before accepting requests. The secret stays out of
URLs, arguments, shared files and WebView. Frame, connection and file limits bound
the interface. This protects against other ordinary apps; processes already
running as the same Termux UID share its permissions.

The trusted, bundled WebView loads no remote pages. xterm.js receives real PTY
bytes. A dedicated tmux server under `~/.local/state/strata` owns terminal and
service processes, without changing the user's normal tmux configuration. A
terminal client obtains a controlling TTY for resize and job-control signals.
Frontend detach, navigation and Activity recreation do not kill tmux sessions.
Shells load `.bashrc`, the existing Omarchy adapter and `session.sh`.

Files are relative to the actual native home. Text edits use revision checks,
atomic replacement and backups; binary/oversized files and links outside the home
are refused by the editor. Drafts survive view recreation. Android document
import/export uses explicit picker grants. Services keep scripts and logs in the
workspace state directory, report process state/exit status and can be stopped
individually. Enabled services restart when the broker starts again; graphics
starts only when requested. The Stop control ends the workspace's own processes,
disables service restart, and preserves files.

Graphics uses native Termux Xvnc, Openbox and aterm packages. RFB has no TCP
listener: a mode-0600 socket inside Termux's private directory is forwarded over
the authenticated connection to bundled noVNC. X11 disables TCP and uses a private
MIT-MAGIC-COOKIE. Openbox decorations and the initial graphical terminal use the
selected palette at startup. This is a native X11 session on Android's kernel,
not a VM, PRoot, root shell, or replacement for Android's protected surfaces.

Android/OEM process limits can stop Termux and its children. No reboot survival,
GPU acceleration, arbitrary desktop binary compatibility, or device-root powers
are implied. Files/packages persist; a killed process must be restarted.

## Validation

`python3 -m unittest discover -s system/workspace/tests -v` uses real native tmux,
PTYs, Bash/Python, sockets, files and services. JVM tests exercise the actual Java
connection, HMAC vectors and refusal of an impersonated runtime.

`system/workspace/tests/browser.mjs` drives the shipped browser assets against the
real broker at phone dimensions. Set `PLAYWRIGHT_MODULE` to a local Playwright
module and `CHROMIUM_PATH` to its browser executable. For graphical checks,
compile `tests/x11_probe.c` with `cc -lX11`, set `STRATA_X11_PROBE` to that binary,
and `STRATA_XVNC_DIRECTORY` to a directory containing a host Xvnc binary. Then run:

```sh
node system/workspace/tests/browser.mjs
```

It checks shell/interpreter output, files, draft recovery, services, and (when
enabled) actual X11 framebuffer pixels and keyboard/pointer effects. Screenshots
go to ignored `.local/workspace-qa/`. No VM or Android emulator is used.

The phone was not connected to ADB during this work. APK installation/update,
Termux grants/bootstrap, Android WebView/IME, document picker, SELinux/socket
access, package downloads, OEM background behavior and memory use still need
physical-device validation. Host graphics checks use a host Xvnc build, not an
Android package binary.
