# Adaptive daily and weekly challenges

## Investigation

MirrorDash is a native Android/Jetpack Compose app. `GymSessionEngine` creates live sessions and `GymRepository.appendSession` persists completed `GymSessionRecord`s, derives XP, totals, weekly streaks and progression. `GymAchievementCatalog` is configuration-driven and evaluates persisted history. `GymProgression` contains qualifying-workout, weekly-boundary and XP formulas. `GymGenerator` already creates goal/level/equipment-aware workouts. `GymChallengeDefinition` currently represents fixed bike/strength programs, not expiring personalized instances.

The app stores profile, device and session state locally. There is no backend, authentication, server clock, push-notification scheduler, injury/limitation data, timezone preference, RPE feedback, or screenshot-test framework. BLE and Health Connect work is in progress; live device data must remain optional. Existing JUnit tests cover core progression/domain logic.

## Proposed incremental architecture

Add a `GymAdaptiveChallenge` feature alongside—not instead of—`GymChallengeDefinition`:

* Immutable, versioned templates define compatible movement slots and safe difficulty ranges.
* An assigned instance contains exact targets, `createdAt`, local expiry, profile ID, deterministic seed, lifecycle, used workout IDs and reward state. It is never re-prescribed on render.
* A deterministic generator consumes existing profile goal/level/equipment/progression plus recent session history. It selects a complementary template (push/pull/core, lower/posterior/core, cycling interval, endurance, mobility) and applies conservative workload coefficients: Easy .70, Medium 1.00, Hard 1.15, Insane 1.30. Insane is unavailable until sufficient qualifying history and completion reliability exist.
* Fairness guardrails avoid a repeated high-load movement family after a recent qualifying session, fall back to mobility/steady cardio, and never infer injury, pain, or medical status. Injury management is explicitly out of scope for MirrorDash challenges.
* Daily expiry is local end-of-day; weekly expiry is Sunday 23:59:59 local time in phase 1. Use injected `Clock`/`ZoneId` for deterministic tests. The current local-only repository cannot claim server-authoritative anti-clock-tampering or cross-device idempotency; those need backend sync.
* Progress is computed only from saved qualifying sessions and each saved workout ID is recorded once per challenge. Daily and weekly can both advance, but qualifying work receives at most one multiplier: daily work is 2x total XP (base + base bonus), weekly work is 5x total XP (base + 4x bonus). If one workout qualifies for both, the weekly 5x multiplier wins; it never stacks to 10x. Completion rewards are fixed and separately idempotent.

## Data and XP changes

Persist challenge instances and completion/reward ledger in existing settings-backed Gym state. Reuse `GymRepository.appendSession` as the sole write point for base XP/history. It will calculate exactly one challenge bonus: daily `base * 1`, weekly `base * 4`, and a fixed completion reward once.

## Delivery phases

1. Models, templates, deterministic generator, expiry/recovery/XP unit tests, and main-page cards.
2. Persist assignments; attach saved workouts to progress; add start/continue and 5x reward ledger.
3. Feedback, swapping, deeper capability dimensions, challenge achievements, notifications, and optional server sync.

## Safety and limitations

Prescriptions are fitness guidance, not medical advice. MirrorDash does not collect, store, infer, or manage injury information. Fairness is provided through conservative equipment-compatible templates, relevant capability, recent training load, completion reliability, easier swaps, and eligibility-gated Insane prescriptions.
