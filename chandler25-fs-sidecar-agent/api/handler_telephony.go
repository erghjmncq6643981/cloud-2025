package api

import (
	"chandler25-fs-sidecar-agent/db"
	"chandler25-fs-sidecar-agent/esl"
	"chandler25-fs-sidecar-agent/governance"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

// TelephonyHandler 软交换信令与分机维护处理器 (支持 PostgreSQL 核心持久化与 FreeSWITCH 热重载)
type TelephonyHandler struct {
	repo       *db.Repository
	gov        *governance.NodeManager
	eslClient  *esl.Client
	healer     *governance.NetworkHealer
	scriptPath string
}

// NewTelephonyHandler 创建 TelephonyHandler
func NewTelephonyHandler(repo *db.Repository, gov *governance.NodeManager, eslClient *esl.Client, scriptPath string) *TelephonyHandler {
	return &TelephonyHandler{
		repo:       repo,
		gov:        gov,
		eslClient:  eslClient,
		scriptPath: scriptPath,
	}
}

// SetNetworkHealer 设置网络自愈器
func (h *TelephonyHandler) SetNetworkHealer(healer *governance.NetworkHealer) {
	h.healer = healer
}

// HandleStatus 系统运行状态透视 (大盘概览)
func (h *TelephonyHandler) HandleStatus(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	rawStatus, err := h.eslClient.ExecuteAPI("status", "")
	if err != nil {
		rawStatus = "FreeSWITCH ESL 连接中断: " + err.Error()
	}

	regCount, chCount, callCount := 0, 0, 0
	var summaryErr error
	if h.repo != nil && h.repo.Healthy() {
		regCount, chCount, callCount, summaryErr = h.repo.GetSummaryCounts()
	} else {
		if regs, regErr := h.getEslRegistrations(""); regErr == nil {
			regCount = len(regs)
		}
		if channels, calls, chErr := h.getEslChannels(); chErr == nil {
			chCount = len(channels)
			callCount = len(calls)
		}
	}
	pgConnected := summaryErr == nil && h.repo != nil && h.repo.Healthy()

	// 解析 status 文本中的关键指标
	uptimeStr := ""
	sessionsStr := fmt.Sprintf("%d", chCount)
	cpsStr := "0.0"
	versionStr := ""

	lines := strings.Split(rawStatus, "\n")
	for _, l := range lines {
		lTrim := strings.TrimSpace(l)
		if strings.HasPrefix(lTrim, "UP ") {
			uptimeStr = lTrim
		} else if strings.HasPrefix(lTrim, "FreeSWITCH (Version ") {
			reVer := regexp.MustCompile(`FreeSWITCH\s+\(Version\s+([^\)]+)\)`)
			if m := reVer.FindStringSubmatch(lTrim); len(m) > 1 {
				versionStr = m[1]
			}
		} else if strings.Contains(lTrim, "session(s) since startup") {
			parts := strings.Fields(lTrim)
			if len(parts) > 0 {
				sessionsStr = parts[0]
			}
		} else if strings.Contains(lTrim, "session(s) - peak") {
			parts := strings.Fields(lTrim)
			if len(parts) > 0 {
				cpsStr = parts[0]
			}
		}
	}

	if versionStr == "" && err == nil {
		if verRaw, vErr := h.eslClient.ExecuteAPI("version", ""); vErr == nil {
			vTrim := strings.TrimSpace(verRaw)
			vTrim = strings.TrimPrefix(vTrim, "FreeSWITCH Version ")
			vTrim = strings.TrimPrefix(vTrim, "FreeSWITCH ")
			if idx := strings.Index(vTrim, " ("); idx != -1 {
				vTrim = strings.TrimSpace(vTrim[:idx])
			}
			versionStr = vTrim
		}
	}
	if versionStr == "" {
		versionStr = "1.11.3"
	}

	snapshot := h.gov.GetSnapshot()
	statusData := map[string]interface{}{
		"node_state":          snapshot.State,
		"max_channels":        snapshot.MaxChannels,
		"node_id":             h.gov.NodeID(),
		"esl_connected":       err == nil,
		"fs_alive":            err == nil,
		"pg_connected":        pgConnected,
		"channels":            chCount,
		"active_channels":     chCount,
		"calls":               callCount,
		"active_calls":        callCount,
		"registrations":       regCount,
		"uptime":              uptimeStr,
		"version":             versionStr,
		"free_switch_version": versionStr,
		"total_sessions":      sessionsStr,
		"cps":                 cpsStr,
		"current_cps":         cpsStr,
		"raw_status":          stripANSI(rawStatus),
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":    200,
		"message": "success",
		"data":    statusData,
	})
}

// HandleRegistrations 查询当前 FreeSWITCH 活跃注册分机 (支持 PG / ESL 自动降级)
func (h *TelephonyHandler) HandleRegistrations(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	userFilter := r.URL.Query().Get("user")
	var regs []db.Registration
	var err error
	if h.repo != nil && h.repo.Healthy() {
		regs, err = h.repo.GetRegistrations(userFilter)
	}
	if err != nil || h.repo == nil || !h.repo.Healthy() {
		regs, err = h.getEslRegistrations(userFilter)
	}
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":  500,
			"error": "Failed to query registrations: " + err.Error(),
		})
		return
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":    200,
		"message": "success",
		"total":   len(regs),
		"data":    regs,
	})
}

