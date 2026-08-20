# Native Animated Lucide

## Investigation

MirrorDash is a single Android application module built with Jetpack Compose (Kotlin 2.0.21, Compose BOM 2024.10.01, minSdk 25, target/compileSdk 36). It uses `com.composables:icons-lucide-android:2.2.1` for the new Gym Lucide assets, but still has legacy Material icons across the application. Unit tests use JUnit; there is no screenshot-test framework. The existing Gym wrapper applies whole-icon scale/rotation only.

Lucide Animated is MIT-licensed React/Motion source. Its components preserve Lucide's 24x24 SVG geometry and use named normal/animate variants. Common patterns are element rotation, scale/translation keyframes, opacity, and path-length drawing. Its hover trigger is translated to native explicit, tap, visibility, state, and continuous triggers; touch does not depend on hover.

## Design

`animatedlucide` is a native Compose package. `AnimatedLucideDefinition` holds static geometry/resource metadata and named declarative timelines. The renderer evaluates any number of tracks, while `AnimatedLucideController` owns start/stop/reset/replay behavior. Definitions never contain playback coroutines. Static resource fallback remains available for icons without an animation definition.

The first engine supports root transform, opacity, keyframes, timing, repeat policy, manual/state/visible/continuous triggers and Android animator-disabled fallback. Element-targeted path trim and transforms are the next renderer capability; they require imported per-element paths rather than the current resource-only Lucide AAR assets.

## Compatibility

Fully supported now: resource-backed root transforms and opacity, concurrent tracks, keyframes, manual/state/visible/continuous triggers, restart/ignore/continue replay, static fallback. Partially supported: element-specific transforms and path reveal (modelled but awaiting imported geometry renderer). Unsupported definitions must remain unregistered rather than silently degrading.

Reference sources: Lucide (ISC) and lucide-animated (MIT). See `LUCIDE_ANIMATED_LICENSES.md`.
