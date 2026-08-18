# Gym Adapter Layer

MirrorDash now keeps the gym feature inside the main app while isolating device-specific behavior behind an internal adapter boundary.

## Why

- Preserve the gym experience even if the original vendor app disappears.
- Keep MirrorDash updates easy to merge because the gym feature stays modular.
- Let community-style integrations evolve without rewriting session logic or UI.

## Current built-in adapters

- `vitruvian.community.mock`
  - Strength-device control and telemetry simulation for the internal Vitruvian-style layer.
- `echelon.community.mock`
  - Cardio-device control and telemetry simulation for bike-style workouts.
- `heartrate.bridge.mock`
  - Heart-rate relay abstraction for watches, straps, or future health bridges.

## Main pieces

- `FitnessDevicePreference.adapterId`
  - Persists which adapter powers a device.
- `FitnessDeviceSnapshot.adapterId`
  - Keeps the active runtime snapshot tied to its adapter.
- `GymAdapterRegistry`
  - Resolves adapters for saved preferences and runtime devices.
- `GymDeviceAdapter`
  - Defines connect/disconnect transitions, snapshot shaping, and telemetry sampling.
- `GymSessionEngine`
  - Owns workout/session flow, but delegates hardware behavior to adapters.

## What this enables next

- Real Vitruvian-community transport instead of mocked strength telemetry.
- Device-specific command/control surfaces without changing the rest of the gym tab.
- Importers for user-supplied workout/video metadata that stay independent from hardware.
- Vendor or community health connectors per profile.