// HandleRegFlush 强制踢掉分机注册 (sofia profile internal flush_inbound_reg)
func (h *TelephonyHandler) HandleRegFlush(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	var req struct {
		Extension string `json:"extension"`
		Realm     string `json:"realm"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Extension == "" {
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "extension is required"})
		return
	}

	cmdArgs := fmt.Sprintf("profile internal flush_inbound_reg %s", req.Extension)
	if req.Realm != "" {
		cmdArgs += fmt.Sprintf(" %s", req.Realm)
	}

	log.Printf("🔌 [Reg Flush] 正在清除分机注册: sofia %s", cmdArgs)
	reply, err := h.eslClient.ExecuteAPI("sofia", cmdArgs)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": err.Error()})
		return
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":      200,
		"message":   "success",
		"extension": req.Extension,
		"reply":     reply,
	})
}

// HandleAllExtensions 综合查询分机资产 (支持 PG / 本地 XML 目录双模，并实时关联 ESL 注册态)
func (h *TelephonyHandler) HandleAllExtensions(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	switch r.Method {
	case http.MethodGet:
		statusFilter := strings.ToLower(r.URL.Query().Get("status")) // "all", "registered", "unregistered"
		searchKw := strings.ToLower(r.URL.Query().Get("keyword"))

		var list []db.ExtensionDetail
		var err error
		if h.repo != nil && h.repo.Healthy() {
			list, err = h.repo.GetAllExtensions(statusFilter, searchKw)
		}
		if err != nil || h.repo == nil || !h.repo.Healthy() || len(list) == 0 {
			list, err = h.getDirectoryExtensions()
		}
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": err.Error()})
			return
		}

		// 实时关联 ESL 注册态
		regs, _ := h.getEslRegistrations("")
		regMap := make(map[string]db.Registration)
		for _, reg := range regs {
			regMap[reg.RegUser] = reg
		}

		for i := range list {
			if reg, ok := regMap[list[i].Extension]; ok {
				list[i].IsRegistered = true
				list[i].NetworkIP = reg.NetworkIP
				list[i].NetworkPort = reg.NetworkPort
				list[i].NetworkProto = reg.NetworkProto
				list[i].Expires = reg.Expires
				list[i].RemainingSeconds = reg.RemainingSeconds
				list[i].PingStatus = "Online"
				list[i].URL = reg.URL
				list[i].Token = reg.Token
				list[i].Realm = reg.Realm
				list[i].Hostname = reg.Hostname
			} else if !list[i].IsRegistered {
				list[i].PingStatus = "Offline"
			}
		}

		// 过滤
		filtered := make([]db.ExtensionDetail, 0, len(list))
		for _, ext := range list {
			if statusFilter == "registered" && !ext.IsRegistered {
				continue
			}
			if statusFilter == "unregistered" && ext.IsRegistered {
				continue
			}
			if searchKw != "" {
				kw := strings.ToLower(searchKw)
				if !strings.Contains(strings.ToLower(ext.Extension), kw) &&
					!strings.Contains(strings.ToLower(ext.Description), kw) &&
					!strings.Contains(strings.ToLower(ext.NetworkIP), kw) {
					continue
				}
			}
			filtered = append(filtered, ext)
		}

		// 排序：按 extension 数字升序
		sort.Slice(filtered, func(i, j int) bool {
			numI, errI := strconv.Atoi(filtered[i].Extension)
			numJ, errJ := strconv.Atoi(filtered[j].Extension)
			if errI == nil && errJ == nil {
				return numI < numJ
			}
			return filtered[i].Extension < filtered[j].Extension
		})

		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "success",
			"total":   len(filtered),
			"data":    filtered,
		})

	case http.MethodPost:
		// 新增分机：写入 XML，若 PG 可用落库 PG，执行 reloadxml
		var req struct {
			Extension    string `json:"extension"`
			Password     string `json:"password"`
			Context      string `json:"context"`
			Callgroup    string `json:"callgroup"`
			EndpointType string `json:"endpoint_type"`
			Description  string `json:"description"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Extension == "" || req.Password == "" {
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "extension and password are required"})
			return
		}

		if req.Context == "" {
			req.Context = "default"
		}
		if req.Callgroup == "" {
			req.Callgroup = "default"
		}
		if req.EndpointType == "" {
			req.EndpointType = "SIP"
		}

		// 1. 自动生成/覆写 FreeSWITCH 本地 XML 配置
		if err := writeExtensionXml(req.Extension, req.Password, req.Context, req.Callgroup); err != nil {
			log.Printf("⚠️ 写入分机 XML 失败: %v", err)
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": "写入 XML 失败: " + err.Error()})
			return
		}

		// 2. 如果 PG 可用，写入 PostgreSQL 持久化
		if h.repo != nil && h.repo.Healthy() {
			_ = h.repo.CreateExtension(db.FsExtension{
				Extension:    req.Extension,
				Password:     req.Password,
				Context:      req.Context,
				Callgroup:    req.Callgroup,
				EndpointType: req.EndpointType,
				IsEnabled:    true,
				Description:  req.Description,
			})
		}

		// 3. 触发 FreeSWITCH ESL reloadxml
		reloadReply, _ := h.eslClient.ExecuteAPI("reloadxml", "")

		log.Printf("✅ [HTTP] 分机 %s 成功创建并热重载 FreeSWITCH (+OK)", req.Extension)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":          200,
			"message":       fmt.Sprintf("分机 %s 已成功生效", req.Extension),
			"extension":     req.Extension,
			"reload_result": reloadReply,
		})

	case http.MethodDelete:
		// 删除分机：删除 XML 文件，若 PG 可用从 PG 删，执行 reloadxml
		ext := r.URL.Query().Get("extension")
		if ext == "" {
			var req struct {
				Extension string `json:"extension"`
			}
			_ = json.NewDecoder(r.Body).Decode(&req)
			ext = req.Extension
		}
		if ext == "" {
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "extension is required"})
			return
		}

		dir := findDirectoryPath()
		_ = os.Remove(filepath.Join(dir, ext+".xml"))
		if h.repo != nil && h.repo.Healthy() {
			_ = h.repo.DeleteExtension(ext)
		}
		_, _ = h.eslClient.ExecuteAPI("reloadxml", "")

		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":      200,
			"message":   fmt.Sprintf("分机 %s 已注销并删除 XML", ext),
			"extension": ext,
		})

	default:
		w.WriteHeader(http.StatusMethodNotAllowed)
	}
}

