#!/bin/bash
echo "Backing up existing files..."
cp /home/pi/.config/lxsession/LXDE-pi/autostart /home/pi/.config/lxsession/LXDE-pi/autostart.alfio-backup 2>/dev/null || :
echo "done."