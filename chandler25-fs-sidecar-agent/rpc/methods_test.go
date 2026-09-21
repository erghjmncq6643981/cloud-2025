package rpc

import (
	"strings"
	"testing"
)

// TestNewChannelUUID verifies generated channel identifiers are plain UUIDs and
// do not carry the retired business prefix.
func TestNewChannelUUID(t *testing.T) {
	value := newChannelUUID()
	if strings.HasPrefix(value, "call-") {
		t.Fatalf("channel UUID must not use the retired call- prefix: %s", value)
	}
	if len(value) != 36 || strings.Count(value, "-") != 4 {
		t.Fatalf("expected UUID-shaped channel identifier, got %s", value)
	}
}
