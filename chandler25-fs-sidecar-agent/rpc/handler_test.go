package rpc

import (
	"chandler25-fs-sidecar-agent/governance"
	"encoding/json"
	"testing"
)

func TestOnlyCanonicalMethodsAreRegistered(t *testing.T) {
	dispatcher := NewDispatcher(nil, nil)
	for _, method := range []string{"call.originate", "call.bridge", "call.hangup", "call.transfer", "call.playAndGetDigits", "call.recordStart", "call.recordStop", "node.drain", "node.resume", "node.status", "node.rawEsl", "FNode.Bridge"} {
		request, _ := json.Marshal(map[string]interface{}{"jsonrpc": "2.0", "id": "contract-test", "method": method})
		var reply struct {
			Error struct {
				Code int `json:"code"`
			} `json:"error"`
		}
		if err := json.Unmarshal(dispatcher.HandleRaw(request), &reply); err != nil {
			t.Fatal(err)
		}
		if reply.Error.Code != ErrCodeMethodNotFound {
			t.Fatalf("%s must be rejected", method)
		}
	}
	for _, method := range []string{"FNode.Dial", "FNode.ChannelBridge", "FNode.Record", "FNode.Status"} {
		if _, ok := dispatcher.handlers[method]; !ok {
			t.Fatalf("missing canonical method %s", method)
		}
	}
}

// TestOfflineDialIsNotReportedAsCapacityExhaustion verifies rejection before touching ESL.
func TestOfflineDialIsNotReportedAsCapacityExhaustion(t *testing.T) {
	d := NewDispatcher(nil, governance.NewNodeManager("test", 10))
	var reply JsonRpcResponse
	if err := json.Unmarshal(d.HandleRaw([]byte(`{"jsonrpc":"2.0","id":"offline","method":"FNode.Dial","params":{}}`)), &reply); err != nil {
		t.Fatal(err)
	}
	if reply.Error == nil || reply.Error.Code != ErrCodeInternalError {
		t.Fatalf("wrong offline result: %+v", reply)
	}
}
