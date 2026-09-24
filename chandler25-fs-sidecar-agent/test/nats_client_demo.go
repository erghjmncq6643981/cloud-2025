package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
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
	natsURL := flag.String("nats", "nats://127.0.0.1:4222", "NATS Server URL")
	nodeID := flag.String("node", "telephony-pod-01", "Target FreeSWITCH Node ID")
	cmd := flag.String("cmd", "listen", "Action: listen | status | drain | resume | originate")
	flag.Parse()

	nc, err := nats.Connect(*natsURL)
	if err != nil {
		log.Fatalf("❌ 连接 NATS 失败: %v", err)
	}
	defer nc.Close()
	log.Printf("✅ 已连接 NATS: %s", *natsURL)

	switch *cmd {
	case "status":
		sendRpc(nc, *nodeID, "FNode.Status", nil)

	case "drain":
		sendRpc(nc, *nodeID, "FNode.Drain", nil)

	case "resume":
		sendRpc(nc, *nodeID, "FNode.Resume", nil)

	case "dial":
		ctrlUUID := fmt.Sprintf("ctrl-%d", time.Now().Unix())
		params := map[string]interface{}{
			"ctrl_uuid": ctrlUUID,
			"uuid":      fmt.Sprintf("%d", time.Now().UnixNano()),
			"destination": map[string]interface{}{
				"call_params": []map[string]interface{}{
					{
						"dial_string": "1007",
						"context":     "default",
						"cid_name":    "FNodeTest",
						"cid_number":  "1008",
					},
				},
			},
			"timeout": 30,
		}
		sendRpc(nc, *nodeID, "FNode.Dial", params)

	case "native_api":
		params := map[string]interface{}{
			"cmd":  "version",
			"args": "",
		}
		sendRpc(nc, *nodeID, "FNode.NativeAPI", params)

	case "listen":
		log.Printf("👂 开始监听集群所有事件 (fs.event.>) 与心跳 (fs.status.>)...")

		// 监听业务事件（Event.Channel、Event.CommandResult、Event.DTMF、Event.Recording 等）
		_, _ = nc.Subscribe("fs.event.>", func(msg *nats.Msg) {
			var pretty map[string]interface{}
			if err := json.Unmarshal(msg.Data, &pretty); err == nil {
				formatted, _ := json.MarshalIndent(pretty, "", "  ")
				fmt.Printf("\n🔔 [收到呼叫事件] Subject: %s\n%s\n", msg.Subject, string(formatted))
			} else {
				fmt.Printf("\n🔔 [收到呼叫事件] Subject: %s\n%s\n", msg.Subject, string(msg.Data))
			}
		})

		// 监听心跳
		_, _ = nc.Subscribe("fs.status.>", func(msg *nats.Msg) {
			fmt.Printf("💓 [收到节点心跳] Subject: %s | Data: %s\n", msg.Subject, string(msg.Data))
		})

		sig := make(chan os.Signal, 1)
		signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
		<-sig
		log.Println("监听结束。")

	default:
		log.Fatalf("未知命令: %s (支持: status | drain | resume | dial | native_api | listen)", *cmd)
	}
}

func sendRpc(nc *nats.Conn, nodeID, method string, params interface{}) {
	req := JsonRpcRequest{
		JSONRPC: "2.0",
		ID:      fmt.Sprintf("req-%d", time.Now().UnixNano()),
		Method:  method,
		Params:  params,
	}

	data, _ := json.Marshal(req)
	subject := fmt.Sprintf("fs.cmd.%s", nodeID)

	log.Printf("📤 [发送 JSON-RPC] Subject: %s\n请求报文: %s", subject, string(data))

	msg, err := nc.Request(subject, data, 3*time.Second)
	if err != nil {
		log.Fatalf("❌ RPC 调用失败: %v", err)
	}

	var prettyJSON map[string]interface{}
	_ = json.Unmarshal(msg.Data, &prettyJSON)
	formatted, _ := json.MarshalIndent(prettyJSON, "", "  ")

	fmt.Printf("\n📥 [收到 JSON-RPC 响应]:\n%s\n", string(formatted))
}
