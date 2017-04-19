#!/bin/bash
#source: https://en.wikipedia.org/wiki/Base36#bash_implementation
export value=`echo $1 | sum | sed -n 's/\([A-Z0-9a-z]\+\).*/\1/p'`
result=""
base36="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
while true; do
  result=${base36:((value%36)):1}${result}
  if [ $((value=${value}/36)) -eq 0 ]; then
    break
  fi
done
/usr/sbin/lpadmin -p "Alfio-${result}" -v "usb://DYMO/LabelWriter%20450%20Turbo?serial=$1" -P /usr/share/cups/model/lw450t.ppd -o printer-error-policy=abort-job -E
echo ${result}