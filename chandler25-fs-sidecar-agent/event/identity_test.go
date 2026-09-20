package event

import (
	"encoding/json"
	"testing"
)

func TestStableSourceIdentity(t *testing.T) {
	raw := map[string]string{"Event-Name": "CHANNEL_CREATE", "Unique-ID": "channel", "Core-UUID": "boot", "Event-Sequence": "1", "Event-Date-Timestamp": "1000000"}
	first := NewNormalizer("node").Normalize(raw)
	second := NewNormalizer("node").Normalize(raw)
	if first.EventID == "" || first.EventID != second.EventID {
		t.Fatal("identity changed on replay")
	}
	raw["Event-Sequence"] = "2"
	if NewNormalizer("node").Normalize(raw).EventID == first.EventID {
		t.Fatal("different events collapsed")
	}
	var envelope struct {
		Params struct {
			Timestamp int64 `json:"timestamp"`
		}
	}
	json.Unmarshal(first.RawJSON, &envelope)
	if envelope.Params.Timestamp != 1000 {
		t.Fatal("source timestamp not preserved")
	}
}
