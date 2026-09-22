package db

import (
	"encoding/json"
	"time"
)

// Registration 对应 FreeSWITCH registrations 原生表
type Registration struct {
	RegUser      string `json:"reg_user"`
	Realm        string `json:"realm"`
	Token        string `json:"token"`
	URL          string `json:"url"`
	Expires      int64  `json:"expires"`
	NetworkIP    string `json:"network_ip"`
	NetworkPort  string `json:"network_port"`
	NetworkProto string `json:"network_proto"`
	Hostname     string `json:"hostname"`
	Metadata     string `json:"metadata"`
	// 增强扩展计算字段
	UserAgent        string `json:"user_agent,omitempty"`
	Status           string `json:"status,omitempty"`
	RemainingSeconds int64  `json:"remaining_seconds"`
	AgentWorkNo      string `json:"agent_work_no,omitempty"`
	AgentName        string `json:"agent_name,omitempty"`
}

// Channel 对应 FreeSWITCH channels 原生表
type Channel struct {
	UUID            string `json:"uuid"`
	Direction       string `json:"direction"`
	Created         string `json:"created"`
	CreatedEpoch    int64  `json:"created_epoch"`
	Name            string `json:"name"`
	State           string `json:"state"`
	CIDName         string `json:"cid_name"`
	CIDNum          string `json:"cid_num"`
	IPAddr          string `json:"ip_addr"`
	Dest            string `json:"dest"`
	Application     string `json:"application"`
	ApplicationData string `json:"application_data"`
	Dialplan        string `json:"dialplan"`
	Context         string `json:"context"`
	ReadCodec       string `json:"read_codec"`
	ReadRate        string `json:"read_rate"`
	WriteCodec      string `json:"write_codec"`
	WriteRate       string `json:"write_rate"`
	CallState       string `json:"callstate"`
	CalleeName      string `json:"callee_name"`
	CalleeNum       string `json:"callee_num"`
	CallUUID        string `json:"call_uuid"`
	Hostname        string `json:"hostname"`
	DurationSec     int64  `json:"duration_sec"`
}

// Call 对应 FreeSWITCH calls 原生表 (Bridge Leg A <-> Leg B)
type Call struct {
	CallUUID         string `json:"call_uuid"`
	CallCreated      string `json:"call_created"`
	CallCreatedEpoch int64  `json:"call_created_epoch"`
	CallerUUID       string `json:"caller_uuid"`
	CalleeUUID       string `json:"callee_uuid"`
	Hostname         string `json:"hostname"`
}

// FsExtension 软交换分机在 PostgreSQL (fs_extension) 中的持久化模型
type FsExtension struct {
	ID                int64     `json:"id"`
	Extension         string    `json:"extension"`
	Password          string    `json:"-"`
	Context           string    `json:"context"`
	Callgroup         string    `json:"callgroup"`
	EffectiveCallerID string    `json:"effective_caller_id"`
	EndpointType      string    `json:"endpoint_type"`
	IsEnabled         bool      `json:"is_enabled"`
	Description       string    `json:"description"`
	CreatedAt         time.Time `json:"created_at"`
	UpdatedAt         time.Time `json:"updated_at"`
}

// ExtensionDetail 综合展现分机持久化配置与实时在线态
type ExtensionDetail struct {
	ID               int64  `json:"id"`
	Extension        string `json:"extension"`
	Password         string `json:"-"`
	Context          string `json:"context"`
	Callgroup        string `json:"callgroup"`
	EndpointType     string `json:"endpoint_type"`
	IsEnabled        bool   `json:"is_enabled"`
	Description      string `json:"description"`
	XmlPath          string `json:"-"`
	IsRegistered     bool   `json:"is_registered"`
	NetworkIP        string `json:"network_ip"`
	NetworkPort      string `json:"network_port"`
	NetworkProto     string `json:"network_proto"`
	UserAgent        string `json:"user_agent"`
	RemainingSeconds int64  `json:"remaining_seconds"`
	PingStatus       string `json:"ping_status"`
	Expires          int64  `json:"expires,omitempty"`
	URL              string `json:"url,omitempty"`
	Token            string `json:"token,omitempty"`
	Realm            string `json:"realm,omitempty"`
	Hostname         string `json:"hostname,omitempty"`
}

// Gateway 运营商网关在 PostgreSQL (fs_gateway) 中的持久化模型
type Gateway struct {
	ID             int64     `json:"id,omitempty"`
	Name           string    `json:"name"`
	Profile        string    `json:"profile"`           // 宿主 Profile，默认 external
	Proxy          string    `json:"proxy"`             // 远端 SBC/代理地址:端口
	Username       string    `json:"username"`          // 运营商 SIP 账号
	Password       string    `json:"-"`                 // 仅供持久化与 XML 写入使用
	AuthUser       string    `json:"auth_user"`         // 鉴权用户名
	FromUser       string    `json:"from_user"`         // From 账号
	FromDomain     string    `json:"from_domain"`       // From 域名
	CallerIdInFrom bool      `json:"caller_id_in_from"` // 是否将主叫写入 From
	Context        string    `json:"context"`           // 路由上下文，默认 public / from-trunk
	Extension      string    `json:"extension"`         // 呼入默认路由
	DtmfType       string    `json:"dtmf_type"`         // rfc2833 / info / inband
	Codecs         string    `json:"codecs"`            // PCMA, PCMU, G729
	Register       bool      `json:"register"`          // 是否主动向运营商注册
	ExpireSeconds  int       `json:"expire_seconds"`    // 租约周期(秒)
	PingSeconds    int       `json:"ping_seconds"`      // OPTIONS 探活间隔(秒)
	Status         string    `json:"status"`            // REGED (UP), NOREG, DOWN
	PingMS         string    `json:"ping_ms"`           // 探活延迟，如 12ms
	IsEnabled      bool      `json:"is_enabled"`
	CreatedAt      time.Time `json:"created_at,omitempty"`
	UpdatedAt      time.Time `json:"updated_at,omitempty"`
}

// FsCdr 软交换底层呼叫详细记录 (PostgreSQL fs_cdr 表)
type FsCdr struct {
	ID                   int64           `json:"id"`
	CallUUID             string          `json:"call_uuid"`
	CallerIDName         string          `json:"caller_id_name"`
	CallerIDNumber       string          `json:"caller_id_number"`
	DestinationNumber    string          `json:"destination_number"`
	Context              string          `json:"context"`
	StartEpoch           int64           `json:"start_epoch"`
	AnswerEpoch          int64           `json:"answer_epoch"`
	EndEpoch             int64           `json:"end_epoch"`
	Duration             int             `json:"duration"`
	Billsec              int             `json:"billsec"`
	HangupCause          string          `json:"hangup_cause"`
	SipHangupDisposition string          `json:"sip_hangup_disposition"`
	Direction            string          `json:"direction"`
	ReadCodec            string          `json:"read_codec"`
	WriteCodec           string          `json:"write_codec"`
	SipUserAgent         string          `json:"sip_user_agent"`
	QualityPercentage    string          `json:"quality_percentage"`
	VariablesJSON        json.RawMessage `json:"variables_json"`
	CreatedAt            string          `json:"created_at"`
}
