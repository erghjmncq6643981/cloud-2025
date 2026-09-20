package governance

const terminalHistoryLimit = 65536

// ObserveChannel updates capacity by channel identity, not event count. Terminal
// tombstones prevent a delayed create from reviving a destroyed UUID. Source
// timestamps are microseconds; zero is allowed but never defeats a tombstone.
func (m *NodeManager) ObserveChannel(uuid, state string, sourceMicros int64) {
	if uuid == "" {
		return
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	previous, exists := m.channelTimes[uuid]
	if !exists && m.eventFloor > 0 && sourceMicros <= m.eventFloor {
		return
	}
	if exists && !m.channels[uuid] {
		return
	}
	if exists && sourceMicros > 0 && previous > sourceMicros && state != "DESTROY" {
		return
	}
	switch state {
	case "DESTROY":
		delete(m.channels, uuid)
		if len(m.terminalOrder) == terminalHistoryLimit {
			oldest := m.terminalOrder[m.terminalNext]
			if m.channelTimes[oldest] > m.eventFloor {
				m.eventFloor = m.channelTimes[oldest]
			}
			delete(m.channelTimes, oldest)
			m.terminalOrder[m.terminalNext] = uuid
			m.terminalNext = (m.terminalNext + 1) % terminalHistoryLimit
		} else {
			m.terminalOrder = append(m.terminalOrder, uuid)
		}
	case "START", "CALLING", "RINGING", "MEDIA", "ANSWERED", "READY", "BRIDGE", "UNBRIDGE", "HOLD", "UNHOLD":
		m.channels[uuid] = true
	default:
		return
	}
	m.channelTimes[uuid] = sourceMicros
	m.revision++
}

// ChannelRevision captures a fence before querying FreeSWITCH.
func (m *NodeManager) ChannelRevision() uint64 {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.revision
}

// ReconcileChannels replaces capacity only when no event was applied during the
// query. Failed queries must never be interpreted as an empty snapshot.
func (m *NodeManager) ReconcileChannels(revision uint64, uuids []string) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	if revision != m.revision {
		return false
	}
	channels := make(map[string]bool, len(uuids))
	for _, uuid := range uuids {
		if uuid == "" {
			return false
		}
		channels[uuid] = true
	}
	for _, timestamp := range m.channelTimes {
		if timestamp > m.eventFloor {
			m.eventFloor = timestamp
		}
	}
	m.channels = channels
	m.channelTimes = make(map[string]int64, len(channels))
	for uuid := range channels {
		m.channelTimes[uuid] = m.eventFloor
	}
	m.terminalOrder = nil
	m.terminalNext = 0
	m.revision++
	return true
}
