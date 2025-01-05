#!/usr/bin/env bash
chown -R pi:pi /home/pi/.config/lxsession
systemctl enable alfio-prerequisite.service
systemctl enable alfio.service
echo 'export CHROMIUM_FLAGS="$CHROMIUM_FLAGS --use-gl=egl"' | sudo tee /etc/chromium.d/egl