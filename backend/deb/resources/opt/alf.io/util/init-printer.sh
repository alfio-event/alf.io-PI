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
        nohup @ALFIO_UTILS_DIR@/printers/ZD410.sh "$1" "Alfio-$2-$device_name" &
        ;;
    "BXL")
        nohup @ALFIO_UTILS_DIR@/printers/TX220.sh "$1" "Alfio-$2-$device_name" &
        ;;
    "DYM")
        nohup @ALFIO_UTILS_DIR@/printers/LW450T.sh "$1" "Alfio-$2-$device_name" &
        ;;
esac