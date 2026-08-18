# Echelon Reflect UX Research

Captured from the installed system app on Monday, August 17, 2026. Research source was the on-device APK plus static decompilation with `apktool` and `jadx`.

## Package + APK State

- Package: `com.echelonfit.reflecttouch`
- Version: `1.0.2` (`versionCode=2`)
- Code path: `/system/bundled_persist-app/reflect_touch_MT12`
- APK pulled to: `analysis/reflect_touch_MT12.apk`
- Decoded manifest/project: `analysis/reflect_touch_MT12_apktool/`
- Decompiled Java tree: `analysis/reflect_touch_MT12_jadx/`
- Current user state on the mirror: `installed=false`, `enabled=3`

The stock app is present as a system package but disabled for user 0, so this pass focused on behavioral and structural research rather than running full live flows.

## App Structure

The app is a classic support-library Android app, not Compose. Its high-level shell is:

- `LandingActivity`
- `LoginActivity`
- `CoreActivity`

Inside `CoreActivity`, the main experience is fragment-driven:

- `HomepageFragment`
- `CategoryListFragment`
- `CategoryWorkoutListFragment`
- `ScheduleFragment`
- `MoreFragment`
- `WorkoutPreviewFragment`
- `WorkoutFragment`
- `WorkoutSummaryFragment`
- `WifiConnectionFragment`

## Manifest Observations

The app requests and uses:

- Bluetooth + Bluetooth Admin
- Fine/coarse location
- Wi-Fi state + Wi-Fi mutation
- Network state
- Calendar read/write
- Internet
- Wake lock
- Write settings
- Storage

This lines up with a mirror appliance that expects to manage onboarding, local connectivity, media playback, and account-linked scheduling.

## Screen Findings

### Landing / Bootstrap

Observed behavior from `LandingActivity`:

- Enables Bluetooth automatically.
- Enables Wi-Fi automatically.
- Performs Wi-Fi scanning and presents a network picker dialog.
- Checks update status and remote config before entering the main experience.
- Shows a simple login entry point.
- Reuses saved user/session state when available.

What works well:

- The device behaves like an appliance, not like a generic tablet app.
- Connectivity is treated as first-run critical path.
- The boot experience is focused and operational.

Problems:

- It is very network-first and fairly imperative.
- It conflates setup, update, and account recovery into one launch funnel.
- The UI appears functional rather than premium.

Our implementation:

- Keep setup discoverable but not blocking for local-first gym mode.
- Surface device readiness inline from the Gym home screen.
- Reserve deep setup for the connection center and settings.

### Home Screen

Observed behavior from `HomepageFragment`:

- Large date/time treatment.
- Weather and ambient information.
- Tap-to-start behavior.
- Sparse, appliance-like idle state.

What works well:

- Good use of the mirror as a passive ambient surface.
- Large typography fits stand-back interaction.

Problems:

- The visual language is dated and opaque in places.
- It does not feel like a modern multiplayer fitness HUD.

Our implementation:

- Preserve the calm, stand-back-ready hierarchy.
- Use darker glass, thinner strokes, and cleaner type.
- Make connected devices and weekly progress visible without turning the page into settings.

### Core Navigation

Observed behavior from `CoreActivity`:

- Bottom navigation for category, schedule, and more.
- Fragment switching inside a single shell.
- Volume and brightness controls are always close at hand.
- The app includes local network and hotspot-oriented behavior.
- There is explicit logic for returning home after inactivity.

What works well:

- Single-shell navigation is easy to reason about.
- Appliance controls stay near the workout experience.

Problems:

- Navigation is more content-library-centric than training-centric.
- A bottom-nav media app shell is not enough for a premium gym operating system feel.

Our implementation:

- MirrorDash keeps its launcher architecture and adds Gym as an optional tab instead of replacing the shell.
- The active workout HUD is session-first, not content-library-first.

### Workout HUD

Observed behavior from `WorkoutFragment`:

- ExoPlayer-based class playback.
- Live timer, heart rate, calories, and leaderboard.
- Heart-rate zone scoring.
- Multiplayer/leaderboard framing.
- Pause/play overlay behavior.
- Workout summary handoff at session end.

What works well:

- Metrics are overlaid on the workout instead of fully hiding the person.
- Leaderboard and scoring create momentum.
- Timer and calorie/HR placement are easy to parse quickly.

Problems:

- The presentation is visually busy for a reflective surface.
- Scoring is tightly tied to the workout fragment instead of a reusable game/session domain.
- It assumes the media-class model more strongly than our unified room-level gym concept.

Our implementation:

- Rebuilt the concept as a mirror-first HUD inside MirrorDash with:
  - persistent session timer
  - player cards
  - live score/combo events
  - challenge selection
  - recent history
  - weekly progress
  - device connection strip
- The current implementation uses mock telemetry and normalized gym session state so the UI is not coupled to a single vendor app flow.

## Key Behavioral Patterns Worth Keeping

- Appliance-style startup
- Large clock/timer hierarchy
- Overlay-first workout metrics
- Visible connected-state feedback
- Graceful pause/resume and workout summary flow

## Key Choices We Should Not Copy

- Proprietary code or assets
- Branded class content
- Their exact navigation structure
- Their scoring implementation
- Their account/update assumptions

## MirrorDash Direction

MirrorDash should treat Reflect as reference behavior, not reference code:

- Original MirrorDash launcher remains intact.
- Gym & Workouts is a new optional tab.
- The mirror itself stays the visual canvas.
- Device communication, session state, and scoring live in our own domain layer.
