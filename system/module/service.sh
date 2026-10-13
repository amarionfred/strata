#!/system/bin/sh
OMA_MODULE=${0%/*}
OMA_WAIT=0
while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$OMA_WAIT" -lt 120 ]; do
    sleep 1
    OMA_WAIT=$((OMA_WAIT + 1))
done
if [ "$(getprop sys.boot_completed)" = 1 ] && [ -f /data/adb/oma-system/autostart ]; then
    "$OMA_MODULE/omactl" start
fi
