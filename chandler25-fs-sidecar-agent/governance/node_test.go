package governance

import (
	"fmt"
	"sync"
	"testing"
)

// TestConcurrentDuplicateLifecycles verifies independent legs remain consistent under concurrent delivery.
func TestConcurrentDuplicateLifecycles(t *testing.T) {
	m := NewNodeManager("test", 100)
	var workers sync.WaitGroup
	for i := 0; i < 100; i++ {
		workers.Add(1)
		go func(i int) {
			defer workers.Done()
			uuid := fmt.Sprint(i)
			m.ObserveChannel(uuid, "START", 1)
			m.ObserveChannel(uuid, "START", 1)
			m.ObserveChannel(uuid, "DESTROY", 2)
			m.ObserveChannel(uuid, "DESTROY", 2)
			m.ObserveChannel(uuid, "START", 1)
		}(i)
	}
	workers.Wait()
	if m.GetSnapshot().ActiveChannels != 0 {
		t.Fatal("concurrent terminal replay leaked capacity")
	}
}

// TestTerminalHistoryIsBounded prevents an unbounded per-call memory leak while retaining a replay floor.
func TestTerminalHistoryIsBounded(t *testing.T) {
	m := NewNodeManager("test", 10)
	for i := 1; i <= terminalHistoryLimit+1; i++ {
		m.ObserveChannel(fmt.Sprint(i), "DESTROY", int64(i))
	}
	if len(m.channelTimes) != terminalHistoryLimit {
		t.Fatal("terminal history exceeded bound")
	}
	m.ObserveChannel("1", "START", 1)
	if m.GetSnapshot().ActiveChannels != 0 {
		t.Fatal("evicted old event revived a leg")
	}
}

// TestSnapshotFence protects newer events and restores calls after process restart.
func TestSnapshotFence(t *testing.T) {
	m := NewNodeManager("test", 10)
	before := m.ChannelRevision()
	m.ObserveChannel("a", "DESTROY", 100)
	if m.ReconcileChannels(before, []string{"a"}) {
		t.Fatal("stale snapshot resurrected terminal leg")
	}
	if !m.ReconcileChannels(m.ChannelRevision(), []string{"b", "c"}) {
		t.Fatal("valid snapshot rejected")
	}
	m.ObserveChannel("a", "START", 50)
	if got := m.GetSnapshot().ActiveChannels; got != 2 {
		t.Fatalf("replay corrupted restored count: %d", got)
	}
	if !m.ReconcileChannels(m.ChannelRevision(), nil) {
		t.Fatal("empty snapshot rejected")
	}
	if m.GetSnapshot().ActiveChannels != 0 {
		t.Fatal("snapshot did not remove missed hangups")
	}
}

// TestAdmissionRequiresHealthyNode verifies fail-closed admission without changing active calls.
func TestAdmissionRequiresHealthyNode(t *testing.T) {
	for _, state := range []NodeState{StateDraining, StateOffline, StateOverloaded, NodeState("UNKNOWN"), NodeState("")} {
		t.Run(string(state), func(t *testing.T) {
			manager := NewNodeManager("node-test", 10)
			manager.ObserveChannel("a", "START", 1)
			manager.ObserveChannel("b", "START", 2)
			manager.SetState(state)
			accepted, reason := manager.IsAcceptingCalls()
			if accepted || reason != state {
				t.Fatalf("non-healthy node admitted call: accepted=%v reason=%s", accepted, reason)
			}
			if manager.GetSnapshot().ActiveChannels != 2 {
				t.Fatal("admission must not terminate active calls")
			}
		})
	}
}

// TestHealthyAdmissionCapacityAndResume verifies capacity remains enforced after resume.
func TestHealthyAdmissionCapacityAndResume(t *testing.T) {
	manager := NewNodeManager("node-test", 2)
	manager.ObserveHealth(true)
	if accepted, _ := manager.IsAcceptingCalls(); !accepted {
		t.Fatal("healthy node with capacity rejected")
	}
	manager.ObserveChannel("a", "START", 1)
	manager.ObserveChannel("b", "START", 2)
	manager.Drain()
	manager.Resume()
	if accepted, reason := manager.IsAcceptingCalls(); accepted || reason != StateOverloaded {
		t.Fatal("resume bypassed capacity limit")
	}
	manager.ObserveChannel("a", "DESTROY", 3)
	if accepted, _ := manager.IsAcceptingCalls(); !accepted {
		t.Fatal("healthy node rejected after capacity became available")
	}
}

// TestDuplicateAndLateEvents verifies one leg cannot consume or release another leg's capacity.
func TestDuplicateAndLateEvents(t *testing.T) {
	m := NewNodeManager("test", 10)
	m.ObserveChannel("a", "START", 1)
	m.ObserveChannel("a", "CALLING", 2)
	m.ObserveChannel("b", "ANSWERED", 3)
	m.ObserveChannel("a", "DESTROY", 4)
	m.ObserveChannel("a", "DESTROY", 5)
	m.ObserveChannel("a", "START", 1)
	m.ObserveChannel("unknown", "DESTROY", 6)
	m.ObserveChannel("", "START", 7)
	if got := m.GetSnapshot().ActiveChannels; got != 1 {
		t.Fatalf("expected surviving leg b only, got %d", got)
	}
}

// TestDrainHealthAndStartup verifies startup and resume never bypass a failed probe.
func TestDrainHealthAndStartup(t *testing.T) {
	m := NewNodeManager("test", 10)
	if ok, _ := m.IsAcceptingCalls(); ok {
		t.Fatal("startup accepted before probe")
	}
	m.ObserveHealth(true)
	m.Drain()
	m.ObserveHealth(false)
	if m.GetState() != StateDraining {
		t.Fatal("probe cancelled drain")
	}
	m.Resume()
	if ok, state := m.IsAcceptingCalls(); ok || state != StateOffline {
		t.Fatal("resume bypassed failed probe")
	}
	m.Drain()
	m.ObserveHealth(true)
	if m.GetState() != StateDraining {
		t.Fatal("recovery cancelled drain")
	}
	m.Resume()
	if ok, _ := m.IsAcceptingCalls(); !ok {
		t.Fatal("healthy resume rejected")
	}
}
