#!/bin/bash
device_name=$(@ALFIO_UTILS_DIR@/device-name.sh $1)

if [[ "$2" == "ZD410" ]]; then
    nohup @ALFIO_UTILS_DIR@/printers/ZD410.sh "$device_name" "$1" &
elif [[ "$2" == "TX220" ]]; then
    nohup @ALFIO_UTILS_DIR@/printers/TX220.sh "$device_name" "$1" &
else
    nohup @ALFIO_UTILS_DIR@/printers/LW450T.sh "$device_name" "$1" &
fi