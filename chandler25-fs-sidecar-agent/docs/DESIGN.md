# FreeSWITCH Sidecar 当前架构

## 1. 目标与边界

`chandler25-fs-sidecar-agent` 与单个 FreeSWITCH 节点共同部署，对上提供 FCC/NATS 协议和运维 HTTP 接口，对下维护 ESL 与 PostgreSQL 连接。

已实现职责：

- Inbound ESL 连接、订阅、断线重连和 API 命令执行；
- NATS 节点命令订阅、标准事件发布和心跳发布；
- FCC JSON-RPC 命令到 FreeSWITCH 操作的映射；
- Channel、DTMF、录音、注册和网关事件清洗；
- 节点容量、离线和排水状态；
- FreeSWITCH 运行表及 Sidecar 管理表查询；
- 分机、网关、CDR、Channel、Profile 和 CLI 运维接口；
- 控制台日志 WebSocket。

Sidecar 不实现坐席账户、技能组、业务路由、流程版本、客户资料和业务话单投影。

2026-09-19 职责确认：自动外呼调度、客户资料、Windows 弹屏业务统一归属 fcc-server；Windows 客户端只执行本机通知和窗口操作。Sidecar 后续补充通道快照、命令幂等/结果查询、事件可靠投递与注册状态能力，实施顺序见 [FCC 分阶段清单](../../../demo-2026/docs/fcc-delivery-plan.md)。这些新增能力尚未整体实现。

本批节点准入仅允许 HEALTHY 且容量未满的节点发起新呼叫，DRAINING/OFFLINE/OVERLOADED/未知状态均拒绝，不修改存量通道。独立 governance 源文件测试通过；完整生产包测试和构建因 go.mod 要求 Go 1.27.1 而本地为 1.25.4 未通过。节点状态自动检测、UUID 计数去重和真实 FreeSWITCH 联调仍待完成。

## 2. 运行拓扑

```text
                         NATS
                    /             \
        fs.cmd.{nodeId}            fs.event.* / fs.status.*
                 |                         |
                 v                         v
fcc-server --> Sidecar :8088 <---------- Java control plane
                 |
        +--------+---------+
        |                  |
        v                  v
   ESL :8021          PostgreSQL
        |          FreeSWITCH runtime tables
        v          and Sidecar metadata
   FreeSWITCH

fswitch-web :8008 --> Sidecar HTTP/WebSocket
fcc-admin :8089 ----> selected Sidecar management HTTP
```

`NODE_ID` 是命令路由和事件归属的唯一节点标识，必须与 Java 侧目标节点一致。

## 3. 进程组件

| 包 | 当前职责 |
| --- | --- |
| `config` | 从环境变量加载节点、NATS、ESL、HTTP、脚本和 PostgreSQL 配置 |
| `esl` | ESL 登录、事件订阅、命令执行、Ping 和重连 |
| `rpc` | JSON-RPC 解析、方法路由、参数验证和 FNode 返回 |
| `event` | FreeSWITCH Header 到标准通知的清洗 |
| `nats` | `fs.cmd` 订阅、事件和心跳发布 |
| `governance` | 节点状态、活跃 Channel 计数和排水 |
| `db` | PostgreSQL 连接、空 Schema 初始化和查询 |
| `api` | 运维 HTTP、CLI 和日志 WebSocket |
| `main.go` | 组件装配、后台任务和信号退出 |

## 4. FCC 命令协议

命令使用 JSON-RPC 2.0，经 `fs.cmd.{nodeId}` 请求/应答。

当前方法：

- `FNode.Dial`
- `FNode.ChannelBridge`
- `FNode.ReadDTMF`
- `FNode.Play`
- `FNode.Record`
- `FNode.Hangup`
- `FNode.Transfer`
- `FNode.NativeAPI`
- `FNode.Drain`
- `FNode.Resume`
- `FNode.Status`

仅保留上述规范方法。`call.*`、`node.*` 和 `FNode.Bridge` 均返回方法不存在，不提供历史兼容层。

同步应答表示 Sidecar 已处理 RPC，并不保证后续振铃、接通、桥接、录音落盘或挂机事件已经完成。最终结果由事件流确认。

`FNode.NativeAPI` 是受限逃生通道，不应成为业务调用的常规接口。

## 5. 标准事件

