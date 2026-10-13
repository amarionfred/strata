# TECNO KL4: device inventory, not support certification

Read-only inspection over authorized USB debugging on 2026-10-13 established:

| Item | Observed value |
| --- | --- |
| Device / market name | TECNO KL4 / TECNO POP 9 |
| Android | 14, API 34, low-RAM configuration |
| Build | `KL4-F201RSTYK-UGo-OP-S2-260414V4446` |
| Fingerprint | `TECNO/KL4-OP-S2/TECNO-KL4:14/UP1A.231005.007/260414V4446:user/release-keys` |
| Android / vendor security patch | 2026-05-01 / 2025-09-05 |
| Android ABIs | arm64-v8a, armeabi-v7a, armeabi |
| Kernel | `5.15.180-android13-8-gb70ce4a70964-ab489`, aarch64 |
| Memory reported by kernel | 2,855,472 KiB (about 2.72 GiB usable physical memory) |
| `/data` size / free space | 52,278,272 / 6,780,192 KiB (free space varies) |
| Root | ADB UID 2000; `su -c id` returned inaccessible or not found |
| Verified boot | locked, green; SELinux enforcing |
| Layout | A/B, dynamic partitions, active slot `_a`; `init_boot_a` and `init_boot_b` present |
| SystemUI | Transsion `TranSystemUI.apk`, version 14.0.0.151 (140000151) |

SystemUI SHA256:
`6e5a20885206a78594fea8e3043955bc84b65fbbca78d369b6c6a715984277b5`.
The observed enabled overlays are `com.android.systemui.os.overlay` and
`android.overlay.systemui.go`. Their presence does not authorize arbitrary
resource replacements or establish which private SystemUI interfaces work.

The structured inventory is [tecno-kl4.json](tecno-kl4.json). The raw probe
output is kept locally in `.local/device-reports/tecno-kl4-2026-10-13.txt`, without
serial numbers, account data, or application contents. Reproduce the inventory
with `adb shell sh < system/probe-device.sh`; separately, `su -c id` was attempted
only to check privilege availability and did not execute because su was absent.

## Deployment decision

The accepted baseline now combines native Termux commands/packages, Shizuku
administration, and a shared interface in the separately identified
[companion APK](../companion/README.md). It does not need root or a different
filesystem. The phone was absent from ADB during implementation, so this APK,
its dependency setup and its phone-wide panels have not been tested on the KL4.
The root-only deployment decision below applies to the optional Magisk/chroot
module, not to native Termux execution.

The user reaffirmed **no flashing, no bootloader unlocking, and no factory
reset**. The bootloader/rooting preparation route is cancelled, not pending a
battery charge. No verified way to obtain real root within those constraints
has been established for this build. Source development and Android integration
can continue within existing permissions; native-root deployment cannot.

The **arm64** runtime package matches the reported architecture. This confirms
the architecture choice only: native namespace creation, Magisk execution,
SELinux behavior, service startup, SystemUI integration, and rollback remain
untested on this phone. USB debugging does not satisfy the real-root requirement.
No runtime/module was installed, no package was disabled, and no reboot,
unlocking, flashing, or partition write was performed. The user enabled USB
debugging and OEM unlocking in Settings; the diagnostics did not change settings.

At the initial inventory, `sys.oem_unlock_allowed` was `0` and
`ro.oem_unlock_supported` was unset. The user subsequently corrected the switch
state to **enabled**, and a follow-up ADB query confirmed
`sys.oem_unlock_allowed=1`. The bootloader still reported locked, with green
verified boot. The switch permits a later unlock; it does not establish an
unlocked bootloader.
Read-only policy
inspection found user 0 is an administrator, device setup is complete, no current
user restrictions are effective, no profile owner is present, and Android reports
the device is not managed. Guest-user defaults are not restrictions on the
current user. These checks do not identify the manufacturer/carrier gate or
prove whether this bootloader can be unlocked. Bootloader protocol testing is
no longer a planned next step under the user's constraints.

## USB recovery and cancelled bootloader preparation

The host kernel log recorded repeated TECNO enumeration/disconnect events and
USB protocol errors (`-71`), including descriptor-read failures and inability
to accept an address. USB debugging authorization itself had succeeded. The
connection returned after restarting the host ADB server and USB re-enumeration;
the exact cause of the intermittent connection has not been isolated. A wireless
debugging menu is not available according to the user, so use USB.

The follow-up battery readings were 4%, then 5%, with charging active. No
bootloader reboot was attempted. The user then reaffirmed the no-flashing
constraint, so do not resume this route after charging.
`../probe-bootloader.py` has not run against this phone and is retained only as
a diagnostic utility, not as a pending TECNO installation step.

The current verified-boot digest reported by the phone is SHA256
`2b3d07f9c150fdd31eedc694235cd2c9f1a9d5310d638d84dbb81a20a6c8ba33`,
with a reported VBMeta total size of 51,008 bytes. This is **not** the checksum
of `init_boot.img`. The stock `init_boot_a` block device is root-readable only;
no stock image was extracted. Firmware acquisition for a flash-based root
installation is outside the accepted scope. No backup of the user's data or
stock partition contents has been made here.

[AOSP specifies a factory reset during a standard bootloader unlock](https://source.android.com/docs/core/architecture/bootloader/locking_unlocking).
[Magisk requires an unlocked bootloader and the appropriate original image](https://topjohnwu.github.io/Magisk/install.html);
those installation steps are excluded by the user's constraint. Installing a
module on a phone with existing root is distinct from installing Magisk itself
on an unrooted phone. The current runtime only implements the former.

The full-phone interface and Android/native integration can continue in source
while these device prerequisites are unresolved. The current module is still a
native runtime preview, not the completed system requested by the user.
