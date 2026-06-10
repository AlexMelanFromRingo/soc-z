# Changelog

## 0.4.0 — 2026-06-10

### Added
- IPv4/IPv6 addresses of the active network on the Network screen.
- Wi-Fi standard (Wi-Fi 4/5/6/7) on the Network screen.
- Battery design capacity (OEM power profile) with estimated full-charge
  capacity and battery-health percentage.
- Live GPU clock with min–max gauge (and busy % on Adreno), read from kgsl /
  devfreq sysfs where the vendor exposes it.
- Share action on the Overview screen — exports the whole device profile as a
  plain-text report.
- Monochrome launcher icon (themed icons on Android 13+).
- JVM unit tests for the text parsers; GitHub Actions CI (build + lint + test,
  APK artifact on every push).
- MIT license.

### Changed
- All UI labels moved from hardcoded Kotlin strings to `strings.xml` —
  the app is now translatable.
- Removed the dead `CoreState.utilization` field: `/proc/stat` is hidden from
  apps since Android 8, so honest per-core load can't be measured.

## 0.3.0 — 2026-06-10

### Fixed
- Stray Russian strings in the English UI: GPU capability flags now read
  "✓ yes / ✗ no", Vulkan "not supported", Sensors header "Found: N".
- Wi-Fi SSID was always hidden: Android requires `ACCESS_FINE_LOCATION` to
  expose the SSID. The permission is now declared and requested on the Network
  screen, `<unknown ssid>` is treated as absent, and the Wi-Fi card no longer
  disappears entirely when the SSID is unavailable.
- Cellular network type never showed: `READ_PHONE_STATE` was checked but never
  declared in the manifest. Now declared and requested at runtime.
- Display screen was a one-shot snapshot: rotation (current display
  orientation), state and refresh rate now update live (1 s poll).
- Battery "Cycles" displayed the battery *status* constant: the code queried
  raw property id 6, which is `BATTERY_PROPERTY_STATUS`, not a cycle counter.
  Cycle count is now read from `EXTRA_CYCLE_COUNT` (Android 14+), with "—"
  shown where unsupported.
- CPU core bars could show negative percentages for offline cores.
- Removed the unused `BLUETOOTH_CONNECT` permission.

### Added
- Live sensor readings on the Sensors screen — values update twice a second
  while the screen is open (one-shot trigger sensors excluded).
- README, architecture and data-source documentation.

## 0.2.0

- Initial feature set: Overview, CPU, GPU (native Vulkan probe), Memory,
  Battery, Display, Storage, Sensors, Network screens.
