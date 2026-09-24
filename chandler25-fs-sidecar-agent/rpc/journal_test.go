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

// TestCommandJournalFinalResult keeps transport replay separate from the
// asynchronous outcome and survives a Sidecar restart.
func TestCommandJournalFinalResult(t *testing.T) {
	root := t.TempDir()
	journal, err := NewCommandJournal(root)
	if err != nil {
		t.Fatal(err)
	}
	req := &JsonRpcRequest{ID: "binding-dtmf-9001", Method: "FNode.ReadDTMF", Params: json.RawMessage(`{"uuid":"channel"}`)}
	accepted := func(req *JsonRpcRequest) *JsonRpcResponse {
		return NewFNodeSuccessResponse(req.ID, "node-a", "channel", "ctrl", 202, "ACCEPTED")
	}
	journal.Execute(req, accepted)
	final := FNodeResult{NodeID: "node-a", Code: 200, Message: "OK", Data: map[string]interface{}{
		"command_status": "SUCCEEDED", "result": map[string]string{"dtmf": "901001"},
	}}
	if err := journal.Complete("binding-dtmf-9001", final); err != nil {
		t.Fatal(err)
	}
	journal, err = NewCommandJournal(root)
	if err != nil {
		t.Fatal(err)
	}
	if err := journal.Complete("binding-dtmf-9001", final); err != nil {
		t.Fatalf("replayed completion must be idempotent: %v", err)
	}
	lookup := journal.Lookup(req.ID)
	data, _ := json.Marshal(lookup.Result)
	var result FNodeResult
	if json.Unmarshal(data, &result) != nil || result.Code != 200 {
		t.Fatalf("missing final outcome after restart: %+v", lookup)
	}
	if journal.Execute(req, func(*JsonRpcRequest) *JsonRpcResponse {
		t.Fatal("transport retry executed command again")
		return nil
	}).Result == nil {
		t.Fatal("transport retry lost its original acceptance")
	}
	if err := journal.Complete("unseen-command", final); err == nil {
		t.Fatal("final result without durable intent must be rejected")
	}
}
