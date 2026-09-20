package rpc

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func TestCommandJournalRestartAndConflict(t *testing.T) {
	root := t.TempDir()
	journal, _ := NewCommandJournal(root)
	req := &JsonRpcRequest{ID: "dial-stable", Method: "FNode.Dial", Params: json.RawMessage(`{"uuid":"a"}`)}
	calls := 0
	handler := func(req *JsonRpcRequest) *JsonRpcResponse { calls++; return NewSuccessResponse(req.ID, "accepted") }
	journal.Execute(req, handler)
	journal, _ = NewCommandJournal(root)
	journal.Execute(req, handler)
	if calls != 1 {
		t.Fatal("duplicate dial after restart")
	}
	req.Params = json.RawMessage(`{"uuid":"b"}`)
	if journal.Execute(req, handler).Error == nil || calls != 1 {
		t.Fatal("identity reused with another request")
	}
}

func TestCommandJournalInterruptedExecutionRemainsUnknown(t *testing.T) {
	root := t.TempDir()
	journal, _ := NewCommandJournal(root)
	req := &JsonRpcRequest{ID: "interrupted", Method: "FNode.Dial", Params: json.RawMessage(`{}`)}
	journal.Execute(req, func(req *JsonRpcRequest) *JsonRpcResponse { return NewSuccessResponse(req.ID, "accepted") })
	key, _ := commandKey(req.ID)
	if err := os.Remove(filepath.Join(root, key+".result")); err != nil {
		t.Fatal(err)
	}
	result := journal.Execute(req, func(*JsonRpcRequest) *JsonRpcResponse { t.Fatal("unknown command reexecuted"); return nil })
	if result.Error == nil || result.Error.Code != -32000 {
		t.Fatal("expected UNKNOWN")
	}
}
