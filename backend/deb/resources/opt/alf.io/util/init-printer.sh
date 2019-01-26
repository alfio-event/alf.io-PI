#!/bin/bash
device_name=$(@ALFIO_UTILS_DIR@/device-name.sh $1)

if [[ "$2" == "ZD410" ]]; then
    nohup @ALFIO_UTILS_DIR@/printers/ZD410.sh "$1" "$device_name" &
elif [[ "$2" == "TX220" ]]; then
    nohup @ALFIO_UTILS_DIR@/printers/TX220.sh "$1" "$device_name" &
else
    nohup @ALFIO_UTILS_DIR@/printers/LW450T.sh "$1" "$device_name" &
fi