// HandleUpdatePassword 更新分机密码（覆写 XML，若 PG 可用更新 PG，触发 reloadxml）
func (h *TelephonyHandler) HandleUpdatePassword(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	if r.Method != http.MethodPost && r.Method != http.MethodPut {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	var req struct {
		Extension string `json:"extension"`
		Password  string `json:"password"`
		Context   string `json:"context"`
		Callgroup string `json:"callgroup"`
	}

	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Extension == "" || req.Password == "" {
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "extension and password are required"})
		return
	}

	if req.Context == "" {
		req.Context = "default"
	}
	if req.Callgroup == "" {
		req.Callgroup = "default"
	}

	// 1. 覆写本地 XML
	if err := writeExtensionXml(req.Extension, req.Password, req.Context, req.Callgroup); err != nil {
		log.Printf("⚠️ 覆写分机 XML 失败: %v", err)
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": "覆写 XML 失败: " + err.Error()})
		return
	}

	// 2. 如果 PG 可用，更新 PostgreSQL
	if h.repo != nil && h.repo.Healthy() {
		_ = h.repo.UpdateExtensionPassword(req.Extension, req.Password)
	}

	// 3. 执行 reloadxml
	reloadReply, _ := h.eslClient.ExecuteAPI("reloadxml", "")

	log.Printf("✅ [HTTP] 分机 %s 密码已更新并热重载生效", req.Extension)
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":          200,
		"message":       fmt.Sprintf("分机 %s 密码已成功更新并热重载生效", req.Extension),
		"extension":     req.Extension,
		"reload_result": reloadReply,
	})
}

// HandleChannels 查询活跃话道与通话 (支持 PG / ESL 自动降级)
func (h *TelephonyHandler) HandleChannels(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	var channels []db.Channel
	var calls []db.Call
	var err error
	if h.repo != nil && h.repo.Healthy() {
		channels, err = h.repo.GetChannels()
		calls, _ = h.repo.GetCalls()
	}
	if err != nil || h.repo == nil || !h.repo.Healthy() {
		channels, calls, _ = h.getEslChannels()
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":     200,
		"message":  "success",
		"channels": channels,
		"calls":    calls,
	})
}

// HandleChannelKill 挂断强拆通道 (uuid_kill)
func (h *TelephonyHandler) HandleChannelKill(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	var req struct {
		UUID  string `json:"uuid"`
		Cause string `json:"cause"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.UUID == "" {
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "uuid is required"})
		return
	}

	if req.Cause == "" {
		req.Cause = "NORMAL_CLEARING"
	}

	log.Printf("✂️ [Channel Kill] 正在挂断信道 UUID=%s 原因=%s", req.UUID, req.Cause)
	reply, err := h.eslClient.ExecuteAPI("uuid_kill", fmt.Sprintf("%s %s", req.UUID, req.Cause))
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": err.Error()})
		return
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":    200,
		"message": "success",
		"uuid":    req.UUID,
		"reply":   reply,
	})
}

// HandleChannelTransfer 盲转通道 (uuid_transfer)
func (h *TelephonyHandler) HandleChannelTransfer(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	var req struct {
		UUID        string `json:"uuid"`
		Destination string `json:"destination"`
		Context     string `json:"context"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.UUID == "" || req.Destination == "" {
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "uuid and destination are required"})
		return
	}

	if req.Context == "" {
		req.Context = "default"
	}

	reply, err := h.eslClient.ExecuteAPI("uuid_transfer", fmt.Sprintf("%s %s XML %s", req.UUID, req.Destination, req.Context))
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": err.Error()})
		return
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":    200,
		"message": "success",
		"reply":   reply,
	})
}

