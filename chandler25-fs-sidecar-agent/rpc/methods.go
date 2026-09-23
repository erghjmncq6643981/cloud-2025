package rpc

import (
	"crypto/rand"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"time"
)

// MediaInfo 媒体参数对象（支持本地音视频文件与 TTS 文本播报）
type MediaInfo struct {
	Type string `json:"type"` // "TEXT", "FILE"
	Data string `json:"data"` // 文件路径或待播报文本
}

// --- 1. FNode.Dial (外呼发起) ---
type CallParam struct {
	DialString string            `json:"dial_string"`
	Context    string            `json:"context"`
	CidName    string            `json:"cid_name"`
	CidNumber  string            `json:"cid_number"`
	UUID       string            `json:"uuid"`
	Params     map[string]string `json:"params"`
}

type DestinationParam struct {
	CallParams   []CallParam       `json:"call_params"`
	GlobalParams map[string]string `json:"global_params"`
}

type FNodeDialParams struct {
	CtrlUUID    string            `json:"ctrl_uuid"`
	UUID        string            `json:"uuid"`
	Ringback    string            `json:"ringback"`
	Sync        bool              `json:"sync"`
	Timeout     int               `json:"timeout"`
	Destination *DestinationParam `json:"destination"`
	ExtraVars   map[string]string `json:"extra_vars"`
}

func (d *Dispatcher) handleFNodeDial(req *JsonRpcRequest) *JsonRpcResponse {
	// 1. 节点容量与状态治理检查
	accepting, state := d.gov.IsAcceptingCalls()
	if !accepting {
		if state == "DRAINING" {
			return NewErrorResponse(req.ID, ErrCodeNodeDraining, "Node is draining and rejecting new calls", nil)
		}
		if state == "OVERLOADED" {
			return NewErrorResponse(req.ID, ErrCodeNodeOverloaded, "Node channel capacity reached limit", nil)
		}
		return NewErrorResponse(req.ID, ErrCodeInternalError, "Node is unavailable for new calls", map[string]string{"node_state": string(state)})
	}

	var p FNodeDialParams
	if err := json.Unmarshal(req.Params, &p); err != nil {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Invalid dial params: "+err.Error(), nil)
	}

	if p.Destination == nil || len(p.Destination.CallParams) == 0 {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required destination.call_params", nil)
	}

	dialString := ""
	dialContext := ""
	cidName := ""
	cidNumber := ""
	uuid := p.UUID
	callVars := make(map[string]string)

	// 从 destination.call_params 提取参数
	if p.Destination != nil && len(p.Destination.CallParams) > 0 {
		cp := p.Destination.CallParams[0]
		if cp.DialString != "" {
			dialString = cp.DialString
		}
		if cp.Context != "" {
			dialContext = cp.Context
		}
		if cp.CidName != "" {
			cidName = cp.CidName
		}
		if cp.CidNumber != "" {
			cidNumber = cp.CidNumber
		}
		if cp.UUID != "" {
			uuid = cp.UUID
		}
		for k, v := range cp.Params {
			callVars[k] = v
		}
	}

	if dialString == "" || dialContext == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required dial_string or context", nil)
	}

	var destinationError error
	dialString, destinationError = resolveDialDestination(dialString, dialContext)
	if destinationError != nil {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, destinationError.Error(), nil)
	}

	if p.Timeout <= 0 {
		p.Timeout = 30
	}
	if p.Ringback == "" {
		p.Ringback = "%(1000,4000,450)"
	}

	if uuid == "" {
		uuid = newChannelUUID()
	}

	var vars []string
	vars = append(vars, fmt.Sprintf("origination_uuid=%s", uuid))
	if p.CtrlUUID != "" {
		vars = append(vars, fmt.Sprintf("ctrl_uuid=%s", p.CtrlUUID))
	}
	if cidName != "" {
		vars = append(vars, fmt.Sprintf("origination_caller_id_name='%s'", cidName))
	}
	if cidNumber != "" {
		vars = append(vars, fmt.Sprintf("origination_caller_id_number='%s'", cidNumber))
	}
	vars = append(vars, fmt.Sprintf("originate_timeout=%d", p.Timeout))
	vars = append(vars, fmt.Sprintf("ringback='%s'", p.Ringback))

	if callVars["absolute_codec_string"] == "" && (p.ExtraVars == nil || p.ExtraVars["absolute_codec_string"] == "") {
		vars = append(vars, "absolute_codec_string='PCMU,PCMA'")
	}
	if callVars["liberal_dtmf"] == "" && (p.ExtraVars == nil || p.ExtraVars["liberal_dtmf"] == "") {
		vars = append(vars, "liberal_dtmf='true'")
	}

	for k, v := range callVars {
		vars = append(vars, fmt.Sprintf("%s='%s'", k, v))
	}
	for k, v := range p.ExtraVars {
		vars = append(vars, fmt.Sprintf("%s='%s'", k, v))
	}

	varStr := "{" + strings.Join(vars, ",") + "}"
	fullDialStr := fmt.Sprintf("%s%s &park", varStr, dialString)

	var res string
	var err error
	if p.Sync {
		res, err = d.esl.ExecuteAPI("originate", fullDialStr)
	} else {
		res, err = d.esl.ExecuteAPI("bgapi", "originate "+fullDialStr)
	}

	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeOriginateFailed, err.Error(), nil)
	}

	if !strings.HasPrefix(res, "+OK") {
		return NewErrorResponse(req.ID, ErrCodeOriginateFailed, "Originate failed: "+res, map[string]string{"raw": res})
	}

	return NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), uuid, p.CtrlUUID, 200, "OK")
}

