#!/usr/bin/env bash
cat > /etc/rc.local <<EOF
#!/bin/sh -e
iw dev wlan0 set power_save off
exit 0
EOF
chmod a+x /etc/rc.local
systemctl enable alfio.service