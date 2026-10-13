#!/system/bin/sh
OMA_MODULE=${0%/*}
"$OMA_MODULE/omactl" stop || true
# Keep /data/adb/oma-system: removing the module must not delete Linux user files.