var dialTargetPattern = regexp.MustCompile(`^[+0-9A-Za-z*#_.-]{1,128}$`)
var dialContextPattern = regexp.MustCompile(`^[A-Za-z0-9_.-]{1,64}$`)

// resolveDialDestination 将业务号码与受控 context 转换为节点侧 FreeSWITCH 拨号表达式。
func resolveDialDestination(dialTarget, dialContext string) (string, error) {
	if !dialTargetPattern.MatchString(dialTarget) {
		return "", fmt.Errorf("dial target contains unsupported characters")
	}
	if !dialContextPattern.MatchString(dialContext) {
		return "", fmt.Errorf("dial context contains unsupported characters")
	}
	return fmt.Sprintf("loopback/%s/%s", dialTarget, dialContext), nil
}

// newChannelUUID 生成不携带业务前缀的 FreeSWITCH 话道 UUID。
func newChannelUUID() string {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		fallback := time.Now().UnixNano()
		for index := range bytes {
			bytes[index] = byte(fallback >> ((index % 8) * 8))
		}
	}
	bytes[6] = (bytes[6] & 0x0f) | 0x40
	bytes[8] = (bytes[8] & 0x3f) | 0x80
	return fmt.Sprintf(
		"%x-%x-%x-%x-%x",
		bytes[0:4],
		bytes[4:6],
		bytes[6:8],
		bytes[8:10],
		bytes[10:16],
	)
}

// --- 2. FNode.ChannelBridge (话道桥接) ---
type FNodeBridgeParams struct {
	CtrlUUID       string `json:"ctrl_uuid"`
	UUID           string `json:"uuid"`
	PeerUUID       string `json:"peer_uuid"`
	FlowControl    string `json:"flow_control"`
	ContinueOnFail bool   `json:"continue_on_fail"`
	// 兼容字段
	UUIDA string `json:"uuidA"`
	UUIDB string `json:"uuidB"`
}

func (d *Dispatcher) handleFNodeBridge(req *JsonRpcRequest) *JsonRpcResponse {
	var p FNodeBridgeParams
	if err := json.Unmarshal(req.Params, &p); err != nil {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Invalid bridge params: "+err.Error(), nil)
	}

	uuidA := p.UUID
	uuidB := p.PeerUUID
	if uuidA == "" && p.UUIDA != "" {
		uuidA = p.UUIDA
	}
	if uuidB == "" && p.UUIDB != "" {
		uuidB = p.UUIDB
	}

	if uuidA == "" || uuidB == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required params: uuid, peer_uuid", nil)
	}

	// 确保双方话道在解除桥接后不会被 FreeSWITCH 自动销毁，而是返回静默驻留 (&park) 等待后续业务（如满意度评价）
	d.esl.ExecuteAPI("uuid_setvar", fmt.Sprintf("%s hangup_after_bridge false", uuidA))
	d.esl.ExecuteAPI("uuid_setvar", fmt.Sprintf("%s park_after_bridge true", uuidA))
	d.esl.ExecuteAPI("uuid_setvar", fmt.Sprintf("%s hangup_after_bridge false", uuidB))
	d.esl.ExecuteAPI("uuid_setvar", fmt.Sprintf("%s park_after_bridge true", uuidB))

	args := fmt.Sprintf("%s %s", uuidA, uuidB)
	res, err := d.esl.ExecuteAPI("uuid_bridge", args)
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeBridgeFailed, err.Error(), nil)
	}

	if !strings.HasPrefix(res, "+OK") {
		return NewErrorResponse(req.ID, ErrCodeBridgeFailed, "Bridge failed: "+res, map[string]string{"raw": res})
	}

	return NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), uuidA, p.CtrlUUID, 200, "OK")
}

