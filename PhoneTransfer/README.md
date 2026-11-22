# Phone Transfer

A Mac app for moving files between your Mac and an Android phone over USB. macOS can't browse Android phones on its own, so this does it through `adb`.

- **Double-click** the app to open Phone Browser in your web browser: your Mac on the left, your phone on the right. Browse, search, sort, and copy files either way.
- **Drop files** on the app's icon to send them to the phone's `Download/FromMac` folder.
- Files copied from the phone go to `~/Downloads/FromPhone` unless you pick another folder.

Phone Browser runs only on your Mac (`127.0.0.1`), and it quits by itself a little after you close its tab.

## Setup

1. Install the tools with [Homebrew](https://brew.sh):
   ```bash
   brew install android-platform-tools python
   ```
2. On the phone, turn on **Developer options > USB debugging**, plug it in, and tap **Allow** when it asks.
3. Build the app:
   ```bash
   ./build.sh
   ```
   This makes `Phone Transfer.app` in this folder. It's self-contained: everything it runs is inside it. Open it from here, or drag it to the Dock.

## What's in here

| File | What it does |
|---|---|
| `src/Phone Transfer.applescript` | The app itself: opens Phone Browser, or sends dropped files |
| `src/server.py`, `src/index.html` | Phone Browser, the two-pane file browser |
| `src/phone-browser` | Starts Phone Browser and opens it |
| `src/phone` | Command line version: `phone send`, `phone get`, `phone ls`, `phone pull` |
| `src/icon.icns` | The app icon |
| `build.sh` | Puts it all together into `Phone Transfer.app` |

To change the app, edit the files in `src/` and run `./build.sh` again.
