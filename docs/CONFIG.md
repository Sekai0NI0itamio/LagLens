# LagLens configuration reference (`config/laglens.json`)

Edit while the game is closed. Invalid values are clamped to safe defaults on
load (corrections are logged). Every option maps to an implemented behavior —
no placebo settings.

| Key | Default | Meaning |
|---|---|---|
| `defaultPage` | `"overview"` | Dashboard page opened by `/laglens` and F9 |
| `sampleIntervalTicks` | `20` | Ticks between 1 Hz-style sampler runs (1–200) |
| `historySeconds` | `300` | Rolling frame-history length, seconds (30–1800, bounded buffer) |
| `spikeWarnMs` | `34.0` | Frame time above this is a spike (~2 missed 60 FPS frames) |
| `spikeSevereMs` | `100.0` | Frame time above this is severe |
| `recurringSpikeMinimum` | `3` | Spikes per window before a problem counts as recurring |
| `monitoringLevel` | `"LIGHT"` | `LIGHT` totals only; `DETAILED` adds per-type entity/BE breakdowns |
| `rollingBufferSec` | `30` | Pre-spike rolling data kept for captures |
| `autoCaptureOnSevereSpike` | `false` | Start a LIGHT capture automatically on a severe spike |
| `autoCaptureSec` | `20` | Length of severe-spike automatic captures, seconds (manual captures run until stopped) |
| `reportDir` | `"laglens-reports"` | Report directory under the game dir (no `..` allowed) |
| `reportRetention` | `20` | How many reports are kept; older `laglens-report-*` files pruned on export |
| `includeEnvMetadata` | `true` | Include OS/CPU/heap metadata in reports |
| `privacyStripEnv` | `false` | Drop hostname/username from reports when true |
| `enableEntitySampling` | `true` | Entity and block-entity census (Entities page needs this) |
| `enableChunkEvents` | `true` | Chunk load/unload event timeline (Terrain page needs this) |
| `pingWarnMs` | `150.0` | Ping above this with stable frames → network finding |
| `serverTickWarnMs` | `50.0` | Tick average above this → server finding (authoritative data only) |
| `chunkEventsWarnPerMin` | `20` | Chunk-event rate considered noteworthy |
| `entityWarnCount` | `150` | Loaded-entity count mentioned in rendering findings |
| `gcTimeWarnMs` | `200.0` | GC time per window considered noteworthy |
| `debugLogging` | `false` | Verbose LagLens self-diagnostics in the log |

Tuning advice: raise `spikeWarnMs` on high-refresh displays (e.g. 20 ms at
120 Hz baselines still flags real hitches; lower it only if isolated spikes
spam findings), and prefer longer `historySeconds` over lower thresholds when
chasing intermittent stutter.
