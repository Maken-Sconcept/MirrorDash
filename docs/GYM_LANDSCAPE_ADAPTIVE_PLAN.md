# Gym landscape adaptive plan

## Investigation

The Gym feature is a Jetpack Compose surface (`GymScreen`) hosted by `MirrorDashActivity`. Its long-lived state is owned by `GymViewModel` and the application-scoped `GymSessionEngine`; profile, device preference, and history state are persisted through `GymRepository`. The manifest leaves activity orientation unspecified. This means the active workout timer, device connections, session id, and save de-duplication are not owned by orientation-specific composables.

`GymScreen` contains the dashboard, workout setup, profiles, achievements, exercise catalog, connection center, session summaries, active workout HUD, cycling free ride, and split-player HUD. Existing `BoxWithConstraints` use adapts dashboard cards, but the active single-player workout had one portrait-first vertical composition.

## Adaptive architecture and tiers

`GymLayoutTier` is the reusable, dimension-based presentation primitive. It evaluates available width and height rather than a raw orientation flag:

| Tier | Window criteria | Presentation |
| --- | --- | --- |
| Portrait | height >= width | Existing stacked experience |
| Compact landscape | height < 480dp or width < 700dp | Two panes: metric stage and compact control rail |
| Medium landscape | otherwise | Metric stage plus 304dp persistent control/telemetry rail |
| Expanded landscape | >= 1200×650dp | Metric stage plus 356dp persistent rail for tablet/mirror readability |

The active single-player session now uses the tier for intentional landscape layout. Workout identity, exercise metric stage, progress/rest state, score, pause/resume, end/save, quit, and status feedback remain available in the same stable locations. Compact mode preserves controls and progress while moving telemetry into the primary metric stage to avoid a vertically overfull rail.

## State-preservation audit

Active workout state is held in `GymSessionEngine.runtimeState`, itself created from an application container. `GymViewModel` observes it instead of holding session state in `remember`; orientation recreation re-collects the same state. Session history is de-duplicated by session id in `GymRepository.appendSession`, avoiding duplicate rewards/history writes. Dashboard-specific selections use a `StateFlow` in the view model; sheet and picker visibility use `rememberSaveable`.

The user-selected free-ride video URI is saveable, but the embedded `VideoView` does not yet retain playback position across recreation. This remains a migration item before claiming video-rotation parity.

## Screen/component inventory and landscape direction

| Surface | Current adaptive approach | Landscape direction |
| --- | --- | --- |
| Dashboard/workout library | Constraint-aware cards and sections | Multi-column groups, constrained reading width |
| Workout generator | Step rail and selectable cards | Keep step rail visible beside the active step on medium/expanded windows |
| Active workout | New tiered HUD | Persistent action rail; compact/medium/expanded panes |
| Cycling free ride | Media + fixed metric rail | Replace fixed rail with tiered metric grid; preserve media state |
| Split session | Two player panels | Stack panels below 700dp width, preserve a shared action bar |
| Challenges, history, profiles, catalog | Scrollable dashboard sections/dialogs | Responsive grids and width-capped dialogs |
| Achievements, profile editor, summaries | Sheets/dialogs | Centered, width-capped surfaces with side-panel option on expanded displays |
| Device connection center | Dashboard section | Persistent secondary pane at expanded widths |

## Test and rollout matrix

Unit coverage verifies portrait, compact landscape, medium landscape, and expanded mirror tier selection. Add Compose screenshot tests for 760×420, 1024×700, 1920×1080, portrait phone, and portrait tablet once the project’s Android test harness is introduced. Repeatedly rotate active workout, rest timer, setup input, dialog, and free-ride playback; verify no duplicate session record, XP reward, or device reconnect.

Future Gym screens must obtain `GymLayoutTier` at the presentation boundary and share existing view-model/session-engine state. Do not branch business logic by orientation.
