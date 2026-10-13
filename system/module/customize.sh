# Sourced by Magisk's installer, with its BusyBox standalone shell.
[ "$BOOTMODE" = true ] || abort "Install from the running Magisk app."
[ "$API" -ge 29 ] || abort "This developer preview requires Android 10 or newer."
case "$(uname -m)" in
    aarch64|arm64) OMA_KERNEL_ARCH=arm64 ;;
    armv7*|armv8l) OMA_KERNEL_ARCH=arm ;;
    x86_64) OMA_KERNEL_ARCH=x64 ;;
    i?86) OMA_KERNEL_ARCH=x86 ;;
    *) abort "The native runtime has no build for this kernel architecture." ;;
esac
OMA_IMAGE_ARCH=$(cat "$MODPATH/payload/arch")
[ "$OMA_KERNEL_ARCH" = "$OMA_IMAGE_ARCH" ] || abort "Use the $OMA_KERNEL_ARCH module, not $OMA_IMAGE_ARCH."

OMA_DATA=/data/adb/oma-system
[ ! -L "$OMA_DATA" ] || abort "The runtime data directory must not be a symlink."
mkdir -p "$OMA_DATA" || abort "Cannot create runtime data directory."
chmod 700 "$OMA_DATA"
[ ! -L "$OMA_DATA/rootfs" ] || abort "The Linux root must not be a symlink."

if [ ! -d "$OMA_DATA/rootfs" ]; then
    OMA_BYTES=$(cat "$MODPATH/payload/unpacked-bytes")
    OMA_REQUIRED_KB=$((OMA_BYTES / 1024 + 524288))
    OMA_FREE_KB=$(df -Pk "$OMA_DATA" | awk 'END {print $4}')
    [ -n "$OMA_FREE_KB" ] && [ "$OMA_FREE_KB" -ge "$OMA_REQUIRED_KB" ] || abort "Not enough free space for the rootfs and 512 MiB reserve."
    (cd "$MODPATH/payload" && sha256sum -c rootfs.sha256) || abort "Root filesystem checksum failed."
    OMA_STAGE="$OMA_DATA/install.$$"
    mkdir -m 700 "$OMA_STAGE" || abort "Cannot create a fresh installation directory."
    if ! tar -xpf "$MODPATH/payload/rootfs.tar.gz" -C "$OMA_STAGE"; then
        rm -rf "$OMA_STAGE"
        abort "Root filesystem extraction failed."
    fi
    chmod 700 "$OMA_STAGE"
    mv "$OMA_STAGE" "$OMA_DATA/rootfs" || abort "Cannot activate the new root filesystem."
    printf '%s\n' "$OMA_IMAGE_ARCH" > "$OMA_DATA/arch"
    touch "$OMA_DATA/autostart"
else
    [ "$(cat "$OMA_DATA/arch" 2>/dev/null)" = "$OMA_IMAGE_ARCH" ] || abort "Existing Linux architecture differs; preserving its files."
    ui_print "- Keeping the existing Linux files and packages"
fi

# These are runtime-owned service plumbing. The user's service definitions stay.
mkdir -p "$OMA_DATA/rootfs/usr/local/libexec" "$OMA_DATA/rootfs/etc/oma-system/services"
cp "$MODPATH/guest/oma-services" "$OMA_DATA/rootfs/usr/local/libexec/oma-services.new"
chmod 755 "$OMA_DATA/rootfs/usr/local/libexec/oma-services.new"
mv "$OMA_DATA/rootfs/usr/local/libexec/oma-services.new" "$OMA_DATA/rootfs/usr/local/libexec/oma-services"
if [ ! -e "$OMA_DATA/rootfs/etc/resolv.conf" ] && [ ! -L "$OMA_DATA/rootfs/etc/resolv.conf" ]; then
    printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$OMA_DATA/rootfs/etc/resolv.conf"
fi
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/bin/oma-session" 0 0 0700
for OMA_SCRIPT in omactl service.sh uninstall.sh; do
    set_perm "$MODPATH/$OMA_SCRIPT" 0 0 0700
done
ui_print "- Native runtime installed; reboot to start it"
ui_print "- Root control: $MODPATH/omactl"
ui_print "- Developer preview: the phone's SystemUI is not replaced"
