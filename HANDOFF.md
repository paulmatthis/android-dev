# Android Dev handoff

Start here to work on the Android projects for this phone. Everything lives in `/Users/paul/Documents/Android Dev/` (renamed from `Android Widgets`), with one folder per project; keep it all out of `~/Sites`. The path has a space, so quote it in shell commands.

| Folder | Project | Status |
|---|---|---|
| `EistWidget/` | Eist Controls, a 3x1 media widget for the Eist reader (`dev.eistcontrols.widget`) | Done, working, released on GitHub |
| `DazzleBright/widget/` | Dinolite, a 1x1 Bluetooth light toggle (`dev.dinolite.widget`) | Done, working |
| `WordGuess/` | Word Guess game (`app.wordguess`) | Its own repo, `paulmatthis/wordguess`, included as a git submodule |

## Git
The folder is the GitHub repo `paulmatthis/android-dev` (public). `WordGuess/` is a submodule, so commit Word Guess changes inside `WordGuess/` and push them to its own repo, then commit the updated pointer here. Keystores, `.signing-pass`, `build/` folders, and the decompiled vendor code are git-ignored; never commit them.

## The phone
- **Device:** Pixel 10a (`stallion`), Android 17.
- **Connection:** USB debugging is on. Plug in the cable, run `adb devices -l`, and check that the phone shows as `device`. If it shows as `unauthorized`, ask the user to tap **Allow** on the phone. A charge-only cable won't work.
- **Locked screen:** `adb exec-out screencap` returns a black image while the phone is locked or dozing. Check with `adb shell dumpsys window | grep isKeyguardShowing`, and ask the user to unlock it instead of guessing.
- **Permissions:** runtime permission prompts (Bluetooth, location, and so on) are for the user to tap. Don't grant them with `pm grant`.

## Tools on the Mac (already installed)
| Tool | Where | Notes |
|---|---|---|
| adb | `/opt/homebrew/bin/adb` | Homebrew cask `android-platform-tools` |
| Java 21 | `$(brew --prefix openjdk@21)` | The system `java` stub doesn't work. Always set `JAVA_HOME` to this. |
| Android SDK | `/opt/homebrew/share/android-commandlinetools` | Has `platforms;android-36` and `build-tools;36.0.0`. The user accepted the SDK licenses on 2026-11-06. |
| sdkmanager | `/opt/homebrew/bin/sdkmanager` | Needs `JAVA_HOME` set. Installing new SDK packages is fine. A new license prompt needs the user's OK first. |
| JADX 1.5.6 | `/opt/homebrew/bin/jadx` | For decompiling a vendor app when its widget or protocol needs to be understood |

There's no Gradle and no Android Studio. Each widget is built by a short `build.sh` that runs aapt2, javac, d8, zipalign, and apksigner directly.

## How the Dinolite project is laid out
```
widget/
  AndroidManifest.xml        package dev.dinolite.widget, minSdk 33, targetSdk 36
  build.sh                   builds and signs build/dinolite.apk
  dinolite.keystore          signing key (password "dinolite"); keep it, updates must use the same key
  res/layout/widget.xml      the widget's RemoteViews layout
  res/xml/widget_info.xml    appwidget-provider: 1x1, resizable, no periodic updates
  res/drawable/              bulb vector icon and rounded background
  src/dev/dinolite/widget/
    DinoliteWidget.java      AppWidgetProvider: tap -> goAsync() -> do the work -> redraw
    LightClient.java         the Bluetooth logic, separate from the widget
    SetupActivity.java       launcher screen: asks for permission, test button, "Add widget" button
```

Build and install:
```bash
"/Users/paul/Documents/Android Dev/DazzleBright/widget/build.sh"
```
```bash
adb install -r "/Users/paul/Documents/Android Dev/DazzleBright/widget/build/dinolite.apk"
```

## Starting a new widget
1. Copy `EistWidget/` (or `DazzleBright/widget/`) to a new project folder, for example `/Users/paul/Documents/Android Dev/<NewName>/`, and delete its `build/` folder, keystore, and `.signing-pass`. The script creates a new key on the first build.
2. Pick a new package name (`dev.<name>.widget`). Change it in `AndroidManifest.xml`, in the `package` line of each Java file, in the source folder path, in the `R.java` path in `build.sh`, and in the output APK name and keystore name in `build.sh`.
3. Replace `LightClient.java` with whatever the new widget does. Keep the same pattern: the provider calls `goAsync()`, the work reports back through a callback, and the widget redraws, all in under about 9 seconds.
4. Update the labels in `res/values/strings.xml` and the app label in the manifest.
5. Build, install, then ask the user to open the app once (for permissions) and add the widget.
6. Check it with `adb logcat -d -s <YourTag> AndroidRuntime:E`.

