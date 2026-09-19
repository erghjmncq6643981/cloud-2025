package esl

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Client Inbound ESL 客户端
type Client struct {
	addr       string
	password   string
	conn       net.Conn
	reader     *bufio.Reader
	mu         sync.Mutex
	closed     bool
	eventChan  chan map[string]string
	cmdReplyMu sync.Mutex
	cmdReplyCh chan string
}

// NewClient 创建 ESL 客户端
func NewClient(addr, password string) *Client {
	return &Client{
		addr:       addr,
		password:   password,
		eventChan:  make(chan map[string]string, 2048),
		cmdReplyCh: make(chan string, 1),
	}
}

// EventChannel 返回接收归一化事件的通道
func (c *Client) EventChannel() <-chan map[string]string {
	return c.eventChan
}

// ConnectAndListen 连接 FreeSWITCH 并开启监听循环，内置自动断线重连
func (c *Client) ConnectAndListen() error {
	for {
		c.mu.Lock()
		if c.closed {
			c.mu.Unlock()
			return nil
		}
		c.mu.Unlock()

		err := c.connect()
		if err != nil {
			log.Printf("⚠️ [ESL] 连接 FreeSWITCH (%s) 失败: %v, 2秒后重试...", c.addr, err)
			time.Sleep(2 * time.Second)
			continue
		}

		log.Printf("✅ [ESL] 成功连接并鉴权 FreeSWITCH: %s", c.addr)

		// 订阅关键呼叫事件 (过滤无意义事件)
		err = c.subscribeEvents()
		if err != nil {
			log.Printf("⚠️ [ESL] 订阅事件失败: %v, 断开重连...", err)
			c.conn.Close()
			time.Sleep(2 * time.Second)
			continue
		}

		// 启动主读取循环 (阻塞直到断开)
		c.readLoop()

		log.Printf("🔌 [ESL] 与 FreeSWITCH 连接断开，准备重连...")
		time.Sleep(2 * time.Second)
	}
}

func (c *Client) connect() error {
	conn, err := net.DialTimeout("tcp", c.addr, 5*time.Second)
	if err != nil {
		return err
	}

	c.mu.Lock()
	c.conn = conn
	c.reader = bufio.NewReader(conn)
	c.mu.Unlock()

	// 1. 等待 auth/request
	headers, err := c.readHeaders()
	if err != nil {
		conn.Close()
		return fmt.Errorf("读取 auth/request 失败: %w", err)
	}

	if headers["content-type"] != "auth/request" {
		conn.Close()
		return fmt.Errorf("期望 auth/request, 收到: %s", headers["content-type"])
	}

	// 2. 发送 auth 密码
	_, err = fmt.Fprintf(conn, "auth %s\n\n", c.password)
	if err != nil {
		conn.Close()
		return fmt.Errorf("发送 auth 密码失败: %w", err)
	}

	// 3. 读取 auth 响应
	authReply, err := c.readHeaders()
	if err != nil {
		conn.Close()
		return fmt.Errorf("读取 auth 结果失败: %w", err)
	}

	replyText := authReply["reply-text"]
	if !strings.HasPrefix(replyText, "+OK") {
		conn.Close()
		return fmt.Errorf("ESL 鉴权失败: %s", replyText)
	}

	return nil
}

func (c *Client) subscribeEvents() error {
	c.mu.Lock()
	conn := c.conn
	c.mu.Unlock()

	if conn == nil {
		return errors.New("ESL 未连接")
	}

	// 订阅呼叫生命周期核心事件、录音、分机注册、中继探活及班长监管事件
	events := "CHANNEL_CREATE CHANNEL_PROGRESS CHANNEL_PROGRESS_MEDIA CHANNEL_ANSWER CHANNEL_PARK CHANNEL_HOLD CHANNEL_UNHOLD CHANNEL_BRIDGE CHANNEL_UNBRIDGE CHANNEL_HANGUP CHANNEL_HANGUP_COMPLETE CHANNEL_EXECUTE_COMPLETE DTMF RECORD_START RECORD_STOP CUSTOM conference::maintenance CUSTOM eavesdrop::start CUSTOM eavesdrop::stop CUSTOM sofia::register CUSTOM sofia::unregister CUSTOM sofia::expire CUSTOM sofia::gateway_state CUSTOM sofia::gateway_add CUSTOM sofia::gateway_delete"
	cmd := fmt.Sprintf("event plain %s\n\n", events)
	_, err := conn.Write([]byte(cmd))
	return err
}

