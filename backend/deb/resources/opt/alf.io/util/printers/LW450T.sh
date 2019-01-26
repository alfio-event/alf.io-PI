#!/usr/bin/env bash
#
# Config script for DYMO Label Writer 450 Turbo
# usage:
# LW450T.sh {serialId} {name}
#
# where:
# {serialId}: serial id of the device
# {name}: device name
#

/usr/sbin/lpadmin -E -p "$2" -v "usb://DYMO/LabelWriter%20450%20Turbo?serial=$1" -P /usr/share/cups/model/lw450t.ppd -o printer-error-policy=abort-job -o printer-is-shared=false
/usr/sbin/cupsenable "$2"
/usr/sbin/cupsaccept "$2"