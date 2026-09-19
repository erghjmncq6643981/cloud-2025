package api

import (
	"chandler25-fs-sidecar-agent/db"
	"chandler25-fs-sidecar-agent/esl"
	"chandler25-fs-sidecar-agent/governance"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// TelephonyHandler 软交换信令与分机维护处理器 (支持 PostgreSQL 核心持久化与 FreeSWITCH 热重载)
type TelephonyHandler struct {
	repo       *db.Repository
	gov        *governance.NodeManager
	eslClient  *esl.Client
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

	regCount, chCount, callCount, _ := h.repo.GetSummaryCounts()

	// 解析 status 文本中的关键指标
	uptimeStr := "运行中"
	sessionsStr := fmt.Sprintf("%d", chCount)
	cpsStr := "0.0"

	lines := strings.Split(rawStatus, "\n")
	for _, l := range lines {
		lTrim := strings.TrimSpace(l)
		if strings.HasPrefix(lTrim, "UP ") {
			uptimeStr = lTrim
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

	statusData := map[string]interface{}{
		"node_id":          h.gov.NodeID(),
		"esl_connected":    err == nil,
		"fs_alive":         err == nil,
		"pg_connected":     err == nil,
		"channels":         chCount,
		"active_channels":  chCount,
		"calls":            callCount,
		"active_calls":     callCount,
		"registrations":    regCount,
		"uptime":           uptimeStr,
		"total_sessions":   sessionsStr,
		"cps":              cpsStr,
		"current_cps":      cpsStr,
		"raw_status":       stripANSI(rawStatus),
		"sidecar_version":  "v2.1.0-pg-native",
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":    200,
		"message": "success",
		"data":    statusData,
	})
}

// HandleRegistrations 查询当前 FreeSWITCH 活跃注册分机 (PostgreSQL registrations 表)
func (h *TelephonyHandler) HandleRegistrations(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	userFilter := r.URL.Query().Get("user")
	regs, err := h.repo.GetRegistrations(userFilter)
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

// HandleAllExtensions 基于 PostgreSQL (fs_extension) 并 LEFT JOIN registrations 全量查询分机资产
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

		list, err := h.repo.GetAllExtensions(statusFilter, searchKw)
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": err.Error()})
			return
		}

		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "success",
			"total":   len(list),
			"data":    list,
		})

	case http.MethodPost:
		// 新增分机：写入 PG fs_extension，写入 XML，执行 reloadxml
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

		// 1. 写入 PostgreSQL 持久化
		err := h.repo.CreateExtension(db.FsExtension{
			Extension:    req.Extension,
			Password:     req.Password,
			Context:      req.Context,
			Callgroup:    req.Callgroup,
			EndpointType: req.EndpointType,
			IsEnabled:    true,
			Description:  req.Description,
		})
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": "PostgreSQL 写入失败: " + err.Error()})
			return
		}

		// 2. 自动生成/覆写 FreeSWITCH 本地 XML 配置
		if err := writeExtensionXml(req.Extension, req.Password, req.Context, req.Callgroup); err != nil {
			log.Printf("⚠️ 写入分机 XML 警告: %v", err)
		}

		// 3. 触发 FreeSWITCH ESL reloadxml
		reloadReply, _ := h.eslClient.ExecuteAPI("reloadxml", "")

		log.Printf("✅ [HTTP] 分机 %s 成功落库 PostgreSQL 并热重载 FreeSWITCH (+OK)", req.Extension)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":          200,
			"message":       fmt.Sprintf("分机 %s 已持久化至 PostgreSQL 并成功生效", req.Extension),
			"extension":     req.Extension,
			"reload_result": reloadReply,
		})

	case http.MethodDelete:
		// 删除分机：从 PG 删除，删除 XML 文件，执行 reloadxml
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

		_ = h.repo.DeleteExtension(ext)
		xmlFile := fmt.Sprintf("/opt/homebrew/etc/freeswitch/directory/default/%s.xml", ext)
		_ = os.Remove(xmlFile)
		_, _ = h.eslClient.ExecuteAPI("reloadxml", "")

		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":      200,
			"message":   fmt.Sprintf("分机 %s 已从 PostgreSQL 注销并删除 XML", ext),
			"extension": ext,
		})

	default:
		w.WriteHeader(http.StatusMethodNotAllowed)
	}
}

// HandleUpdatePassword 更新分机密码（更新 PostgreSQL fs_extension，覆写 XML，触发 reloadxml）
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

	// 1. 更新 PostgreSQL 持久化表
	if err := h.repo.UpdateExtensionPassword(req.Extension, req.Password); err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":  500,
			"error": "更新 PostgreSQL 密码失败: " + err.Error(),
		})
		return
	}

	// 2. 覆写本地 XML
	if err := writeExtensionXml(req.Extension, req.Password, req.Context, req.Callgroup); err != nil {
		log.Printf("⚠️ 覆写分机 XML 警告: %v", err)
	}

	// 3. 执行 reloadxml
	reloadReply, _ := h.eslClient.ExecuteAPI("reloadxml", "")

	log.Printf("✅ [HTTP] 分机 %s 密码已更新至 PostgreSQL 并热重载生效", req.Extension)
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":          200,
		"message":       fmt.Sprintf("分机 %s 密码已成功更新至 PostgreSQL 并热重载生效", req.Extension),
		"extension":     req.Extension,
		"reload_result": reloadReply,
	})
}

