# Eist Controls

A simple 3x1 home screen widget for [Eist](https://play.google.com/store/apps/details?id=io.eist.app) ebook and audiobook reader app on Android.

Not affiliated with Eist's developer.

## Install

1. On your Android phone (Android 9 or newer), download [`eist-controls.apk`](https://github.com/paulmatthis/android-dev/releases/latest/download/eist-controls.apk) and open it. Allow your browser to install apps if Android asks.
2. Open **Eist Controls** and tap **Turn on access**, then enable the setting.
   - If Android says it's a **restricted setting**, tap **Open App info** in Eist Controls, tap the **⋮** menu at the top right, choose **Allow restricted settings**, and try again.
3. Tap **Add widget to home screen**, or long-press your home screen, choose **Widgets**, and find Eist Controls.

### Why it needs notification access

Android only lets an app see and control what another app is playing if it has notification access. Eist Controls never reads your notifications. It only looks for Eist's player, and it has no internet permissions whatsoever.

## Notes

- Tapping the cover opens the Eist app.
- Eist reads books in sentences, so a skip often lands on the nearest sentence start rather than to the exact second.

## Build and release

Needs the Android SDK command line tools (platform 36, build-tools 36.0.0), Java 21, and the GitHub CLI. No Gradle.

- `./build.sh` builds `build/eist-controls.apk`.
- `./release.sh` builds and publishes a new GitHub release. Bump `versionCode` and `versionName` in `AndroidManifest.xml` first. The release page always shows [`RELEASE_NOTES.md`](RELEASE_NOTES.md) exactly as written, so edit that file to change it.

The first build makes a signing key (`eist-controls.keystore` and `.signing-pass`). They are git-ignored; keep a backup, because updates have to be signed with the same key.
