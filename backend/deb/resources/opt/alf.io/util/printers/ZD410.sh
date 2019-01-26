#!/usr/bin/env bash
#
# Config script for Zebra ZD410
# usage:
# ZD410.sh {serialId} {name}
#
# where:
# {serialId}: serial id of the device
# {name}: device name
#
/usr/sbin/lpadmin -E -p "$2" -v "usb://Zebra%20Technologies/ZTC%20ZD410-203dpi%20ZPL?serial=$1" -m drv:///sample.drv/zebra.ppd -o printer-error-policy=abort-job -o printer-is-shared=false
su pi -c "/usr/bin/lpoptions -p $2 -o usb-unidir-default=true -o Darkness=30 -o zePrintRate=4"
/usr/sbin/cupsenable "$2"
/usr/sbin/cupsaccept "$2"
/sbin/rmmod usblp