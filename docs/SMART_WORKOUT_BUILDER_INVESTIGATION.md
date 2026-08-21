# Smart Workout Builder investigation

## What is already in place

- The exercise catalogue is merged from `gym/workout_catalog.json` and `gym/workout_library.json`.
- Each library video supplies a local URI or NAS-relative path. `GymContentRepository` resolves the local source first, then downloads the NAS source into the app cache for playback.
- The existing workout generator already ranks real catalogue exercises from goal, level, equipment, selected muscles, duration, and desired exercise count.

## Gaps addressed by this change

- Exercise rows previously opened a modal, which made video playback and follow-up actions feel temporary.
- There was no user-owned workout queue or favourites collection beside the automatic generator.
- The Exercises tab did not make the current selection visible while browsing.

## Delivery approach

The Gym dashboard now keeps an in-memory, screen-scoped workout queue and favourites collection. The exercise detail is a full dashboard surface with a large, video-first player, real targets/equipment, a favourite control, and an add/remove workout action. The existing generator remains the source of truth for generated sessions; the queue is deliberately kept separate until a persisted custom-session format is added to the session engine.

## Known boundary

Favourites and the queue survive navigation within the Gym screen, but not process restart. Persisting them needs a backwards-compatible settings migration and a custom-session payload in `GymSessionEngine`; this was not safe to infer without changing session history semantics.
