#!/usr/bin/env sh

chromium-browser http://localhost \
  --start-maximized \
  --kiosk \
  --incognito \
  --allow-running-insecure-content \
  --allow-insecure-localhost \
  --noerrdialogs \
  --disable-translate \
  --no-first-run \
  --fast --fast-start \
  --disable-infobars \
  --disable-features=TranslateUI \
  --disk-cache-dir=/dev/null \
  --touchpad-overscroll-history-navigation=0 \
  --overscroll-history-navigation=0 \
  --disable-touch-drag-drop \
  --no-default-browser-check \
  --disable-pinch
