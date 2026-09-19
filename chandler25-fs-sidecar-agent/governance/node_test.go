package governance

import "testing"

// TestAdmissionRequiresHealthyNode verifies fail-closed admission without changing active calls.
func TestAdmissionRequiresHealthyNode(t *testing.T) {
	for _, state := range []NodeState{StateDraining, StateOffline, StateOverloaded, NodeState("UNKNOWN"), NodeState("")} {
		t.Run(string(state), func(t *testing.T) {
			manager := NewNodeManager("node-test", 10)
			manager.SetActiveChannels(2)
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
	if accepted, _ := manager.IsAcceptingCalls(); !accepted {
		t.Fatal("healthy node with capacity rejected")
	}
	manager.SetActiveChannels(2)
	manager.Drain()
	manager.Resume()
	if accepted, reason := manager.IsAcceptingCalls(); accepted || reason != StateOverloaded {
		t.Fatal("resume bypassed capacity limit")
	}
	manager.DecrementChannels()
	if accepted, _ := manager.IsAcceptingCalls(); !accepted {
		t.Fatal("healthy node rejected after capacity became available")
	}
}
