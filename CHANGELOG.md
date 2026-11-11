# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Security

- The kiosk WebView is confined to the hosts of the start URL and playlist (subdomains included).
  Links, scripted navigation and redirects to other hosts are blocked. A blocked main-frame load
  shows a local "Address not allowed" page with the blocked host instead of a blank screen, and is
  not reported as a successful load (no retry loop). Redirects to an SSO host need that host in
  the playlist
- Hosts typed with non-ASCII letters (`bücher.example`) are converted to punycode before they
  join the allowlist, so they keep loading
- Plain `http://` only loads for private/LAN hosts or hosts the owner added as `http://`;
  adding an `http://` URL on a public host now asks for confirmation
- Removed the redundant `android:usesCleartextTraffic` manifest flag

### Fixed

- Camera no longer mistakes a global light change for a person: the median frame-to-frame delta is
  subtracted before counting changed pixels, so the panel dimming from DIM to SLEEP stops waking
  the screen at night
- Frames captured while auto-exposure is still converging are discarded after the camera binds
- Pulsed mode resets its reference frame on every pulse — it used to compare the first frame of a
  window against one from the previous window, seconds earlier, which both missed passers-by and
  produced false wakes
- The camera start/stop race is closed: an asynchronous bind aborts if a stop was requested while
  the provider was loading
- Replaced WebView instances are now destroyed instead of leaked, and a dead render process is torn
  down before the new one is created — this was the memory pressure that got the process killed
- The screen state machine freezes while no activity is attached, instead of recording transitions
  no panel ever displayed
- Auto-refresh set to "Disabled" no longer reloads in a tight loop when the clock was already armed
- Turning a sensor off in settings now stops the one already running
- The camera pulse interval chosen in settings is honoured instead of being silently capped
- Sensitivity changes reach the live analyser instead of waiting for the camera to restart
- Irreversible decisions (asking for the camera, entering lock task) wait for the stored config
  rather than acting on factory defaults

### Added

- Scheduled deep sleep window (touch-only wake, all sensors off) and a language selector (English, Portuguese, Spanish)
- Foreground watchdog service that holds the process and brings the kiosk back when something
  covers it, with backoff from 5s to 60s
- Power-aware camera duty cycle: continuous analysis on mains, pulsed on battery
- Screen state persisted across process death, so a relaunch returns to the same state
- Cable provisioning instructions for retail Fire tablets, where device owner is unavailable
- The watchdog switches off the Fire lock-screen ad again when Fire OS re-enables it (needs `WRITE_SECURE_SETTINGS`, granted over ADB)
- The watchdog restarts the process while the screen is asleep when video memory grows past a limit, and a cold start no longer lights the screen if the stored state was asleep
- Unit test suite (`./gradlew testDebugUnitTest`)
- Project documentation under `.claude/docs/`
- `README.pt-BR.md`, a Portuguese translation of the README
- CI runs the unit tests, uses actions pinned by commit SHA and read-only token permissions

### Changed

- README rewritten against the current code: settings ranges and defaults, permissions, provisioning commands, known limitations
- Generated and machine-specific files (`graphify-out/`, local Fire state and plans under `.claude/`) are no longer tracked
- Settings screenshots regenerated from the current build with synthetic data

### Removed

- `cameraPollingIntervalSeconds` setting, which was persisted but never read by anything (the "Camera polling" row is gone from the drawer)

## [1.0.0] - 2025-06-01

### Added

- URL playlist with per-item rotation timers
- Kiosk Lock Task Mode with immersive sticky fallback
- 3-state sleep/wake system (ACTIVE, DIM, SLEEP) simulating screen-off via brightness control
- CameraX motion detection with configurable sensitivity (LOW/MEDIUM/HIGH)
- Proximity sensor wake trigger with debounce
- Accelerometer shake wake trigger
- WebView crash recovery with exponential backoff (5s, 15s, 30s, 60s)
- Auto-refresh with configurable interval (5-120 min)
- Connectivity monitoring with offline screen
- Settings drawer with optional PIN protection (disabled by default)
- Cleartext HTTP support for internal network digital signage
- Device owner setup for full kiosk lockdown
- Boot receiver for auto-launch on device startup
- Internationalization support (English + Portuguese)
