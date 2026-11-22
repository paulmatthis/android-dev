# Eist Controls

A 3x1 home screen widget for [Eist](https://play.google.com/store/apps/details?id=io.eist.app), the ebook and audiobook reader. Eist doesn't have a widget of its own, so this adds one in the style of the Libby and Libro.fm widgets:

- the cover of the book you're listening to (tap it to open Eist)
- the book title and chapter
- skip back, play/pause, and skip forward (10, 15, or 30 seconds; pick in the app)

It's a separate app that talks to Eist through Android's standard media controls. It isn't made by or affiliated with Eist's developer.

## Install

1. On your Android phone (Android 9 or newer), download `eist-controls.apk` from the [latest release](https://github.com/paulmatthis/android-dev/releases/latest) and open it. Allow your browser to install apps if Android asks.
2. Open **Eist Controls** and tap **Turn on access**, then switch it on.
   - If Android says it's a **restricted setting**, tap **Open App info** in Eist Controls, tap the **⋮** menu at the top right, choose **Allow restricted settings**, and try again.
3. Tap **Add widget to home screen**, or long-press your home screen, choose **Widgets**, and find Eist Controls.

### Why it needs notification access

Android only lets an app see and control what another app is playing if it has notification access. Eist Controls never reads your notifications; it only looks for Eist's player. It has no internet permission, so nothing it sees can leave your phone.

## Notes

- Tapping the cover opens Eist the same way its playback notification does. Eist has no link that jumps straight to the player, so it reopens on the last screen you were on.
- Eist reads books in sentences, so a skip usually lands on the nearest sentence start rather than to the exact second.
- When Eist isn't running, the widget shows Eist's icon and opens Eist when tapped.

## Build

Needs the Android SDK command line tools (platform 36, build-tools 36.0.0) and Java 21. No Gradle.

```bash
./build.sh
```

The first build makes a signing key (`eist-controls.keystore` and `.signing-pass`). They are git-ignored; keep a backup, because updates have to be signed with the same key.
