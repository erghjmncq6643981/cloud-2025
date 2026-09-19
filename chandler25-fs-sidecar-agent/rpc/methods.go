package rpc

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"time"
)

// MediaInfo 媒体参数对象（支持本地音视频文件与 TTS 文本播报）
type MediaInfo struct {
	Type   string `json:"type"`   // "TEXT", "FILE"
	Data   string `json:"data"`   // 文件路径或待播报文本
	Voice  string `json:"voice"`  // 发音人 (如 "aiqi")
	Engine string `json:"engine"` // 引擎 (如 "ali", "flite")
}

// --- 1. FNode.Dial (外呼发起) ---
type CallParam struct {
	DialString string            `json:"dial_string"`
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
	Extension   string            `json:"extension"`   // 简写入参兼容
	DialString  string            `json:"dial_string"` // 简写入参兼容
	CidName     string            `json:"cid_name"`
	CidNumber   string            `json:"cid_number"`
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
		return NewErrorResponse(req.ID, ErrCodeNodeOverloaded, "Node channel capacity reached limit", nil)
	}

	var p FNodeDialParams
	if err := json.Unmarshal(req.Params, &p); err != nil {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Invalid dial params: "+err.Error(), nil)
	}

	dialString := p.DialString
	cidName := p.CidName
	cidNumber := p.CidNumber
	uuid := p.UUID
	callVars := make(map[string]string)

	// 从 destination.call_params 提取参数
	if p.Destination != nil && len(p.Destination.CallParams) > 0 {
		cp := p.Destination.CallParams[0]
		if cp.DialString != "" {
			dialString = cp.DialString
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
	} else if dialString == "" && p.Extension != "" {
		dialString = fmt.Sprintf("user/%s", p.Extension)
	}

	if dialString == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required destination/dial_string/extension", nil)
	}

	// 自动补充 user/ 前缀
	if !strings.Contains(dialString, "/") {
		dialString = fmt.Sprintf("user/%s", dialString)
	}

	if p.Timeout <= 0 {
		p.Timeout = 30
	}
	if p.Ringback == "" {
		p.Ringback = "%(1000,4000,450)"
	}

	if uuid == "" {
		uuid = fmt.Sprintf("call-%d", time.Now().UnixNano())
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

// --- 2. FNode.ChannelBridge (话道桥接) ---
type FNodeBridgeParams struct {
	CtrlUUID       string `json:"ctrl_uuid"`
	UUID           string `json:"uuid"`
	PeerUUID       string `json:"peer_uuid"`
	FlowControl    string `json:"flow_control"`
	ContinueOnFail bool   `json:"continue_on_fail"`
	// 兼容字段
	UUIDA          string `json:"uuidA"`
	UUIDB          string `json:"uuidB"`
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

	// 媒体解析
	audioSrc := p.AudioFile
	if audioSrc == "" {
		audioSrc = p.Media.Data
	}
	if p.Media.Type == "TEXT" && audioSrc != "" {
		// 如果是文本，尝试通过 say 模块或 TTS 引擎播报
		if p.Media.Engine != "" {
			audioSrc = fmt.Sprintf("tts:%s:%s:%s", p.Media.Engine, p.Media.Voice, audioSrc)
		} else {
			audioSrc = fmt.Sprintf("say:zh:text:iterated:%s", audioSrc)
		}
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

	actionAfter := strings.ToLower(p.ActionAfter)
	if actionAfter == "" {
		actionAfter = "hangup"
	}

	var inlineApp string
	if actionAfter == "park" {
		// 导航收号等中间流程：收号完成/超时后转入 park 驻留，等待后续路由桥接
		inlineApp = fmt.Sprintf("play_and_get_digits:%d %d %d %d %s %s %s dtmf_val %s %d,park",
			p.MinDigits, p.MaxDigits, tries, timeout, p.Terminators, audioSrc, intervalSilence, regex, digitTimeout)
	} else {
		// 满意度评价等收尾流程：支持播报致谢语并挂机
		thankYouAudio := p.ThankYouFile
		if thankYouAudio == "" {
			defaultThankYou := "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_thankyou.wav"
			if _, err := os.Stat(defaultThankYou); err == nil {
				thankYouAudio = defaultThankYou
			}
		}

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

// --- 4. FNode.Play (放音播报) ---
type FNodePlayParams struct {
	CtrlUUID string    `json:"ctrl_uuid"`
	UUID     string    `json:"uuid"`
	Media    MediaInfo `json:"media"`
	FilePath string    `json:"file_path"` // 兼容直接传文件
}

func (d *Dispatcher) handleFNodePlay(req *JsonRpcRequest) *JsonRpcResponse {
	var p FNodePlayParams
	if err := json.Unmarshal(req.Params, &p); err != nil || p.UUID == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required param: uuid", nil)
	}

	audioSrc := p.FilePath
	if audioSrc == "" {
		audioSrc = p.Media.Data
	}
	if p.Media.Type == "TEXT" && audioSrc != "" {
		if p.Media.Engine != "" {
			audioSrc = fmt.Sprintf("tts:%s:%s:%s", p.Media.Engine, p.Media.Voice, audioSrc)
		} else {
			audioSrc = fmt.Sprintf("say:zh:text:iterated:%s", audioSrc)
		}
	}
	if audioSrc == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing media/data or file_path", nil)
	}

	// 采用 uuid_broadcast 异步非阻塞推流播放，播放完成后自动恢复原状态
	args := fmt.Sprintf("%s %s both", p.UUID, audioSrc)
	res, err := d.esl.ExecuteAPI("uuid_broadcast", args)
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeMediaError, err.Error(), nil)
	}

	return NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), p.UUID, p.CtrlUUID, 200, res)
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

	action := strings.ToLower(p.Action)
	if action != "start" && action != "stop" {
		action = "start"
	}

	args := fmt.Sprintf("%s %s %s", p.UUID, action, filePath)
	res, err := d.esl.ExecuteAPI("uuid_record", args)
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeMediaError, err.Error(), nil)
	}

	return NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), p.UUID, p.CtrlUUID, 200, res)
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
	CtrlUUID    string `json:"ctrl_uuid"`
	UUID        string `json:"uuid"`
	Destination string `json:"destination"`
	Inline      bool   `json:"inline"`
}

func (d *Dispatcher) handleFNodeTransfer(req *JsonRpcRequest) *JsonRpcResponse {
	var p FNodeTransferParams
	if err := json.Unmarshal(req.Params, &p); err != nil || p.UUID == "" || p.Destination == "" {
		return NewErrorResponse(req.ID, ErrCodeInvalidParams, "Missing required params: uuid, destination", nil)
	}

	var args string
	if p.Inline {
		args = fmt.Sprintf("%s '%s' inline", p.UUID, p.Destination)
	} else {
		args = fmt.Sprintf("%s %s", p.UUID, p.Destination)
	}

	res, err := d.esl.ExecuteAPI("uuid_transfer", args)
	if err != nil {
		return NewErrorResponse(req.ID, ErrCodeInternalError, err.Error(), nil)
	}

	return NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), p.UUID, p.CtrlUUID, 200, res)
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
