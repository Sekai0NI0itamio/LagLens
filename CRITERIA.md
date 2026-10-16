# Criteria: LagLens v0.1.0 (first release candidate)

Every box is checked only with CI evidence (workflow run + logs + artifacts) in view.

## Foundation
- [ ] `LagLens` folder exists in workspace root; git repo initialized inside `LagLens`, not the parent
- [ ] Public GitHub repo `LagLens` created; local repo connected; no unrelated files committed
- [ ] GitHub Actions `build` workflow passes: checkout, Temurin Java 21, Gradle 8.10.2, caching
- [ ] `./gradlew build` equivalent compiles the mod in CI (no local compilation used as evidence)
- [ ] All unit tests pass in CI; test reports uploaded
- [ ] Remapped mod JAR exists, is non-empty, uploaded as a CI artifact with test reports
- [ ] No production release created; no Modrinth/CurseForge upload

## Functionality (verified by tests + code review of hooks)
- [ ] `/laglens` opens the dashboard; subcommands `status`, `capture`, `export`, `config` respond
- [ ] F9 (configurable category) keybind opens the dashboard
- [ ] Frame-time history + P50/P95/P99 + spike detection computed from real samples
- [ ] Memory/GC page reads real MXBean data; unavailable values shown as unavailable, never zero
- [ ] Entity, chunk, network, server pages show measured data or explicit unavailable reasons
- [ ] Findings carry severity, confidence, evidence label, window, unknowns, next step
- [ ] Capture start/stop/export writes versioned JSON + Markdown reports locally and asynchronously
- [ ] Per-mod CPU attribution is never fabricated; evidence labels used honestly
- [ ] README documents metrics, limitations, privacy, CI artifacts; LICENSE (MIT) present

## Failure cases (must hold)
- [ ] No measurement is invented when an integration is absent (Sodium/Iris/server companion missing)
- [ ] Disabled collectors do no work; buffers stay bounded under sustained load
- [ ] Failing CI is fixed via code changes + push, never by weakening checks