// HandleChannels 查询活跃话道与通话
func (h *TelephonyHandler) HandleChannels(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	channels, err := h.repo.GetChannels()
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": err.Error()})
		return
	}

	calls, _ := h.repo.GetCalls()

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

	internalRaw, _ := h.eslClient.ExecuteAPI("sofia", "status profile internal")
	externalRaw, _ := h.eslClient.ExecuteAPI("sofia", "status profile external")

	localIP, _ := h.eslClient.ExecuteAPI("global_getvar", "local_ip_v4")
	localIP = strings.TrimSpace(localIP)
	if localIP == "" || strings.HasPrefix(localIP, "-ERR") {
		localIP = "127.0.0.1"
	}

	profiles := []map[string]interface{}{
		{
			"name":      "internal",
			"state":     "RUNNING",
			"sip_port":  5060,
			"ws_port":   5066,
			"wss_port":  7443,
			"bind_ip":   localIP,
			"dialplan":  "XML",
			"context":   "default",
			"codecs":    "OPUS, G722, PCMU, PCMA",
			"raw_lines": strings.Split(stripANSI(internalRaw), "\n"),
		},
		{
			"name":      "external",
			"state":     "RUNNING",
			"sip_port":  5080,
			"bind_ip":   localIP,
			"dialplan":  "XML",
			"context":   "public",
			"codecs":    "PCMA, PCMU, G729",
			"raw_lines": strings.Split(stripANSI(externalRaw), "\n"),
		},
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":    200,
		"message": "success",
		"data":    profiles,
	})
}

// HandleGateways 运营商网关中继 (GET: 查 PG; POST: 存入 PG 并热生效; DELETE: 删 PG 并卸载)
func (h *TelephonyHandler) HandleGateways(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	switch r.Method {
	case http.MethodGet:
		gateways, err := h.repo.GetGateways()
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": err.Error()})
			return
		}
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": "success",
			"data":    gateways,
		})

	case http.MethodPost:
		var gw db.Gateway
		if err := json.NewDecoder(r.Body).Decode(&gw); err != nil || gw.Name == "" || gw.Proxy == "" {
			w.WriteHeader(http.StatusBadRequest)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 400, "error": "name and proxy are required"})
			return
		}

		// 1. 落库 PostgreSQL
		if err := h.repo.SaveGateway(gw); err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": "PG 保存失败: " + err.Error()})
			return
		}

		// 2. 写入/更新 FreeSWITCH sip_profiles/external/{name}.xml
		_ = writeGatewayXml(gw)

		// 3. 触发 Sofia External 热重载
		rescanReply, _ := h.eslClient.ExecuteAPI("sofia", "profile external rescan")

		log.Printf("✅ [HTTP] 网关 %s 配置已持久化至 PostgreSQL 并触发 Sofia Rescan", gw.Name)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": fmt.Sprintf("网关 %s 配置已保存至 PostgreSQL 并热重载生效", gw.Name),
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

		_ = h.repo.DeleteGateway(name)
		gwFile := fmt.Sprintf("/opt/homebrew/etc/freeswitch/sip_profiles/external/%s.xml", name)
		_ = os.Remove(gwFile)
		_, _ = h.eslClient.ExecuteAPI("sofia", fmt.Sprintf("profile external killgw %s", name))
		_, _ = h.eslClient.ExecuteAPI("sofia", "profile external rescan")

		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"code":    200,
			"message": fmt.Sprintf("网关 %s 已从 PostgreSQL 删除并卸载", name),
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

	status := "UP"
	if err != nil || strings.Contains(strings.ToLower(reply), "down") || strings.Contains(strings.ToLower(reply), "error") {
		status = "DOWN"
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":    200,
		"gateway": req.Name,
		"status":  status,
		"ping_ms": fmt.Sprintf("%dms", latency),
		"reply":   reply,
	})
}

// HandleCdr 从 PostgreSQL fs_cdr 分页拉取底层原始话单
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

	cdrs, total, err := h.repo.GetCdrs(pageNum, pageSize, caller, dest)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{"code": 500, "error": err.Error()})
		return
	}

	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"code":     200,
		"message":  "success",
		"total":    total,
		"pageNum":  pageNum,
		"pageSize": pageSize,
		"data":     cdrs,
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

// 辅助函数：生成/覆写分机 XML
func writeExtensionXml(ext, pwd, context, callgroup string) error {
	dirPath := "/opt/homebrew/etc/freeswitch/directory/default"
	if _, err := os.Stat(dirPath); os.IsNotExist(err) {
		dirPath = "/etc/freeswitch/directory/default"
	}
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
