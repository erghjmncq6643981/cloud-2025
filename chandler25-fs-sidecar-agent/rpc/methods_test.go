package rpc

import (
	"strings"
	"testing"
)

// TestPlayCommand verifies that closing media is played before the terminal
// hangup instead of being interrupted by a second command.
func TestPlayCommand(t *testing.T) {
	command, args, err := playCommand(
		"channel-a",
		"/work/closing.wav",
		"PARK",
		"closing-voice-1",
		"ctrl-1",
	)
	if err != nil {
		t.Fatal(err)
	}
	if command != "uuid_transfer" ||
		!strings.Contains(args, "set:fcc_command_id=closing-voice-1") ||
		!strings.Contains(args, "playback:/work/closing.wav,park") {
		t.Fatalf("unexpected closing playback command: %s %s", command, args)
	}

	if _, _, err = playCommand("channel-a", "/work/closing.wav", "TRANSFER", "play-1", "ctrl-1"); err == nil {
		t.Fatal("unsupported post action must be rejected")
	}
}

// TestRequestCommandID verifies command-result correlation cannot inject an
// inline FreeSWITCH application.
func TestRequestCommandID(t *testing.T) {
	if value, err := requestCommandID("binding-result-9001"); err != nil || value == "" {
		t.Fatalf("expected canonical command id, value=%q err=%v", value, err)
	}
	if _, err := requestCommandID("bad,hangup"); err == nil {
		t.Fatal("unsafe command id must be rejected")
	}
}

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

// TestResolveTransferDestination verifies that Java sends business fields and
// the Sidecar alone builds the FreeSWITCH transfer expression.
func TestResolveTransferDestination(t *testing.T) {
	destination, err := resolveTransferDestination("901001", "default")
	if err != nil {
		t.Fatal(err)
	}
	if destination != "901001 XML default" {
		t.Fatalf("unexpected transfer destination: %s", destination)
	}
	if _, err = resolveTransferDestination("901001 XML default", "default"); err == nil {
		t.Fatal("raw FreeSWITCH transfer expression must be rejected")
	}
}

// TestReadDTMFPostAction verifies that only the canonical post actions reach
// the FreeSWITCH inline application builder.
func TestReadDTMFPostAction(t *testing.T) {
	action, err := readDTMFPostAction(" PARK ")
	if err != nil || action != "park" {
		t.Fatalf("expected canonical park action, action=%q err=%v", action, err)
	}
	if _, err = readDTMFPostAction("transfer"); err == nil {
		t.Fatal("unknown DTMF post action must be rejected")
	}
}

// TestRecordAction verifies that recording does not default an invalid or
// missing operation to START.
func TestRecordAction(t *testing.T) {
	action, err := recordAction("STOP")
	if err != nil || action != "stop" {
		t.Fatalf("expected canonical stop action, action=%q err=%v", action, err)
	}
	if _, err = recordAction(""); err == nil {
		t.Fatal("missing recording action must be rejected")
	}
}
