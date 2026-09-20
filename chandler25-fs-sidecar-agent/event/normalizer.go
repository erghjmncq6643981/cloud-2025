package event

import (
	"crypto/rand"
	"encoding/json"
	"fmt"
	"strconv"
	"time"
)

// ChannelEventParams 标准 Event.Channel 参数定义
type ChannelEventParams struct {
	NodeID      string            `json:"node_id"`
	CtrlUUID    string            `json:"ctrl_uuid,omitempty"`
	UUID        string            `json:"uuid"`
	PeerUUID    string            `json:"peer_uuid,omitempty"`
	State       string            `json:"state"` // START, CALLING, RINGING, ANSWERED, MEDIA, READY, BRIDGE, UNBRIDGE, DESTROY
	Domain      string            `json:"domain,omitempty"`
	CidName     string            `json:"cid_name,omitempty"`
	CidNumber   string            `json:"cid_number,omitempty"`
	DestNumber  string            `json:"dest_number,omitempty"`
	Direction   string            `json:"direction,omitempty"` // inbound, outbound
	Bridged     bool              `json:"bridged"`
	Answered    bool              `json:"answered"`
	Hold        bool              `json:"hold"`
	Video       bool              `json:"video"`
	CreateEpoch int64             `json:"create_epoch,omitempty"`
	RingEpoch   int64             `json:"ring_epoch,omitempty"`
	AnswerEpoch int64             `json:"answer_epoch,omitempty"`
	EndEpoch    int64             `json:"end_epoch,omitempty"`
	Duration    int               `json:"duration,omitempty"`
	Billsec     int               `json:"billsec,omitempty"`
	Cause       string            `json:"cause,omitempty"`
	Context     string            `json:"context,omitempty"`
	Timestamp   int64             `json:"timestamp"`
	Params      map[string]string `json:"params,omitempty"`
}

// DTMFEventParams 标准 Event.DTMF 参数
type DTMFEventParams struct {
	NodeID     string `json:"node_id"`
	CtrlUUID   string `json:"ctrl_uuid,omitempty"`
	UUID       string `json:"uuid"`
	Digit      string `json:"digit"`
	DurationMs int    `json:"duration_ms"`
	Timestamp  int64  `json:"timestamp"`
}

// RecordEventParams 标准 Event.Recording 参数
type RecordEventParams struct {
	NodeID    string `json:"node_id"`
	CtrlUUID  string `json:"ctrl_uuid,omitempty"`
	UUID      string `json:"uuid"`
	Action    string `json:"action"` // START, STOP
	FilePath  string `json:"file_path"`
	Seconds   int    `json:"seconds,omitempty"`
	Timestamp int64  `json:"timestamp"`
}

// RegistrationEventParams 标准 Event.Registration 参数 (SIP 分机注册/注销/过期)
type RegistrationEventParams struct {
	NodeID    string `json:"node_id"`
	User      string `json:"user"`
	Domain    string `json:"domain"`
	Status    string `json:"status"` // REGISTERED, UNREGISTERED, EXPIRED
	NetworkIP string `json:"network_ip,omitempty"`
	Port      string `json:"port,omitempty"`
	UserAgent string `json:"user_agent,omitempty"`
	Contact   string `json:"contact,omitempty"`
	Timestamp int64  `json:"timestamp"`
}

// GatewayEventParams 标准 Event.Gateway 参数 (SIP 中继网关状态变动)
type GatewayEventParams struct {
	NodeID    string `json:"node_id"`
	Gateway   string `json:"gateway"`
	Profile   string `json:"profile,omitempty"`
	State     string `json:"state"` // UP, DOWN, FAILED, NOREG
	Timestamp int64  `json:"timestamp"`
}

// SupervisionEventParams 标准 Event.Supervision 参数 (班长监听、耳语、强插)
type SupervisionEventParams struct {
	NodeID     string `json:"node_id"`
	Action     string `json:"action"` // START, STOP
	SpyUUID    string `json:"spy_uuid"`
	TargetUUID string `json:"target_uuid"`
	Timestamp  int64  `json:"timestamp"`
}

