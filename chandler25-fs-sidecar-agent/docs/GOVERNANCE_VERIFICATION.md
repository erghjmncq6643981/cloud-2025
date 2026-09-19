# 全新 FCC 契约与验证记录

2026-09-19：不提供旧 call.* / node.* / FNode.Bridge 兼容接口。当前唯一录音方法为 Event.Recording，NATS subject 为 fs.event.{nodeId}.record；wire 字段 ctrl_uuid 表示控制标识，不是业务 callId。

## 数据与密码

新建空库使用 db/schema_fs.sql，启动 EnsureSchema 也仅创建空表。禁止自动导入演示 XML、网关、分机或 CDR。

Gateway 与分机模型的密码不参与 JSON 序列化。网关写入请求单独接收 password；更新空密码时 SQL 保留旧值，并以 RETURNING 取回实际值用于写 XML，GET/POST 响应均不返回密码。该 SQL 已静态核查，尚未经过真实 PostgreSQL 验证。

网关查询未接入运行时探活投影，所以状态为 UNKNOWN、延迟为空。发送探活命令的耗时使用 command_duration_ms，不冒充 SIP RTT。Profile 查询错误返回 502，不生成 RUNNING、固定端口或本机地址。

## 开发库结构修正顺序

这是新系统的空库基线调整，不引入旧业务兼容或历史数据迁移逻辑。已有开发库不会因 CREATE TABLE IF NOT EXISTS 自动改变默认值：

1. 先备份开发库并保存当前默认值定义。
2. 在受控维护窗口执行以下 DDL，再启动当前 Java / Sidecar / 前端版本。
3. 验证新建网关与 CDR 未测量字段为空，确认真实服务连接后再做通话联调。

```sql
ALTER TABLE fs_gateway ALTER COLUMN status SET DEFAULT 'UNKNOWN';
ALTER TABLE fs_gateway ALTER COLUMN ping_ms SET DEFAULT '';
ALTER TABLE fs_cdr ALTER COLUMN quality_percentage SET DEFAULT '';
```

不自动覆盖既有行：旧演示数据需由操作者确认来源后清除，不能把真实历史数据当作样例删除。上述 DDL 不改变索引或数据类型；回退可恢复备份中的默认值定义，不涉及行回填。未在本机执行 SQL，未验证锁耗时。

## 验证

- fswitch-web 构建及 Vue/TypeScript 检查通过。
- 录音事件、旧方法拒绝、响应凭据脱敏有 Go 测试源码。
- Go 编译和测试未通过环境前置条件：本机 1.25.4，go.mod 要求 1.27.1，工具链下载失败。
- Java 控制面 Maven 编译和测试被 JDK 17 阻塞，要求 JDK 21。
- 未进行真实数据库、NATS、ESL、SIP、录音媒体端到端验证。

## 仍需治理

运维接口尚需原生授权、持久审计、资源参数校验与配置写入事务协调；RPC 成功只表示受理，不能证明通话、网关或录音最终成功。Core NATS 事件没有持久重放保障。
