# LagLens — Minecraft performance diagnostics

![build](https://github.com/Sekai0NI0itamio/LagLens/actions/workflows/build.yml/badge.svg)

LagLens is a **client-side** Fabric mod for Minecraft Java Edition 1.21.1 that
answers: **"What exactly is making my Minecraft lag, what evidence supports
that conclusion, and what should I do next?"**

It measures real frame times, memory/GC, entity and chunk activity, network
latency, and (where available) server tick data — then turns those
measurements into ranked, evidence-labeled findings with concrete next steps.
Reports export locally as JSON + Markdown for sharing with support forums.

LagLens is a **diagnostic tool, not an optimizer**: it observes, measures,
explains, and recommends. It never disables mods, removes entities, touches
packets, skips ticks, or changes gameplay.

## Who it is for

- Players with low FPS or stuttering who want evidence instead of guesses.
- Modpack users wondering whether entities, chunks, memory, or the server is
  the problem.
- Support volunteers who need a clean, versioned performance report.

## Requirements

- Minecraft Java Edition **1.21.1**
- Fabric Loader **>= 0.16.14** (pinned: 0.16.14)
- Fabric API **0.102.0+1.21.1**
- Java **21**

Only Minecraft 1.21.1 is supported in this release. Other versions are not
claimed until they are built and tested in CI.

## Installation

1. Install Fabric Loader for 1.21.1 and place Fabric API in `mods/`.
2. Download `laglens-<version>.jar` from a successful GitHub Actions run
   (**Actions → build → Artifacts → laglens-jar**).
3. Put it in `mods/` and launch. No server-side installation is needed; the
   mod is client-only and works on any server.

## Opening the dashboard

- Command: `/laglens` (client command, works anywhere).
- Keybind: **F9** by default (Minecraft controls → LagLens category).
- Subcommands: `/laglens status`, `/laglens capture start|detailed|stop`,
  `/laglens export`, `/laglens config`.

## Dashboard sections

| Page | What it shows |
|---|---|
| Overview | FPS, avg/P50/P95/P99 frame times, spike count, frame graph, heap, entities, chunks, ping, top finding |
| Bottlenecks | Ranked findings with severity, confidence, evidence label, and next steps (click to expand) |
| Terrain & Chunks | Loaded chunks, load/unload rate, spike coincidences; unavailable stages named honestly |
| Entities | Client entity totals (+ per-type in DETAILED mode), sampled block entities |
| Rendering | Frame distribution, render distance, shader/renderer presence, CPU/GPU caveats |
| Network | Ping, jitter, history, disconnects; packet inspection and loss explicitly unavailable |
| Memory & GC | Heap used/committed/max, GC count/time, last pause, how to read memory correctly |
| Server | Tick avg vs the 50 ms budget when an integrated server or companion provides data |
| Mods | Installed mod list, optional-integration status, and the no-fabricated-attribution policy |
| Capture & Reports | Recording workflow, retention, privacy state |
| Settings | Self-overhead, active thresholds, config location |

## Metrics: FPS vs frame time vs ping vs tick

- **FPS** is an instant rate; it hides micro-stutter. **Frame time** (ms per
  frame, with P95/P99) is the primary smoothness signal.
- **Ping** is round-trip responsiveness to the server; high ping with smooth
  frames is a network issue, not an FPS issue.
- **Server tick** is how long the server needs per 20 TPS tick (50 ms budget).
  A client cannot see a remote server's ticks — LagLens shows them only for
  the integrated server or a connected companion, otherwise "unavailable".

## Recording and exporting a report

1. Open the dashboard and press **C** (or `/laglens capture start`).
2. Reproduce the lag for 30–60 seconds.
3. Press **C** again, then **E** (or `/laglens export`).
4. Find `laglens-report-<timestamp>.json` + `.md` in `<game>/laglens-reports/`.
5. Read the Markdown, check the JSON, and share either. Nothing is uploaded
   automatically — ever.

## Findings and confidence

Every finding shows **severity** (Info/Warning/Critical), **confidence**
(Low/Medium/High), and an **evidence label**:

- *Directly measured* — a dedicated instrument saw it.
- *Profile-derived* — a sampling profiler integration supplied it.
- *Strong correlation* — repeated timing alignment (3+ coincidences).
- *Possible contributor* — plausible, unproven.
- *Insufficient evidence* — more capture needed; the UI says so.

Per-mod CPU percentages are **never** shown without a real profiler source.
A mod being installed is not evidence it is slow.

## Optional integrations

Sodium, Iris, Lithium, ModernFix, ImmediatelyFast, Entity Culling, and Spark
are detected when present and noted on the Mods page. None is required.
Coexistence is the goal; LagLens controls none of them.

## Server companion

Client-only by design. A future optional companion can supply tick data over
the versioned `laglens:diagnostics/v1` protocol — see
[`docs/SERVER_PROTOCOL.md`](docs/SERVER_PROTOCOL.md). Without it, remote tick
data is "unavailable", never guessed from ping.

## Troubleshooting

- **Dashboard shows "no frame samples"**: open a world first; the title
  screen produces no HUD-pass samples.
- **Server page unavailable on a server**: expected without the companion.
- **False "smooth" right after joining**: the engine needs ~30 samples before
  spike rules apply.
- **LagLens in the mod list but no `/laglens`**: you need Fabric API
  0.102.0+1.21.1; check `latest.log` for the `LagLens initialized` line.

## Known limitations

- Frame times measure the render-loop period at the HUD pass, not GPU time.
- Chunk mesh-rebuild/upload timings and server-side generation cost are not
  exposed by vanilla and are reported unavailable.
- No packet inspection or packet-loss measurement (application level only).
- No per-mod CPU attribution without a profiler integration.
- Title/pause-menu buttons are not added (no mixin injection in v1);
  command + keybind are the entry points.

## Privacy

Reports are written locally to `<game>/laglens-reports/` and never uploaded.
They include mod ids/versions and (optionally) hardware metadata. Set
`privacyStripEnv: true` to drop hostname/username, or
`includeEnvMetadata: false` to drop hardware data entirely. Inspect every
report before sharing.

## Development artifacts (CI)

Every push builds and tests via GitHub Actions (`build` workflow): compile,
unit tests, JAR build, artifact verification, JAR + test-report uploads.
Download development builds from the Actions tab. See
[`docs/DEVELOPER.md`](docs/DEVELOPER.md) for the pipeline and
[`docs/CONFIG.md`](docs/CONFIG.md) for every setting.

## Contributing

Issues and pull requests are welcome. Please include a LagLens report JSON
when reporting performance bugs, and keep PRs to client-side, mixin-free
changes unless an integration is discussed first.

## License

MIT — see [LICENSE](LICENSE). LagLens bundles no third-party assets.
