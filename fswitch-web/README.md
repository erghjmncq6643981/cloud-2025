# fswitch-web

FreeSWITCH operations console for the Go Sidecar. The application is intended for communications engineers and platform operators; it is not the FCC agent desktop or business administration console.

## Runtime relationship

```text
Browser :8008
    |
    | HTTP /api/v1 and WebSocket /api/v1/telephony/ws/console-logs
    v
chandler25-fs-sidecar-agent :8088
    |                         |
    | ESL :8021               | PostgreSQL
    v                         v
FreeSWITCH              runtime/configuration data
```

The Vite development proxy targets `http://127.0.0.1:8088`.

## Implemented surfaces

- Node and FreeSWITCH status dashboard.
- SIP registrations and extension lifecycle operations.
- Active channels/calls with kill and transfer controls.
- Sofia profile and gateway inspection/management.
- Raw FreeSWITCH CDR query.
- Restricted `fs_cli` command execution.
- Console log WebSocket with reconnect behavior.

All API access is centralized in `src/api/telephony.ts`. The application polls the main status collections every three seconds.

## Development

Prerequisites:

- Node.js compatible with Vite 6.
- Running Sidecar on port `8088`.
- Sidecar dependencies such as FreeSWITCH, NATS, and PostgreSQL as required by the inspected page.

```bash
npm ci
npm run dev
npm run build
```

Development URL: `http://localhost:8008`.

## Engineering baseline

Project-specific rules are in [AGENTS.md](./AGENTS.md). New work must move toward feature-owned modules under `src/features/**`, centralized polling/error state, and explicit confirmation for service-affecting actions.

Current debt that must not be expanded:

- `ExtensionsView.vue` is above the 600-line design-review threshold.
- Several views combine query state, forms, commands, and rendering.
- There is no automated frontend test suite.
- Refresh failures are currently easy to hide behind last-known or default values.
- Sidecar management endpoints include privileged and raw-command operations; production deployment requires network isolation, authentication, authorization, and audit at the serving boundary.

Generated output, local logs, recordings, binaries, and `dist` are not source artifacts.
