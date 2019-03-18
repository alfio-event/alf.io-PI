#!/bin/bash
#
# usage:
# remove-printer.sh {serialId} {prefix}
#
# where:
# {serialId}: serial id of the device
# {prefix}: device name prefix (e.g. DYM for Dymo printers)
#
device_name=$(@ALFIO_UTILS_DIR@/device-name.sh $1)
systemd-run --uid=pi --no-block --on-active=10 /usr/sbin/lpadmin -x "Alfio-$2-${device_name}"