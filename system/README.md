# Strata native Linux runtime

The intended product is a complete phone interface with Android apps and native
Linux tools. This component implements the native runtime foundation. It is not
yet a complete phone OS or a validated Tecno SystemUI replacement.

The Strata app now controls this module from its System, Terminal, Software, and
Files tabs. [Install](../README.md#root-and-linux).

This optional module requires root. It keeps the existing Android kernel and app
framework. There is no VM, CPU emulation, PRoot, or fake UID in this runtime.

The user requires installation **without flashing, bootloader unlocking, or a
factory reset**. This module needs root already available through a supported
root manager; it does not root a phone. The connected TECNO is unrooted, so this
runtime cannot currently be deployed there within the accepted constraints.
The earlier bootloader/rooting preparation route has been cancelled.

The accepted [no-flashing architecture](../docs/NO_FLASH_ARCHITECTURE.md) uses
a separate native app runtime and an ADB/Shizuku administration service. Its
first APK is implemented in [companion/](companion/README.md), with device
validation pending. The component below is the optional root-based runtime;
root is not required for the companion's native Termux commands/packages.

## What is implemented

`native/session.c` starts Linux in real mount, PID, and hostname namespaces,
then enters a persistent root filesystem with `chroot`. Linux and Android use
the same kernel. The helper runs as actual UID 0; the kernel performs permission
checks, mounts, execution, and process cleanup. Network access uses the existing
network namespace. The runtime does not supply missing kernel features.

The supervisor reaps child processes, forwards termination, and tears down its
PID namespace on exit. A parent-death signal and pipe close the race where the
supervisor is killed during startup. Mounts are private and sysfs is exposed
read-only. These are lifecycle measures; a root chroot is not a security boundary
against a malicious root user.

The Magisk module installs a checksum-pinned minimal Alpine rootfs as a directory
on `/data`'s actual Linux filesystem. It starts user-defined foreground services,
preserves files across runtime restarts and module updates, and retains the rootfs
when the module is removed. The minimal image is a small bring-up base; packages
are installed with its real `apk` package manager. The original Omalauncher app's
Arch/PRoot backend is not yet connected to this new runtime.

The controller provides:

```text
omactl start / stop / status        manage persistent native services
omactl shell                       enter the running namespace
omactl exec COMMAND [ARG ...]       execute in the running namespace
omactl run COMMAND [ARG ...]        run a separate native session
omactl autostart on / off           control startup after Android boots
```

Root control path: `/data/adb/modules/oma_native_system/omactl`.
Service definitions are executable foreground scripts under
`/etc/oma-system/services` in Linux. Restart the runtime after changing them.
Their logs are under `/var/log/oma-system`; startup errors go to
`/data/adb/oma-system/session.log` on Android. The initial image has no additional
user services enabled. For example, `omactl run /sbin/apk add sudo tree` performs
an actual package installation when the network is available. A fresh Linux
rootfs uses 1.1.1.1 and 8.8.8.8 for DNS; edit Linux's `/etc/resolv.conf` when the
network requires different resolvers. Existing resolver settings are preserved.
Android's private DNS settings are not automatically inherited by Linux tools.

## First target

The first connected device reports TECNO POP 9 / KL4, Android 14 (API 34), and a
64-bit Android userspace/kernel. Read-only inspection found a locked bootloader
and no accessible `su`; the native runtime has **not** been installed or tested
on it. [The device report](devices/tecno-kl4.md) records the exact build and
remaining prerequisites; `devices/tecno-kl4.json` holds the structured inventory.

The user requires at least **64 GB total phone storage**. Do not treat
64 GB as required free space or compare it directly to the smaller `/data`
partition. The preview installer checks its actual image size plus a 512 MiB
free-space reserve; additional packages and the final UI need their own budgets.

Collect a read-only report before considering device installation:

```sh
adb shell sh < system/probe-device.sh
```

`probe-bootloader.py` remains a read-only diagnostic utility prepared earlier;
it has not been run against the phone and is not part of the accepted TECNO
installation workflow. Do not reboot this phone into its bootloader as the next
step. USB debugging and the enabled OEM-unlocking switch do not supply root.

The probe does not invoke `su`. Kernel features, usable root privileges, SELinux
behavior, Android userspace ABI, and the exact SystemUI build require device
verification. Root support does not imply that a device can be rooted by a
universal method. This preview is packaged for Magisk; other root managers have
not been integrated or tested.

## Build

Use Zig 0.17.0 to cross-compile the static helper. Download the matching official
minirootfs into `.local/native/`; `images.lock.json` pins every image checksum.
The builder prints the exact source URL if the image is missing and validates
archive paths before including the image in a module.

```sh
python3 system/build.py --arch arm64 --zig /path/to/zig
python3 system/build.py --arch arm --zig /path/to/zig
```

Artifacts and their SHA256 files are written under `.local/native/dist/`.
These are developer runtime modules, not a finished OS distribution. Their
architecture must match the kernel; the installer rejects a mismatch. No device
installation is performed by the build script.

## Native host verification

Host testing uses a normal Linux user namespace with subordinate UID/GID mappings.
It executes native x86_64 binaries directly on the running kernel, without a VM.
It requires `newuidmap`/`newgidmap`, an allocated `/etc/subuid` and `/etc/subgid`
range, and a kernel permitting unprivileged user namespaces. The helper itself
does not use a user namespace on Android, where the root manager supplies UID 0.

1. Build the host helper: `make -C system/native`.
2. Verify and extract `alpine-minirootfs-3.24.2-x86_64.tar.gz` from the pinned
   source into `.local/native/rootfs`, without preserving host ownership.
3. Configure the fixture's `/etc/resolv.conf` for the test network.
4. Install test packages natively:

   ```sh
   unshare --user --map-auto --map-root-user .local/native/oma-session \
     .local/native/rootfs -- /sbin/apk --no-cache add tree sudo
   ```

5. Run `python3 -m unittest discover -s system/tests -v`.

Tests exercise the shared kernel, actual UID 0 and filesystem mounts, persistent
files, installed packages and sudo, command exit codes, environment isolation,
invalid rootfs rejection, and cleanup after normal termination and SIGKILL.
Host success does not validate a phone's SELinux rules or root-manager behavior.

## Remaining system integration

The app now includes a root command service and a runtime controller bridge.
The complete product still needs unified files and windows, notification shade, lock screen, recents,
settings, installer UX, and tested recovery on each supported Android/vendor
build. Resource overlays can change permitted resources; replacing arbitrary
system behavior requires version-specific platform integration. No stock Android
package-removal list or generic SystemUI swap is included.

See [the accepted system direction](../docs/SYSTEM_DIRECTION.md) for the full
scope and primary platform references.