// --- 3. FNode.ReadDTMF (放音并收号) ---
type FNodeReadDTMFParams struct {
	CtrlUUID     string    `json:"ctrl_uuid"`
	UUID         string    `json:"uuid"`
	Media        MediaInfo `json:"media"`
	MinDigits    int       `json:"min_digits"`
	MaxDigits    int       `json:"max_digits"`
	Tries        int       `json:"tries"`
	Timeout      int       `json:"timeout"`       // 毫秒 (未按键时等待时长及重播间隔)
	DigitTimeout int       `json:"digit_timeout"` // 毫秒
	Terminators  string    `json:"terminators"`
	AudioFile    string    `json:"audio_file"` // 兼容直接传文件
	ThankYouFile string    `json:"thank_you_file"`
	Regex        string    `json:"regex"`
	ActionAfter  string    `json:"action_after"` // "park" 或 "hangup" (默认)
}

func (d *Dispatcher) handleFNodeReadDTMF(req *JsonRpcRequest) *JsonRpcResponse {
	var p FNodeReadDTMFParams
	if err := json.Unmarshal(req.Params, &p); err != nil || p.UUID == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required param: uuid", nil)
	}

	if p.MinDigits <= 0 {
		p.MinDigits = 1
	}
	if p.MaxDigits <= 0 {
		p.MaxDigits = 1
	}
	if p.Timeout <= 0 {
		p.Timeout = 5000
	}
	if p.Terminators == "" {
		p.Terminators = "#"
	}

	regex := p.Regex
	if regex == "" {
		regex = "[1-4]"
	}

	// 媒体解析。TEXT 必须先由 Sidecar TTS provider 落盘，禁止把文本拼进 ESL 魔法字符串。
	audioSrc, mediaErr := d.resolveMedia(p.Media, p.AudioFile)
	if mediaErr != nil {
		return NewErrorResponse(req.ID, ErrCodeMediaError, mediaErr.Error(), nil)
	}
	if audioSrc == "" {
		audioSrc = "silence_stream://250"
	}

	tries := p.Tries
	if tries <= 0 {
		tries = 2
	}
	timeout := p.Timeout
	if timeout <= 0 {
		timeout = 2000
	}
	digitTimeout := p.DigitTimeout
	if digitTimeout <= 0 {
		digitTimeout = 2000
	}
	intervalSilence := fmt.Sprintf("silence_stream://%d", timeout)

	actionAfter, actionErr := readDTMFPostAction(p.ActionAfter)
	if actionErr != nil {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, actionErr.Error(), nil)
	}

	var inlineApp string
	if actionAfter == "park" {
		// 导航收号等中间流程：收号完成/超时后转入 park 驻留，等待后续路由桥接
		inlineApp = fmt.Sprintf("play_and_get_digits:%d %d %d %d %s %s %s dtmf_val %s %d,park",
			p.MinDigits, p.MaxDigits, tries, timeout, p.Terminators, audioSrc, intervalSilence, regex, digitTimeout)
	} else {
		// 满意度评价等收尾流程：支持播报致谢语并挂机
		thankYouAudio := p.ThankYouFile

		if thankYouAudio != "" {
			inlineApp = fmt.Sprintf("play_and_get_digits:%d %d %d %d %s %s %s dtmf_val %s %d,playback:%s,hangup:NORMAL_CLEARING",
				p.MinDigits, p.MaxDigits, tries, timeout, p.Terminators, audioSrc, intervalSilence, regex, digitTimeout, thankYouAudio)
		} else {
			inlineApp = fmt.Sprintf("play_and_get_digits:%d %d %d %d %s %s %s dtmf_val %s %d,hangup:NORMAL_CLEARING",
				p.MinDigits, p.MaxDigits, tries, timeout, p.Terminators, audioSrc, intervalSilence, regex, digitTimeout)
		}
	}

	args := fmt.Sprintf("%s '%s' inline", p.UUID, inlineApp)
	res, err := d.esl.ExecuteAPI("uuid_transfer", args)
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeMediaError, err.Error(), nil)
	}
	if !strings.HasPrefix(res, "+OK") {
		return NewErrorResponse(req.ID, ErrCodeMediaError, "ReadDTMF failed: "+res, map[string]string{"raw": res})
	}

	return NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), p.UUID, p.CtrlUUID, 202, "OK")
}

