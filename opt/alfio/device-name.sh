#!/bin/bash
#source: https://en.wikipedia.org/wiki/Base36#bash_implementation
export value=`echo $1 | sum | sed -n 's/\([A-Z0-9a-z]\+\).*/\1/p' | sed -e 's/^[0]*//'`
result=""
base36="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
while true; do
  result=${base36:((value%36)):1}${result}
  if [ $((value=${value}/36)) -eq 0 ]; then
    break
  fi
done
echo ${result}