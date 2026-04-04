# OPEN-KIOSK

Open-source Android kiosk browser for digital signage. A lightweight alternative to Fully Kiosk Browser.

## Features

- **URL Playlist** — Configure multiple URLs with individual rotation timers
- **Kiosk Lock Mode** — Lock Task Mode (device owner) or immersive sticky fallback
- **Smart Sleep/Wake** — 3-state system: ACTIVE -> DIM -> SLEEP with configurable timeouts
- **Camera Motion Detection** — CameraX-based movement detection to wake the screen
- **Proximity & Shake Wake** — Native sensor support for screen wake triggers
- **Auto-Recovery** — WebView crash recovery, auto-refresh, connectivity monitoring
- **Local Settings** — Swipe-to-reveal drawer protected by PIN

## Requirements

- Android 9+ (API 28)
- Kotlin 2.0+
- Android Studio Hedgehog or newer

## Build

```bash
./gradlew assembleDebug
```

## Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Device Owner Setup (Full Kiosk Lock)

For complete kiosk lockdown (blocks home, recents, status bar), set the app as device owner:

```bash
adb shell dpm set-device-owner com.openkiosk/.receiver.KioskDeviceAdminReceiver
```

**Note:** Device must have no accounts configured (factory reset or fresh setup).

Without device owner, the app uses immersive sticky mode (less restrictive but works without special setup).

## Configuration

Swipe from the left edge to open the settings drawer (PIN protected, default: `0000`).

### Settings

| Section | Options |
|---------|---------|
| General | Start URL, auto-refresh interval |
| Sleep & Wake | Active/DIM/Sleep timeouts, DIM brightness |
| Sensors | Camera motion, proximity, shake wake toggles and sensitivity |
| Playlist | Add/remove URLs, set duration per item |
| Kiosk | Lock Task Mode toggle, PIN change |

## Architecture

- **Stack:** Kotlin + Jetpack Compose + CameraX + Room + Hilt
- **Min SDK:** 28 (Android 9)
- **Single module** with logical package separation

```
com.openkiosk/
  presentation/    # UI (Compose screens, ViewModels)
  domain/          # Business logic (PlaylistManager, models)
  data/            # Persistence (Room, repositories)
  sensors/         # CameraX motion detection, sensor wake
  sleep/           # Screen state machine (ACTIVE/DIM/SLEEP)
  kiosk/           # Lock Task Mode management
  webview/         # WebView recovery
  receiver/        # Boot receiver, device admin
  di/              # Hilt modules
```

## License

Apache License 2.0
