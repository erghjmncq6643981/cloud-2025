package nats

import (
	"chandler25-fs-sidecar-agent/event"
	"encoding/json"
	"fmt"
	natsgo "github.com/nats-io/nats.go"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

// EventOutbox persists events before publication and deletes only after JetStream ACK.
type EventOutbox struct {
	root      string
	nodeID    string
	client    *Client
	stop      chan struct{}
	mu        sync.Mutex
	lastOrder int64
}
type outboxEvent struct {
	Category string          `json:"category"`
	Data     json.RawMessage `json:"data"`
	ID       string          `json:"id"`
}

// StartEventOutbox requires a provisioned FCC_EVENTS stream; it never creates or changes server retention.
func StartEventOutbox(root string, client *Client, nodeID string) (*EventOutbox, error) {
	if root == "" {
		return nil, fmt.Errorf("EVENT_OUTBOX_DIR is required")
	}
	if err := os.MkdirAll(root, 0700); err != nil {
		return nil, err
	}
	out := &EventOutbox{root: root, nodeID: nodeID, client: client, stop: make(chan struct{})}
	entries, err := os.ReadDir(root)
	if err != nil {
		return nil, err
	}
	for _, entry := range entries {
		order, err := strconv.ParseInt(strings.SplitN(entry.Name(), "-", 2)[0], 10, 64)
		if err == nil && order > out.lastOrder {
			out.lastOrder = order
		}
	}
	go out.run()
	return out, nil
}

// Store synchronously flushes the normalized envelope; a disk error must stop ingress.
func (o *EventOutbox) Store(e *event.NormalizedEventResult) error {
	if !regexp.MustCompile(`^[a-f0-9]{64}$`).MatchString(e.EventID) {
		return fmt.Errorf("invalid source event identity")
	}
	o.mu.Lock()
	defer o.mu.Unlock()
	data, err := json.Marshal(outboxEvent{Category: e.Category, Data: e.RawJSON, ID: e.EventID})
	if err != nil {
		return err
	}
	file, err := os.CreateTemp(o.root, "event-*.tmp")
	if err != nil {
		return err
	}
	name := file.Name()
	defer os.Remove(name)
	if _, err = file.Write(data); err == nil {
		err = file.Sync()
	}
	closeErr := file.Close()
	if err != nil {
		return err
	}
	if closeErr != nil {
		return closeErr
	}
	// Preserve ingress order even when source events share a millisecond or the
	// wall clock moves backwards. On restart, advance past persisted filenames.
	order := time.Now().UnixNano()
	if order <= o.lastOrder {
		order = o.lastOrder + 1
	}
	o.lastOrder = order
	target := filepath.Join(o.root, fmt.Sprintf("%020d-%s.json", order, e.EventID))
	if _, err = os.Stat(target); err == nil {
		return nil
	}
	return os.Rename(name, target)
}

func (o *EventOutbox) run() {
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-o.stop:
			return
		case <-ticker.C:
			o.flush()
		}
	}
}

func (o *EventOutbox) flush() {
	if o.client == nil || !o.client.nc.IsConnected() {
		return
	}
	js, err := o.client.nc.JetStream(natsgo.MaxWait(3 * time.Second))
	if err != nil {
		return
	}
	entries, err := os.ReadDir(o.root)
	if err != nil {
		return
	}
	published := 0
	for _, entry := range entries {
		if filepath.Ext(entry.Name()) != ".json" {
			continue
		}
		filename := filepath.Join(o.root, entry.Name())
		data, err := os.ReadFile(filename)
		if err != nil {
			continue
		}
		var item outboxEvent
		if json.Unmarshal(data, &item) != nil {
			continue
		}
		subject := fmt.Sprintf("fs.event.%s.%s", o.nodeID, item.Category)
		if _, err = js.Publish(subject, item.Data, natsgo.MsgId(item.ID)); err != nil {
			return
		}
		_ = os.Remove(filename)
		published++
		if published >= 100 {
			return
		}
	}
}

// Close stops publishing; queued events remain on disk for the next process.
func (o *EventOutbox) Close() { close(o.stop) }
