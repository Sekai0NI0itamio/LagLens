# LagLens developer guide

## Measurement pipeline

```
collectors/ ──1 Hz + per-frame──▶ LagLensSession ──snapshot──▶ DiagnosticEngine
     │                                   │                           │
     │ phases: collect → store → correlate → analyze → report → UI   │
     ▼                                   ▼                           ▼
 bounded buffers                  MetricsSnapshot              List<Finding>
 (RingBuffer, TimeSeries,          (typed, units fixed,         (severity, confidence,
  event deques; all capped)        unavailable ≠ zero)          evidence, next step)
```

Seams between stages are small interfaces:

- `LagLensSession.MinecraftWorldReader` — the only seam touching Minecraft
  classes for sampling (production: `ClientWorldReader`; tests: null reader).
- `IntegrationRegistry.ModQuery` — mod-presence checks without Loader in tests.
- `ReportWriter.buildJson` — pure report construction, tested without disk.

## Packages

- `com.laglens` — client entrypoint (`LagLensMod`).
- `command`, `client` — `/laglens` tree, live world reader.
- `ui` — `LagLensScreen` (navigation/input) + `PageRenderer` (page content).
- `metrics` — `Clock`, `RingBuffer`, `FrameStats`, `SpikeDetector`,
  `TimeSeries`, `MetricsSnapshot` (all pure, all unit-tested).
- `collectors` — one class per domain, each independently disableable.
- `analysis` — `DiagnosticEngine` + `Evidence`/`Severity`/`Confidence`/`Finding`.
- `capture` — `CaptureSession` (bounded) + `ReportWriter`/`ReportSchema`.
- `config` — validated `LagLensConfig`.
- `integration` — optional-mod registry + companion protocol constants.

## Instrumentation interfaces

New collectors follow this shape:

1. A pure state class in `collectors/` (bounded storage, `available()` flag,
   `reset()`).
2. A guarded probe in `ClientWorldReader.sample` (or a Fabric event in
   `LagLensMod`), each in its own try/catch that marks unavailable on failure.
3. Fields on `MetricsSnapshot.Builder` with documented units.
4. A rule in `DiagnosticEngine` (or an explicit "shown, not diagnosed" page
   section) plus unit tests.

Rules for probes: monotonic clock for durations, no per-frame allocation,
bounded iteration, never touch server classes from client code, never invent
a value when the hook is missing.

## Adding a diagnostic rule

Add a case to `DiagnosticEngine.analyze` with: minimum-sample guard,
availability guard, coincidence threshold, `Evidence` at most
`STRONG_CORRELATION` unless a dedicated instrument backs it, and a `nextStep`.
Cover it in `DiagnosticEngineTest` (fires when it should, silent when data is
missing).

## Tests and CI

- `src/test`: JUnit 5, pure-logic only (no Minecraft classes on that path).
- CI (`.github/workflows/build.yml`): `help` → `compileJava` → `test` →
  `build` → artifact verification → upload JAR + reports. Least-privilege
  (`contents: read`), 30 min timeout.
- In-game validation that CI cannot do (screen layout, keybind feel, menu
  behavior) is listed honestly in CRITERIA.md and the README.

## Versioning

- Mod version: `gradle.properties` (`mod_version`).
- Report schema: `ReportSchema.SCHEMA_VERSION` (`laglens-report/1`).
- Companion protocol: `ServerCompanionProtocol.PROTOCOL_VERSION` (see
  `docs/SERVER_PROTOCOL.md`).