// HandleSofiaProfiles 查询 Sofia Profile
func (h *TelephonyHandler) HandleSofiaProfiles(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	// 先获取总体 sofia status 以提取各 profile 的真实运行状态
	overallStatus, _ := h.eslClient.ExecuteAPI("sofia", "status")
	profileStates := make(map[string]string)
	for _, l := range strings.Split(overallStatus, "\n") {
		fields := strings.Fields(l)
		if len(fields) >= 4 && fields[1] == "profile" {
			// e.g. internal profile sip:mod_sofia@192.168.18.64:5060 RUNNING (0)
			pName := fields[0]
			pState := strings.Join(fields[3:], " ")
			profileStates[pName] = pState
		}
	}

	profiles := make([]map[string]interface{}, 0, 2)
	for _, name := range []string{"internal", "external"} {
		raw, err := h.eslClient.ExecuteAPI("sofia", "status profile "+name)
		if err != nil || strings.Contains(strings.ToLower(raw), "invalid profile") || strings.HasPrefix(strings.TrimSpace(raw), "-ERR") {
			if h.healer != nil {
				if healed, _ := h.healer.ReconcileAndHeal(); healed {
					raw, err = h.eslClient.ExecuteAPI("sofia", "status profile "+name)
				}
			}
		}
		if err != nil || strings.Contains(strings.ToLower(raw), "invalid profile") || strings.HasPrefix(strings.TrimSpace(raw), "-ERR") {
			w.WriteHeader(http.StatusBadGateway)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 502, "error": "Sofia Profile 查询失败"})
			return
		}

		st := profileStates[name]
		if st == "" {
			st = "RUNNING"
		}

		profile := map[string]interface{}{
			"name":      name,
			"state":     st,
			"raw_lines": strings.Split(stripANSI(raw), "\n"),
			"sip_port":  5060,
		}
		if name == "external" {
			profile["sip_port"] = 5080
		}

		for _, line := range strings.Split(raw, "\n") {
			line = strings.TrimSpace(line)
			if line == "" {
				continue
			}
			parts := strings.Fields(line)
			if len(parts) < 2 {
				continue
			}
			value := strings.Join(parts[1:], " ")
			switch strings.ToUpper(parts[0]) {
			case "SIP-IP":
				profile["bind_ip"] = value
			case "DIALPLAN":
				profile["dialplan"] = value
			case "CONTEXT":
				profile["context"] = value
			}

			if strings.HasPrefix(line, "CODECS IN") {
				profile["codecs"] = strings.TrimSpace(strings.TrimPrefix(line, "CODECS IN"))
			}

			// 解析端口
			if strings.HasPrefix(line, "BIND-URL") {
				if re := regexp.MustCompile(`:(\d+)`); re.MatchString(value) {
					matches := re.FindStringSubmatch(value)
					if len(matches) > 1 {
						if p, err := strconv.Atoi(matches[1]); err == nil {
							profile["sip_port"] = p
						}
					}
				}
			} else if strings.HasPrefix(line, "WS-BIND-URL") {
				if re := regexp.MustCompile(`:(\d+)`); re.MatchString(value) {
					matches := re.FindStringSubmatch(value)
					if len(matches) > 1 {
						if p, err := strconv.Atoi(matches[1]); err == nil {
							profile["ws_port"] = p
						}
					}
				}
			} else if strings.HasPrefix(line, "WSS-BIND-URL") {
				if re := regexp.MustCompile(`:(\d+)`); re.MatchString(value) {
					matches := re.FindStringSubmatch(value)
					if len(matches) > 1 {
						if p, err := strconv.Atoi(matches[1]); err == nil {
							profile["wss_port"] = p
						}
					}
				}
			}
		}

		if profile["dialplan"] == nil || profile["dialplan"] == "" {
			profile["dialplan"] = "XML"
		}
		if profile["context"] == nil || profile["context"] == "" {
			if name == "internal" {
				profile["context"] = "default"
			} else {
				profile["context"] = "public"
			}
		}

		profiles = append(profiles, profile)
	}
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":    200,
		"message": "success",
		"data":    profiles,
	})
}

// HandleGateways 运营商网关中继 (GET: 查 PG / ESL; POST: 存入 PG / XML 并热生效; DELETE: 删 PG / XML 并卸载)
func (h *TelephonyHandler) HandleGateways(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	switch r.Method {
	case http.MethodGet:
		var gateways []db.Gateway
		var err error
		if h.repo != nil && h.repo.Healthy() {
			gateways, err = h.repo.GetGateways()
		}
		if err != nil || h.repo == nil || !h.repo.Healthy() {
			gateways = h.getEslGateways()
		}
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "success",
			"data":    gateways,
		})

	case http.MethodPost:
		var req struct {
			db.Gateway
			Password string `json:"password"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Name == "" || req.Proxy == "" {
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "name and proxy are required"})
			return
		}

		// 1. 如果 PG 可用，落库 PostgreSQL
		gw := req.Gateway
		gw.Password = req.Password
		if h.repo != nil && h.repo.Healthy() {
			_ = h.repo.SaveGateway(&gw)
		}

		// 2. 写入/更新 FreeSWITCH sip_profiles/external/{name}.xml
		if err := writeGatewayXml(gw); err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": "配置写入 XML 失败: " + err.Error()})
			return
		}

		// 3. 触发 Sofia External 热重载
		rescanReply, _ := h.eslClient.ExecuteAPI("sofia", "profile external rescan")

		log.Printf("✅ [HTTP] 网关 %s 配置已保存并触发 Sofia Rescan", gw.Name)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": fmt.Sprintf("网关 %s 配置已保存，Sofia 重扫描已受理", gw.Name),
			"rescan":  rescanReply,
			"data":    gw,
		})

	case http.MethodDelete:
		name := r.URL.Query().Get("name")
		if name == "" {
			var req struct {
				Name string `json:"name"`
			}
			_ = json.NewDecoder(r.Body).Decode(&req)
			name = req.Name
		}
		if name == "" {
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "name is required"})
			return
		}

		if h.repo != nil && h.repo.Healthy() {
			_ = h.repo.DeleteGateway(name)
		}
		gwFile := fmt.Sprintf("/opt/homebrew/etc/freeswitch/sip_profiles/external/%s.xml", name)
		_ = os.Remove(gwFile)
		_, _ = h.eslClient.ExecuteAPI("sofia", fmt.Sprintf("profile external killgw %s", name))
		_, _ = h.eslClient.ExecuteAPI("sofia", "profile external rescan")

		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": fmt.Sprintf("网关 %s 已成功删除并卸载", name),
		})

	default:
		w.WriteHeader(http.StatusMethodNotAllowed)
	}
}

// HandlePingGateway 向特定网关发送 SIP OPTIONS 探活心跳
func (h *TelephonyHandler) HandlePingGateway(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	var req struct {
		Name  string `json:"name"`
		Proxy string `json:"proxy"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.Name == "" {
		req.Name = r.URL.Query().Get("name")
	}

	start := time.Now()
	// 执行 sofia profile external ping gateway
	reply, err := h.eslClient.ExecuteAPI("sofia", fmt.Sprintf("profile external ping %s", req.Name))
	latency := time.Since(start).Milliseconds()

	if err != nil || strings.HasPrefix(strings.TrimSpace(reply), "-ERR") || strings.Contains(strings.ToLower(reply), "error") {
		w.WriteHeader(http.StatusBadGateway)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 502, "error": "网关探活指令失败"})
		return
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":                200,
		"gateway":             req.Name,
		"status":              "UNKNOWN",
		"command_duration_ms": latency,
		"reply":               reply,
	})
}

