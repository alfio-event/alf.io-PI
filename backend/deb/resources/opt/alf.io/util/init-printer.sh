#!/bin/bash
#
# usage:
# init-printer.sh {prefix}-{serialId}
#
# where:
# {prefix}: device name prefix (e.g. DYM)
# {serialId}: serial id of the device
#
serialId=$(echo "$1" | cut -c 1-4 --complement)
device_name=$(@ALFIO_UTILS_DIR@/device-name.sh $serialId)
prefix=$(echo "$1" | cut -c 1-3)

case "$prefix" in
    "ZBR")
        @ALFIO_UTILS_DIR@/printers/ZD410.sh "$serialId" "Alfio-$prefix-$device_name"
        ;;
    "BXL")
        @ALFIO_UTILS_DIR@/printers/TX220.sh "$serialId" "Alfio-$prefix-$device_name"
        ;;
    "DYM")
        @ALFIO_UTILS_DIR@/printers/LW450T.sh "$serialId" "Alfio-$prefix-$device_name"
        ;;
esac