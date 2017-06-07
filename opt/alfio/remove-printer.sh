#!/bin/bash
device_name=$(/opt/alfio/device-name.sh $1)
/usr/sbin/lpadmin -x "Alfio-${device_name}"