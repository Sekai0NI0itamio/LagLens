# LagLens server companion protocol (v1, design)

Status: **designed, not yet implemented**. The client release works fully
without any companion; this document reserves the wire design so a future
companion and client agree.

## Goals

- Let a consenting server share tick-distribution summaries with LagLens
  clients for client/server separation in diagnosis.
- Never expose profiling to unauthorized players; never accept profiling
  commands from clients.

## Transport

- Channel id: `laglens:diagnostics/v1` (see `ServerCompanionProtocol`).
- Encoding: JSON payloads, UTF-8, each under 64 KB.
- Cadence: summaries at 1 Hz; detailed profiling only inside an explicit,
  operator-confirmed session with a bounded duration (default 60 s).

## Messages

### `tick-summary` (server → permitted clients, 1 Hz)

```json
{
  "protocol": 1,
  "type": "tick-summary",
  "windowMs": 1000,
  "avgTickMs": 32.4,
  "p95TickMs": 48.1,
  "overBudgetTicks": 3,
  "tps": 20.0
}
```

### `profiling-start` / `profiling-stop` (server → client)

Operator-initiated only. Announces a bounded profiling window the client may
correlate against; the client cannot request one.

### `profiling-data` (server → client, end of window)

Subsystem and (where instrumented) mod-level timings with the same evidence
labels the client uses (`DIRECT`, `PROFILE_DERIVED`, ...).

## Security model

- Tick summaries go only to players with an explicit permission
  (e.g. `laglens.view`); detailed data requires a second permission
  (e.g. `laglens.profile`) **plus** an operator starting the window in-game
  or from console.
- No client-to-server profiling requests exist in v1; any such packet is
  ignored.
- No world data, chat, player IPs, or server file paths are transmitted.

## Client behavior

- Unknown protocol versions are ignored (client keeps "unavailable").
- Companion data is labeled `companion` in snapshots and reports, distinct
  from `integrated` data.
- If both integrated and companion sources exist (LAN host with companion),
  integrated wins for the local world and the companion is noted.