Sidecar 向 `fs.event.{nodeId}.{category}` 发布 JSON-RPC Notification：

| Category | Method | 关键内容 |
| --- | --- | --- |
| `channel` | `Event.Channel` | Channel UUID、Ctrl ID、状态、方向、号码、时间和原因 |
| `dtmf` | `Event.DTMF` | Channel、按键和持续时间 |
| `record` | `Event.Recording` | 开始/停止、文件路径和时长 |
| `registration` | `Event.Registration` | 分机、域、注册状态、地址和 UA |
| `gateway` | `Event.Gateway` | 网关、Profile 和状态 |

Channel 状态包括 `START`、`CALLING`、`RINGING`、`ANSWERED`、`MEDIA`、`READY`、`BRIDGE`、`UNBRIDGE` 和 `DESTROY` 的子集，取决于 FreeSWITCH 原始事件。

当前事件报文没有稳定的源 `event_id`。Java 侧自行生成 ID 不能完成跨重投的协议级去重，这是已知可靠性缺口。NATS Core 事件也不提供持久重放。

Sidecar 与 Java 统一使用 `Event.Recording`，NATS 分类保持 `record`。两侧已有契约测试源码；本机 Go 工具链不可用，尚不能据此宣称录音真实链路已验证。

## 6. 节点治理

节点维护：

- `HEALTHY`：可接收新呼叫；
- `OFFLINE`：ESL Ping 失败；
- `DRAINING`：拒绝新呼叫，存量通话继续；
- 活跃 Channel 和最大容量快照；
- 周期心跳 `fs.status.{nodeId}.heartbeat`。

当前活跃数由事件增减维护，进程重启或事件丢失后可能与 FreeSWITCH 实际状态偏离。运维判断需要结合状态查询和 PostgreSQL/FreeSWITCH 运行表。

## 7. PostgreSQL

Sidecar 读取 FreeSWITCH 原生运行表：

- `registrations`
- `channels`
- `calls`
- 其他由 FreeSWITCH 配置决定的运行表

Sidecar 还维护扩展表：

- `fs_extension`
- `fs_gateway`
- `fs_cdr`
- SIP/Profile 相关管理数据

空库结构位于 `db/repo.go` 与 `db/schema_fs.sql`。启动不导入演示分机、网关或话单。网关状态默认 UNKNOWN，延迟和录音质量未测量时为空。已有开发库的修正顺序见 `docs/GOVERNANCE_VERIFICATION.md`。

## 8. HTTP 管理面

`api/server.go` 当前注册：

- 健康检查；
- 分机创建、查询和删除；
- 状态、注册、Channel/Call、Profile、网关和 CDR 查询；
- 注册注销、Channel 强拆/转接、网关修改和密码更新；
- CLI 命令执行；
- 控制台日志 WebSocket。

这些接口直接影响软交换或暴露底层数据。当前服务自身没有完整的认证、角色、租户和审计实现，生产必须通过可信网络或认证网关限制访问。

## 9. 故障行为

- ESL 断开：客户端后台重连，节点可能进入 `OFFLINE`。
- PostgreSQL 不可用：启动会记录失败，依赖数据库的接口不可视为健康。
- NATS 初次连接失败：当前进程继续启动，但没有可用 NATS Client 时不会自动补建完整命令订阅链路。
- NATS 发布失败：事件只记录错误，不会持久重试。
- HTTP 日志 WebSocket：独立 ESL 日志订阅，客户端需处理断线重连。
- 进程退出：关闭 ticker、NATS 和 ESL；HTTP server 当前没有独立的优雅 shutdown 编排。

## 10. 安全与验证

- ESL、PostgreSQL、NATS 和 SIP 凭据只从部署环境注入。
- 日志不得打印凭据、完整个人号码或无界协议正文。
- CLI、强拆、强制注销、密码和网关操作需要上游授权和业务审计。
- 已有录音事件、RPC 方法边界和响应脱敏测试；`test/*.go` 是两个需要分别运行的手动联调程序。它们位于同一目录且包含重复的 `main`/DTO 定义，因此当前不能用 `go test ./...` 作为全仓验证命令。
- 完整验证需要 NATS、PostgreSQL、FreeSWITCH ESL、SIP 注册终端和可观察的媒体/事件链路。