// HandleCdr 从 PostgreSQL fs_cdr 分页拉取底层原始话单 (若 PG 未连接则优雅降级为空)
func (h *TelephonyHandler) HandleCdr(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	pageNum, _ := strconv.Atoi(r.URL.Query().Get("pageNum"))
	pageSize, _ := strconv.Atoi(r.URL.Query().Get("pageSize"))
	if pageNum <= 0 {
		pageNum = 1
	}
	if pageSize <= 0 {
		pageSize = 10
	}

	caller := r.URL.Query().Get("caller")
	dest := r.URL.Query().Get("destination")

	if h.repo != nil && h.repo.Healthy() {
		cdrs, total, err := h.repo.GetCdrs(pageNum, pageSize, caller, dest)
		if err == nil {
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"code":     200,
				"message":  "success",
				"total":    total,
				"pageNum":  pageNum,
				"pageSize": pageSize,
				"data":     cdrs,
			})
			return
		}
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":     200,
		"message":  "success",
		"total":    0,
		"pageNum":  pageNum,
		"pageSize": pageSize,
		"data":     []db.FsCdr{},
	})
}

// HandleCliExec 在线执行 fs_cli 原生命令
func (h *TelephonyHandler) HandleCliExec(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	var req struct {
		Command string `json:"command"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || strings.TrimSpace(req.Command) == "" {
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "command is required"})
		return
	}

	cmdStr := strings.TrimSpace(req.Command)
	parts := strings.SplitN(cmdStr, " ", 2)
	baseCmd := parts[0]
	args := ""
	if len(parts) > 1 {
		args = parts[1]
	}

	log.Printf("💻 [CLI Exec] 执行 FreeSWITCH 指令: %s %s", baseCmd, args)
	reply, err := h.eslClient.ExecuteAPI(baseCmd, args)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    500,
			"command": cmdStr,
			"error":   err.Error(),
		})
		return
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":    200,
		"command": cmdStr,
		"output":  reply,
	})
}

// findDirectoryPath 查找分机 XML 目录物理路径
func findDirectoryPath() string {
	candidates := []string{
		"/opt/homebrew/etc/freeswitch/directory/default",
		"/opt/homebrew/Cellar/freeswitch/1.11.3/etc/freeswitch/directory/default",
		"/etc/freeswitch/directory/default",
		"/usr/local/freeswitch/conf/directory/default",
	}
	for _, p := range candidates {
		if fi, err := os.Stat(p); err == nil && fi.IsDir() {
			return p
		}
	}
	return "/opt/homebrew/etc/freeswitch/directory/default"
}

// getDirectoryExtensions 从 FreeSWITCH 物理目录读取并解析所有分机 XML
func (h *TelephonyHandler) getDirectoryExtensions() ([]db.ExtensionDetail, error) {
	dirPath := findDirectoryPath()
	entries, err := os.ReadDir(dirPath)
	if err != nil {
		return nil, fmt.Errorf("读取分机目录失败 (%s): %w", dirPath, err)
	}

	reExt := regexp.MustCompile(`<user\s+id="([^"]+)"`)
	rePwd := regexp.MustCompile(`<param\s+name="password"\s+value="([^"]*)"`)
	reContext := regexp.MustCompile(`<variable\s+name="user_context"\s+value="([^"]*)"`)
	reCallgroup := regexp.MustCompile(`<variable\s+name="callgroup"\s+value="([^"]*)"`)
	reCallerName := regexp.MustCompile(`<variable\s+name="effective_caller_id_name"\s+value="([^"]*)"`)

	var list []db.ExtensionDetail
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".xml") {
			continue
		}
		// 排除非分机模板文件，如 example.com.xml
		if entry.Name() == "example.com.xml" {
			continue
		}

		filePath := filepath.Join(dirPath, entry.Name())
		data, err := os.ReadFile(filePath)
		if err != nil {
			continue
		}
		content := string(data)

		// 排除包含 <gateways> 的外呼网关模板
		if strings.Contains(content, "<gateways>") {
			continue
		}

		ext := strings.TrimSuffix(entry.Name(), ".xml")
		// 提取分机号
		if m := reExt.FindStringSubmatch(content); len(m) > 1 && m[1] != "" {
			ext = m[1]
		}
		// 排除变量占位符如 $${default_provider}
		if strings.HasPrefix(ext, "$") {
			continue
		}
		pwd := "$${default_password}"
		if m := rePwd.FindStringSubmatch(content); len(m) > 1 {
			pwd = m[1]
		}
		ctx := "default"
		if m := reContext.FindStringSubmatch(content); len(m) > 1 && m[1] != "" {
			ctx = m[1]
		}
		cg := "default"
		if m := reCallgroup.FindStringSubmatch(content); len(m) > 1 && m[1] != "" {
			cg = m[1]
		}
		desc := "标准 SIP 分机 " + ext
		if m := reCallerName.FindStringSubmatch(content); len(m) > 1 && m[1] != "" {
			desc = m[1]
		}

		epType := "SIP"
		if strings.HasPrefix(ext, "901") || strings.Contains(content, "rtp_secure_media") || strings.Contains(content, "webrtc") {
			epType = "WebRTC"
		}

		list = append(list, db.ExtensionDetail{
			Extension:    ext,
			Password:     pwd,
			Context:      ctx,
			Callgroup:    cg,
			EndpointType: epType,
			IsEnabled:    true,
			Description:  desc,
			XmlPath:      filePath,
			PingStatus:   "Offline",
		})
	}
	return list, nil
}

