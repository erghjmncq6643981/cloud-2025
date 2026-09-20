package event

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"strconv"
	"time"
)

// Normalize adds a stable source identity and source timestamp to every event.
// FreeSWITCH Core-UUID/Event-Sequence identify an event across Sidecar restarts;
// full raw-event hashing is the fallback when those headers are absent.
func (n *Normalizer) Normalize(raw map[string]string) *NormalizedEventResult {
	result := n.normalizePayload(raw)
	if result == nil {
		return nil
	}
	source := raw["Core-UUID"] + ":" + raw["Event-Sequence"]
	if raw["Core-UUID"] == "" || raw["Event-Sequence"] == "" {
		encoded, _ := json.Marshal(raw)
		source = string(encoded)
	}
	digest := sha256.Sum256([]byte(n.nodeID + ":" + source))
	id := hex.EncodeToString(digest[:])
	var envelope map[string]interface{}
	if json.Unmarshal(result.RawJSON, &envelope) != nil {
		return nil
	}
	params, ok := envelope["params"].(map[string]interface{})
	if !ok {
		return nil
	}
	params["event_id"] = id
	params["source_boot_id"] = raw["Core-UUID"]
	params["received_at"] = time.Now().UnixMilli()
	if micros, err := strconv.ParseInt(raw["Event-Date-Timestamp"], 10, 64); err == nil && micros > 0 {
		params["timestamp"] = micros / 1000
	}
	result.RawJSON, _ = json.Marshal(envelope)
	result.EventID = id
	return result
}
