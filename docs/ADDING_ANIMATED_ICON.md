# Adding a native animated Lucide icon

1. Add a Lucide drawable/resource (or imported 24x24 geometry) and a unique `AnimatedLucideDefinition` in `AnimatedLucideRegistry`.
2. Give every animation target a stable ID. Use `root` only for whole-icon transforms.
3. Define one or more `AnimationTrack`s with explicit keyframes, easing, origin and replay policy.
4. Register the definition by name. The renderer does not need changing.
5. Add unit tests for sorted keyframes, replay policy and the representative progress values 0/25/50/75/100.

Do not convert upstream TSX with regex. A future importer must use an AST or a controlled exported JSON IR, pin the upstream commit, and emit diagnostics for unsupported Motion properties. Keep generated definitions separate from hand-authored overrides.
