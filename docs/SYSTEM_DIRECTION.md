# Native Android/Linux system direction

This document records the intended product, not a claim that it is implemented.
The checkout is based on Omalauncher commit
`4135e6efadb6aa14936247d6352de1cb89ea9a1a`.

The user accepted combining Android administration and a shared phone interface,
while retaining native terminal commands and package installation. The
[no-flashing architecture](NO_FLASH_ARCHITECTURE.md) is the current baseline.
The first [companion APK](../system/companion/README.md) implements this path and
awaits device validation. Simple persistent native files are sufficient. Host
root, kernel administration and full SystemUI replacement remain explicit gaps.
The root-based module is retained only for already-rooted devices.

Preview 0.2 shares the launcher's 22 Omarchy palettes and color code, applies
native Termux colors and portable Bash/Starship/tmux defaults, and accepts custom
`colors.toml` imports. See the [installation guide](../system/companion/INSTALL.md).

## Product requirements

- Keep Android application compatibility and avoid replacing the existing
  Android installation as the normal installation method.
- Do not flash the user's phone, unlock its bootloader, or factory-reset it.
  Requiring root for the full version does not authorize installing root by
  those methods. The user explicitly reaffirmed this constraint on 2026-10-13.
- Own the phone's everyday interface: home, app switching, notifications,
  quick settings, lock screen, settings, files, terminal, and software management.
- Provide persistent files/configuration, native programs and real package
  installation. The accepted baseline uses ordinary native app permissions and
  authorized ADB/Shizuku administration. Root and kernel powers remain optional
  capabilities for devices that already have them, not simulated command names.
- Integrate Android and Linux processes through deliberate interfaces so they
  feel like one system.
- Use no virtual machines. PRoot's simulated root does not satisfy the full
  system's privilege requirement.
- Reach many Android models. Do not describe an untested device or an unsupported
  system interface as compatible.

## Accepted implementation direction

Use the device's existing Linux kernel for both Android and the Linux userspace.
Keep Android's app runtime, Binder, package and activity management, graphics,
hardware services, and vendor drivers. APIs alone are insufficient to run the
installed Android applications.

The baseline companion uses Termux's native execution and package ecosystem via
its supported command/result API. Interactive shells open Termux sessions;
the companion supplies its own command jobs, configuration editor, package UI,
home, notification center and floating navigation. Android management uses typed
Shizuku UserService calls with actual identity/permission checks. It installs
alongside the current launcher and makes no system changes just by installation.
It does not require a VM, PRoot, a guest distro, or root to execute native tools.

The optional rooted backend manages a real Linux rootfs and starts native processes
using chroot and whichever namespace facilities the actual kernel supports.
The rootfs is a directory on a Linux filesystem, or a separately mounted image
when the kernel and policy permit it. A chroot is a filesystem view, not a
separate kernel or a security boundary by itself. Filesystem ownership, mounts,
privilege changes, and process lifetimes must be real and observable.

Keep Android init responsible for the phone. Supervise Linux services separately;
do not promise an unmodified desktop boot sequence or systemd until its required
kernel, namespace, cgroup, and SELinux behavior has been verified. Device root
does not create kernel features or drivers that the phone lacks.

Use Omalauncher's UI and integration code where useful, but build the full system
as additional components:

| Component | Responsibility |
| --- | --- |
| Home and applications | Common app discovery, launches, app switching, and visual design |
| Shared interface | Home, files, commands, software, notification center and optional floating access; protected UI integration requires separate qualification |
| Android administration | Authorized typed Shizuku controls with actual UID and result checks |
| Native userspace | Real Termux packages/processes and persistent files; interactive sessions initially use Termux itself |
| Optional root backend | Native chroot, users, root operations and service supervision on already-rooted phones |
| Android integration | Explicit file sharing, clipboard, audio, input, notifications, and window integration |
| Device profile | Supported Android build, resource identifiers, privileges, kernel facilities, and validation results |

System resource overlays can change permitted resources. They cannot replace
arbitrary code or universally restyle apps that draw their own interfaces.
Substantial SystemUI changes require implementation against the relevant Android
and vendor version, including its private interfaces and signing/policy rules.

## Installation and recovery behavior

The original full-root/kernel-control requirement needs existing usable root or platform-level integration.
A normal installation on a locked, unrooted phone cannot acquire those powers.
The user accepted root for that version, while prohibiting flashing, bootloader
unlocking, and factory resets, and subsequently accepted the no-root baseline
above. The optional native module therefore
targets phones with existing usable root. It does not supply that root.

On supported rooted phones, investigate a systemless module that supplies files
and starts the companion while retaining the stock installation. Systemless
installation does not make arbitrary system replacements compatible or make
root setup universal. Boot-image patching/flashing and bootloader unlocking are
outside the accepted installation path. The TECNO rooting/bootloader preparation
route has been cancelled; do not resume it when the battery is charged or USB
returns. No compatible method to obtain real root without flashing has been
verified for this phone. ADB's shell permissions do not satisfy the requirement.

The requested removal of unwanted Android components must use a device-specific
package/dependency plan with a recoverable previous state. Replace working
functionality before disabling the component that currently supplies it. An
unknown package is not evidence that it is unnecessary. A generic package
deletion list cannot preserve app compatibility across all manufacturers.

## Current integration milestone

1. Build the separate companion APK and check its command and configuration boundaries.
2. On the KL4, initialize Termux and verify a real command result, package install,
   interactive session, shared file and persistent native configuration.
3. Activate Shizuku over USB and test permitted Android controls and readback.
4. Test home, notification-center and floating-panel behavior, permission revocation,
   lock/unlock and background lifecycle on the real device.
5. Introduce a runtime abstraction before migrating the upstream launcher off
   PRoot; qualify native service supervision separately.
6. Implement further protected system surfaces only through supported device
   interfaces. Add device profiles and evidence before claiming support for friends.

Read-only ADB inspection on 2026-10-13 identified the connected phone as
TECNO POP 9 / KL4, Android 14 (API 34), arm64, build
`KL4-F201RSTYK-UGo-OP-S2-260414V4446`. The bootloader reports locked with green
verified boot; `su -c id` reports that su is inaccessible or not found. Native
root-module deployment is blocked on usable root under the no-flashing constraint.
The accepted native Termux/Shizuku companion can be tested without rooting. No
device was available through ADB during its implementation, so actual runtime
integration, UI behavior and OEM compatibility remain unverified.
No phone has been rooted, modified, or stripped by this work.
`system/probe-device.sh` is a read-only starting point. It does not
grant root and does not prove that a native runtime or system replacement works.
The implemented native runtime and its host test procedure are documented in
`system/README.md`. The exact inventory and remaining device qualification are
recorded in `system/devices/tecno-kl4.json` and `system/devices/tecno-kl4.md`.
The user requires a minimum total phone storage capacity of 64 GB.

## Primary references

- [AOSP architecture](https://source.android.com/docs/core/architecture)
- [Android application sandbox](https://source.android.com/docs/security/app-sandbox)
- [SystemUI source overview](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/README.md)
- [Runtime resource overlays and their restrictions](https://source.android.com/docs/core/runtime/rros)
- [Magisk module structure and boot services](https://topjohnwu.github.io/Magisk/guides.html)
- [Bootloader locking and unlocking](https://source.android.com/docs/core/architecture/bootloader/locking_unlocking)
