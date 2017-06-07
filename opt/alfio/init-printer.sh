#!/bin/bash
device_name=$(/opt/alfio/device-name.sh $1)
/usr/sbin/lpadmin -E -p "Alfio-${device_name}" -v "usb://DYMO/LabelWriter%20450%20Turbo?serial=$1" -P /usr/share/cups/model/lw450t.ppd -o printer-error-policy=abort-job -o printer-is-shared=false
echo ${device_name}