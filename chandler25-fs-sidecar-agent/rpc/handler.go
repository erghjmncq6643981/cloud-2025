package rpc

import (
	"encoding/json"
	"fmt"
	"log"

	"chandler25-fs-sidecar-agent/esl"
	"chandler25-fs-sidecar-agent/governance"
)

type MethodHandler func(req *JsonRpcRequest) *JsonRpcResponse

// MediaResolver resolves caller text into a local file shared with FreeSWITCH.
type MediaResolver interface {
	Resolve(text, voice string) (string, error)
}

// Dispatcher JSON-RPC 2.0 路由器
type Dispatcher struct {
	handlers      map[string]MethodHandler
	esl           *esl.Client
	gov           *governance.NodeManager
	journal       *CommandJournal
	mediaResolver MediaResolver
}

// UseCommandJournal enables persistent deduplication before accepting traffic.
func (d *Dispatcher) UseCommandJournal(directory string) error {
	journal, err := NewCommandJournal(directory)
	if err == nil {
		d.journal = journal
	}
	return err
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

// SetMediaResolver configures the provider used by TEXT media commands.
func (d *Dispatcher) SetMediaResolver(resolver MediaResolver) {
	d.mediaResolver = resolver
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

	var resp *JsonRpcResponse
	if d.journal != nil && req.Method != "FNode.ChannelSnapshot" && req.Method != "FNode.Status" && req.Method != "FNode.CommandResult" && req.Method != "FNode.Drain" && req.Method != "FNode.Resume" {
		resp = d.journal.Execute(&req, handler)
	} else {
		resp = handler(&req)
	}
	out, _ := json.Marshal(resp)
	return out
}

func (d *Dispatcher) registerMethods() {
	// FNode 标准规范控制方法
	d.handlers["FNode.Dial"] = d.handleFNodeDial
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
	d.handlers["FNode.ChannelSnapshot"] = d.handleChannelSnapshot
	d.handlers["FNode.CommandResult"] = func(req *JsonRpcRequest) *JsonRpcResponse {
		var params struct {
			CommandID string `json:"command_id"`
		}
		if json.Unmarshal(req.Params, &params) != nil || params.CommandID == "" || d.journal == nil {
			return NewErrorResponse(req.ID, ErrCodeInvalidParams, "command_id and configured journal required", nil)
		}
		response := d.journal.Lookup(params.CommandID)
		response.ID = req.ID
		return response
	}

	log.Printf("[RPC] 已注册 %d 个 FNode 标准控制方法", len(d.handlers))
}
