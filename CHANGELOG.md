# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

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
