package nats

import (
	"bufio"
	"fmt"
	"net"
	"strings"
	"testing"
	"time"

	"chandler25-fs-sidecar-agent/governance"
	"chandler25-fs-sidecar-agent/rpc"
)

// TestInitialOutageRestoresSubscription exercises the NATS wire handshake after initial refusal.
func TestInitialOutageRestoresSubscription(t *testing.T) {
	reserved, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	address := reserved.Addr().String()
	reserved.Close()
	c, err := NewClient("nats://"+address, "recovery-test", rpc.NewDispatcher(nil, governance.NewNodeManager("recovery-test", 10)))
	if err != nil {
		t.Fatalf("initial outage lost client: %v", err)
	}
	defer c.Close()
	if err := c.StartListeningRPC(); err != nil {
		t.Fatal(err)
	}
	if err := c.StartListeningDispatchRPC(); err != nil {
		t.Fatal(err)
	}
	listener, err := net.Listen("tcp", address)
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	_ = listener.(*net.TCPListener).SetDeadline(time.Now().Add(10 * time.Second))
	conn, err := listener.Accept()
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
	fmt.Fprint(conn, "INFO {\"server_id\":\"test\",\"version\":\"2.10.0\",\"proto\":1,\"max_payload\":1048576}\r\n")
	reader := bufio.NewReader(conn)
	nodeSubjectSeen := false
	dispatchSubjectSeen := false
	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			t.Fatalf("subscription not recovered: %v", err)
		}
		if strings.HasPrefix(line, "PING") {
			fmt.Fprint(conn, "PONG\r\n")
		}
		if strings.HasPrefix(line, "SUB fs.cmd.recovery-test ") {
			nodeSubjectSeen = true
		}
		if strings.HasPrefix(line, "SUB fs.cmd.dispatch fs-sidecar-dispatchers ") {
			dispatchSubjectSeen = true
		}
		if nodeSubjectSeen && dispatchSubjectSeen {
			return
		}
	}
}
