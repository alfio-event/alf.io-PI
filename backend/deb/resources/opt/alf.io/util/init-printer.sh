#!/bin/bash
#
# usage:
# init-printer.sh {serialId} {prefix} {model} {busId} {deviceId} {vendorId} {modelId}
#
# where:
# {serialId}: serial id of the device
# {prefix}: device name prefix (e.g. DYM)
#
device_name=$(@ALFIO_UTILS_DIR@/device-name.sh $1)
systemd-run --uid=pi --no-block --on-active=10 "@ALFIO_UTILS_DIR@/printers/$3.sh" "$1" "Alfio-$2-$device_name" "$4.$5.$6.$7"