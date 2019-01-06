#!/bin/bash
#source: http://www.v2g-evse.com/projects/raspbian-stretch-java-ethernet-on-a-raspberry-pi-cm3/
echo "adding Azul Systems repository..."
apt-get install dirmngr
apt-key adv -keyserver http://repos.azulsystems.com/RPM-GPG-KEY-azulsystems
echo "deb http://repos.azulsystems.com/debian stable main" > /etc/apt/sources.list.d/zulu.list
apt-get update
echo "done. Backing up existing files..."
cp /home/pi/.config/lxsession/LXDE-pi/autostart /home/pi/.config/lxsession/LXDE-pi/autostart.alfio-backup
echo "done. Initializing directories..."