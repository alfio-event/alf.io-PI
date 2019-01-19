#!/usr/bin/env bash

ALFIO_RELEASE="@ALFIO_VERSION@"
ALFIO_VERSION="@ALFIO_VERSION@-@ALFIO_BUILDNUM@"

set -e

echo "    _    _  __  _       "
echo "   / \  | |/ _|(_) ___  "
echo "  / _ \ | | |_ | |/ _ \ "
echo " / ___ \| |  _|| | (_) |"
echo "/_/   \_\_|_|(_)_|\___/ "

echo
echo
echo "This script installs Alf.io-PI on a brand-new Raspberry-PI"
echo "Some steps require root privileges, so you will be prompted for your Password"
echo
echo
echo "Setting keyboard layout to en_US"
sudo sed -i 's|XKBLAYOUT=....|XKBLAYOUT="'us'"|g' /etc/default/keyboard
echo "done."
echo
echo "Updating repos data"
sudo apt-get update
echo "done."
echo
echo "Installing dependencies"
sudo apt-get install nginx cups chromium-browser printer-driver-dymo openjdk-9-jdk unclutter wget
echo "done."
echo
echo "Updating default Java(tm) installation"
sudo update-java-alternatives --set java-1.9.0-openjdk-armhf
echo "done."
echo
echo "Downloading Alf.io-PI v$ALFIO_VERSION"
rm -f "/tmp/alf.io-pi_${ALFIO_VERSION}_all.deb"
wget "https://github.com/alfio-event/alf.io-PI/releases/download/v${ALFIO_RELEASE}/alf.io-pi_${ALFIO_VERSION}_all.deb" -P /tmp/
echo "done."
echo
echo "Installing Alf.io-PI v$ALFIO_VERSION"
sudo dpkg -i "/tmp/alf.io-pi_${ALFIO_VERSION}_all.deb"
echo "done."
echo
if grep -q lcd_rotate /boot/config.txt; then
    echo "Screen rotation OK"
else
    echo "Set screen rotation"
    sudo echo "lcd_rotate=2" >> /boot/config.txt
    echo "done."
fi
echo
echo "Congratulations! Alf.io-PI has been successfully installed!"
echo
echo "Now it's time to edit the configuration."
nano /home/pi/alfio.properties
echo "Configuration complete."
echo
echo "Please run the following command to restart your PI:"
echo "      sudo reboot"
