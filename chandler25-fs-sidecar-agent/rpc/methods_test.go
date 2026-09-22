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

// TestResolveDialDestination verifies that business routing context is expanded
// only inside the Sidecar and that raw FreeSWITCH expressions cannot be injected.
func TestResolveDialDestination(t *testing.T) {
	destination, err := resolveDialDestination("13800000000", "mobile")
	if err != nil {
		t.Fatal(err)
	}
	if destination != "loopback/13800000000/mobile" {
		t.Fatalf("unexpected context destination: %s", destination)
	}

	if _, err = resolveDialDestination("sofia/gateway/carrier/13800000000", "mobile"); err == nil {
		t.Fatal("raw FreeSWITCH destination must be rejected when context is supplied")
	}
	if _, err = resolveDialDestination("13800000000", "bad/context"); err == nil {
		t.Fatal("invalid context must be rejected")
	}
	if _, err = resolveDialDestination("1001", ""); err == nil {
		t.Fatal("missing context must be rejected")
	}
}
