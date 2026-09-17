package rpc

import (
	"encoding/json"
	"fmt"
	"log"

	"chandler25-fs-sidecar-agent/esl"
	"chandler25-fs-sidecar-agent/governance"
)

type MethodHandler func(req *JsonRpcRequest) *JsonRpcResponse

// Dispatcher JSON-RPC 2.0 路由器
type Dispatcher struct {
	handlers map[string]MethodHandler
	esl      *esl.Client
	gov      *governance.NodeManager
}

func NewDispatcher(eslClient *esl.Client, govManager *governance.NodeManager) *Dispatcher {
	d := &Dispatcher{
		handlers: make(map[string]MethodHandler),
		esl:      eslClient,
		gov:      govManager,
	}
	d.registerMethods()
	return d
}

// HandleRaw 接收原始 JSON 字节流，分发并返回响应字节流
func (d *Dispatcher) HandleRaw(data []byte) []byte {
	var req JsonRpcRequest
	err := json.Unmarshal(data, &req)
	if err != nil {
		resp := NewErrorResponse(nil, ErrCodeParseError, "Parse error: "+err.Error(), nil)
		out, _ := json.Marshal(resp)
		return out
	}

	if req.JSONRPC != "2.0" || req.Method == "" {
		resp := NewErrorResponse(req.ID, ErrCodeInvalidRequest, "Invalid JSON-RPC 2.0 Request", nil)
		out, _ := json.Marshal(resp)
		return out
	}

	handler, exists := d.handlers[req.Method]
	if !exists {
		resp := NewErrorResponse(req.ID, ErrCodeMethodNotFound, fmt.Sprintf("Method '%s' not found", req.Method), nil)
		out, _ := json.Marshal(resp)
		return out
	}

	resp := handler(&req)
	out, _ := json.Marshal(resp)
	return out
}

func (d *Dispatcher) registerMethods() {
	// FNode 标准规范控制方法
	d.handlers["FNode.Dial"] = d.handleFNodeDial
	d.handlers["FNode.Bridge"] = d.handleFNodeBridge
	d.handlers["FNode.ChannelBridge"] = d.handleFNodeBridge
	d.handlers["FNode.ReadDTMF"] = d.handleFNodeReadDTMF
	d.handlers["FNode.Play"] = d.handleFNodePlay
	d.handlers["FNode.Record"] = d.handleFNodeRecord
	d.handlers["FNode.Hangup"] = d.handleFNodeHangup
	d.handlers["FNode.Transfer"] = d.handleFNodeTransfer
	d.handlers["FNode.NativeAPI"] = d.handleFNodeNativeAPI
	d.handlers["FNode.Drain"] = d.handleFNodeDrain
	d.handlers["FNode.Resume"] = d.handleFNodeResume
	d.handlers["FNode.Status"] = d.handleFNodeStatus

	// 兼容旧版调用
	d.handlers["call.originate"] = d.handleFNodeDial
	d.handlers["call.bridge"] = d.handleFNodeBridge
	d.handlers["call.hangup"] = d.handleFNodeHangup
	d.handlers["call.transfer"] = d.handleFNodeTransfer
	d.handlers["call.playAndGetDigits"] = d.handleFNodeReadDTMF
	d.handlers["call.recordStart"] = d.handleFNodeRecord
	d.handlers["call.recordStop"] = d.handleFNodeRecord
	d.handlers["node.drain"] = d.handleFNodeDrain
	d.handlers["node.resume"] = d.handleFNodeResume
	d.handlers["node.status"] = d.handleFNodeStatus
	d.handlers["node.rawEsl"] = d.handleFNodeNativeAPI

	log.Printf("🛠️ [RPC] 已成功注册 %d 个标准 JSON-RPC 2.0 控制方法 (FNode.* 与兼容方法)", len(d.handlers))
}
