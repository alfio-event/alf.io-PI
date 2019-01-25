#!/bin/bash
device_name=$(@ALFIO_UTILS_DIR@/device-name.sh $1)
nohup /usr/sbin/lpadmin -x "Alfio-${device_name}" &