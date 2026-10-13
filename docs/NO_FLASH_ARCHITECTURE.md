# Accepted native system suite without flashing

The user accepted Android management plus a shared phone interface, retaining
real terminal commands and package installation, on 2026-10-13. The first
[companion APK](../system/companion/README.md) implements this integration path;
it is not a device-qualified replacement OS. It preserves the no-flashing/no-unlocking/no-reset and
no-VM constraints. It does not silently mark the original real-root, kernel
control, or complete SystemUI replacement requirements as satisfied.

## Root integration in 0.5.0

The app now connects to an existing root manager with a UID-checked `su` service
and controls the native Magisk runtime. System provides runtime startup, shutdown,
autostart, and service scripts. Terminal and Software support native Linux commands
and Alpine packages alongside Termux. The current Tecno is still unrooted, so these
root features are unavailable on it. Full SystemUI integration remains unfinished.
The original baseline below describes the features that work without device root.

## Architectural change

Build the common runtime as native Android-compatible programs plus a coordinated
Android interface suite. For the first prototype, use upstream Termux as a
separate native runtime component, accessed through its supported command API;
use its native packages without installing PRoot or a guest distribution. Use
the existing Android kernel and application framework. Make privileged Android
management a separate capability supplied by an authorized ADB/Shizuku service.
Retain the existing root/chroot module as an optional backend only for phones
that already have usable root.

The source currently instantiates `Proot` directly in `Runtime.kt`; package
management, files, themes, widgets, Wayland, and bridges depend on its paths and
execution methods. A complete migration needs a runtime abstraction and separate
package/path adapters. Replacing the command named `proot` with a shell would
not make the existing Arch packages work in a different execution environment.

Suggested boundaries:

```text
Home / files / terminal / software / settings / notification interface
    | ordinary app permissions       | authorized, typed management requests
Native program runtime              Shizuku UserService (ADB shell UID 2000)
    |                                | Android Binder services
Existing Android kernel + framework + vendor hardware services
```

Android apps continue to run through Android itself. Native tools execute as
real processes on the same kernel. Connect them through explicit file sharing,
clipboard integration, process/session APIs, and window management. No VM,
PRoot, simulated root identity, or namespace requiring root belongs in this
baseline.

## Components and actual powers

| Component | Concrete implementation | Boundary |
| --- | --- | --- |
| Native terminal and tools | Native executables/interpreters compiled for Android; real process exit status, pipes and signals | A curated compatible package repository; arbitrary desktop distro binaries are not promised |
| Files | Persistent private `home`, configuration, package data and service state on `/data`; DocumentsProvider for explicit Android sharing | A real directory hierarchy, not a separately mounted filesystem or replacement for Android's `/` |
| Software installation | Initially the native Termux package manager/repositories; later a qualified fork-owned distribution | Repository/prefix, target-SDK and execution rules must be preserved and tested |
| Services | User-started jobs supervised by the app with foreground-service integration | Android/OEM lifecycle and background restrictions remain; service survival needs device tests |
| Android app integration | Existing package/intent APIs, app catalog and shared files | Android still owns APK execution, security and hardware services |
| Android administration | Authorized Shizuku UserService calls, checking the actual backend identity and each operation | Shell UID 2000 is not root; available permissions vary by OS/vendor |
| Common visual interface | Coordinated home, files, terminal, software and settings UI; notification access and permitted overlays where available | Protected SystemUI, credential screens and third-party app interiors cannot be universally replaced by an ordinary APK |
| Device root / kernel administration | Existing root backend when genuinely present | Unavailable on the current unrooted KL4; do not substitute a fake `sudo` |

