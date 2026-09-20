package main

import (
	"fmt"
	"log"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"chandler25-fs-sidecar-agent/api"
	"chandler25-fs-sidecar-agent/config"
	"chandler25-fs-sidecar-agent/db"
	"chandler25-fs-sidecar-agent/esl"
	"chandler25-fs-sidecar-agent/event"
	"chandler25-fs-sidecar-agent/governance"
	"chandler25-fs-sidecar-agent/nats"
	"chandler25-fs-sidecar-agent/rpc"
)

const banner = `
======================================================================
       FreeSWITCH High-Availability Sidecar Agent (Telephony Pod)
             NATS + JSON-RPC 2.0 Cloud-Native Gateway
======================================================================
`

func main() {
	fmt.Print(banner)

	// 1. 加载配置
	cfg := config.LoadConfig()
	log.Printf("🚀 [启动] 软交换一体化节点 ID: %s", cfg.NodeID)
	log.Printf("⚙️ [配置] NATS URL: %s, FS ESL: %s, 最大并发通道: %d, HTTP 管理端口: %s",
		cfg.NatsURL, cfg.FSEslAddr, cfg.MaxChannels, cfg.HttpPort)

	// 1.5 初始化 PostgreSQL 核心数据库连接池
	dbClient, err := db.InitDB(cfg.PostgresDSN)
	if err != nil {
		log.Printf("⚠️ [PostgreSQL] 核心数据库初始连接失败: %v (后续操作将降级重试)", err)
	}
	repo := db.NewRepository(dbClient)

	// 2. 初始化节点治理管理器
	gov := governance.NewNodeManager(cfg.NodeID, cfg.MaxChannels)

	// 3. 初始化 Inbound ESL 客户端
	eslClient := esl.NewClient(cfg.FSEslAddr, cfg.FSEslPassword)

	// 4. 初始化事件清洗器与 JSON-RPC 路由器
	normalizer := event.NewNormalizer(cfg.NodeID)
	dispatcher := rpc.NewDispatcher(eslClient, gov)
	if err := dispatcher.UseCommandJournal(os.Getenv("COMMAND_JOURNAL_DIR")); err != nil {
		log.Fatalf("[启动] 命令持久目录不可用: %v", err)
	}

	// 5. 初始化 NATS 客户端
	natsClient, err := nats.NewClient(cfg.NatsURL, cfg.NodeID, dispatcher)
	if err != nil {
		log.Fatalf("❌ [NATS] 客户端初始化失败: %v", err)
	} else {
		log.Printf("[NATS] 客户端已初始化，首次连接失败时自动重连并恢复订阅")
		if err := natsClient.StartListeningRPC(); err != nil {
			log.Fatalf("❌ [NATS] 监听 RPC 命令失败: %v", err)
		}
	}

	// 5.5 初始化并启动管理面 HTTP 同步服务 (分机开户、控制面 API 及终端日志流)
	eventOutbox, err := nats.StartEventOutbox(os.Getenv("EVENT_OUTBOX_DIR"), natsClient, cfg.NodeID)
	if err != nil {
		log.Fatalf("[启动] 事件持久目录不可用: %v", err)
	}
	httpServer := api.NewServer(cfg, gov, eslClient, repo)
	go func() {
		if err := httpServer.Start(); err != nil {
			log.Printf("❌ [HTTP] HTTP 管理服务异常: %v", err)
		}
	}()

	// 6. 异步启动 FreeSWITCH ESL 连接与重连守护
	go func() {
		if err := eslClient.ConnectAndListen(); err != nil {
			log.Printf("❌ [ESL] 监听结束: %v", err)
		}
	}()

	// 7. 异步启动事件清洗与 NATS 广播通道
	go func() {
		for rawEvent := range eslClient.EventChannel() {
			normEvent := normalizer.Normalize(rawEvent)
			if normEvent == nil {
				continue
			}

			// 更新节点容量治理快照 (基于 Event.Channel 状态机)
			if normEvent.IsChannelState {
				sourceMicros, _ := strconv.ParseInt(rawEvent["Event-Date-Timestamp"], 10, 64)
				gov.ObserveChannel(normEvent.UUID, normEvent.State, sourceMicros)
			}

			// 发布标准化事件到 NATS (fs.event.{nodeId}.channel / dtmf / record)
			if err := eventOutbox.Store(normEvent); err != nil {
				gov.ObserveHealth(false)
				log.Fatalf("[事件持久化] 写盘失败，停止接入防止静默丢事件: %v", err)
			}
		}
	}()

	// 8. 启动周期心跳上报任务
	ticker := time.NewTicker(time.Duration(cfg.HeartbeatIntervalSec) * time.Second)
	go func() {
		for range ticker.C {
			// 并发事件修改时丢弃旧快照，下轮重试；查询失败不清空存量话道。
			revision := gov.ChannelRevision()
			channels, snapshotErr := eslClient.ChannelUUIDs()
			if snapshotErr != nil {
				gov.ObserveHealth(false)
			} else if gov.ReconcileChannels(revision, channels) {
				gov.ObserveHealth(true)
			}

			if natsClient != nil {
				snapshot := gov.GetSnapshot()
				_ = natsClient.PublishHeartbeat(snapshot)
			}
		}
	}()

	// 9. 监听系统信号优雅停机
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	sig := <-sigChan
	log.Printf("🛑 [退出] 收到信号 %v，开始优雅停止 Sidecar Agent...", sig)

	ticker.Stop()
	eventOutbox.Close()
	if natsClient != nil {
		natsClient.Close()
	}
	eslClient.Close()

	log.Printf("👋 [退出] Sidecar Agent 资源已安全释放，进程结束。")
}
