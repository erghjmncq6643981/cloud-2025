package governance

import (
	"sync"
	"sync/atomic"
	"time"
)

type NodeState string

const (
	StateHealthy    NodeState = "HEALTHY"
	StateDraining  NodeState = "DRAINING"
	StateOverloaded NodeState = "OVERLOADED"
	StateOffline    NodeState = "OFFLINE"
)

// NodeManager 软交换节点生命周期与治理管理器
type NodeManager struct {
	nodeID         string
	maxChannels    int
	state          NodeState
	activeChannels int64
	startTime      time.Time
	mu             sync.RWMutex
}

func NewNodeManager(nodeID string, maxChannels int) *NodeManager {
	return &NodeManager{
		nodeID:      nodeID,
		maxChannels: maxChannels,
		state:       StateHealthy,
		startTime:   time.Now(),
	}
}

func (m *NodeManager) NodeID() string {
	return m.nodeID
}

func (m *NodeManager) SetState(s NodeState) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.state = s
}

func (m *NodeManager) GetState() NodeState {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.state
}

// Drain 开启呼叫沥干模式（停止接入新呼叫，等待存量自然挂机）
func (m *NodeManager) Drain() {
	m.SetState(StateDraining)
}

// Resume 恢复接单
func (m *NodeManager) Resume() {
	m.SetState(StateHealthy)
}

func (m *NodeManager) IncrementChannels() {
	atomic.AddInt64(&m.activeChannels, 1)
}

func (m *NodeManager) DecrementChannels() {
	current := atomic.AddInt64(&m.activeChannels, -1)
	if current < 0 {
		atomic.StoreInt64(&m.activeChannels, 0)
	}
}

func (m *NodeManager) SetActiveChannels(n int64) {
	atomic.StoreInt64(&m.activeChannels, n)
}

// IsAcceptingCalls 检查当前是否允许接入新通话
func (m *NodeManager) IsAcceptingCalls() (bool, NodeState) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	if m.state == StateDraining {
		return false, StateDraining
	}

	active := atomic.LoadInt64(&m.activeChannels)
	if active >= int64(m.maxChannels) {
		return false, StateOverloaded
	}

	return true, m.state
}

// NodeStatusSnapshot 节点状态快照报文
type NodeStatusSnapshot struct {
	NodeID         string    `json:"nodeId"`
	State          NodeState `json:"state"`
	ActiveChannels int64     `json:"activeChannels"`
	MaxChannels    int       `json:"maxChannels"`
	UptimeSeconds  int64     `json:"uptimeSeconds"`
	Timestamp      int64     `json:"timestamp"`
}

func (m *NodeManager) GetSnapshot() *NodeStatusSnapshot {
	m.mu.RLock()
	defer m.mu.RUnlock()

	return &NodeStatusSnapshot{
		NodeID:         m.nodeID,
		State:          m.state,
		ActiveChannels: atomic.LoadInt64(&m.activeChannels),
		MaxChannels:    m.maxChannels,
		UptimeSeconds:  int64(time.Since(m.startTime).Seconds()),
		Timestamp:      time.Now().UnixMilli(),
	}
}
