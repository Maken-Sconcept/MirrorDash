# Gym Video Import Example

Reference clip provided by the user:

- `\\SCONCEPT\Berthier\MirrorDash Recordings\CASA FR_2026-08-15_00-24-06.ts`

Observed media details available locally:

- container/file type: `.ts`
- duration: `44` seconds
- file size: `13,761,296` bytes
- shell-reported bitrate: about `384 kbps`

Notes:

- Resolution and frame-rate were not available from the tools installed in this environment.
- The clip is still usable as the reference shape for MirrorDash workout-video mapping.

Suggested use in MirrorDash:

- map each exercise name or exercise id to a local `videoPath`
- optionally add a `thumbnailPath`
- store workout tags, equipment, and short coaching cues beside the media path
- keep the video library external to code so new videos can be added without changing the gym UI

Example manifest file:

- `app/src/main/assets/gym/video_manifest_example.json`
