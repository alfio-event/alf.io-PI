#!/usr/bin/env bash
/usr/sbin/lpadmin -E -p "Alfio-DYM-$2" -v "usb://DYMO/LabelWriter%20450%20Turbo?serial=$1" -P /usr/share/cups/model/lw450t.ppd -o printer-error-policy=abort-job -o printer-is-shared=false
/usr/sbin/cupsenable "Alfio-DYM-$2"
/usr/sbin/cupsaccept "Alfio-DYM-$2"