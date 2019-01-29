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
sudo sed -i 's|XKBLAYOUT=....|XKBLAYOUT="'us'"|g' /etc/default/keyboard
print_bold "done."
echo

if [[ "UTC" != "$(date +'%Z')" ]]; then
    print_bold "Setting Timezone to UTC"
    sudo rm -f /etc/localtime
    sudo ln -s /usr/share/zoneinfo/UTC /etc/localtime
    print_bold "done."
    echo
fi

print_bold "Updating repos data"
sudo apt-get update
print_bold "done."
echo

print_bold "Installing dependencies"
sudo apt-get install --assume-yes nginx cups cups-client cups-bsd chromium-browser printer-driver-dymo openjdk-9-jdk unclutter wget dirmngr software-properties-common dpkg-sig
sudo usermod -a -G lpadmin pi
print_bold "done."
echo

print_bold "Updating default Java(tm) installation"
sudo update-java-alternatives --set java-1.9.0-openjdk-armhf
print_bold "done."
echo

print_bold "Downloading DYMO definitions (ppd)"
wget http://download.dymo.com/dymo/Software/Download%20Drivers/Linux/Download/dymo-cups-drivers-1.4.0.tar.gz -P /tmp/
tar -C /tmp/ -xzf /tmp/dymo-cups-drivers-1.4.0.tar.gz
sudo cp /tmp/dymo-cups-drivers-1.4.*/ppd/*.ppd /usr/share/cups/model/
print_bold "done."

print_bold "Importing Alf.io-PI key"
sudo gpg --keyserver keyserver.ubuntu.com --recv-key 0xD959470077C4660E
#sudo add-apt-repository "deb https://repo.alf.io/ stretch main"
#sudo apt-get update
print_bold "done."

print_bold "Downloading Alf.io-PI v$ALFIO_VERSION"
rm -f "/tmp/alf.io-pi_${ALFIO_VERSION}_all.deb"
wget "https://github.com/alfio-event/alf.io-PI/releases/download/v${ALFIO_RELEASE}/alf.io-pi_${ALFIO_VERSION}_all.deb" -P /tmp/
sudo dpkg-sig --verify -k D959470077C4660E "/tmp/alf.io-pi_${ALFIO_VERSION}_all.deb"
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

print_bold "Congratulations! Alf.io-PI has been successfully installed!"
echo
print_bold "Now it's time to edit the configuration..."

sleep 2

# backing up existing file, if any
touch ${CONFIG_FILE_PATH} && mv ${CONFIG_FILE_PATH} "${CONFIG_FILE_PATH}.$(date '+%Y-%m-%dT%H%M%S')"
cp ${CONFIG_TEMPLATE_PATH} ${CONFIG_FILE_PATH}
NOW=`date '+%Y-%m-%d %H:%M:%S'`
echo ""  >> ${CONFIG_FILE_PATH}
echo "# Edited by get-alfio-pi.sh on ${NOW}" >> ${CONFIG_FILE_PATH}

master_url=$(whiptail --inputbox "Enter the Alf.io instance URL" 10 50 --ok-button Save --nocancel "https://" 3>&1 1>&2 2>&3)
echo "master.url=${master_url}" >> ${CONFIG_FILE_PATH}

authMethod=$(whiptail --radiolist "Authentication method" 30 50 2 --ok-button Save --nocancel "API Key" "(default)" 1 "Username/Password" "(legacy)" 0 3>&1 1>&2 2>&3)
case ${authMethod} in
  'Username/Password')
    username=$(whiptail --inputbox "Enter the Username" 10 50 --ok-button Save --nocancel 3>&1 1>&2 2>&3)
    echo "master.username=${username}" >> ${CONFIG_FILE_PATH}
    password=$(whiptail --passwordbox "Enter the Password" 10 50 --ok-button Save --nocancel 3>&1 1>&2 2>&3)
    echo "master.password=${password}" >> ${CONFIG_FILE_PATH}
    ;;
  *)
    api_key=$(whiptail --inputbox "Enter the API Key" 10 50 --ok-button Save --nocancel 3>&1 1>&2 2>&3)
    echo "master.apiKey=${api_key}" >> ${CONFIG_FILE_PATH}
    ;;
esac

print_bold "Configuration complete."
echo
print_bold "Please run the following command to restart your PI:"
print_bold "      sudo reboot"