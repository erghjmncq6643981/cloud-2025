package rpc

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"
)

// CommandJournal persists intent before any effect. Missing result after a crash
// means UNKNOWN and is never automatically executed again.
type CommandJournal struct {
	root string
	mu   sync.Mutex
}

// NewCommandJournal opens a durable, node-local directory on a persistent volume.
func NewCommandJournal(root string) (*CommandJournal, error) {
	if root == "" {
		return nil, fmt.Errorf("COMMAND_JOURNAL_DIR is required")
	}
	if err := os.MkdirAll(root, 0700); err != nil {
		return nil, err
	}
	return &CommandJournal{root: root}, nil
}

func commandKey(id interface{}) (string, error) {
	text, ok := id.(string)
	if !ok || text == "" || len(text) > 200 {
		return "", fmt.Errorf("command ID must be a nonempty string of at most 200 characters")
	}
	digest := sha256.Sum256([]byte(text))
	return hex.EncodeToString(digest[:]), nil
}

// Execute serializes local command claims and durably stores only request hashes and results.
func (j *CommandJournal) Execute(req *JsonRpcRequest, handler MethodHandler) *JsonRpcResponse {
	j.mu.Lock()
	defer j.mu.Unlock()
	key, err := commandKey(req.ID)
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeInvalidRequest, err.Error(), nil)
	}
	var params interface{}
	if len(req.Params) > 0 {
		if json.Unmarshal(req.Params, &params) != nil {
			return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Invalid params", nil)
		}
	}
	canonical, _ := json.Marshal(struct {
		Method string
		Params interface{}
	}{req.Method, params})
	fingerprint := sha256.Sum256(canonical)
	intent := filepath.Join(j.root, key+".intent")
	file, err := os.OpenFile(intent, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if os.IsExist(err) {
		previous, readErr := os.ReadFile(intent)
		if readErr != nil || string(previous) != hex.EncodeToString(fingerprint[:]) {
			return NewErrorResponse(req.ID, ErrCodeInvalidRequest, "Command identity conflict or damaged journal", nil)
		}
		return j.result(req.ID, key)
	}
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeInternalError, "Cannot persist command intent", nil)
	}
	_, err = file.WriteString(hex.EncodeToString(fingerprint[:]))
	if err == nil {
		err = file.Sync()
	}
	closeErr := file.Close()
	if err != nil || closeErr != nil {
		return NewErrorResponse(req.ID, ErrCodeInternalError, "Cannot flush command intent", nil)
	}
	response := handler(req)
	data, err := json.Marshal(response)
	if err != nil {
		return NewErrorResponse(req.ID, -32000, "Command outcome UNKNOWN", nil)
	}
	// Separate intent/result files ensure a crash cannot erase proof of execution.
	output, err := os.OpenFile(filepath.Join(j.root, key+".result"), os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err == nil {
		_, err = output.Write(data)
		if err == nil {
			err = output.Sync()
		}
		closeErr = output.Close()
	}
	if err != nil || closeErr != nil {
		return NewErrorResponse(req.ID, -32000, "Command outcome UNKNOWN; result persistence failed", nil)
	}
	return response
}

func (j *CommandJournal) result(id interface{}, key string) *JsonRpcResponse {
	data, err := os.ReadFile(filepath.Join(j.root, key+".result"))
	var response JsonRpcResponse
	if err != nil || json.Unmarshal(data, &response) != nil {
		return NewErrorResponse(id, -32000, "Command outcome UNKNOWN; do not redial", nil)
	}
	response.ID = id
	return &response
}

// Lookup returns the recorded RPC outcome without reexecuting the command.
func (j *CommandJournal) Lookup(id interface{}) *JsonRpcResponse {
	j.mu.Lock()
	defer j.mu.Unlock()
	key, err := commandKey(id)
	if err != nil {
		return NewErrorResponse(id, ErrCodeInvalidParams, err.Error(), nil)
	}
	if _, err = os.Stat(filepath.Join(j.root, key+".intent")); os.IsNotExist(err) {
		return NewErrorResponse(id, -32004, "Command not recorded", nil)
	}
	return j.result(id, key)
}
