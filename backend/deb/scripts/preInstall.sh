#!/bin/bash
echo "Backing up existing files..."
cp /home/pi/.config/lxsession/LXDE-pi/autostart /home/pi/.config/lxsession/LXDE-pi/autostart.alfio-backup 2>/dev/null || :
mkdir -p /opt/alf.io/app
chown -R pi:pi /opt/alf.io/app
echo "done."