package governance

import (
	"sync"
	"time"
)

type NodeState string

const (
	StateHealthy    NodeState = "HEALTHY"
	StateDraining   NodeState = "DRAINING"
	StateOverloaded NodeState = "OVERLOADED"
	StateOffline    NodeState = "OFFLINE"
)

// NodeManager 软交换节点生命周期与治理管理器
type NodeManager struct {
	nodeID         string
	maxChannels    int
	state          NodeState
	channels       map[string]bool
	channelTimes   map[string]int64
	healthy        bool
	startTime      time.Time
	mu             sync.RWMutex
}

func NewNodeManager(nodeID string, maxChannels int) *NodeManager {
	return &NodeManager{
		nodeID:      nodeID,
		maxChannels: maxChannels,
		state:       StateOffline,
		channels:    make(map[string]bool),
		channelTimes: make(map[string]int64),
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
	m.healthy = s == StateHealthy
}

func (m *NodeManager) GetState() NodeState {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.state
}

// Drain 开启呼叫沥干模式（停止接入新呼叫，等待存量自然挂机）
func (m *NodeManager) Drain() {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.state = StateDraining
}

// Resume 恢复接单
func (m *NodeManager) Resume() {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.healthy {
		m.state = StateHealthy
	} else {
		m.state = StateOffline
	}
}

// ObserveHealth updates probe health without cancelling an operator's drain.
func (m *NodeManager) ObserveHealth(alive bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.healthy = alive
	if m.state != StateDraining {
		if alive {
			m.state = StateHealthy
		} else {
			m.state = StateOffline
		}
	}
}

// IsAcceptingCalls 检查当前是否允许接入新通话
func (m *NodeManager) IsAcceptingCalls() (bool, NodeState) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	// 仅健康节点可接新呼叫；离线、过载及未知状态一律拒绝。
	if m.state != StateHealthy {
		return false, m.state
	}

	active := len(m.channels)
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
		ActiveChannels: int64(len(m.channels)),
		MaxChannels:    m.maxChannels,
		UptimeSeconds:  int64(time.Since(m.startTime).Seconds()),
		Timestamp:      time.Now().UnixMilli(),
	}
}
