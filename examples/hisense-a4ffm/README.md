# Hisense HiSmart 4K A4FFM (Android TV 9)

- Home: `launcher/` built with `MIN_SDK=28` (Android 9). Dock: Netflix, YouTube, Prime, Disney+, Plex, Spotify.
- Build and install:
  `MIN_SDK=28 STOCK_LAUNCHER=com.google.android.tvlauncher launcher/build-and-install.sh`
  (needs JDK 17 and the Android SDK; set `JAVA_HOME` and `ANDROID_HOME` if they are not the Homebrew ones).
- The Wi-Fi pill shows "Ethernet" on a wired TV. The setup wizard is left enabled on purpose (it can own the saved Wi-Fi network); `DISABLE_SETUPWRAITH=1` turns that on.
- Change the dock: edit `dock_apps` in `launcher/res/values/config.xml`, rebuild. Wallpaper: see `launcher/WALLPAPER-CREDITS.md`.
- Re-enable one app: `adb shell pm enable <package>` (or `tools/tv.py 'pm enable <package>'`).
- Undo everything: see the header of `debloat.txt`.
- Debloat list header: nothing re-enabled itself after a reboot.
- Network ADB (port 5555) was closed at the end of the setup; re-enable USB debugging in Developer options to reconnect.
