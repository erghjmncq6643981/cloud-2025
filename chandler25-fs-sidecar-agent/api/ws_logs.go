package api

import (
	"bufio"
	"fmt"
	"log"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return true // 允许跨域连接
	},
}

// WSLogHandler WebSocket 实时日志流推送
type WSLogHandler struct {
	eslAddr     string
	eslPassword string
	clients     map[*websocket.Conn]bool
	clientsMu   sync.Mutex
	broadcast   chan string
}

// NewWSLogHandler 创建 WSLogHandler
func NewWSLogHandler(eslAddr, eslPassword string) *WSLogHandler {
	h := &WSLogHandler{
		eslAddr:     eslAddr,
		eslPassword: eslPassword,
		clients:     make(map[*websocket.Conn]bool),
		broadcast:   make(chan string, 1000),
	}
	go h.startESLLogSubscriber()
	go h.startBroadcastLoop()
	return h
}

// HandleWS 处理 WebSocket 接入 (/api/v1/telephony/ws/console-logs)
func (h *WSLogHandler) HandleWS(w http.ResponseWriter, r *http.Request) {
	ws, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("⚠️ [WebSocket] 升级失败: %v", err)
		return
	}
	defer ws.Close()

	h.clientsMu.Lock()
	h.clients[ws] = true
	h.clientsMu.Unlock()
	log.Printf("🔌 [WebSocket] 新客户端连接实时日志流 (当前连接数: %d)", len(h.clients))

	// 发送初始问候
	_ = ws.WriteJSON(map[string]interface{}{
		"type":    "connected",
		"message": "Connected to FreeSWITCH Console Log Stream via Go Sidecar",
		"time":    time.Now().Format("2006-01-02 15:04:05"),
	})

	// 监听客户端消息（支持客户端发指令）
	for {
		_, _, err := ws.ReadMessage()
		if err != nil {
			h.clientsMu.Lock()
			delete(h.clients, ws)
			h.clientsMu.Unlock()
			log.Printf("🔌 [WebSocket] 客户端断开连接")
			break
		}
	}
}

// startESLLogSubscriber 建立独立 ESL 连接，订阅 log notice
func (h *WSLogHandler) startESLLogSubscriber() {
	for {
		conn, err := net.DialTimeout("tcp", h.eslAddr, 5*time.Second)
		if err != nil {
			time.Sleep(3 * time.Second)
			continue
		}

		reader := bufio.NewReader(conn)

		// 鉴权
		_, err = fmt.Fprintf(conn, "auth %s\n\n", h.eslPassword)
		if err != nil {
			conn.Close()
			time.Sleep(3 * time.Second)
			continue
		}

		// 订阅日志 notice 级别
		_, err = fmt.Fprintf(conn, "log notice\n\n")
		if err != nil {
			conn.Close()
			time.Sleep(3 * time.Second)
			continue
		}

		log.Printf("📜 [ESL Log] 成功订阅 FreeSWITCH 实时日志流 (log notice)")

		// 读取循环
		for {
			line, err := reader.ReadString('\n')
			if err != nil {
				break
			}
			line = strings.TrimSpace(line)
			if line != "" && !strings.HasPrefix(line, "Content-") && !strings.HasPrefix(line, "Reply-") {
				select {
				case h.broadcast <- line:
				default:
				}
			}
		}

		conn.Close()
		time.Sleep(3 * time.Second)
	}
}

// startBroadcastLoop 广播日志到所有活跃 WebSocket 客户端
func (h *WSLogHandler) startBroadcastLoop() {
	for logLine := range h.broadcast {
		msg := map[string]interface{}{
			"type": "log",
			"text": logLine,
			"time": time.Now().Format("15:04:05"),
		}

		h.clientsMu.Lock()
		for client := range h.clients {
			err := client.WriteJSON(msg)
			if err != nil {
				client.Close()
				delete(h.clients, client)
			}
		}
		h.clientsMu.Unlock()
	}
}
