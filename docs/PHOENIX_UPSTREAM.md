# Phoenix Upstream Strategy

Status captured on Monday, August 17, 2026 from the local checkout at `C:\Users\Maken\Documents\GitHub\VitruvianProjectPhoenix`.

## Current Local State

- Local path: `C:\Users\Maken\Documents\GitHub\VitruvianProjectPhoenix`
- Current remote:
  - `origin https://github.com/9thLevelSoftware/VitruvianProjectPhoenix.git`
- README describes the project as:
  - Kotlin
  - Jetpack Compose
  - MVVM / Clean Architecture
  - Hilt
  - Nordic BLE library
  - Room + DataStore
- README advertises an MIT license, but this local checkout does not currently include a visible `LICENSE` file. Confirm the exact license text before vendoring or redistributing source.

## What We Want From Phoenix

We want the device communication layer, not the app shell:

- BLE scanning
- connection lifecycle
- UUID definitions
- command/protocol builder logic
- monitor sample parsing
- rep notifications
- heuristic/safety signals
- reconnect behavior

We do not want to import:

- Phoenix screens
- Phoenix navigation
- Phoenix Room schema
- Phoenix Hilt graph
- Phoenix workout UI assumptions

## Useful Source Areas

Primary files worth wrapping:

- `app/src/main/java/com/example/vitruvianredux/util/BleConstants.kt`
- `app/src/main/java/com/example/vitruvianredux/data/ble/VitruvianBleManager.kt`
- `app/src/main/java/com/example/vitruvianredux/data/repository/BleRepositoryImpl.kt`
- `app/src/main/java/com/example/vitruvianredux/util/ProtocolBuilder.kt`
- `app/src/main/java/com/example/vitruvianredux/util/ProtocolTester.kt`

Notable behaviors already present upstream:

- scan filtering for Vitruvian devices (`Vee`)
- Nordic BLE manager based connection handling
- monitor-data and rep-event flows
- deload/release safety signaling
- reconnection-request flow
- protocol-testing utilities for unstable devices

## Recommended MirrorDash Boundary

MirrorDash should keep a thin adapter layer, conceptually:

```text
Vitruvian Phoenix BLE/Protocol
        ->
MirrorDash Vitruvian adapter
        ->
Normalized FitnessTelemetry
        ->
Gym session engine
        ->
Gym UI
```

That adapter should be responsible for:

- mapping Phoenix metrics into our telemetry model
- translating connection state into our gym connection state
- exposing only the commands MirrorDash actually needs
- keeping safety-critical machine commands separate from scoring/game logic

## Why Thin Integration Matters

Phoenix is architecturally different from MirrorDash:

- Phoenix uses Hilt and Room.
- MirrorDash uses a lightweight app container and DataStore-backed settings.
- Phoenix is a full machine-control app.
- MirrorDash needs a mirror-room fitness subsystem inside an existing launcher.

The safest path is to adapt, not transplant.

## Fork Layout

Once we have our own fork, use:

```bash
git remote rename origin upstream
git remote add origin <our-fork-url>
git fetch upstream
git fetch origin
```

Recommended branches:

- `upstream/main` or the upstream default branch
- `main` for our maintained integration branch
- short-lived feature branches for isolated adapter work

## Update Workflow

```bash
git fetch upstream
git checkout main
git merge upstream/main
```

If upstream uses a different default branch name, adjust accordingly.

## Allowed Modification Zone

Preferred:

- small extraction helpers
- adapter-facing BLE/repository APIs
- bug fixes that are likely upstreamable
- protocol docs and test harness improvements

Avoid:

- rewriting the BLE stack in our fork
- mixing MirrorDash UI concerns into Phoenix
- removing or bypassing safety behavior
- replacing Phoenix architecture wholesale

## Patch Inventory

Current MirrorDash repo state: no Phoenix source modifications have been made yet.

If changes become necessary, maintain a numbered list such as:

- `001` Adapter-safe BLE facade
- `002` Headless telemetry export
- `003` Additional reconnect diagnostics

Each item should note whether it can be proposed upstream.

## Safety Notes

Vitruvian control must remain independent from gamification:

- session score must never directly drive machine load
- challenge logic must not issue resistance/load changes
- deload/disconnect handling must retain higher priority than UI state

MirrorDash can celebrate the workout, but Phoenix-style device safety must remain authoritative.
