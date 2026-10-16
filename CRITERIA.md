# Criteria: LagLens v0.1.0 (first release candidate)

Evidence: GitHub Actions run 38895056560 (green, 2026-10-16) — validate,
compileJava, test, build, artifact verification all BUILD SUCCESSFUL;
53 unit tests, 0 failures/errors/skips (see laglens-test-reports artifact);
`laglens-0.1.0.jar` (111 KB, remapped, contains fabric.mod.json v0.1.0)
downloaded and inspected from the laglens-jar artifact.

## Foundation
- [x] `LagLens` folder exists in workspace root; git repo initialized inside `LagLens`, not the parent
- [x] Public GitHub repo `LagLens` created; local repo connected; no unrelated files committed
- [x] GitHub Actions `build` workflow passes: checkout, Temurin Java 21, Gradle 8.10.2, caching
- [x] `./gradlew build` equivalent compiles the mod in CI (no local compilation used as evidence)
- [x] All unit tests pass in CI; test reports uploaded
- [x] Remapped mod JAR exists, is non-empty, uploaded as a CI artifact with test reports
- [x] No production release created; no Modrinth/CurseForge upload

## Functionality (verified by tests + code review of hooks)
- [x] `/laglens` opens the dashboard; subcommands `status`, `capture`, `export`, `config` respond
- [x] F9 keybind opens the dashboard (registration + tick poll; in-game feel needs manual check)
- [x] Frame-time history + P50/P95/P99 + spike detection computed from real samples (logic tested; HUD-pass hook placement reviewed)
- [x] Memory/GC page reads real MXBean data; unavailable values shown as unavailable, never zero
- [x] Entity, chunk, network, server pages show measured data or explicit unavailable reasons
- [x] Findings carry severity, confidence, evidence label, window, unknowns, next step
- [x] Capture start/stop/export writes versioned JSON + Markdown reports locally and asynchronously
- [x] Per-mod CPU attribution is never fabricated; evidence labels used honestly
- [x] README documents metrics, limitations, privacy, CI artifacts; LICENSE (MIT) present

## Failure cases (must hold)
- [x] No measurement is invented when an integration is absent (Sodium/Iris/server companion missing)
- [x] Disabled collectors do no work; buffers stay bounded under sustained load (bounded-buffer tests + review)
- [x] Failing CI is fixed via code changes + push, never by weakening checks

## Remaining manual validation (CI cannot do this; documented in README)
- [ ] In-game: dashboard layout at various window sizes, keybind feel, menu behavior
- [ ] In-game: frame-graph movement, capture/export files on disk, report contents
- [ ] Multiplayer: ping display, disconnect reset, remote server page shows unavailable
