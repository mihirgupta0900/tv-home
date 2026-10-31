# Bluetooth manager: project framing

Status: draft for review. Target: Hisense HiSmart 4K A4FFM (Android 9, `MIN_SDK=28`).

## Problem

When the TV powers on, Android reconnects to the last Bluetooth device it used. If that device
(headphones/speaker) is in use by someone else's phone, the TV takes it and knocks them off.
There is also no in-launcher way to connect, disconnect or discover devices.

## Goals

1. **Never steal a device.** The TV only connects when the user asks.
2. **A `Bluetooth` pill** next to `Inputs` in the top-right pill row.
3. **Manage devices:** list paired devices with connection state, connect, disconnect, forget, discover and pair new ones.
4. **Sharing:** pair many devices; investigate playing audio to two at once.

Devices in scope: headphones/speakers and Bluetooth remotes.

## Non-goals

- TCL Google TV support (later; newer Android has different Bluetooth APIs).
- Keyboards, mice, gamepads (not requested).
- Background services. The launcher stays service-free; any boot hook must be a receiver that exits quickly.

## Approach

### 1. Never auto-connect
Android's stack reconnects bonded devices by profile priority. Plan: use the hidden
`BluetoothA2dp.setPriority()` (available on Android 9, via reflection):

- On pairing or first sight, set each audio device to `PRIORITY_OFF` so the stack won't auto-connect it.
- When the user taps Connect: set `PRIORITY_ON`, connect, then set it back to `PRIORITY_OFF`.
- Disconnect is explicit.

**Spike findings so far (Hisense, Android 9, 2026-10-31):**
- The TV stores per-device priority in `Settings.Global` as `bluetooth_a2dp_sink_priority_<ADDRESS>`. The headphones (Soundcore) were `1000` (auto-connect); unknown devices are `-1`.
- Setting it works from `adb shell settings put global ...`, and it reads back as `0`. So the app can write this directly if it holds `WRITE_SECURE_SETTINGS` (granted over ADB at install, like location), with no hidden-API reflection.
- The A2DP log shows the headphones' 13:41 connection was an incoming event, not one the TV started. Priority `0` should block incoming connections too; this is not yet confirmed.
- `0` = never connect, `100` = connect only when asked, `1000` = auto-connect.

- Reboot test: after a reboot (uptime 0 min) the priority was still `0` and the Soundcore Space Q45 was `NotConnected`. Network ADB also came back on its own after the reboot.

- **Confirmed:** with priority `0`, switching the headphones on made them try to connect (two incoming events at 15:00:56) and the TV stayed `Disconnected`. This is the knock-off fix, and it needs no root and no hidden API.

**Still to verify on the TV:**
- Does it survive a full power cut (not just a reboot)?
- Does connecting manually still work after flipping `0` to `100`?

**Original risks:**
- Does `PRIORITY_OFF` survive a reboot and a power cycle on this firmware?
- Does Hisense's own Bluetooth settings or the MediaTek stack override it?
- Does hidden-API reflection work for a non-system app on Android 9?

**Fallback if it doesn't:** forget the device on disconnect (needs re-pairing each time), or a boot/`ACL_CONNECTED` receiver that disconnects connections the user didn't start. The second briefly knocks the other user off, so it's a last resort.

### 2. The pill and screen
- New pill `Bluetooth` (icon `ᛒ`) in [MainActivity.java](../launcher/src/com/mihir/tvhome/MainActivity.java), between `Inputs` and `Wi-Fi`.
- Opens a D-pad-friendly screen (new `BluetoothActivity`): *My devices* list (name, type, Connected/Not connected), actions Connect / Disconnect / Forget, and a *Discover* button with a live list of nearby devices to pair.
- Needs `BLUETOOTH`, `BLUETOOTH_ADMIN` (normal permissions). Discovery needs location, which the launcher already has via ADB.
- Remote pairing uses the same flow (HID); the screen shows a hint to put the remote in pairing mode.

### 3. Sharing
- **Many paired devices:** supported by the design above.
- **Audio to two devices at once:** not supported by Android 9. Options to research, none promised:
  - Headphones with Bluetooth multipoint, paired to TV and phone, so each stays connected without a fight (helps goal 1).
  - Output from the TV's audio jack/optical to a hardware splitter or Auracast/dual-transmitter.
  - A TV or firmware upgrade to Android 13+ with LE Audio broadcast.

## Phases

| Phase | Deliverable | Done when |
|---|---|---|
| 0 | Spike on the real TV | Can read bonded devices; know if `PRIORITY_OFF` persists across power cycle |
| 1 | Never-steal behavior | Power-cycling the TV with the headphones on a phone no longer disconnects the phone |
| 2 | Pill + manager screen | Connect, disconnect, forget, discover and pair from the pill, using only the remote |
| 3 | Sharing research | Short written verdict on dual audio for this TV |

## Phase 1 status (2026-10-31)

Built and installed on the Hisense. `BtPolicy` holds paired audio devices at priority `0`; `BootReceiver` re-locks after power-on; the home screen re-locks on resume, on disconnect and on new pairing. Tested over ADB: a device set to `100` went back to `0` after returning Home, and `WRITE_SECURE_SETTINGS` was granted at install.

Known gap: no background service, so a device opened to `100` from the TV's own Settings stays open until the next boot or the next time Home shows.

Local build tools live in `tools/sdk` (git-ignored). Build with:
`JAVA_HOME=tools/sdk/jdk/Contents/Home ANDROID_HOME=tools/sdk/android MIN_SDK=28 STOCK_LAUNCHER=com.google.android.tvlauncher launcher/build-and-install.sh`

## Open questions

- Does the headphone brand support multipoint?
- Should Disconnect also turn the device's priority back off (default: yes)?
