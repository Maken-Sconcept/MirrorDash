# MuscleWiki 3.2.3 Research

Source package inspected:

- `C:\Users\Maken\Downloads\MuscleWiki_+Workout+&+Fitness_3.2.3_APKPure.xapk`

Decoded outputs:

- `analysis/musclewiki_3_2_3_xapk2/`
- `analysis/musclewiki_3_2_3_apktool/`

## What it is

- Flutter app, not a native Android UI we can directly port into Compose.
- Main activity: `com.musclewiki.macro.MainActivity`
- Uses RevenueCat for premium/paywall flows.
- Uses Health sync permissions for workouts, calories, heart rate, steps, and distance.
- Deep links indicate public product areas for `share`, `ref`, `exercise`, and `deals`.

## Useful product patterns

The app is built around four strong ideas we can reproduce natively in MirrorDash:

1. Exercise discovery by equipment/body map.
2. Single workout generation from user preferences.
3. Weekly routine generation from goals, days, split, and equipment.
4. Saved workouts plus in-session tracking, reordering, sharing, and progress summaries.

## Generator flow found in strings

The bundled English language file shows two separate generation flows:

### Workout generator

- Goal
- Level
- Number of exercises
- Regenerate workout
- Remove exercise from generated workout
- Estimated time
- Save to My Workouts

### Routine generator

- Personalize / Info
- Level
- Goal
- Days
- Split
- Equipment
- Generate
- Routine preview
- Start workout
- Save routine
- Regenerate routine
- Swipe to remove exercises
- Targeted muscles / body map
- Recovery-aware scheduling
- "Let AI decide" split option

## Bundled content that matters

Inside `assets/flutter_assets/assets/` the app ships:

- `data/categories.json`
- `data/content/<locale>/exercises.bin`
- `data/content/<locale>/routines.bin`
- `data/content/<locale>/workouts.bin`
- `data/muscles.json`
- `data/muscle_group.json`
- `data/exercise_strength_standards.json`
- body map and skeleton assets
- category icons
- workout illustrations and summary art

The categories file is plain JSON and confirms the generator is equipment-driven. Categories include:

- Barbell
- Dumbbells
- Bodyweight
- Machine
- Kettlebells
- Cables
- Band
- Cardio
- Stretches
- TRX
- Pilates

The `exercises.bin`, `routines.bin`, and `workouts.bin` files are bundled binary data. They are likely app-specific serialized content, not a drop-in format for MirrorDash.

## What to borrow for MirrorDash

We should borrow the structure, not the proprietary implementation:

- A `Create Workout` flow with:
  - goal
  - level
  - equipment available
  - desired workout length or exercise count
- A `Create Routine` flow with:
  - days per week
  - split strategy
  - recovery-friendly spacing
- A generated result screen with:
  - workout cards
  - targeted muscle summary
  - estimated duration
  - save / regenerate / start actions
- A session HUD that can show:
  - elapsed time
  - calories
  - heart rate
  - body-part emphasis

## Best fit in current MirrorDash code

Current gym surface lives in:

- `app/src/main/kotlin/com/sconcept/mirrordash/gym/GymScreen.kt`
- `app/src/main/kotlin/com/sconcept/mirrordash/gym/GymViewModel.kt`
- `app/src/main/kotlin/com/sconcept/mirrordash/gym/GymModels.kt`

Best insertion points:

- Add generator preference models to `GymModels.kt`
- Add generator state and actions to `GymViewModel.kt`
- Add `Create Workout` and `Create Routine` entry cards to `GymScreen.kt`
- Reuse the existing setup sheet pattern for a multi-step generator wizard

## Recommended MirrorDash v1

Build our own Mirror-native generator with generic fitness concepts:

- Goals: build muscle, gain strength, lose weight, mobility, recovery
- Levels: novice, beginner, intermediate, advanced
- Equipment: bodyweight, dumbbells, barbell, kettlebells, bands, cables, bench, bike, rower
- Output:
  - warmup
  - main blocks
  - finisher
  - cooldown

This gives us the MuscleWiki-style usefulness without depending on their Flutter code, bundled binaries, or premium asset pipeline.