// readDTMFPostAction validates the small protocol vocabulary before any ESL
// expression is assembled. An omitted value keeps the historical safe terminal
// behavior, while unknown values are rejected instead of being treated as hangup.
func readDTMFPostAction(value string) (string, error) {
	action := strings.ToLower(strings.TrimSpace(value))
	switch action {
	case "":
		return "hangup", nil
	case "park", "hangup":
		return action, nil
	default:
		return "", fmt.Errorf("unsupported action_after: %s", value)
	}
}

// --- 4. FNode.Play (放音播报) ---
type FNodePlayParams struct {
	CtrlUUID    string    `json:"ctrl_uuid"`
	UUID        string    `json:"uuid"`
	Media       MediaInfo `json:"media"`
	FilePath    string    `json:"file_path"`    // 兼容直接传文件
	ActionAfter string    `json:"action_after"` // "NONE" 或 "HANGUP"
}

func (d *Dispatcher) handleFNodePlay(req *JsonRpcRequest) *JsonRpcResponse {
	var p FNodePlayParams
	if err := json.Unmarshal(req.Params, &p); err != nil || p.UUID == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required param: uuid", nil)
	}

	audioSrc, mediaErr := d.resolveMedia(p.Media, p.FilePath)
	if mediaErr != nil {
		return NewErrorResponse(req.ID, ErrCodeMediaError, mediaErr.Error(), nil)
	}
	if audioSrc == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing media/data or file_path", nil)
	}

	command, args, commandErr := playCommand(p.UUID, audioSrc, p.ActionAfter)
	if commandErr != nil {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, commandErr.Error(), nil)
	}
	res, err := d.esl.ExecuteAPI(command, args)
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeMediaError, err.Error(), nil)
	}

	return NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), p.UUID, p.CtrlUUID, 200, res)
}

// playCommand maps the protocol-level post action to one atomic FreeSWITCH
// execution. HANGUP uses an inline transfer so the channel is not torn down
// before playback finishes; ordinary playback remains non-blocking.
func playCommand(uuid, audioSrc, actionAfter string) (string, string, error) {
	switch strings.ToUpper(strings.TrimSpace(actionAfter)) {
	case "", "NONE":
		return "uuid_broadcast", fmt.Sprintf("%s %s both", uuid, audioSrc), nil
	case "HANGUP":
		return "uuid_transfer", fmt.Sprintf(
			"%s '%s' inline",
			uuid,
			fmt.Sprintf("playback:%s,hangup:NORMAL_CLEARING", audioSrc),
		), nil
	default:
		return "", "", fmt.Errorf("unsupported action_after: %s", actionAfter)
	}
}

// resolveMedia turns FILE data into a path and TEXT data into a provider-owned
// shared file. Provider-specific fields stay inside the Sidecar boundary.
func (d *Dispatcher) resolveMedia(media MediaInfo, explicitPath string) (string, error) {
	if strings.EqualFold(media.Type, "TEXT") {
		if d.mediaResolver == nil {
			return "", fmt.Errorf("text media requires configured tts provider")
		}
		if strings.TrimSpace(media.Data) == "" {
			return "", fmt.Errorf("text media data is empty")
		}
		return d.mediaResolver.Resolve(media.Data, "")
	}
	path := explicitPath
	if path == "" {
		path = media.Data
	}
	if path == "" {
		return "", fmt.Errorf("missing media/data or file_path")
	}
	return path, nil
}

// --- 5. FNode.Record (录音启停) ---
type FNodeRecordParams struct {
	CtrlUUID string `json:"ctrl_uuid"`
	UUID     string `json:"uuid"`
	Action   string `json:"action"` // "START", "STOP"
	Path     string `json:"path"`
	FilePath string `json:"file_path"` // 兼容
}

func (d *Dispatcher) handleFNodeRecord(req *JsonRpcRequest) *JsonRpcResponse {
	var p FNodeRecordParams
	if err := json.Unmarshal(req.Params, &p); err != nil || p.UUID == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required param: uuid", nil)
	}

	filePath := p.Path
	if filePath == "" {
		filePath = p.FilePath
	}
	if filePath == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing path/file_path", nil)
	}

	action, actionErr := recordAction(p.Action)
	if actionErr != nil {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, actionErr.Error(), nil)
	}

	args := fmt.Sprintf("%s %s %s", p.UUID, action, filePath)
	res, err := d.esl.ExecuteAPI("uuid_record", args)
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeMediaError, err.Error(), nil)
	}

	return NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), p.UUID, p.CtrlUUID, 200, res)
}

