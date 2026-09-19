# chandler26-jdk17-freeswitch-FCC

JDK 17 FCC protocol and call-flow verification service. This project demonstrates the `Action + Flow + FCC` model against the Go Sidecar, but it is not the current backend used by `chandler26-fcc-admin-web` or `chandler26-fcc-client-web`.

The current business backend is `demo-2026/chandler26-jdk21-fcc`.

## Implemented scope

- Spring Boot 3.3.6 and Java 17.
- NATS request/reply client for `FNode.*` JSON-RPC commands.
- Subscription to `fs.event.>` and mapping into Spring application events.
- In-memory `CallSessionManager`.
- Hardcoded inbound, two-leg outbound, and automatic notification flows.
- Action executors for dial, bridge, play, DTMF, record, and hangup.
- Verification REST controller under `/api/fcc`.
- Sidecar HTTP client for extension create/check/delete and health.

This implementation does not provide the current MySQL business fact model, Redis runtime state, administrator APIs, Sa-Token authorization, agent WebSocket endpoint, or the production web contracts.

## Runtime relationship

```text
JDK 17 verification service
       |
       | NATS fs.cmd / fs.event
       v
Go Sidecar --> ESL --> FreeSWITCH
```

The service defaults to port `8088`, which conflicts with the Sidecar default HTTP port on the same host. Run it on a different `server.port` when the Sidecar is local.

The default node ID is a development-machine hostname. Override `fcc.default-node-id` so it exactly matches the Sidecar `NODE_ID`.

## Verification endpoints

- `GET /api/fcc/status`
- `POST /api/fcc/call/outbound`
- `POST /api/fcc/call/notify`
- `POST /api/fcc/call/inbound-simulate`
- `POST /api/fcc/call/hangup`
- `POST /api/fcc/call/bridge`
- `POST /api/fcc/call/play`
- `POST /api/fcc/call/read-dtmf`
- `POST /api/fcc/call/record`
- `POST /api/fcc/call/transfer`
- `GET /api/fcc/flow/records/{callUuid}`

These endpoints are technical verification surfaces, not the `/api/admin` or `/api/telephony` contracts consumed by the current frontends.

## Build

```bash
mvn -q -DskipTests compile
mvn -q test
```

The integration-style test class may require NATS and Sidecar dependencies. Report those environment limits separately instead of treating test discovery as end-to-end success.

## Maintenance rule

Keep this project compatible only when it is intentionally used for protocol regression or migration comparison. New FCC product behavior belongs in `chandler26-jdk21-fcc`; avoid implementing the same business feature in both backends.