// StandardRpcNotification 标准 JSON-RPC 2.0 通知报文
type StandardRpcNotification struct {
	JSONRPC string      `json:"jsonrpc"`
	Method  string      `json:"method"`
	Params  interface{} `json:"params"`
}

// NormalizedEventResult 归一化事件结果封装
type NormalizedEventResult struct {
	EventID        string
	Category       string // "channel", "dtmf", "record", "conf", "registration"
	State          string // 通道状态，如 "START", "CALLING", "DESTROY", 或分机状态 "REGISTERED", "UNREGISTERED"
	UUID           string
	CtrlUUID       string
	RawJSON        []byte
	IsChannelState bool
}

// Normalizer 事件清洗器
type Normalizer struct {
	nodeID string
}

// NewNormalizer 创建清洗器
func NewNormalizer(nodeID string) *Normalizer {
	return &Normalizer{nodeID: nodeID}
}

// Normalize 将 FreeSWITCH 原始海量 Header 字典清洗并映射为 FNode 标准规范事件
func (n *Normalizer) normalizePayload(raw map[string]string) *NormalizedEventResult {
	rawEventName := raw["Event-Name"]
	if rawEventName == "" {
		return nil
	}

	nowMs := time.Now().UnixMilli()

	// 特殊处理 CUSTOM 事件（如 sofia 分机注册/注销/过期事件），无 Unique-ID 话道标识
	if rawEventName == "CUSTOM" {
		subclass := raw["Event-Subclass"]
		switch subclass {
		case "sofia::register", "sofia::unregister", "sofia::expire":
			status := "REGISTERED"
			if subclass == "sofia::unregister" {
				status = "UNREGISTERED"
			} else if subclass == "sofia::expire" {
				status = "EXPIRED"
			}
			user := raw["from-user"]
			if user == "" {
				user = raw["username"]
			}
			domain := raw["from-host"]
			if domain == "" {
				domain = raw["realm"]
			}
			regParams := RegistrationEventParams{
				NodeID:    n.nodeID,
				User:      user,
				Domain:    domain,
				Status:    status,
				NetworkIP: raw["network-ip"],
				Port:      raw["network-port"],
				UserAgent: raw["user-agent"],
				Contact:   raw["contact"],
				Timestamp: nowMs,
			}
			notif := StandardRpcNotification{
				JSONRPC: "2.0",
				Method:  "Event.Registration",
				Params:  regParams,
			}
			data, _ := json.Marshal(notif)
			return &NormalizedEventResult{
				Category:       "registration",
				State:          status,
				UUID:           user,
				RawJSON:        data,
				IsChannelState: false,
			}

		case "sofia::gateway_state", "sofia::gateway_add", "sofia::gateway_delete":
			gwName := raw["Gateway"]
			if gwName == "" {
				gwName = raw["gateway-name"]
			}
			gwState := raw["State"]
			if gwState == "" {
				if subclass == "sofia::gateway_add" {
					gwState = "UP"
				} else if subclass == "sofia::gateway_delete" {
					gwState = "DOWN"
				} else {
					gwState = "UNKNOWN"
				}
			}
			gwParams := GatewayEventParams{
				NodeID:    n.nodeID,
				Gateway:   gwName,
				Profile:   raw["Profile"],
				State:     gwState,
				Timestamp: nowMs,
			}
			notif := StandardRpcNotification{
				JSONRPC: "2.0",
				Method:  "Event.Gateway",
				Params:  gwParams,
			}
			data, _ := json.Marshal(notif)
			return &NormalizedEventResult{
				Category:       "gateway",
				State:          gwState,
				UUID:           gwName,
				RawJSON:        data,
				IsChannelState: false,
			}

		case "eavesdrop::start", "eavesdrop::stop":
			action := "START"
			if subclass == "eavesdrop::stop" {
				action = "STOP"
			}
			supParams := SupervisionEventParams{
				NodeID:     n.nodeID,
				Action:     action,
				SpyUUID:    raw["Spy-Unique-ID"],
				TargetUUID: raw["Target-Unique-ID"],
				Timestamp:  nowMs,
			}
			notif := StandardRpcNotification{
				JSONRPC: "2.0",
				Method:  "Event.Supervision",
				Params:  supParams,
			}
			data, _ := json.Marshal(notif)
			return &NormalizedEventResult{
				Category:       "supervision",
				State:          action,
				UUID:           raw["Spy-Unique-ID"],
				RawJSON:        data,
				IsChannelState: false,
			}
		}
	}

	uuid := raw["Unique-ID"]
	if uuid == "" {
		return nil
	}

	// 提取业务控制会话 ID (ctrl_uuid)
	ctrlUUID := raw["variable_ctrl_uuid"]
	if ctrlUUID == "" {
		ctrlUUID = raw["variable_sip_h_X-Ctrl-UUID"]
	}

	switch rawEventName {
	case "CHANNEL_CREATE":
		direction := raw["Call-Direction"]
		if direction == "" {
			direction = raw["Caller-Direction"]
		}
		if direction == "" {
			direction = "inbound"
		}

		state := "START"
		if direction == "outbound" {
			state = "CALLING"
		}

		params := n.buildChannelParams(raw, uuid, ctrlUUID, state, direction, nowMs)
		return n.wrapChannelNotification(params, state, uuid, ctrlUUID)

	case "CHANNEL_PROGRESS", "CHANNEL_PROGRESS_MEDIA":
		state := "RINGING"
		if rawEventName == "CHANNEL_PROGRESS_MEDIA" {
			state = "MEDIA"
		}
		direction := raw["Call-Direction"]
		params := n.buildChannelParams(raw, uuid, ctrlUUID, state, direction, nowMs)
		return n.wrapChannelNotification(params, state, uuid, ctrlUUID)

	case "CHANNEL_ANSWER":
		state := "ANSWERED"
		direction := raw["Call-Direction"]
		params := n.buildChannelParams(raw, uuid, ctrlUUID, state, direction, nowMs)
		params.Answered = true
		return n.wrapChannelNotification(params, state, uuid, ctrlUUID)

	case "CHANNEL_PARK":
		state := "READY"
		direction := raw["Call-Direction"]
		params := n.buildChannelParams(raw, uuid, ctrlUUID, state, direction, nowMs)
		return n.wrapChannelNotification(params, state, uuid, ctrlUUID)

	case "CHANNEL_HOLD":
		state := "HOLD"
		direction := raw["Call-Direction"]
		params := n.buildChannelParams(raw, uuid, ctrlUUID, state, direction, nowMs)
		params.Hold = true
		return n.wrapChannelNotification(params, state, uuid, ctrlUUID)

	case "CHANNEL_UNHOLD":
		state := "UNHOLD"
		direction := raw["Call-Direction"]
		params := n.buildChannelParams(raw, uuid, ctrlUUID, state, direction, nowMs)
		params.Hold = false
		return n.wrapChannelNotification(params, state, uuid, ctrlUUID)

	case "CHANNEL_BRIDGE":
		state := "BRIDGE"
		direction := raw["Call-Direction"]
		params := n.buildChannelParams(raw, uuid, ctrlUUID, state, direction, nowMs)
		params.Bridged = true
		params.Answered = true

		peerUUID := raw["Bridge-A-Unique-ID"]
		if peerUUID == uuid || peerUUID == "" {
			peerUUID = raw["Bridge-B-Unique-ID"]
		}
		if peerUUID == uuid || peerUUID == "" {
			peerUUID = raw["Other-Leg-Unique-ID"]
		}
		params.PeerUUID = peerUUID

		return n.wrapChannelNotification(params, state, uuid, ctrlUUID)

	case "CHANNEL_UNBRIDGE":
		state := "UNBRIDGE"
		direction := raw["Call-Direction"]
		params := n.buildChannelParams(raw, uuid, ctrlUUID, state, direction, nowMs)
		params.Bridged = false

		peerUUID := raw["Bridge-A-Unique-ID"]
		if peerUUID == uuid || peerUUID == "" {
			peerUUID = raw["Bridge-B-Unique-ID"]
		}
		if peerUUID == uuid || peerUUID == "" {
			peerUUID = raw["Other-Leg-Unique-ID"]
		}
		params.PeerUUID = peerUUID

		return n.wrapChannelNotification(params, state, uuid, ctrlUUID)

	case "CHANNEL_HANGUP_COMPLETE", "CHANNEL_DESTROY":
		state := "DESTROY"
		direction := raw["Call-Direction"]
		params := n.buildChannelParams(raw, uuid, ctrlUUID, state, direction, nowMs)
		params.Cause = raw["Hangup-Cause"]
		if params.Cause == "" {
			params.Cause = "NORMAL_CLEARING"
		}
		if dur, err := strconv.Atoi(raw["variable_duration"]); err == nil {
			params.Duration = dur
		}
		if bill, err := strconv.Atoi(raw["variable_billsec"]); err == nil {
			params.Billsec = bill
		}
		if billmsec, err := strconv.Atoi(raw["variable_billmsec"]); err == nil && params.Billsec == 0 {
			params.Billsec = billmsec / 1000
		}
		return n.wrapChannelNotification(params, state, uuid, ctrlUUID)

	case "DTMF":
		dur, _ := strconv.Atoi(raw["DTMF-Duration"])
		dtmfParams := DTMFEventParams{
			NodeID:     n.nodeID,
			CtrlUUID:   ctrlUUID,
			UUID:       uuid,
			Digit:      raw["DTMF-Digit"],
			DurationMs: dur,
			Timestamp:  nowMs,
		}
		notif := StandardRpcNotification{
			JSONRPC: "2.0",
			Method:  "Event.DTMF",
			Params:  dtmfParams,
		}
		data, _ := json.Marshal(notif)
		return &NormalizedEventResult{
			Category:       "dtmf",
			State:          "DTMF",
			UUID:           uuid,
			CtrlUUID:       ctrlUUID,
			RawJSON:        data,
			IsChannelState: false,
		}

	case "CHANNEL_EXECUTE_COMPLETE":
		app := raw["Application"]
		if app == "play_and_get_digits" {
			dtmfVal := raw["variable_dtmf_val"]
			if dtmfVal == "" {
				dtmfVal = raw["Application-Response"]
			}
			if dtmfVal != "" && dtmfVal != "_none_" {
				dtmfParams := DTMFEventParams{
					NodeID:     n.nodeID,
					CtrlUUID:   ctrlUUID,
					UUID:       uuid,
					Digit:      dtmfVal,
					DurationMs: 100,
					Timestamp:  nowMs,
				}
				notif := StandardRpcNotification{
					JSONRPC: "2.0",
					Method:  "Event.DTMF",
					Params:  dtmfParams,
				}
				data, _ := json.Marshal(notif)
				return &NormalizedEventResult{
					Category:       "dtmf",
					State:          "DTMF",
					UUID:           uuid,
					CtrlUUID:       ctrlUUID,
					RawJSON:        data,
					IsChannelState: false,
				}
			}
		}
		return nil

	case "RECORD_START":
		recParams := RecordEventParams{
			NodeID:    n.nodeID,
			CtrlUUID:  ctrlUUID,
			UUID:      uuid,
			Action:    "START",
			FilePath:  raw["Record-File-Path"],
			Timestamp: nowMs,
		}
		notif := StandardRpcNotification{
			JSONRPC: "2.0",
			Method:  "Event.Recording",
			Params:  recParams,
		}
		data, _ := json.Marshal(notif)
		return &NormalizedEventResult{
			Category:       "record",
			State:          "RECORD_START",
			UUID:           uuid,
			CtrlUUID:       ctrlUUID,
			RawJSON:        data,
			IsChannelState: false,
		}

	case "RECORD_STOP":
		recSec, _ := strconv.Atoi(raw["Record-Seconds"])
		recParams := RecordEventParams{
			NodeID:    n.nodeID,
			CtrlUUID:  ctrlUUID,
			UUID:      uuid,
			Action:    "STOP",
			FilePath:  raw["Record-File-Path"],
			Seconds:   recSec,
			Timestamp: nowMs,
		}
		notif := StandardRpcNotification{
			JSONRPC: "2.0",
			Method:  "Event.Recording",
			Params:  recParams,
		}
		data, _ := json.Marshal(notif)
		return &NormalizedEventResult{
			Category:       "record",
			State:          "RECORD_STOP",
			UUID:           uuid,
			CtrlUUID:       ctrlUUID,
			RawJSON:        data,
			IsChannelState: false,
		}
	}

	return nil
}

