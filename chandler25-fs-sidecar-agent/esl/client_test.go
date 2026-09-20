package esl

import (
	"io"
	"net"
	"strings"
	"testing"
)

// TestTimeoutRetiresConnection proves a late uncorrelated ESL reply cannot satisfy the next command.
func TestTimeoutRetiresConnection(t *testing.T) {
	clientConn, serverConn := net.Pipe()
	defer serverConn.Close()
	c := NewClient("", "")
	c.conn, c.ready = clientConn, true
	go io.Copy(io.Discard, serverConn)
	if _, err := c.ExecuteAPI("status", ""); err == nil || !strings.Contains(err.Error(), "结果未知") {
		t.Fatalf("expected unknown outcome on timeout, got %v", err)
	}
	if _, err := c.ExecuteAPI("status", ""); err == nil || !strings.Contains(err.Error(), "离线") {
		t.Fatalf("timed-out connection reused: %v", err)
	}
}

// TestRepliesAreConnectionScoped rejects an old connection response after reconnect.
func TestRepliesAreConnectionScoped(t *testing.T) {
	current, peer := net.Pipe()
	old, oldPeer := net.Pipe()
	defer current.Close()
	defer peer.Close()
	defer old.Close()
	defer oldPeer.Close()
	c := NewClient("", "")
	c.conn, c.ready = current, true
	go func() {
		buffer := make([]byte, 128)
		_, _ = peer.Read(buffer)
		c.cmdReplyCh <- apiReply{conn: old, body: "stale"}
		c.cmdReplyCh <- apiReply{conn: current, body: "+OK current"}
	}()
	result, err := c.ExecuteAPI("status", "")
	if err != nil || result != "+OK current" {
		t.Fatalf("wrong reply: %q %v", result, err)
	}
	c.invalidate(old)
	if c.conn != current || !c.ready {
		t.Fatal("old disconnect invalidated new connection")
	}
}
