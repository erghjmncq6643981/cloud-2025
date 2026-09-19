# FreeSWITCH Sidecar Rules

## Scope And Boundary

- This project is the Go node-side gateway deployed with FreeSWITCH. It owns ESL, FCC JSON-RPC translation, event normalization, node governance, PostgreSQL runtime access, and the operations HTTP/WebSocket surface.
- It does not own FCC agents, customer data, skills, business routing, flow versions, or business CDR projections.
- Java controls FreeSWITCH through NATS and this Sidecar. Do not add Java-specific business policy or a second control protocol.

## Structure

- Keep package responsibilities stable: `esl`, `rpc`, `event`, `nats`, `governance`, `db`, `api`, and `config`.
- Protocol DTOs and method/subject names are centralized; do not construct NATS subjects, ESL commands, or response envelopes throughout handlers.
- At 600 production lines perform a design review. Do not add substantial behavior to a file approaching 1000 lines; split by query/command path, protocol mapping, resource type, or persistence responsibility first.
- Existing hotspots `db/repo.go` and `api/handler_telephony.go` require decomposition before substantial feature growth.
- Exported types/functions require clear Go comments. Comments describe protocol/business invariants, not syntax.

## Protocol And Reliability

- Preserve the exact subjects `fs.cmd.{nodeId}`, `fs.event.{nodeId}.{category}`, and `fs.status.{nodeId}.heartbeat`.
- Preserve qualified identity names: node ID, control ID, Channel UUID, event ID, command ID, bridge ID, and business ID are not interchangeable.
- A JSON-RPC reply confirms Sidecar handling, not final ringing, bridge, recording, or hangup outcome.
- This is a new system without a legacy production contract. Protocol changes update Java, Go, fixtures, and documentation together and use one canonical representation. Recording events use only `Event.Recording`.
- Event publication failure, duplicate delivery, reconnect, restart, stale channel count, and draining behavior need explicit handling and tests.
- Do not block NATS callbacks indefinitely. Bound ESL/database/HTTP work and expose timeout/degraded state.
- Raw ESL/CLI is an audited, validated operations escape hatch, never a general business API.

## Data And Security

- PostgreSQL queries are bounded and paginated where data can grow. Do not issue SQL in loops.
- Schema changes include forward DDL, compatibility, backfill/seed impact, rollback/repair, and execution order.
- Credentials come from environment/secret management. Never log or return ESL, SIP, NATS, database, gateway, or extension secrets.
- Mutation and raw-command HTTP endpoints require a trusted network or authenticated gateway until native authorization and audit are implemented.
- Mask phone numbers and bound raw protocol/log output.

## Verification

- Build the production binary with `go build -o fs-sidecar-agent .`.
- Compile production packages with `go test ./api ./config ./db ./esl ./event ./governance ./nats ./rpc`.
- The two programs under `test/` are separate manual tools and currently cannot be compiled together with `go test ./...` because they duplicate `main` and DTO names.
- Protocol/event changes require NATS fixtures and, where available, real Sidecar + FreeSWITCH verification. State unavailable PostgreSQL, NATS, ESL, SIP, and media checks honestly.
- Run `git diff --check` and exclude binaries, logs, recordings, coverage, and generated output.

## Focused Review

- For cross-stack telephony and event work, use `../../demo-2026/agents/fcc-realtime-reliability.md` when the sibling product workspace is available.
- For SQL, indexing, pagination, batching, or event volume, use `../../demo-2026/agents/data-performance.md`.
- For operations HTTP, credentials, raw commands, recordings, and personal data, use `../../demo-2026/agents/security-and-authorization.md`.
- For documentation, generated artifacts, and cross-repository scope, use `../../demo-2026/agents/repository-integrity.md`.