## Eist Controls reference
- **Eist:** package `io.eist.app`, a Flutter app that uses the `audio_service` plugin, so it publishes a normal Android `MediaSession` (title = chapter, album = book, artist = author, cover bitmap included). It supports seek-to, play/pause, rewind, and fast-forward, and it snaps seeks to sentence starts.
- **How the widget sees Eist:** `EistListener` is a `NotificationListenerService`. Android only shares other apps' media sessions with an enabled listener. The user turns on access once from the setup screen (don't toggle it with adb). The listener watches Eist's session and redraws the widget on every change.
- **Buttons:** skip uses `seekTo(position +/- N seconds)`, where N is 10, 15, or 30 (set in the app). Cover tap sends Eist's own session activity `PendingIntent`, the same one its notification uses. Eist has no deep link to the player, so it reopens on its last screen.
- **Release:** bump `versionCode` and `versionName` in `EistWidget/AndroidManifest.xml`, run `build.sh`, and attach `build/eist-controls.apk` to a new GitHub release. Keep `eist-controls.keystore` and `.signing-pass` backed up; a new key means people must uninstall before updating.
- **Testing on the phone:** the widget's buttons can be tapped with `adb shell input tap`, and `adb shell dumpsys media_session` shows Eist's state and position.

## Lessons from the first build
- **javac flags:** `-source 8 -target 8` plus `-bootclasspath "android.jar:core-lambda-stubs.jar"`. Target 11 rejects `-bootclasspath`, and without the stubs file, lambdas fail with "Unable to find method metafactory". `build.sh` already does this.
- **apksigner and keytool** need `JAVA_HOME` set too, or they fail with "Unable to locate a Java Runtime". `build.sh` sets it, but one-off commands in a new shell don't.
- **RemoteViews limits:** use only `FrameLayout`, `LinearLayout`, `ImageView`, `TextView`, `ProgressBar`, and other RemoteViews-safe views. Tinting with `setInt(id, "setColorFilter", color)` works.
- **Tap handling:** use an explicit broadcast with `PendingIntent.FLAG_IMMUTABLE`, keep the receiver `exported="false"`, and set `updatePeriodMillis="0"` unless the widget really needs polling.
- **Edge to edge:** with targetSdk 35 or higher, Android draws activities under the status bar. Hide the action bar and pad the content with the window insets, as `EistWidget/.../SetupActivity.java` does. `Theme.DeviceDefault.DayNight.NoActionBar` doesn't exist in platform 36.
- **Testing:** adb can't send a broadcast to a receiver that isn't exported, so a button in the setup activity is the easy way to test the logic before trying the widget.
- **Bluetooth on the same phone:** several apps can use the same light connection at once, so the widget worked with the vendor app open or closed.

## Dinolite reference (in case the new widget also talks to these lights)
- **Light:** "Dinolite" in the Bedroom, Bluetooth name `IOTBTC87`, made by Zengge and sold under the Dazzle Bright brand (`com.dazzle.bright`).
- **Why the stock widget is empty:** it lists and controls devices only through Zengge's cloud API, which filters out devices it sees as offline. It never uses Bluetooth.
- **GATT:** service `ffff`, write `ff01` (write without response), notify `ff02` (enable through CCCD `2902`). Request MTU 247 or the 86-byte status replies get cut off.
- **Frame:** `00 seq 80 00 00 len len+1 0a payload`, where `len` is the payload length.
- **Commands:** on `71 23`, off `71 24`, status query `EA 81 8A 8B 59`.
- **Reply:** an 8-byte header plus JSON `{"code":0,"payload":"EA81..."}`. Byte 6 of the decoded payload is power: `23` on, `24` off.
- **No key, login, or pairing** is needed.
- Other similar lights the phone has seen: `IOTBT167`, `LEDnetWF02003529CC44`, and `LEDnetWF02003529DF3C`. They're probably the same protocol, but this hasn't been tested.

## Capturing what a vendor app sends (if a new widget needs it)
1. Ask the user to enable **Developer options > Enable Bluetooth HCI snoop log**, toggle Bluetooth off and on, then use the vendor app. This is a phone setting, so the user changes it, not Claude.
2. The log is in a protected location, so `adb pull` fails. Run `adb bugreport bugreport.zip` in the background (it takes a few minutes), then extract `FS/data/misc/bluetooth/logs/btsnoop_hci.log`.
3. Parse it with a small Python script (btsnoop records, H4 type 2 ACL, L2CAP CID 4 ATT). The old parser was in a scratchpad that has since been cleared, so write a new one.
4. A bug report holds personal data. Delete it and the capture once they're no longer needed, and ask the user before deleting.

## Working with this user
- Explain plainly; the user isn't an Android developer and prefers simple, working results to deep internals.
- In prose, avoid em dashes and en dashes, and use the Oxford comma.
- The user asked for this folder to be tracked on GitHub (2026-11-22). Commit and push when asked; there's no need for pull requests on this solo repo.

## Leftovers
`DazzleBright/decompiled/`, `DazzleBright/native/`, and the empty `DazzleBright/logs/` folder are from analyzing the Dazzle Bright app. They aren't needed for building widgets, and the user can delete them anytime.
