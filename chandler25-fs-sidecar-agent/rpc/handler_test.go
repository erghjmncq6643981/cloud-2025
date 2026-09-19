package rpc

import (
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
