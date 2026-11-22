#!/bin/zsh
# Builds the APK and publishes it as a new GitHub release.
# The release page text is always RELEASE_NOTES.md, word for word.
# Bump versionCode and versionName in AndroidManifest.xml first.
# Usage: ./release.sh
set -euo pipefail
cd "${0:A:h}"
VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)"
TAG="eist-controls-v$VERSION"
if gh release view "$TAG" --repo paulmatthis/android-dev >/dev/null 2>&1; then
  echo "$TAG already exists. Bump versionName in AndroidManifest.xml first." >&2
  exit 1
fi
./build.sh
gh release create "$TAG" build/eist-controls.apk --repo paulmatthis/android-dev \
  --target main --title "Eist Controls $VERSION" --notes-file RELEASE_NOTES.md --latest
