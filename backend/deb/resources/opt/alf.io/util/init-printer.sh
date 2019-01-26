#!/bin/bash
#
# usage:
# init-printer.sh {serialId} {prefix}
#
# where:
# {serialId}: serial id of the device
# {prefix}: device name prefix (e.g. DYM)
#
device_name=$(@ALFIO_UTILS_DIR@/device-name.sh $1)

case "$2" in
    "ZBR")
        systemd-run --uid=pi --no-block --on-active=10 @ALFIO_UTILS_DIR@/printers/ZD410.sh "$1" "Alfio-$2-$device_name"
        ;;
    "BXL")
        systemd-run --uid=pi --no-block --on-active=10 @ALFIO_UTILS_DIR@/printers/TX220.sh "$1" "Alfio-$2-$device_name"
        ;;
    "DYM")
        systemd-run --uid=pi --no-block --on-active=10 @ALFIO_UTILS_DIR@/printers/LW450T.sh "$1" "Alfio-$2-$device_name"
        ;;
esac