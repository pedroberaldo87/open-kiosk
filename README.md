# OPEN-KIOSK

Open-source Android kiosk browser for digital signage. A lightweight alternative to Fully Kiosk Browser.

## Features

- **URL Playlist** — Configure multiple URLs with individual rotation timers
- **Kiosk Lock Mode** — Lock Task Mode (device owner) or immersive sticky fallback
- **Smart Sleep/Wake** — 3-state system (ACTIVE → DIM → SLEEP) with configurable timeouts. Simulates screen-off via brightness control + black overlay — the app never actually turns the screen off, keeping sensors alive
- **Camera Motion Detection** — CameraX-based presence detection using Y-plane grayscale frame comparison. Configurable sensitivity (LOW/MEDIUM/HIGH) and polling interval (1-15s). Wakes the screen when movement is detected in front of the camera
- **Shake Wake** — Accelerometer-based wake trigger with debounce
- **Auto-Recovery** — WebView crash recovery (key-based recomposition), auto-refresh with configurable interval, exponential backoff on errors, connectivity monitoring with offline screen
- **Local Settings** — Swipe-to-reveal drawer with optional PIN protection (disabled by default)
- **HTTP Support** — Cleartext traffic allowed for internal network digital signage

## Tested Hardware

- Amazon Fire HD 8 (FireOS / Android 9+)

## Requirements

- Android 9+ (API 28)
- Front-facing camera (optional, for motion detection)

## Quick Start

### Build

```bash
# Debug APK (~56MB)
./gradlew assembleDebug

# Release APK (~2.4MB, requires signing config)
./gradlew assembleRelease
```

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release Signing

Create a `local.properties` file in the project root:

```properties
signing.storeFile=../keystore/your-keystore.jks
signing.storePassword=your-password
signing.keyAlias=your-alias
signing.keyPassword=your-key-password
```

Generate a keystore:

```bash
keytool -genkeypair -v -keystore keystore/open-kiosk.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias openkiosk
```

## Device Owner Setup (Full Kiosk Lock)

For complete kiosk lockdown (blocks home, recents, status bar):

```bash
adb shell dpm set-device-owner com.openkiosk/.receiver.KioskDeviceAdminReceiver
```

**Note:** Device must have no accounts configured (factory reset or fresh setup).

To remove device owner:

```bash
adb shell dpm remove-active-admin com.openkiosk/.receiver.KioskDeviceAdminReceiver
```

Without device owner, the app uses immersive sticky mode (less restrictive but works without special setup).

## Configuration

Swipe from the left edge to open the settings drawer. PIN protection is disabled by default — enable it in Kiosk settings.

### Settings Sections

**General**
- Start URL
- Auto-refresh interval (5-120 min)

**Sleep & Wake**
- Active → DIM timeout (10-300s, default: 30s)
- DIM → Sleep timeout (5-120s, default: 60s)
- DIM brightness level (5-50%, default: 20%)

**Sensors**
- Camera motion detection toggle + sensitivity (LOW/MEDIUM/HIGH)
- Camera polling interval (1-15s, default: 5s)
- Shake detection toggle

**Playlist**
- Add/remove URLs with individual rotation timers
- Duration per item (5-300s)

**Kiosk**
- Lock Task Mode toggle
- PIN protection toggle (disabled by default)
- PIN change (default: 0000)

### How Sleep/Wake Works

The app uses a 3-state system inspired by Fully Kiosk Browser:

1. **ACTIVE** — Screen at normal brightness, content displayed
2. **DIM** — Screen brightness reduced (configurable), content still visible
3. **SLEEP** — Screen brightness at 0 + black overlay. Appears off but the app stays alive

The screen never actually turns off at the OS level. This keeps sensors and camera running so they can detect presence and wake the screen instantly.

### Camera Motion Detection

The front camera captures low-resolution frames (320x240) at configurable intervals. Each frame's Y-plane (grayscale luminance) is compared with the previous frame using absolute pixel difference. If the ratio of changed pixels exceeds the sensitivity threshold, the screen wakes up.

Sensitivity thresholds:
- **HIGH** — 3% of pixels changed (most sensitive)
- **MEDIUM** — 5% of pixels changed (default)
- **LOW** — 8% of pixels changed (least sensitive)

**Note:** Camera motion detection does not work on emulators (virtual camera is static). Test on real hardware.

## Architecture

- **Stack:** Kotlin 2.0 + Jetpack Compose + CameraX + Room + Hilt
- **Min SDK:** 28 (Android 9) — Target SDK: 34
- **Single module** with logical package separation

```
com.openkiosk/
  presentation/    # UI (MainActivity, Compose screens, ViewModels)
  domain/          # Business logic (PlaylistManager, models)
  data/            # Persistence (Room database, repositories)
  sensors/         # CameraX motion detection, accelerometer wake
  sleep/           # Screen state machine (ACTIVE/DIM/SLEEP)
  kiosk/           # Lock Task Mode management
  webview/         # WebView recovery manager
  receiver/        # Boot receiver, device admin receiver
  di/              # Hilt dependency injection modules
```

## Debugging

```bash
# Monitor sensor and camera logs
adb logcat -s KioskViewModel:D MotionDetection:D SensorWake:D
```

## License

Apache License 2.0
