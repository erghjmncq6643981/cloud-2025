# FreeSWITCH Operations Frontend Rules

## Product Boundary

- This project is the FreeSWITCH/Sidecar operations console. It consumes Sidecar `/api/v1` HTTP and console-log WebSocket endpoints.
- It does not own FCC business calls, agents, routing, authentication, or customer workflows. Those belong to `demo-2026/chandler26-jdk21-fcc` and its business frontends.
- Raw CLI execution is a restricted operations escape hatch. Do not construct raw FreeSWITCH commands in view components or present CLI success as a verified business outcome.

## Structure And Data

- New product code belongs in `src/features/**`; generic transport, UI, formatting, and feedback utilities belong in `src/shared/**`.
- Feature domains are `dashboard`, `extensions`, `channels`, `cdr`, `sip`, `gateways`, `modules`, and `terminal`.
- A feature owns its UI, API adapter, state/composable, pure models, and tests. Keep `App.vue` limited to shell, navigation, and top-level polling coordination.
- At 600 production lines perform a design review; at 1000 lines splitting is mandatory. Split by table/query state, forms, command actions, protocol mapping, and presentation.
- Centralize response mapping in API adapters. Views never depend on multiple possible field names or silently fabricate healthy defaults.
- Treat database IDs, channel UUIDs, call UUIDs, and node IDs as opaque strings.
- Centralize polling, cancellation, backoff, and freshness metadata. Every interval, WebSocket, and listener must be released on unmount.

## Operations Interaction

- Status screens distinguish loading, connected, degraded, stale, offline, permission denied, and command failure.
- Registration flush, channel kill/transfer, gateway changes, extension deletion, reload, and raw CLI execution require explicit confirmation and readable outcome feedback.
- Do not swallow request errors. A failed refresh must preserve the last known data as stale or clear it explicitly; it must not continue to look live.
- Keep the console dense and scan-oriented. Avoid decorative metrics and placeholder values presented as measurements.
- Never render or return gateway passwords, ESL credentials, database credentials, raw authorization headers, or unbounded protocol payloads.

## Verification

- Run `npm run build` after Vue, TypeScript, import, or configuration changes.
- Add focused tests when extracting response mapping, filtering, polling, command validation, or log parsing logic.
- Verify changed workflows in a browser, including offline Sidecar, PostgreSQL unavailable, WebSocket reconnect, empty registrations/channels, long identifiers, and destructive confirmation.
- Run `git diff --check` and confirm that `dist`, coverage output, local logs, binaries, and generated artifacts are not included.

## Focused Review

- For feature ownership, oversized views, state, API mapping, and browser quality, use `../../demo-2026/agents/frontend-engineering-governance.md` when the sibling product workspace is available.
- For commands, Sidecar events, Channel state, logging WebSocket, and FreeSWITCH outcomes, use `../../demo-2026/agents/fcc-realtime-reliability.md`.
- For privileged operations, credentials, raw CLI, recordings, and protocol data, use `../../demo-2026/agents/security-and-authorization.md`.
- For documentation, generated artifacts, build configuration, and cross-repository scope, use `../../demo-2026/agents/repository-integrity.md`.
