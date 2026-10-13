# Strata 0.6.0-preview checks

Checked on 2026-10-13. The shipped APK contains the native runtime broker and
embedded terminal, files, packages, services and graphical frontend.

| Check | Result |
| --- | --- |
| Companion JVM tests | 31 passed |
| Native workspace integration | 9 passed |
| Installer regression tests | 23 passed |
| Embedded browser workspace | Passed at phone dimensions |
| Actual native X11 graphics | Pixels, pointer and keyboard passed |
| APK build | Passed |
| Lint | 0 errors, 19 warnings |
| Signature | Verified; same certificate as the installed 0.5 preview |
| Bundled runtime/frontend assets | Match tested source bytes |

Native checks execute real Bash/Python, PTYs, tmux sessions, processes and sockets.
They cover resize, Ctrl-C, reconnection with persistent cwd/environment, interpreter
execution and exit codes, conflict-safe file edits/backups, service process identity,
logs/stop, authentication/replay rejection and stopping without deleting files.
A separate test runs the exact APK stdin bootstrap, terminates and restarts the
broker process, and verifies that native terminal/service PIDs are unchanged.
Java tests cover mutual authentication and refusing an impersonated server.

The browser drives the actual bundled frontend against the native broker. It
checks terminal/interpreter output, editor saves, draft recovery across navigation,
services and a real X11 application's displayed pixels and keyboard/pointer effects.
Host graphics use an isolated Arch Xvnc 1.16.2-5 binary; no VM, PRoot, Android
emulator, phone flashing or system-package changes were used.

**Physical-phone validation is pending.** ADB had no connected device. The user's
previous installation report concerns 0.5. It does not validate this APK's update,
RUN_COMMAND/stdin bootstrap, WebView/IME, picker, Android X11 packages/SELinux,
background lifecycle, performance or memory use on the Tecno KL4. Protected Android
surfaces and the full phone-wide presentation remain unfinished.

Original launcher/OWL and root-runtime code are preserved and unchanged; their
previous checks were not rerun. Optional root-runtime ZIP assets remain version
0.5.0 and retain their existing checksums. They require a phone with existing root.

Application ID: `dev.omalauncher.systempreview`; version code: 6; Android 10+.

APK SHA256: `55a3476fb50f2b206b6a4a3798f0b7d943e2a0fcdc4925c65d71975e51aeaa11`.

[Machine-readable results](../../docs/validation-strata-0.6.0.json) ·
[Architecture and reproducible checks](../../docs/NATIVE_WORKSPACE.md)
