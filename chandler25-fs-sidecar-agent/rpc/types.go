package rpc

import "encoding/json"

// JSON-RPC 2.0 基础报文规范
type JsonRpcRequest struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      interface{}     `json:"id"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params,omitempty"`
}

type JsonRpcResponse struct {
	JSONRPC string        `json:"jsonrpc"`
	ID      interface{}   `json:"id"`
	Result  interface{}   `json:"result,omitempty"`
	Error   *JsonRpcError `json:"error,omitempty"`
}

type JsonRpcError struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}

// 标准 JSON-RPC 错误码
const (
	ErrCodeParseError     = -32700
	ErrCodeInvalidRequest = -32600
	ErrCodeMethodNotFound = -32601
	ErrCodeInvalidParams  = -32602
	ErrCodeInternalError  = -32603

	// 呼叫中心通信专有错误码
	ErrCodeChannelNotFound = -32001
	ErrCodeBridgeFailed    = -32002
	ErrCodeOriginateFailed = -32003
	ErrCodeMediaError      = -32004
	ErrCodeNodeOverloaded  = -32005
	ErrCodeNodeDraining    = -32006
)

// FNodeResult FNode 规范统一返回体
type FNodeResult struct {
	NodeID   string      `json:"node_id"`
	Code     int         `json:"code"`
	Message  string      `json:"message"`
	UUID     string      `json:"uuid,omitempty"`
	CtrlUUID string      `json:"ctrl_uuid,omitempty"`
	Cause    string      `json:"cause,omitempty"`
	DTMF     string      `json:"dtmf,omitempty"`
	Data     interface{} `json:"data,omitempty"`
}

func NewSuccessResponse(id interface{}, result interface{}) *JsonRpcResponse {
	return &JsonRpcResponse{
		JSONRPC: "2.0",
		ID:      id,
		Result:  result,
	}
}

func NewFNodeSuccessResponse(id interface{}, nodeID, uuid, ctrlUUID string, code int, message string) *JsonRpcResponse {
	if code == 0 {
		code = 200
	}
	if message == "" {
		message = "OK"
	}
	return &JsonRpcResponse{
		JSONRPC: "2.0",
		ID:      id,
		Result: &FNodeResult{
			NodeID:   nodeID,
			Code:     code,
			Message:  message,
			UUID:     uuid,
			CtrlUUID: ctrlUUID,
			Cause:    "SUCCESS",
		},
	}
}

func NewErrorResponse(id interface{}, code int, message string, data interface{}) *JsonRpcResponse {
	return &JsonRpcResponse{
		JSONRPC: "2.0",
		ID:      id,
		Error: &JsonRpcError{
			Code:    code,
			Message: message,
			Data:    data,
		},
	}
}

