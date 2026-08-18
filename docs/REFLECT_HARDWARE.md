# Reflect Hardware Validation

Captured on Monday, August 17, 2026 from the attached mirror via `adb -s 73MR5DWRQR`.

## Device Identity

- Serial: `73MR5DWRQR`
- Manufacturer: `rockchip`
- Brand: `Android`
- Model: `rk3288`
- Hardware: `rk30board`
- Android version: `7.1.2`
- Primary ABI: `armeabi-v7a`

## Display

- Physical size: `1080x1920`
- Physical density: `160`
- Override density: `136`
- Effective UX profile: tall portrait mirror with large physical pixels and relatively low logical density

This confirms the gym surface should stay portrait-first and should favor large typography, sparse chrome, and low-overdraw overlays.

## Memory

- Total RAM: `2046304 kB` (~2.0 GB)
- Free RAM at sample time: `376240 kB`
- Available RAM at sample time: `1253684 kB`

Top PSS entries from `dumpsys meminfo` at sample time:

- `system`: `82355K`
- `com.android.settings`: `20524K`
- `com.toptech.tppowerservice`: `7576K`
- `android.rockchip.update.service`: `7100K`
- `com.sconcept.mirrordash.debug`: `0K` at the sampled instant

## Performance Implications

- Treat this as constrained hardware, not a modern flagship tablet.
- Avoid expensive blur stacks, large constantly animating gradients, and high-frequency chart redraws.
- Keep BLE collection and UI rendering decoupled so telemetry can arrive faster than Compose recomposes.
- Prefer local persistence batching over writing every sample.
- Preserve black-heavy layouts because they read well on the mirror and reduce unnecessary fill.

## Recommended Gym Runtime Defaults

- Default to the current mock-telemetry architecture for UI development.
- Throttle live graph updates to a human-readable cadence instead of raw packet frequency.
- Keep connection scanning user-driven or time-bounded.
- Keep session HUD surfaces minimal by default, with expanded debug only in settings.