// getEslRegistrations 从 FreeSWITCH ESL show registrations as json 查活跃注册态
func (h *TelephonyHandler) getEslRegistrations(userFilter string) ([]db.Registration, error) {
	raw, err := h.eslClient.ExecuteAPI("show", "registrations as json")
	if err != nil {
		return nil, err
	}

	var parsed struct {
		RowCount int `json:"row_count"`
		Rows     []struct {
			RegUser      string `json:"reg_user"`
			Realm        string `json:"realm"`
			Token        string `json:"token"`
			URL          string `json:"url"`
			Expires      string `json:"expires"`
			NetworkIP    string `json:"network_ip"`
			NetworkPort  string `json:"network_port"`
			NetworkProto string `json:"network_proto"`
			Hostname     string `json:"hostname"`
			Metadata     string `json:"metadata"`
		} `json:"rows"`
	}
	if err := json.Unmarshal([]byte(raw), &parsed); err != nil {
		return nil, err
	}

	now := time.Now().Unix()
	var regs []db.Registration
	for _, r := range parsed.Rows {
		if userFilter != "" && !strings.EqualFold(r.RegUser, userFilter) {
			continue
		}
		exp, _ := strconv.ParseInt(r.Expires, 10, 64)
		rem := exp - now
		if rem < 0 {
			rem = 0
		}
		status := "Online"
		if rem == 0 {
			status = "Offline"
		}

		regs = append(regs, db.Registration{
			RegUser:          r.RegUser,
			Realm:            r.Realm,
			Token:            r.Token,
			URL:              r.URL,
			Expires:          exp,
			NetworkIP:        r.NetworkIP,
			NetworkPort:      r.NetworkPort,
			NetworkProto:     r.NetworkProto,
			Hostname:         r.Hostname,
			Metadata:         r.Metadata,
			RemainingSeconds: rem,
			Status:           status,
		})
	}
	return regs, nil
}

// getEslChannels 从 FreeSWITCH ESL 查询活跃通道和通话
func (h *TelephonyHandler) getEslChannels() ([]db.Channel, []db.Call, error) {
	channels := make([]db.Channel, 0)
	calls := make([]db.Call, 0)

	rawCh, err := h.eslClient.ExecuteAPI("show", "channels as json")
	if err == nil {
		var parsedCh struct {
			Rows []map[string]interface{} `json:"rows"`
		}
		if json.Unmarshal([]byte(rawCh), &parsedCh) == nil {
			for _, r := range parsedCh.Rows {
				uuid, _ := r["uuid"].(string)
				name, _ := r["name"].(string)
				state, _ := r["state"].(string)
				cidName, _ := r["cid_name"].(string)
				cidNum, _ := r["cid_num"].(string)
				ipAddr, _ := r["ip_addr"].(string)
				dest, _ := r["dest"].(string)
				application, _ := r["application"].(string)
				appData, _ := r["application_data"].(string)
				dialplan, _ := r["dialplan"].(string)
				ctx, _ := r["context"].(string)
				readCodec, _ := r["read_codec"].(string)
				writeCodec, _ := r["write_codec"].(string)
				callstate, _ := r["callstate"].(string)
				calleeName, _ := r["callee_name"].(string)
				calleeNum, _ := r["callee_num"].(string)
				callUUID, _ := r["call_uuid"].(string)
				hostname, _ := r["hostname"].(string)
				created, _ := r["created"].(string)

				channels = append(channels, db.Channel{
					UUID:            uuid,
					Name:            name,
					State:           state,
					CIDName:         cidName,
					CIDNum:          cidNum,
					IPAddr:          ipAddr,
					Dest:            dest,
					Application:     application,
					ApplicationData: appData,
					Dialplan:        dialplan,
					Context:         ctx,
					ReadCodec:       readCodec,
					WriteCodec:      writeCodec,
					CallState:       callstate,
					CalleeName:      calleeName,
					CalleeNum:       calleeNum,
					CallUUID:        callUUID,
					Hostname:        hostname,
					Created:         created,
				})
			}
		}
	}

	rawCalls, err := h.eslClient.ExecuteAPI("show", "calls as json")
	if err == nil {
		var parsedCalls struct {
			Rows []map[string]interface{} `json:"rows"`
		}
		if json.Unmarshal([]byte(rawCalls), &parsedCalls) == nil {
			for _, r := range parsedCalls.Rows {
				callUUID, _ := r["call_uuid"].(string)
				callCreated, _ := r["call_created"].(string)
				callerUUID, _ := r["caller_uuid"].(string)
				calleeUUID, _ := r["callee_uuid"].(string)
				hostname, _ := r["hostname"].(string)

				calls = append(calls, db.Call{
					CallUUID:    callUUID,
					CallCreated: callCreated,
					CallerUUID:  callerUUID,
					CalleeUUID:  calleeUUID,
					Hostname:    hostname,
				})
			}
		}
	}

	return channels, calls, nil
}

