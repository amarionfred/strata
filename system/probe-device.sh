#!/system/bin/sh
# Read-only capability inventory. Run: adb shell sh < system/probe-device.sh
# Does not invoke su, enable debugging, modify settings, mount, or install anything.

if ! command -v getprop >/dev/null 2>&1; then
    printf '%s\n' 'Run this script in an Android shell (for example through adb).' >&2
    exit 2
fi

report_property() {
    printf '%s=%s\n' "$1" "$(getprop "$2")"
}

printf '%s\n' 'report_version=2'
report_property manufacturer ro.product.manufacturer
report_property model ro.product.model
report_property market_name ro.product.marketname
report_property device ro.product.device
report_property android_release ro.build.version.release
report_property android_sdk ro.build.version.sdk
report_property security_patch ro.build.version.security_patch
report_property vendor_security_patch ro.vendor.build.security_patch
report_property low_ram ro.config.low_ram
report_property build_id ro.build.id
report_property build_incremental ro.build.version.incremental
report_property build_display ro.build.display.id
report_property build_fingerprint ro.build.fingerprint
report_property cpu_abis ro.product.cpu.abilist
report_property cpu_abis_64 ro.product.cpu.abilist64
report_property treble_enabled ro.treble.enabled
report_property flash_locked ro.boot.flash.locked
report_property vbmeta_device_state ro.boot.vbmeta.device_state
report_property verified_boot_state ro.boot.verifiedbootstate
report_property vbmeta_digest ro.boot.vbmeta.digest
report_property vbmeta_digest_algorithm ro.boot.vbmeta.hash_alg
report_property vbmeta_total_size ro.boot.vbmeta.size
report_property oem_unlock_supported ro.oem_unlock_supported
report_property oem_unlock_allowed sys.oem_unlock_allowed
report_property usb_config sys.usb.config
report_property usb_state sys.usb.state
report_property slot_suffix ro.boot.slot_suffix
report_property ab_updates ro.build.ab_update
report_property dynamic_partitions ro.boot.dynamic_partitions
# Empty properties are unknown, not evidence of unsupported functionality.
printf 'kernel=%s\n' "$(uname -r)"
printf 'machine=%s\n' "$(uname -m)"
printf 'shell_uid=%s\n' "$(id -u)"

if [ "$(id -u)" = 0 ]; then
    printf '%s\n' 'root_access=current_shell_uid_0'
else
    printf '%s\n' 'root_access=not_tested'
fi
if command -v su >/dev/null 2>&1; then
    printf '%s\n' 'su_command=visible_not_executed'
else
    printf '%s\n' 'su_command=not_visible_to_this_shell'
fi
if command -v getenforce >/dev/null 2>&1; then
    printf 'selinux=%s\n' "$(getenforce 2>/dev/null)"
else
    printf '%s\n' 'selinux=unknown'
fi

# Presence means observable, not that the caller may create/use a namespace.
for namespace_name in mnt pid net user cgroup; do
    if [ -e "/proc/self/ns/$namespace_name" ]; then
        printf 'namespace_%s=visible_permission_not_tested\n' "$namespace_name"
    else
        printf 'namespace_%s=not_visible\n' "$namespace_name"
    fi
done

printf '%s\n' 'filesystems_begin'
if [ -r /proc/filesystems ]; then
    cat /proc/filesystems
else
    printf '%s\n' 'not_readable'
fi
printf '%s\n' 'filesystems_end' 'support_verdict=unqualified_requires_device_testing'
printf '%s\n' 'memory_begin'
if [ -r /proc/meminfo ]; then
    sed -n '/^MemTotal:/p; /^MemAvailable:/p' /proc/meminfo
fi
printf '%s\n' 'memory_end' 'data_partition_space_begin'
df -k /data 2>/dev/null || printf '%s\n' 'not_readable'
printf '%s\n' 'data_partition_space_end'
# /data capacity is smaller than advertised phone storage; do not equate them.

printf '%s\n' 'battery_begin'
dumpsys battery 2>/dev/null | sed -n '/powered:/p; /status:/p; /level:/p'
printf '%s\n' 'battery_end'

printf '%s\n' 'boot_partition_links_begin'
for partition_name in boot init_boot vendor_boot; do
    for partition_suffix in '' _a _b; do
        partition_path="/dev/block/by-name/$partition_name$partition_suffix"
        if [ -L "$partition_path" ]; then
            ls -l "$partition_path"
        fi
    done
done
printf '%s\n' 'boot_partition_links_end' 'systemui_begin'
pm path com.android.systemui 2>/dev/null
dumpsys package com.android.systemui 2>/dev/null | sed -n '/versionCode=/p; /versionName=/p'
cmd overlay list com.android.systemui 2>/dev/null
printf '%s\n' 'systemui_end'
