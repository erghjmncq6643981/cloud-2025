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
	nc          *nats.Conn
	nodeID      string
	dispatcher  *rpc.Dispatcher
	sub         *nats.Subscription
	dispatchSub *nats.Subscription
	closed      bool
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
	sub, err := c.nc.Subscribe(cmdSubject, c.handleRPC)

	if err != nil {
		return fmt.Errorf("订阅 RPC 命令通道 (%s) 失败: %w", cmdSubject, err)
	}

	c.sub = sub
	log.Printf("👂 [NATS] 已成功监听节点 JSON-RPC 指令通道: %s", cmdSubject)
	return nil
}

// StartListeningDispatchRPC 订阅逻辑命令入口 (fs.cmd.dispatch)。
//
// 单节点部署时由本地 Dispatcher 直接执行。多节点部署不能简单地让所有 Sidecar
// 竞争该主题，必须由独立 Coordinator 根据话道 ownership、容量和节点状态路由后，
// 再投递到 fs.cmd.{nodeId}；因此本订阅是单节点可运行的过渡入口。
func (c *Client) StartListeningDispatchRPC() error {
	const cmdSubject = "fs.cmd.dispatch"
	const queueGroup = "fs-sidecar-dispatchers"
	sub, err := c.nc.QueueSubscribe(cmdSubject, queueGroup, c.handleRPC)
	if err != nil {
		return fmt.Errorf("订阅逻辑 RPC 命令通道 (%s) 失败: %w", cmdSubject, err)
	}
	c.dispatchSub = sub
	log.Printf("[NATS] 已监听逻辑命令入口: %s (queue=%s, 单节点直执行)", cmdSubject, queueGroup)
	return nil
}

// handleRPC 统一执行 JSON-RPC 并回写 request-reply 应答。
func (c *Client) handleRPC(msg *nats.Msg) {
	respBytes := c.dispatcher.HandleRaw(msg.Data)
	if msg.Reply != "" {
		_ = c.nc.Publish(msg.Reply, respBytes)
	}
}

// PublishEvent 发布标准化 JSON-RPC 2.0 事件到 NATS。
// 事件类型包括 Event.Channel、Event.CommandResult、Event.DTMF 和 Event.Recording 等。
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
	if c.dispatchSub != nil {
		_ = c.dispatchSub.Unsubscribe()
	}
	if c.nc != nil {
		c.nc.Close()
	}
}