Native tools do not need UID 0 merely to be real programs. Termux is an existing
example of a native Android userspace and package ecosystem. Its binaries are
compiled for its own filesystem prefix, so copying its repository unchanged
into a renamed fork is not a valid integration plan.
[Termux execution model](https://github.com/termux/termux-packages/wiki/Termux-execution-environment).

## Reuse the native runtime before rebuilding its distribution

Termux exposes `RUN_COMMAND` to other Android apps, including command results
through a PendingIntent. The caller needs its explicit runtime permission and
Termux's `allow-external-apps` setting enabled. Declare package visibility for
the modern launcher target. Keep this first bridge scoped to the user-selected
runtime and authenticated callback; it is not a general broadcast shell.
[Upstream integration API](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).

The upstream native runtime currently targets API 28. Keep that component's
execution model separate from Omalauncher's API 37 UI for the initial proof.
This is a concrete compatibility dependency, not a claim of universal Android
support. Test installation and execution on each supported Android version;
do not promise Play Store eligibility or future target-SDK compatibility.
[Termux build configuration](https://github.com/termux/termux-app/blob/master/gradle.properties).

A future runtime fork under our own package identity must rebuild the package
prefix and preserve upstream licensing and provenance. A first integration with
upstream Termux avoids declaring an untested, newly rebuilt repository working.
Its command API supports job/result integration; a seamless embedded interactive
terminal and native GUI windows still require additional session/display work.

## Alternative executable packaging requires validation

The existing app targets API 37. Android restricts direct execution of files
downloaded into writable private app data for apps targeting API 29 or above.
An ordinary `download; chmod; exec` package installer is therefore not an adequate
design for this app. [Android's execution restriction](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission).

If a future independent runtime must target modern APIs, prototype native tools
delivered as signed APK code packages, with the executable
payload extracted by Package Manager to its code directory. Keep writable home,
configuration and package state separate. An app runtime resolves installed code
locations through Package Manager; binaries must be compiled for the resulting
environment and dependency search paths. Start by verifying one executable in
the main APK, then validate the separate code-package path, updates, dependencies,
and process behavior on the actual KL4 before committing to that packaging model.
Do not silently lower the launcher's target SDK. This alternative is exploratory,
not a prerequisite for the first Termux-backed proof or an established package
distribution implementation.

Use Android's ordinary package installer for code updates. An active shell
service may perform supported installation operations on the user's request;
without it, normal Android installation confirmation remains part of the flow.
Package installs, updates and removals affect this environment, not boot/system
partitions. Scripts and data packages still need authenticity and integrity checks.

## Management service and restart behavior

Use Shizuku's existing authorization and UserService mechanism for the prototype.
It supports separate Java/native service code with the starter's identity. Check
that identity: ADB normally supplies UID 2000; only actual root supplies UID 0.
Give the UI typed operations such as reading settings, managing explicitly
selected packages, and executing approved service controls. Do not expose an
unauthenticated network shell to widgets or other apps. Treat SELinux and platform
permission denials as unsupported capabilities, not reasons to claim success.
[Shizuku API](https://github.com/RikkaApps/Shizuku-API).

On the KL4, plan USB activation because the user reports no Wireless debugging
menu. After a reboot, the ADB-backed service needs starting again. OEM process
killing may also interrupt it. The core home/files/native tools should remain
usable without that service; administrative actions show their unavailable state.
A boot receiver cannot regain ADB privileges by itself. This is a recurring
activation requirement, not a one-time installation promise.
[Shizuku startup and lifecycle documentation](https://shizuku.rikka.app/guide/setup/).

## Phone-wide presentation and Android component removal

Build the common interface first. A notification center can consume permitted
NotificationListener events, but that alone does not replace the system shade.
System resource overlays can only change resources permitted by the target's
overlay policy, and cannot replace arbitrary behavior. Secure lock-screen
authentication remains Android's responsibility. These are explicit gaps in the
original complete-phone-replacement goal, not finished features.
[Notification access](https://developer.android.com/reference/android/service/notification/NotificationListenerService),
[resource overlays](https://source.android.com/docs/core/runtime/rros),
[keyguard APIs](https://developer.android.com/reference/android/app/KeyguardManager).

For unwanted packages, first provide the replacement, identify dependencies, and
snapshot current per-user state. Prefer reversible, explicitly reviewed per-user
disable operations when the shell service allows them. Disabling a preinstalled
package does not remove its APK from the system partition or reclaim that
partition's occupied space. Preserve essential phone, networking, permission,
installer, framework and recovery functions. No blanket stripping on download.

## First proof before a broad refactor

1. Build a separately identified integration APK; keep installed Omalauncher
   intact. Use an upstream Termux runtime with its explicit command permission.
2. Install a native package through the runtime's package manager, execute it
   from the integration app, and receive its actual output and exit status.
   Record its real UID/kernel and persistent file output. Use no PRoot/VM/root.
3. Exercise a native process exchanging a file with an Android component. Stop
   and restart the runtime and verify that installed packages/files persist.
4. Add a USB-started Shizuku UserService and demonstrate an actual read-only
   management call, reporting the UID and supported permissions.
5. Verify core runtime/UI behavior without the administration service and then
   its USB reactivation. Test native service supervision under Android lifecycle
   restrictions; do not equate one successful foreground job with daemon support.
6. Only then migrate the upstream PRoot-dependent runtime and begin qualifying
   notification, settings and other system surfaces against the KL4 build.

The companion implements command/result integration, package actions, config
editing, file-provider entry, typed Shizuku controls, home, notification-center
and optional floating-panel surfaces. Its native command/argument/configuration
tests run on the host. None of the new APK/runtime tests has run on the phone;
ADB had no device connected during implementation. The earlier root runtime's
host tests do not establish this architecture's operation on the KL4. No phone
setup or bootloader action was performed during companion development.

This architecture makes substantial native functionality possible within the stated
installation constraints. It still does not deliver host root, arbitrary kernel
changes, or guaranteed replacement of every protected Android surface. Those
original requirements remain unsatisfied on the current phone.
