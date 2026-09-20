package nats

import (
	"encoding/json"
	"fmt"
	"log"
	"time"

	"chandler25-fs-sidecar-agent/event"
	"chandler25-fs-sidecar-agent/governance"
	"chandler25-fs-sidecar-agent/rpc"

	"github.com/nats-io/nats.go"
)

// Client NATS 客户端管理层
type Client struct {
	nc         *nats.Conn
	nodeID     string
	dispatcher *rpc.Dispatcher
	sub        *nats.Subscription
	closed     bool
}

func NewClient(natsURL, nodeID string, dispatcher *rpc.Dispatcher) (*Client, error) {
	opts := []nats.Option{
		nats.Name(fmt.Sprintf("fs-sidecar-%s", nodeID)),
		nats.ReconnectWait(2 * time.Second),
		nats.MaxReconnects(-1),          // 无限重连
		nats.RetryOnFailedConnect(true), // 首次离线也保留连接对象与订阅恢复
		nats.DisconnectErrHandler(func(c *nats.Conn, err error) {
			log.Printf("🔌 [NATS] 连接断开: %v, 准备重连...", err)
		}),
		nats.ReconnectHandler(func(c *nats.Conn) {
			log.Printf("✅ [NATS] 重新连接成功: %s", c.ConnectedUrl())
		}),
	}

	nc, err := nats.Connect(natsURL, opts...)
	if err != nil {
		return nil, fmt.Errorf("连接 NATS 失败 (%s): %w", natsURL, err)
	}

	return &Client{
		nc:         nc,
		nodeID:     nodeID,
		dispatcher: dispatcher,
	}, nil
}

// StartListeningRPC 订阅本节点的控制通道 (fs.cmd.{nodeId})
func (c *Client) StartListeningRPC() error {
	cmdSubject := fmt.Sprintf("fs.cmd.%s", c.nodeID)
	sub, err := c.nc.Subscribe(cmdSubject, func(msg *nats.Msg) {
		// 执行 JSON-RPC 调度
		respBytes := c.dispatcher.HandleRaw(msg.Data)

		// 如果对端提供了 Reply-To (Request-Reply 模式)，同步回写结果
		if msg.Reply != "" {
			_ = c.nc.Publish(msg.Reply, respBytes)
		}
	})

	if err != nil {
		return fmt.Errorf("订阅 RPC 命令通道 (%s) 失败: %w", cmdSubject, err)
	}

	c.sub = sub
	log.Printf("👂 [NATS] 已成功监听节点 JSON-RPC 指令通道: %s", cmdSubject)
	return nil
}

// PublishEvent 发布标准化 JSON-RPC 2.0 事件到 NATS (Event.Channel, Event.DTMF, Event.Recording)
func (c *Client) PublishEvent(normEvent *event.NormalizedEventResult) error {
	if c.nc == nil || c.nc.IsClosed() {
		return fmt.Errorf("NATS 连接已关闭")
	}

	// 统一主题模式: fs.event.{nodeId}.{category} (例如: fs.event.fs-node-01.channel)
	subject := fmt.Sprintf("fs.event.%s.%s", c.nodeID, normEvent.Category)

	return c.nc.Publish(subject, normEvent.RawJSON)
}

// PublishHeartbeat 发布节点状态心跳
func (c *Client) PublishHeartbeat(snapshot *governance.NodeStatusSnapshot) error {
	if c.nc == nil || c.nc.IsClosed() {
		return fmt.Errorf("NATS 连接已关闭")
	}

	data, err := json.Marshal(snapshot)
	if err != nil {
		return err
	}

	subject := fmt.Sprintf("fs.status.%s.heartbeat", c.nodeID)
	return c.nc.Publish(subject, data)
}

func (c *Client) Close() {
	if c.sub != nil {
		_ = c.sub.Unsubscribe()
	}
	if c.nc != nil {
		c.nc.Close()
	}
}
