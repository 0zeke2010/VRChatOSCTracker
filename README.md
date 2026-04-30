# VRChat OSC Tracker — Android App

Tracks the foreground app and/or now-playing media on your Android phone and
sends the info to VRChat's OSC chatbox in real time.

## What it shows in your chatbox

```
📱 YouTube  |  🎵 Daft Punk - Get Lucky  [21:34]
```

The format is configurable — you can turn the time and/or media off.

---

## Requirements

| Requirement | Details |
|---|---|
| Android version | 8.0 (API 26) or higher |
| VRChat | OSC must be enabled (Settings → OSC → Enable) |
| Network | Phone and PC on the same Wi-Fi, OR same device via 127.0.0.1 |

---

## Build instructions

### Option A — Android Studio (recommended)

1. Install **Android Studio Hedgehog** or later.
2. Open the `VRChatOSCTracker/` folder as a project.
3. Let Gradle sync finish.
4. Plug in your phone (enable USB debugging) or use an emulator.
5. Press **Run** (▶).

### Option B — Command line

```bash
cd VRChatOSCTracker
./gradlew assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

Install the APK:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## First-time setup (on the phone)

### 1. Grant Usage Access (required)
- Open the app → tap **Grant usage access**
- Find **VRChat OSC Tracker** in the list → enable it
- Return to the app

### 2. Grant Notification Listener (optional — for media info)
- Tap **Grant notification access**
- Find **VRChat OSC Tracker** → enable it
- This allows reading now-playing info from any media app

### 3. Configure the OSC target

| Field | Value |
|---|---|
| Host IP | `192.168.5.220` (pre-filled) |
| Port | `9000` (VRChat default — do not change unless you know why) |
| Interval | How often to update the chatbox (seconds). `5` is a good default |

### 4. Enable OSC in VRChat

In VRChat desktop: **Settings → OSC → Enable**

The OSC port for *receiving* is `9000` by default. No further config needed.

### 5. Press Start tracker

The notification bar will show the last sent message. A "Stop" button in the
notification lets you stop it without opening the app.

---

## How it works

```
Android UsageStatsManager  ──┐
                              ├─▶  TrackerService  ──▶  OSCSender  ──UDP──▶  VRChat :9000
Android MediaSession API   ──┘         (polls every N sec)          /chatbox/input
```

### OSC packet format

VRChat's chatbox address is `/chatbox/input` with arguments:

| # | Type | Value |
|---|---|---|
| 1 | string | The message text |
| 2 | string | `""` (unused, required by OSC spec) |
| 3 | bool | `true` = bypass keyboard, show immediately |

The app builds a standards-compliant OSC 1.0 UDP packet (no library needed —
all string padding logic is in `OSCMessage.java`).

---

## File overview

```
app/src/main/java/com/vrchat/osctracker/
├── OSCMessage.java          Build raw OSC 1.0 UDP packet bytes
├── OSCSender.java           Send UDP to host:port
├── MediaListenerService.java  Read now-playing via MediaSession API
├── TrackerService.java      Foreground service — poll + send loop
└── MainActivity.java        Settings UI + permission checks
```

---

## Troubleshooting

**Messages not appearing in VRChat**
- Check OSC is enabled in VRChat settings
- Check the IP address — use `ipconfig` (Windows) or `ip addr` (Linux) to find your PC's LAN IP
- Make sure both devices are on the same Wi-Fi network (not guest isolation)
- Check the port is `9000` and no firewall is blocking UDP

**Chatbox shows nothing after a few minutes**
- VRChat clears the chatbox after ~10 seconds of no updates — this is normal
- The service keeps sending on the interval; if the app was killed by Android's battery
  optimizer, go to Settings → Battery → VRChat OSC Tracker → Unrestricted

**"Usage access required" even after granting**
- Some OEM Android skins (MIUI, One UI) require additional steps. Check your
  manufacturer's documentation for "Usage access" or "Digital Wellbeing" settings.

**Media info not showing**
- Confirm Notification Listener is enabled
- Some apps (Spotify) require the screen to be on before their MediaSession is active
- The feature only reads metadata — it does not control playback

---

## Privacy

All data stays on-device and on your local network. Nothing is sent to any server.
The app has no internet permission beyond sending UDP to the IP you configure.