func (n *Normalizer) buildChannelParams(raw map[string]string, uuid, ctrlUUID, state, direction string, nowMs int64) ChannelEventParams {
	cidName := raw["Caller-Caller-ID-Name"]
	cidNumber := raw["Caller-Caller-ID-Number"]
	destNumber := raw["Caller-Destination-Number"]
	if destNumber == "" {
		destNumber = raw["Caller-Callee-ID-Number"]
	}

	domain := raw["variable_domain_name"]
	if domain == "" {
		domain = raw["Caller-Context"]
	}

	createEpoch, _ := strconv.ParseInt(raw["Caller-Channel-Created-Time"], 10, 64)
	if createEpoch > 0 {
		createEpoch = createEpoch / 1000000 // FS 微秒转秒
	}
	ringEpoch, _ := strconv.ParseInt(raw["Caller-Channel-Progress-Time"], 10, 64)
	if ringEpoch > 0 {
		ringEpoch = ringEpoch / 1000000
	}
	answerEpoch, _ := strconv.ParseInt(raw["Caller-Channel-Answered-Time"], 10, 64)
	if answerEpoch > 0 {
		answerEpoch = answerEpoch / 1000000
	}
	endEpoch, _ := strconv.ParseInt(raw["Caller-Channel-Hangup-Time"], 10, 64)
	if endEpoch > 0 {
		endEpoch = endEpoch / 1000000
	}

	dtmfVal := raw["variable_dtmf_val"]
	if dtmfVal == "_none_" {
		dtmfVal = ""
	}

	return ChannelEventParams{
		NodeID:      n.nodeID,
		CtrlUUID:    ctrlUUID,
		UUID:        uuid,
		State:       state,
		Domain:      domain,
		CidName:     cidName,
		CidNumber:   cidNumber,
		DestNumber:  destNumber,
		Direction:   direction,
		Context:     raw["Caller-Context"],
		CreateEpoch: createEpoch,
		RingEpoch:   ringEpoch,
		AnswerEpoch: answerEpoch,
		EndEpoch:    endEpoch,
		Timestamp:   nowMs,
		Params: map[string]string{
			"authenticated_extension": raw["variable_sip_auth_username"],
			"sip_user_agent":          raw["variable_sip_user_agent"],
			"codec":                   raw["variable_read_codec"],
			"dtmf_val":                dtmfVal,
		},
	}
}

func (n *Normalizer) wrapChannelNotification(params ChannelEventParams, state, uuid, ctrlUUID string) *NormalizedEventResult {
	notif := StandardRpcNotification{
		JSONRPC: "2.0",
		Method:  "Event.Channel",
		Params:  params,
	}
	data, _ := json.Marshal(notif)
	return &NormalizedEventResult{
		Category:       "channel",
		State:          state,
		UUID:           uuid,
		CtrlUUID:       ctrlUUID,
		RawJSON:        data,
		IsChannelState: true,
	}
}

func generateID(prefix string) string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return fmt.Sprintf("%s-%x", prefix, b)
}
