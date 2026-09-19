package event

import (
	"encoding/json"
	"testing"
)

// TestNormalizerRecordingContract verifies the recording fixture shared with the Java consumer.
func TestNormalizerRecordingContract(t *testing.T) {
	normalizer := NewNormalizer("fs-node-01")
	result := normalizer.Normalize(map[string]string{
		"Event-Name":         "RECORD_STOP",
		"Unique-ID":          "channel-01",
		"variable_ctrl_uuid": "ctrl-01",
		"Record-File-Path":   "/recordings/call-01.wav",
		"Record-Seconds":     "45",
	})
	if result == nil {
		t.Fatal("recording event must be normalized")
	}
	if result.Category != "record" {
		t.Fatalf("unexpected category: %s", result.Category)
	}

	var fixture struct {
		JSONRPC string `json:"jsonrpc"`
		Method  string `json:"method"`
		Params  struct {
			NodeID   string `json:"node_id"`
			CtrlUUID string `json:"ctrl_uuid"`
			UUID     string `json:"uuid"`
			Action   string `json:"action"`
			FilePath string `json:"file_path"`
			Seconds  int    `json:"seconds"`
		} `json:"params"`
	}
	if err := json.Unmarshal(result.RawJSON, &fixture); err != nil {
		t.Fatalf("decode normalized event: %v", err)
	}
	if fixture.JSONRPC != "2.0" || fixture.Method != "Event.Recording" {
		t.Fatalf("unexpected envelope: jsonrpc=%s method=%s", fixture.JSONRPC, fixture.Method)
	}
	if fixture.Params.NodeID != "fs-node-01" || fixture.Params.CtrlUUID != "ctrl-01" || fixture.Params.UUID != "channel-01" {
		t.Fatalf("identity fields changed: %+v", fixture.Params)
	}
	if fixture.Params.Action != "STOP" || fixture.Params.FilePath != "/recordings/call-01.wav" || fixture.Params.Seconds != 45 {
		t.Fatalf("recording fields changed: %+v", fixture.Params)
	}
}
