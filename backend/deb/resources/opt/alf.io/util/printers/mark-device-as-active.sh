#!/bin/sh
mkdir -p /opt/alf.io/run
rm -f /opt/alf.io/run/*
echo "$1" > "/opt/alf.io/run/$2"
