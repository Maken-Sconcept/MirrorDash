# Walkie-talkie home shortcuts: investigation

## Existing architecture

MirrorDash is a native Android launcher built with Jetpack Compose. `MirrorDashActivity` owns the
launcher layers and notification shade; `AppContainer` provides app-scoped services. The Clock
page is the home surface. Settings are a single DataStore-backed `MirrorDashSettings` flow, which
is already observed by both UI and the process-wide `WalkieTalkieEngine`.

## Devices and communication

Configured targets are `WalkieTalkiePeer(name, ip)`. Nearby peers arrive through NSD/mDNS in
`WalkieTalkieDiscoveryBridge`, and can be promoted into the configured list by the existing
auto-add option. The engine resolves peers to UDP addresses and owns the one audio session;
incoming and transmitting activity are exposed through `WalkieTalkieUiState`. The current
architecture uses the IP address as the peer identifier. Per the implementation decision for
this change, that existing identity stays internal and is reused for shortcut persistence.

## Current surfaces

The notification shade contains quick settings, brightness, volume, and system notifications,
but no walkie device controls. The Clock page is the home surface; Gym and Jellyfin are existing
launcher destinations rather than walkie peers. There is no existing room-shortcut registry.

## Proposed change

Add one persisted `walkieTalkieHomeShortcutIps` preference. Both the notification device bar and
Clock home shortcuts read that single value plus the live engine state. The notification bar shows
every configured peer, keeps unavailable peers visible, and separates the shortcut switch from a
press-and-hold talk action. The Clock surface renders only selected peers and starts the existing
walkie session.

## Persistence and risks

The preference stores only configured peer identifiers and does not add, remove, authorize, or
disconnect peers. An IP change remains a limitation of the pre-existing identity model; stale
entries are harmless and are reconciled when the peer list is rendered. TV, Jellyfin, and Gym are
not walkie peers in the current model, so no special destination is written to the preference.

## Testing

Add focused unit tests for initials and shortcut selection reconciliation, then compile the app.
The UI uses horizontal lazy rows, explicit content descriptions, disabled states, and separate
touch targets for remote/touch accessibility.
