package main

import (
	"encoding/json"
	"fmt"
	"log"
	"time"

	"github.com/nats-io/nats.go"
)

type JsonRpcRequest struct {
	JSONRPC string      `json:"jsonrpc"`
	ID      interface{} `json:"id"`
	Method  string      `json:"method"`
	Params  interface{} `json:"params,omitempty"`
}

func main() {
	nc, err := nats.Connect("nats://127.0.0.1:4222")
	if err != nil {
		log.Fatalf("连接 NATS 失败: %v", err)
	}
	defer nc.Close()

	nodeID := "qiandingjundeMacBook-Pro.local"
	ctrlUUID := fmt.Sprintf("ctrl-flow-%d", time.Now().Unix())
	callUUID := fmt.Sprintf("call-flow-%d", time.Now().Unix())

	log.Printf("👂 订阅节点事件通道: fs.event.%s.channel", nodeID)
	eventCount := 0
	_, err = nc.Subscribe(fmt.Sprintf("fs.event.%s.channel", nodeID), func(msg *nats.Msg) {
		eventCount++
		var parsed map[string]interface{}
		_ = json.Unmarshal(msg.Data, &parsed)
		formatted, _ := json.MarshalIndent(parsed, "", "  ")
		fmt.Printf("\n🔔 [捕获到 Event.Channel 事件 #%d]:\n%s\n", eventCount, string(formatted))
	})
	if err != nil {
		log.Fatalf("订阅失败: %v", err)
	}

	time.Sleep(500 * time.Millisecond)

	// 1. 发起 FNode.Dial
	dialReq := JsonRpcRequest{
		JSONRPC: "2.0",
		ID:      "test-dial-1",
		Method:  "FNode.Dial",
		Params: map[string]interface{}{
			"ctrl_uuid": ctrlUUID,
			"uuid":      callUUID,
			"destination": map[string]interface{}{
				"call_params": []map[string]interface{}{
					{
						"dial_string": "user/1007",
						"cid_name":    "FlowTester",
						"cid_number":  "1008",
					},
				},
			},
			"timeout": 15,
		},
	}

	data, _ := json.Marshal(dialReq)
	log.Printf("📤 发送 FNode.Dial (ctrl_uuid: %s, uuid: %s)...", ctrlUUID, callUUID)
	msg, err := nc.Request(fmt.Sprintf("fs.cmd.%s", nodeID), data, 5*time.Second)
	if err != nil {
		log.Fatalf("FNode.Dial 请求失败: %v", err)
	}
	fmt.Printf("📥 FNode.Dial 响应:\n%s\n", string(msg.Data))

	// 等待 2 秒观察振铃或就绪事件
	time.Sleep(2 * time.Second)

	// 2. 发起 FNode.Hangup 结束测试
	hangupReq := JsonRpcRequest{
		JSONRPC: "2.0",
		ID:      "test-hangup-1",
		Method:  "FNode.Hangup",
		Params: map[string]interface{}{
			"ctrl_uuid": ctrlUUID,
			"uuid":      callUUID,
			"cause":     "NORMAL_CLEARING",
		},
	}
	hData, _ := json.Marshal(hangupReq)
	log.Printf("📤 发送 FNode.Hangup...")
	hMsg, err := nc.Request(fmt.Sprintf("fs.cmd.%s", nodeID), hData, 3*time.Second)
	if err != nil {
		log.Printf("Hangup 请求响应错误: %v", err)
	} else {
		fmt.Printf("📥 FNode.Hangup 响应:\n%s\n", string(hMsg.Data))
	}

	// 等待 DESTROY 事件到达
	time.Sleep(2 * time.Second)
	log.Printf("🏁 测试流完成，共收到 %d 个 Event.Channel 事件", eventCount)
}
