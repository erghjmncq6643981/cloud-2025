# chandler25-fs-sidecar-agent

FreeSWITCH node-side gateway implemented in Go. One Sidecar is deployed with one FreeSWITCH node and exposes a stable FCC boundary to Java control-plane services and the FreeSWITCH operations console.

## Responsibilities

- Maintain the local inbound ESL connection to FreeSWITCH.
- Normalize selected FreeSWITCH events into FCC JSON-RPC notifications.
- Subscribe to node-scoped NATS request/reply commands.
- Publish node heartbeat and capacity state.
- Enforce basic node state such as `HEALTHY`, `OFFLINE`, and `DRAINING`.
- Expose HTTP/WebSocket operations endpoints on `8088`.
- Read FreeSWITCH runtime tables and maintain Sidecar extension/gateway/CDR metadata in PostgreSQL.
- Execute extension lifecycle scripts and selected ESL administration commands.

The Sidecar does not own FCC agents, business routing, flow versions, customer records, or business CDR projections.

## Runtime architecture

```text
fcc-server :8085
    |
    | NATS request/reply and events
    v
NATS <------> Sidecar :8088 <------> PostgreSQL
                  |
                  | ESL :8021
                  v
              FreeSWITCH

fswitch-web :8008 ------ HTTP/WebSocket ------^
fcc-admin :8089 -------- selected admin HTTP --^
```

## NATS contract

| Subject | Direction | Purpose |
| --- | --- | --- |
| `fs.cmd.{nodeId}` | Java to Sidecar | JSON-RPC 2.0 commands |
| `fs.event.{nodeId}.channel` | Sidecar to Java | normalized channel lifecycle |
| `fs.event.{nodeId}.dtmf` | Sidecar to Java | DTMF events |
| `fs.event.{nodeId}.record` | Sidecar to Java | recording events |
| `fs.event.{nodeId}.registration` | Sidecar to Java | SIP registration events |
| `fs.status.{nodeId}.heartbeat` | Sidecar to control plane | node health and capacity |

Implemented RPC methods include `FNode.Dial`, `FNode.ChannelBridge`, `FNode.ReadDTMF`, `FNode.Play`, `FNode.Record`, `FNode.Hangup`, `FNode.Transfer`, `FNode.NativeAPI`, `FNode.Drain`, `FNode.Resume`, and `FNode.Status`. Only these canonical names are registered; old `call.*`, `node.*`, and `FNode.Bridge` aliases are rejected.

The `nodeId` must exactly match the Java `FCC_DEFAULT_NODE_ID`; otherwise commands are sent to an unconsumed subject.

## HTTP and WebSocket API

The server listens on `HTTP_PORT`, default `8088`.

- `GET /health` and `GET /api/v1/health`
- `/api/v1/extensions` for extension create/check/delete
- `GET /api/v1/telephony/status`
- registration query and flush
- extension query and password update
- channel/call query, kill, and transfer
- Sofia profile and gateway operations
- raw FreeSWITCH CDR query
- `POST /api/v1/telephony/cli/exec`
- `WS /api/v1/telephony/ws/console-logs`

The CLI, gateway, extension, registration, and channel mutation endpoints are privileged operations. The current Go HTTP server does not provide a complete production authorization boundary; restrict it to a trusted management network or place it behind an authenticated gateway.

## Configuration

| Environment variable | Purpose |
| --- | --- |
| `NODE_ID` | node identity used in NATS subjects |
| `NATS_URL` | NATS server URL |
| `FS_ESL_ADDR` | FreeSWITCH ESL address |
| `FS_ESL_PASSWORD` | ESL credential |
| `MAX_CHANNELS` | capacity limit |
| `HEARTBEAT_INTERVAL_SEC` | heartbeat interval |
| `HTTP_PORT` | management HTTP port |
| `EXTENSION_SCRIPT` | extension lifecycle script |
| `PG_DSN` | PostgreSQL connection string |
| `LOG_LEVEL` | logging level |

Production secrets must be supplied by environment/secret management and must not be committed or logged.

## Build and run

The current module declares Go `1.27.1`.

```bash
go build -o fs-sidecar-agent .
go test ./api ./config ./db ./esl ./event ./governance ./nats ./rpc
go run .
```

A local NATS instance can be started with the repository `docker-compose.yml`. FreeSWITCH, PostgreSQL, extension script paths, and credentials still need to match the host deployment.

## Verification limits

- Focused tests cover recording event names, rejection of legacy RPC aliases, and credential exclusion from public JSON. Runtime integration still requires external services.
- `test/nats_client_demo.go` and `test/verify_fnode_flow.go` are separate manual integration programs. They both declare `package main` and duplicate names, so `go test ./...` is not a valid repository-wide command until those tools are split into separate directories.
- An end-to-end result requires real NATS, PostgreSQL, FreeSWITCH ESL, SIP endpoints, and observable media/events.

See [docs/DESIGN.md](./docs/DESIGN.md) for implemented protocol and data boundaries.