// getEslGateways 从 FreeSWITCH ESL 查询网关状态
func (h *TelephonyHandler) getEslGateways() []db.Gateway {
	raw, err := h.eslClient.ExecuteAPI("sofia", "status gateway")
	if err != nil {
		return []db.Gateway{}
	}

	var list []db.Gateway
	lines := strings.Split(raw, "\n")
	for _, l := range lines {
		l = strings.TrimSpace(l)
		if strings.HasPrefix(l, "===") || strings.HasPrefix(l, "Profile::") || l == "" || strings.Contains(l, "gateway:") {
			continue
		}
		fields := strings.Fields(l)
		if len(fields) < 3 {
			continue
		}
		name := fields[0]
		profile := "external"
		if strings.Contains(name, "::") {
			parts := strings.SplitN(name, "::", 2)
			profile = parts[0]
			name = parts[1]
		}
		proxy := fields[1]
		status := fields[2]
		pingMs := ""
		if len(fields) > 3 {
			pingMs = fields[3]
		}

		list = append(list, db.Gateway{
			Name:      name,
			Profile:   profile,
			Proxy:     proxy,
			Status:    status,
			PingMS:    pingMs,
			IsEnabled: true,
		})
	}
	return list
}

// 辅助函数：生成/覆写分机 XML
func writeExtensionXml(ext, pwd, context, callgroup string) error {
	dirPath := findDirectoryPath()
	_ = os.MkdirAll(dirPath, 0755)

	targetFile := filepath.Join(dirPath, fmt.Sprintf("%s.xml", ext))
	content := fmt.Sprintf(`<include>
  <user id="%s">
    <params>
      <param name="password" value="%s"/>
      <param name="vm-password" value="%s"/>
    </params>
    <variables>
      <variable name="toll_allow" value="domestic,international,local"/>
      <variable name="accountcode" value="%s"/>
      <variable name="user_context" value="%s"/>
      <variable name="effective_caller_id_name" value="Extension %s"/>
      <variable name="effective_caller_id_number" value="%s"/>
      <variable name="outbound_caller_id_name" value="$${outbound_caller_name}"/>
      <variable name="outbound_caller_id_number" value="$${outbound_caller_id}"/>
      <variable name="callgroup" value="%s"/>
      <!-- 支持 WebRTC / SIP 双模自适应加密与转码 -->
      <variable name="rtp_secure_media" value="optional"/>
    </variables>
  </user>
</include>`, ext, pwd, ext, ext, context, ext, ext, callgroup)

	return os.WriteFile(targetFile, []byte(content), 0644)
}

// 辅助函数：生成/覆写运营商网关 XML
func writeGatewayXml(gw db.Gateway) error {
	dirPath := "/opt/homebrew/etc/freeswitch/sip_profiles/external"
	if _, err := os.Stat(dirPath); os.IsNotExist(err) {
		dirPath = "/etc/freeswitch/sip_profiles/external"
	}
	_ = os.MkdirAll(dirPath, 0755)

	targetFile := filepath.Join(dirPath, fmt.Sprintf("%s.xml", gw.Name))

	regStr := "true"
	if !gw.Register {
		regStr = "false"
	}
	callerIdInFromStr := "true"
	if !gw.CallerIdInFrom {
		callerIdInFromStr = "false"
	}

	content := fmt.Sprintf(`<include>
  <gateway name="%s">
    <param name="proxy" value="%s"/>
    <param name="username" value="%s"/>
    <param name="password" value="%s"/>
    <param name="register" value="%s"/>
    <param name="from-domain" value="%s"/>
    <param name="caller-id-in-from" value="%s"/>
    <param name="context" value="%s"/>
    <param name="expire-seconds" value="%d"/>
    <param name="ping" value="%d"/>
    <param name="dtmf-type" value="%s"/>
  </gateway>
</include>`, gw.Name, gw.Proxy, gw.Username, gw.Password, regStr, gw.FromDomain, callerIdInFromStr, gw.Context, gw.ExpireSeconds, gw.PingSeconds, gw.DtmfType)

	return os.WriteFile(targetFile, []byte(content), 0644)
}

// CORS 跨域头封装
func setCORS(w http.ResponseWriter) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")
}

// 清除 ANSI 颜色控制字符
var ansiRegex = regexp.MustCompile(`\x1b\[[0-9;]*[a-zA-Z]`)

func stripANSI(str string) string {
	return ansiRegex.ReplaceAllString(str, "")
}

// findVarsXmlPath 查找 vars.xml 文件真实物理路径
func findVarsXmlPath() string {
	candidates := []string{
		"/opt/homebrew/etc/freeswitch/vars.xml",
		"/opt/homebrew/Cellar/freeswitch/1.11.3/etc/freeswitch/vars.xml",
		"/etc/freeswitch/vars.xml",
		"/usr/local/freeswitch/conf/vars.xml",
	}
	for _, p := range candidates {
		if fi, err := os.Stat(p); err == nil && !fi.IsDir() {
			return p
		}
	}
	return "/opt/homebrew/etc/freeswitch/vars.xml"
}

