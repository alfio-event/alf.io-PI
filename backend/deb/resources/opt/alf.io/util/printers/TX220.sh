#!/usr/bin/env bash
#
# Config script for Bixolon SLP-TX220
# Please note that you need to install the linux drivers beforehand. Please ask the support for the link.
#
# usage:
# TX220.sh {serialId} {name}
#
# where:
# {serialId}: serial id of the device
# {name}: device name
#
/usr/sbin/lpadmin -E -p "$2" -v "usb://BIXOLON/SLP-TX220?serial=$1" -P /usr/share/cups/model/Bixolon/SLPTX220_v1.1.0.ppd -o printer-error-policy=abort-job -o printer-is-shared=false