// recordAction validates the required recording operation. Defaulting an
// unknown value to START could unexpectedly begin recording, so it fails closed.
func recordAction(value string) (string, error) {
	action := strings.ToLower(strings.TrimSpace(value))
	switch action {
	case "start", "stop":
		return action, nil
	default:
		return "", fmt.Errorf("unsupported recording action: %s", value)
	}
}

// --- 6. FNode.Hangup (挂机) ---
type FNodeHangupParams struct {
	CtrlUUID string `json:"ctrl_uuid"`
	UUID     string `json:"uuid"`
	Cause    string `json:"cause"`
}

func (d *Dispatcher) handleFNodeHangup(req *JsonRpcRequest) *JsonRpcResponse {
	var p FNodeHangupParams
	if err := json.Unmarshal(req.Params, &p); err != nil || p.UUID == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required param: uuid", nil)
	}

	if p.Cause == "" {
		p.Cause = "NORMAL_CLEARING"
	}

	args := fmt.Sprintf("%s %s", p.UUID, p.Cause)
	res, err := d.esl.ExecuteAPI("uuid_kill", args)
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeInternalError, err.Error(), nil)
	}

	return NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), p.UUID, p.CtrlUUID, 200, res)
}

// --- 7. FNode.NativeAPI (原生指令执行/逃生通道) ---
type FNodeNativeAPIParams struct {
	CtrlUUID string `json:"ctrl_uuid"`
	Cmd      string `json:"cmd"`
	Args     string `json:"args"`
	Command  string `json:"command"` // 兼容
}

func (d *Dispatcher) handleFNodeNativeAPI(req *JsonRpcRequest) *JsonRpcResponse {
	var p FNodeNativeAPIParams
	if err := json.Unmarshal(req.Params, &p); err != nil {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Invalid native params: "+err.Error(), nil)
	}

	cmd := p.Cmd
	if cmd == "" {
		cmd = p.Command
	}
	if cmd == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing cmd/command", nil)
	}

	res, err := d.esl.ExecuteAPI(cmd, p.Args)
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeInternalError, err.Error(), nil)
	}

	resp := NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), "", p.CtrlUUID, 200, "OK")
	if r, ok := resp.Result.(*FNodeResult); ok {
		r.Data = map[string]string{"response": res}
	}
	return resp
}

// --- 8. FNode.Transfer (呼叫转接) ---
type FNodeTransferParams struct {
	CtrlUUID string `json:"ctrl_uuid"`
	UUID     string `json:"uuid"`
	Target   string `json:"target"`
	Context  string `json:"context"`
}

func (d *Dispatcher) handleFNodeTransfer(req *JsonRpcRequest) *JsonRpcResponse {
	var p FNodeTransferParams
	if err := json.Unmarshal(req.Params, &p); err != nil || p.UUID == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required param: uuid", nil)
	}
	destination, destinationErr := resolveTransferDestination(p.Target, p.Context)
	if destinationErr != nil {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, destinationErr.Error(), nil)
	}

	res, err := d.esl.ExecuteAPI("uuid_transfer", fmt.Sprintf("%s %s", p.UUID, destination))
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeInternalError, err.Error(), nil)
	}

	return NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), p.UUID, p.CtrlUUID, 200, res)
}

// resolveTransferDestination converts a business target and routing context to
// the node-local FreeSWITCH transfer expression.
func resolveTransferDestination(target, context string) (string, error) {
	if !dialTargetPattern.MatchString(target) {
		return "", fmt.Errorf("transfer target contains unsupported characters")
	}
	if !dialContextPattern.MatchString(context) {
		return "", fmt.Errorf("transfer context contains unsupported characters")
	}
	return fmt.Sprintf("%s XML %s", target, context), nil
}

// --- 9. 节点治理与运维类方法 ---
func (d *Dispatcher) handleFNodeDrain(req *JsonRpcRequest) *JsonRpcResponse {
	d.gov.Drain()
	resp := NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), "", "", 200, "Node entered DRAINING mode")
	if r, ok := resp.Result.(*FNodeResult); ok {
		r.Data = map[string]string{"state": "DRAINING"}
	}
	return resp
}

func (d *Dispatcher) handleFNodeResume(req *JsonRpcRequest) *JsonRpcResponse {
	d.gov.Resume()
	resp := NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), "", "", 200, "Node resumed accepting calls")
	if r, ok := resp.Result.(*FNodeResult); ok {
		r.Data = map[string]string{"state": "HEALTHY"}
	}
	return resp
}

func (d *Dispatcher) handleFNodeStatus(req *JsonRpcRequest) *JsonRpcResponse {
	return NewSuccessResponse(req.ID, d.gov.GetSnapshot())
}
