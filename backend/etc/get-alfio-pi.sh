#!/usr/bin/env bash

ALFIO_RELEASE="@ALFIO_VERSION@"
ALFIO_VERSION="@ALFIO_VERSION@-@ALFIO_BUILDNUM@"
CONFIG_TEMPLATE_PATH="@ALFIO_CONFIG_DIR@/application.properties.sample"
CONFIG_FILE_PATH="@ALFIO_CONFIG_DIR@/application.properties"

bold=$(tput bold)
reset=$(tput sgr0)

set -e

function print_bold() {
    echo "${bold}$1${reset}"
}

print_bold "    _    _  __  _       "
print_bold "   / \  | |/ _|(_) ___  "
print_bold "  / _ \ | | |_ | |/ _ \ "
print_bold " / ___ \| |  _|| | (_) |"
print_bold "/_/   \_\_|_|(_)_|\___/ "

echo
echo
echo "This script installs Alf.io-PI on a brand-new Raspberry-PI"
echo "Some steps require root privileges, so you may be prompted for your Password"
echo
echo
print_bold "Setting keyboard layout to en_US"
sudo raspi-config nonint do_change_locale en_US.UTF-8
sudo raspi-config nonint do_configure_keyboard us
print_bold "done."
echo

print_bold "Setting Timezone to UTC"
sudo raspi-config nonint do_change_timezone 'Etc/UTC'
print_bold "done."
echo

print_bold "Updating repos data"
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com --recv-keys 32E9750179FCEA62
echo "deb [arch=armhf] https://apt.bell-sw.com/ stable main" | sudo tee /etc/apt/sources.list.d/bellsoft.list
sudo apt-get update -qq
sudo apt-get upgrade --assume-yes
print_bold "done."
echo

print_bold "Installing dependencies"
sudo apt-get install --assume-yes bellsoft-java17-lite nginx cups cups-client cups-bsd printer-driver-dymo wget dirmngr software-properties-common xserver-xorg-video-all xserver-xorg-input-all xserver-xorg-core xinit x11-xserver-utils chromium-browser unclutter-xfixes
sudo usermod -a -G lpadmin pi
sudo raspi-config nonint do_wayland W1
print_bold "done."
echo



print_bold "Importing Alf.io-PI key"
sudo gpg --keyserver keyserver.ubuntu.com --recv-key 0x682497B470AC18A3
print_bold "done."

print_bold "Downloading Alf.io-PI v$ALFIO_VERSION"
rm -f "/tmp/alf.io-pi_${ALFIO_VERSION}_all.deb"
wget "https://github.com/alfio-event/alf.io-PI/releases/download/v${ALFIO_RELEASE}/alf.io-pi_${ALFIO_VERSION}_all.deb" -P /tmp/
print_bold "done."

print_bold "Installing Alf.io-PI v$ALFIO_VERSION"
sudo dpkg -i "/tmp/alf.io-pi_${ALFIO_VERSION}_all.deb"
sudo rm -f /etc/nginx/sites-enabled/default
print_bold "done."
echo

if sudo grep -q lcd_rotate /boot/config.txt; then
    print_bold "Screen rotation OK"
else
    print_bold "Please fix screen rotation by adding the following line"
    echo "   lcd_rotate=2"
    print_bold "to /boot/config.txt"
fi
echo

sudo dphys-swapfile swapoff && sudo dphys-swapfile uninstall && sudo systemctl disable dphys-swapfile.service

print_bold "Congratulations! Alf.io-PI has been successfully installed!"
echo
print_bold "Now it's time to edit the configuration..."

sleep 2

# backing up existing file, if any
touch ${CONFIG_FILE_PATH} && mv ${CONFIG_FILE_PATH} "${CONFIG_FILE_PATH}.$(date '+%Y-%m-%dT%H%M%S')"
cp ${CONFIG_TEMPLATE_PATH} ${CONFIG_FILE_PATH}
NOW=$(date '+%Y-%m-%d %H:%M:%S')
echo ""  >> ${CONFIG_FILE_PATH}
echo "# Edited by get-alfio-pi.sh on ${NOW}" >> ${CONFIG_FILE_PATH}

master_url=$(whiptail --inputbox "Enter the Alf.io instance URL" 10 50 --ok-button Save --nocancel "https://" 3>&1 1>&2 2>&3)
echo "master.url=${master_url}" >> ${CONFIG_FILE_PATH}

api_key=$(whiptail --inputbox "Enter the API Key" 10 50 --ok-button Save --nocancel 3>&1 1>&2 2>&3)
echo "master.apiKey=${api_key}" >> ${CONFIG_FILE_PATH}

print_bold "Configuration complete."
echo
print_bold "Please run the following command to restart your PI:"
print_bold "      sudo reboot"