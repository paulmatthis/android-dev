#!/bin/zsh
# Builds "Phone Transfer.app" in this folder. The app is self-contained:
# the server, web page, and helper scripts are copied inside it.
# Needs adb and python3 from Homebrew (brew install android-platform-tools python).
# Usage: ./build.sh
set -euo pipefail
cd "${0:A:h}"
APP="Phone Transfer.app"
rm -rf "$APP"
osacompile -o "$APP" "src/Phone Transfer.applescript"
R="$APP/Contents/Resources"
cp src/server.py src/index.html src/phone src/phone-browser "$R/"
chmod +x "$R/phone" "$R/phone-browser"
# Use our icon instead of the default script icon.
cp src/icon.icns "$R/droplet.icns"
rm -f "$R/Assets.car"
/usr/libexec/PlistBuddy -c "Delete :CFBundleIconName" "$APP/Contents/Info.plist" 2>/dev/null || true
codesign --force --sign - "$APP" 2>/dev/null
echo "Built $PWD/$APP"