// getAvailableHostIPs 获取本机所有有效的非回环 IPv4 地址
func getAvailableHostIPs() []string {
	ips := []string{}
	ifaces, err := net.Interfaces()
	if err != nil {
		return ips
	}
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			var ip net.IP
			switch v := addr.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			if ip != nil && !ip.IsLoopback() && ip.To4() != nil {
				ips = append(ips, ip.String())
			}
		}
	}
	return ips
}

// HandleVars 处理 vars.xml 常用配置读取与变更
func (h *TelephonyHandler) HandleVars(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	varsPath := findVarsXmlPath()

	switch r.Method {
	case http.MethodGet:
		contentBytes, err := os.ReadFile(varsPath)
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"code":  500,
				"error": fmt.Sprintf("读取 vars.xml 失败 (%s): %v", varsPath, err),
			})
			return
		}
		content := string(contentBytes)

		// 解析常用变量
		vars := map[string]string{
			"local_ip_v4":      extractXmlVar(content, "local_ip_v4"),
			"domain":           extractXmlVar(content, "domain"),
			"default_password": extractXmlVar(content, "default_password"),
			"external_sip_ip":  extractXmlVar(content, "external_sip_ip"),
			"external_rtp_ip":  extractXmlVar(content, "external_rtp_ip"),
			"sound_prefix":     extractXmlVar(content, "sound_prefix"),
			"hold_music":       extractXmlVar(content, "hold_music"),
			"rtp_sdes_suites":  extractXmlVar(content, "rtp_sdes_suites"),
		}

		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "success",
			"data": map[string]interface{}{
				"file_path":     varsPath,
				"vars":          vars,
				"available_ips": getAvailableHostIPs(),
				"raw_content":   content,
			},
		})

	case http.MethodPost, http.MethodPut:
		var req struct {
			Vars map[string]string `json:"vars"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Vars == nil {
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "vars object is required"})
			return
		}

		contentBytes, err := os.ReadFile(varsPath)
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"code":  500,
				"error": fmt.Sprintf("读取 vars.xml 失败 (%s): %v", varsPath, err),
			})
			return
		}
		content := string(contentBytes)

		// 检查 local_ip_v4 是否变更
		oldIp := extractXmlVar(content, "local_ip_v4")

		// 逐项更新变量
		for k, v := range req.Vars {
			if strings.TrimSpace(k) == "" {
				continue
			}
			content = setOrReplaceXmlVar(content, k, v)
		}

		// 写回文件
		if err := os.WriteFile(varsPath, []byte(content), 0644); err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"code":  500,
				"error": fmt.Sprintf("保存 vars.xml 失败: %v", err),
			})
			return
		}

		// 触发 FreeSWITCH ESL reloadxml
		reloadReply, _ := h.eslClient.ExecuteAPI("reloadxml", "")

		// 如果 local_ip_v4 发生变更，重启/拉起 Sofia 协议栈
		newIp := req.Vars["local_ip_v4"]
		sofiaRestarted := false
		if newIp != "" && newIp != oldIp {
			_, _ = h.eslClient.ExecuteAPI("sofia", "profile internal restart")
			_, _ = h.eslClient.ExecuteAPI("sofia", "profile external restart")
			_, _ = h.eslClient.ExecuteAPI("sofia", "profile internal start")
			_, _ = h.eslClient.ExecuteAPI("sofia", "profile external start")
			sofiaRestarted = true
		}

		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":            200,
			"message":         "vars.xml 配置已成功保存并热重载生效",
			"reload_result":   reloadReply,
			"sofia_restarted": sofiaRestarted,
			"file_path":       varsPath,
		})

	default:
		w.WriteHeader(http.StatusMethodNotAllowed)
	}
}

// 辅助函数：从 XML 文本提取变量值
func extractXmlVar(content, key string) string {
	pattern := fmt.Sprintf(`<X-PRE-PROCESS\s+cmd="set"\s+data="%s=([^"]*)"\s*/>`, regexp.QuoteMeta(key))
	re := regexp.MustCompile(pattern)
	matches := re.FindStringSubmatch(content)
	if len(matches) > 1 {
		return matches[1]
	}
	return ""
}

var reSingleDollarVar = regexp.MustCompile(`(^|[^\$])\$\{([^}]+)\}`)

// sanitizeFsVar 防范前端或外部 API 误传单美元符号 ${...}，自动纠正为 FreeSWITCH 预处理全局宏 $${...}
func sanitizeFsVar(val string) string {
	if val == "" {
		return val
	}
	for reSingleDollarVar.MatchString(val) {
		val = reSingleDollarVar.ReplaceAllString(val, `${1}$${${2}}`)
	}
	return val
}

// 辅助函数：替换或新增 XML 变量
func setOrReplaceXmlVar(content, key, value string) string {
	value = sanitizeFsVar(value)
	pattern := fmt.Sprintf(`(?m)^[ \t]*<X-PRE-PROCESS\s+cmd="set"\s+data="%s=[^"]*"\s*/>`, regexp.QuoteMeta(key))
	re := regexp.MustCompile(pattern)
	replacement := fmt.Sprintf(`  <X-PRE-PROCESS cmd="set" data="%s=%s"/>`, key, value)
	if re.MatchString(content) {
		return re.ReplaceAllString(content, replacement)
	}
	// 如果不存在，尝试在 <include> 后插入
	if strings.Contains(content, "<include>") {
		return strings.Replace(content, "<include>", "<include>\n"+replacement, 1)
	}
	return content + "\n" + replacement
}
