# chandler25-fs-sidecar-agent

FreeSWITCH node-side gateway implemented in Go. One Sidecar is deployed with one FreeSWITCH node and exposes a stable FCC boundary to Java control-plane services and the FreeSWITCH operations console.

## Responsibilities

- Maintain the local inbound ESL connection to FreeSWITCH.
- Normalize selected FreeSWITCH events into FCC JSON-RPC notifications.
- Subscribe to the logical dispatch command entry and the node-scoped internal request/reply command.
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
| `fs.cmd.dispatch` | Java to Sidecar Coordinator | logical JSON-RPC 2.0 commands; node is selected below the FCC boundary |
| `fs.cmd.{nodeId}` | Coordinator to Sidecar | internal JSON-RPC 2.0 commands |
| `fs.event.{nodeId}.channel` | Sidecar to Java | normalized channel lifecycle |
| `fs.event.{nodeId}.dtmf` | Sidecar to Java | DTMF events |
| `fs.event.{nodeId}.record` | Sidecar to Java | recording events |
| `fs.event.{nodeId}.registration` | Sidecar to Java | SIP registration events |
| `fs.status.{nodeId}.heartbeat` | Sidecar to control plane | node health and capacity |

Implemented RPC methods include `FNode.Dial`, `FNode.ChannelBridge`, `FNode.ReadDTMF`, `FNode.Play`, `FNode.Record`, `FNode.Hangup`, `FNode.Transfer`, `FNode.NativeAPI`, `FNode.Drain`, `FNode.Resume`, and `FNode.Status`. Only these canonical names are registered; old `call.*`, `node.*`, and `FNode.Bridge` aliases are rejected.

`FNode.Dial` accepts a business target in `dial_string` and a required dialplan `context`. The Sidecar validates both values and expands them to `loopback/{target}/{context}`. Java callers do not send gateway/profile dial strings; carrier gateway selection remains in the FreeSWITCH dialplan for that context.

Java no longer configures or sends a `nodeId`. In the current single-node mode, the dispatch ingress executes locally. A multi-node deployment requires a Coordinator/ownership registry to route `channel_uuid` commands and new `Dial` requests before using the internal `fs.cmd.{nodeId}` subject.

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
| `DISPATCH_INGRESS_ENABLED` | enable the local single-node `fs.cmd.dispatch` ingress; set false on nodes behind a real Coordinator |

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

### TTS 配置

`FNode.Play` 和 `FNode.ReadDTMF` 的 `media.type=TEXT` 会由 Sidecar 调用阿里云 NLS TTS，音频以内容哈希文件名原子写入 `TTS_WORK_DIR`，该目录必须同时挂载给 FreeSWITCH。FCC 只发送文案，不发送 `tts:`/`say:` 字符串；未配置 provider 时命令明确失败。

```text
TTS_PROVIDER=aliyun_nls
TTS_WORK_DIR=/var/lib/fcc/tts
ALIYUN_NLS_APP_KEY=...
ALIYUN_NLS_ACCESS_KEY_ID=...
ALIYUN_NLS_ACCESS_KEY_SECRET=...
ALIYUN_NLS_VOICE=siyue
```

也可以注入短期 `ALIYUN_NLS_TOKEN`，此时 Sidecar 不请求 AccessKey token 接口。密钥只从部署环境注入，日志不会输出密钥、文本或 token。
# 运行可靠性配置（2026-09-20）

当前启动必须配置独占持久目录 `COMMAND_JOURNAL_DIR` 与 `EVENT_OUTBOX_DIR`，并预先创建 NATS JetStream `FCC_EVENTS` 流。不得在升级时清空这些目录。

`FNode.ChannelSnapshot` 是非缓存只读查询，成功结果 `data` 包含 `complete: true`、`channel_uuids`、`started_at`、`completed_at`（毫秒）。ESL 查询失败返回错误，不能视为空节点。`FNode.CommandResult` 参数是 `command_id`，仅查询原命令结果，不重复执行未知副作用。

命令 journal 同 ID 不同参数拒绝；意图存在但结果缺失返回 UNKNOWN。当前要求每个节点仅一个 Sidecar 进程使用其目录，尚无多进程文件锁及自动保留清理策略。部署及跨电脑验收见 [FCC 验收指南](../../demo-2026/docs/fcc-cross-machine-acceptance.md)。
