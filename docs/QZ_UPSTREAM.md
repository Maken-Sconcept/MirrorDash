# QZ Upstream Strategy

Status captured on Monday, August 17, 2026 from the local checkout at `external/qdomyos-zwift`.

## Current Local State

- Local path: `external/qdomyos-zwift`
- Current remote:
  - `origin https://github.com/cagnulein/qdomyos-zwift.git`
- License in checkout: GNU GPL v3
- Existing upstream-friendly integration point: `docs/40_web_socket_api.md`

Important constraint:

- QZ is GPL software. Do not paste QZ source into MirrorDash and treat it as ordinary in-process app code.

## Recommended Boundary

Use QZ as a separate GPL device engine and integrate through its existing socket layer.

Recommended runtime shape:

1. QZ connects to the Echelon bike over BLE.
2. QZ exposes workout/device telemetry over its existing WebSocket API.
3. MirrorDash consumes that telemetry through a thin adapter.
4. MirrorDash converts it into our normalized `FitnessTelemetry`.
5. The gym session engine and UI remain vendor-neutral.

This keeps:

- QZ changes isolated
- upstream rebases realistic
- licensing clearer
- MirrorDash free of scattered QZ internals

## Why WebSocket First

QZ already documents a WebSocket API with:

- periodic workout state events
- commands such as `start`, `pause`, `stop`
- control messages such as `setresistance`

That means we should prefer consuming an existing published interface before considering driver edits.

## Fork Layout

Once our own fork exists, use:

```bash
git remote rename origin upstream
git remote add origin <our-fork-url>
git fetch upstream
git fetch origin
```

Recommended branch model:

- `upstream/master` or `upstream/main`: official source of truth
- `main`: our maintained integration branch
- short-lived feature branches for QZ-specific changes

## Update Workflow

```bash
git fetch upstream
git checkout main
git merge upstream/master
```

If the upstream default branch changes, adapt the final command accordingly.

If we want a cleaner linear history:

```bash
git fetch upstream
git checkout main
git rebase upstream/master
```

Use merge if we expect multiple local integration branches. Use rebase only if the team is comfortable force-pushing the fork.

## Allowed Customization Zone

Preferred custom work:

- dedicated bridge files that translate QZ WebSocket payloads to MirrorDash telemetry
- launch/config scripts for headless or appliance mode
- narrowly scoped startup automation
- documentation and configuration defaults

Avoid unless absolutely required:

- editing core Echelon device drivers
- threading MirrorDash assumptions through QZ UI code
- removing license/subscription flows
- broad UI rewrites inside QZ

## Patch Inventory

Current MirrorDash repo state: no QZ source modifications have been made yet.

If future changes become necessary, maintain an inventory like:

- `001` Local mirror telemetry bridge
- `002` Headless launch profile
- `003` Auto-connect profile for mirror hardware

Each patch should document:

- reason
- touched files
- whether upstream contribution is possible
- expected conflict risk on rebase

## Licensing Obligations

If we distribute a modified QZ build:

- keep the GPL license text
- preserve copyright notices
- mark modifications clearly
- make corresponding source available as required by GPL

MirrorDash should treat QZ as an external GPL component, not as a silent code donor.

## MirrorDash Integration Recommendation

Short term:

- Keep today’s mock gym telemetry inside MirrorDash.
- Stand up a QZ bridge adapter that reads its WebSocket workout events.

Long term:

- Run QZ in a controlled, separate process/service on the mirror.
- Normalize bike metrics into the same player/session pipeline used by the Gym tab.
