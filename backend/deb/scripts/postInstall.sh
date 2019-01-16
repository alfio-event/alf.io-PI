#!/usr/bin/env bash
echo "iw dev wlan0 set power_save off" | cat - /etc/rc.local > /etc/rc.local.alfio && mv /etc/rc.local.alfio /etc/rc.local
chmod a+x /etc/rc.local
systemctl enable alfio.service