// ExecuteAPI 同步执行 FreeSWITCH API 指令 (如 api uuid_bridge ...)
func (c *Client) ExecuteAPI(command, args string) (string, error) {
	c.cmdReplyMu.Lock()
	defer c.cmdReplyMu.Unlock()

	c.mu.Lock()
	conn := c.conn
	c.mu.Unlock()

	if conn == nil {
		return "", errors.New("FreeSWITCH ESL 离线")
	}

	var fullCmd string
	if args != "" {
		fullCmd = fmt.Sprintf("api %s %s\n\n", command, args)
	} else {
		fullCmd = fmt.Sprintf("api %s\n\n", command)
	}

	// 清理旧可能遗留的响应
	select {
	case <-c.cmdReplyCh:
	default:
	}

	_, err := conn.Write([]byte(fullCmd))
	if err != nil {
		return "", fmt.Errorf("发送指令失败: %w", err)
	}

	// 等待 api/response
	select {
	case reply := <-c.cmdReplyCh:
		return strings.TrimSpace(reply), nil
	case <-time.After(5 * time.Second):
		return "", errors.New("执行 ESL 指令超时 (5s)")
	}
}

func (c *Client) readLoop() {
	for {
		headers, err := c.readHeaders()
		if err != nil {
			if errors.Is(err, io.EOF) || strings.Contains(err.Error(), "use of closed") {
				return
			}
			log.Printf("⚠️ [ESL] 读取消息头异常: %v", err)
			return
		}

		contentType := headers["content-type"]
		contentLengthStr := headers["content-length"]
		contentLength := 0
		if contentLengthStr != "" {
			contentLength, _ = strconv.Atoi(contentLengthStr)
		}

		var body []byte
		if contentLength > 0 {
			body = make([]byte, contentLength)
			_, err := io.ReadFull(c.reader, body)
			if err != nil {
				log.Printf("⚠️ [ESL] 读取消息体异常: %v", err)
				return
			}
		}

		switch contentType {
		case "api/response":
			select {
			case c.cmdReplyCh <- string(body):
			default:
			}
		case "command/reply":
			// 响应确认
		case "text/event-plain":
			eventMap := parsePlainEvent(string(body))
			if len(eventMap) > 0 {
				select {
				case c.eventChan <- eventMap:
				default:
					log.Printf("⚠️ [ESL] 事件缓冲区满，丢弃事件: %s", eventMap["Event-Name"])
				}
			}
		case "text/disconnect-notice":
			log.Printf("🔌 [ESL] 收到 FreeSWITCH 断开通知")
			return
		}
	}
}

func (c *Client) readHeaders() (map[string]string, error) {
	headers := make(map[string]string)
	for {
		line, err := c.reader.ReadString('\n')
		if err != nil {
			return nil, err
		}
		line = strings.TrimRight(line, "\r\n")
		if line == "" {
			// 空行表示 Header 结束
			break
		}
		parts := strings.SplitN(line, ":", 2)
		if len(parts) == 2 {
			k := strings.ToLower(strings.TrimSpace(parts[0]))
			v := strings.TrimSpace(parts[1])
			headers[k] = v
		}
	}
	return headers, nil
}

func parsePlainEvent(body string) map[string]string {
	result := make(map[string]string)
	lines := strings.Split(body, "\n")
	for _, line := range lines {
		line = strings.TrimRight(line, "\r\n")
		if line == "" {
			continue
		}
		parts := strings.SplitN(line, ":", 2)
		if len(parts) == 2 {
			k := strings.TrimSpace(parts[0])
			v := strings.TrimSpace(parts[1])
			// FreeSWITCH URL 编码还原
			if strings.Contains(v, "%") {
				if unescaped, err := url.QueryUnescape(v); err == nil {
					v = unescaped
				}
			}
			result[k] = v
		}
	}
	return result
}

// Ping 执行 status 探活
func (c *Client) Ping() bool {
	res, err := c.ExecuteAPI("status", "")
	return err == nil && strings.Contains(res, "UP")
}

// Close 关闭客户端
func (c *Client) Close() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.closed = true
	if c.conn != nil {
		c.conn.Close()
	}
}
