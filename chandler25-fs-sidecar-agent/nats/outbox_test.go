package nats

import (
	"chandler25-fs-sidecar-agent/event"
	natsgo "github.com/nats-io/nats.go"
	"os"
	"strings"
	"testing"
	"time"
)

// TestOutboxRestartPreservesIngressOrder exercises the real disk queue and broker
// acknowledgement. It only connects to an explicitly enabled disposable broker.
func TestOutboxRestartPreservesIngressOrder(t *testing.T) {
	if os.Getenv("FCC_TEST_NATS_URL") != "nats://127.0.0.1:14222" {
		t.Skip("disposable NATS not enabled")
	}
	nc, err := natsgo.Connect(os.Getenv("FCC_TEST_NATS_URL"))
	if err != nil {
		t.Fatal(err)
	}
	defer nc.Close()
	js, err := nc.JetStream()
	if err != nil {
		t.Fatal(err)
	}
	name := "FCC_TEST_OUTBOX"
	if _, err = js.AddStream(&natsgo.StreamConfig{Name: name, Subjects: []string{"fs.event.outbox-test.>"}, Storage: natsgo.FileStorage}); err != nil {
		t.Fatal(err)
	}
	defer js.DeleteStream(name)
	root := t.TempDir()
	out, err := StartEventOutbox(root, nil, "outbox-test")
	if err != nil {
		t.Fatal(err)
	}
	out.Close()
	for _, id := range []string{strings.Repeat("f", 64), strings.Repeat("a", 64)} {
		if err = out.Store(&event.NormalizedEventResult{Category: "channel", EventID: id, RawJSON: []byte(`{"params":{"timestamp":1},"id":"` + id + `"}`)}); err != nil {
			t.Fatal(err)
		}
	}
	restarted, err := StartEventOutbox(root, nil, "outbox-test")
	if err != nil {
		t.Fatal(err)
	}
	restarted.Close()
	if err = restarted.Store(&event.NormalizedEventResult{Category: "channel", EventID: strings.Repeat("b", 64), RawJSON: []byte(`{"params":{"timestamp":0},"id":"b"}`)}); err != nil {
		t.Fatal(err)
	}
	restarted.client = &Client{nc: nc}
	restarted.flush()
	entries, err := os.ReadDir(root)
	if err != nil || len(entries) != 0 {
		t.Fatalf("acked queue not drained: %v %v", entries, err)
	}
	sub, err := js.PullSubscribe("fs.event.outbox-test.>", "outbox-test", natsgo.BindStream(name))
	if err != nil {
		t.Fatal(err)
	}
	messages, err := sub.Fetch(3, natsgo.MaxWait(time.Second))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(messages[0].Data), strings.Repeat("f", 64)) || !strings.Contains(string(messages[1].Data), strings.Repeat("a", 64)) {
		t.Fatal("source timestamp/hash reordered ingress")
	}
	for _, message := range messages {
		if err = message.AckSync(); err != nil {
			t.Fatal(err)
		}
	}
}
