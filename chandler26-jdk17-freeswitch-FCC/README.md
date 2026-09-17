# chandler26-jdk17-freeswitch-FCC

基于 **指令/动作 (Action) + 流程 (Flow) + FCC (FreeSWITCH Call Center 协议)** 架构的云原生呼叫中心核心控制引擎。

参考自 `call-center-backend` 的工业级设计理念，全面升级为 **Java 17 + Spring Boot 3.3.6**。通过 **FCC 协议（Go Sidecar + NATS + JSON-RPC 2.0）** 彻底与底层 FreeSWITCH 软交换实现物理与逻辑解耦。

---

## 架构模型

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          1. 流程编排层 (Flow Layer)                       │
│  CallStageState: START -> CALLING -> ROUTE -> CONNECTED -> END         │
│  FlowConfig (业务模型流程模版: 客服呼入流程 / 坐席双向外呼流程 / 自动外呼)        │
│  StageHandlers: CallStartHandler, CallRouteHandler, CallConnectedHandler│
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ 驱动动作节点 FlowNode
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        2. 动作/指令执行层 (Action Layer)                  │
│  DefaultActionHandlersManager (动作调度中心 & 流程轨迹审计)               │
│  ActionType: DIAL_AGENT, DIAL_GUEST, CHANNEL_BRIDGE, PLAY, READ_DTMF...│
│  ActionHandlers: DialAgentHandler, ChannelBridgeHandler, PlayHandler... │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ 调用标准化 FCC 客户端
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         3. FCC 协议通信层 (FCC Layer)                    │
│  FccClient: 统一封装 NATS + JSON-RPC 2.0 (FNode.* 同步/异步请求)          │
│  FccEventListener: 监听 NATS (fs.event.*.channel / dtmf / record)       │
│  将 Event.Channel 状态机实时分发为 Spring ApplicationEvents              │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ NATS 消息总线 (fs.cmd.* / fs.event.*)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              Go Sidecar Agent (fs-agent) + FreeSWITCH 引擎               │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 核心特性

1. **零 ESL 侵入与进程故障隔离**：
   * Java 端不再直连 FreeSWITCH 的 8021 ESL Socket，无任何 FreeSWITCH 原生 SDK 依赖。
   * 所有通信经由本地高可靠 NATS 总线与 Go Sidecar (`fs-agent`) 交互，延迟 < 2ms。
2. **全局会话追踪 (`ctrl_uuid`)**：
   * 业务控制端生成唯一的 `ctrl_uuid` 贯穿指令下发与事件回调，彻底解决 FreeSWITCH 多 Leg、转接、重呼 UUID 混乱的问题。
3. **状态机驱动业务闭环 (`Event.Channel`)**：
   * 监听 FNode 归一化的标准 9 个状态流（`CALLING` -> `RINGING` -> `ANSWERED` -> `READY` -> `DESTROY`）。
4. **流程与动作解耦 (Flow & Action)**：
   * 新增业务模型只需在 `FlowConfig` 中配置动作链即可，无须修改任何底层的通信与状态机代码。

---

## 快速上手与验证接口

### 1. 启动依赖
确保 NATS (`127.0.0.1:4222`) 与 Go Sidecar (`fs-agent`) 处于运行状态。

### 2. 启动服务
```bash
mvn clean package -DskipTests
java -jar target/chandler26-jdk17-freeswitch-FCC-1.0.0-SNAPSHOT.jar
```
服务默认监听端口：`8088`。

### 3. 技术与功能验证接口 (REST)

* **探活与节点状态**：
  ```bash
  curl http://localhost:8088/api/fcc/status
  ```
* **发起双向外呼验证**：
  ```bash
  curl -X POST "http://localhost:8088/api/fcc/call/outbound?agentExt=1008&destNumber=1007"
  ```
* **查看流程执行轨迹 (Audit Trail)**：
  ```bash
  curl "http://localhost:8088/api/fcc/flow/records/{callUuid}"
  ```
* **手动挂机**：
  ```bash
  curl -X POST "http://localhost:8088/api/fcc/call/hangup?uuid={channelUuid}"
  ```
