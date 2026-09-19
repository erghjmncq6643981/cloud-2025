package api

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os/exec"
	"strings"

	"chandler25-fs-sidecar-agent/config"
	"chandler25-fs-sidecar-agent/db"
	"chandler25-fs-sidecar-agent/esl"
	"chandler25-fs-sidecar-agent/governance"
)

// Server 软交换管理面 HTTP 同步服务
type Server struct {
	cfg       *config.Config
	gov       *governance.NodeManager
	eslClient *esl.Client
	repo      *db.Repository
	telephony *TelephonyHandler
	wsLogs    *WSLogHandler
	mux       *http.ServeMux
}

// ExtensionReq 创建分机请求体
type ExtensionReq struct {
	Extension string `json:"extension"`
	Password  string `json:"password"`
	Context   string `json:"context,omitempty"`
	Callgroup string `json:"callgroup,omitempty"`
}

// BaseResponse 统一 HTTP 响应
type BaseResponse struct {
	Code      int         `json:"code"`
	Message   string      `json:"message"`
	Extension string      `json:"extension,omitempty"`
	Data      interface{} `json:"data,omitempty"`
	Error     string      `json:"error,omitempty"`
}

// NewServer 创建 HTTP 管理服务器
func NewServer(cfg *config.Config, gov *governance.NodeManager, eslClient *esl.Client, repo *db.Repository) *Server {
	s := &Server{
		cfg:       cfg,
		gov:       gov,
		eslClient: eslClient,
		repo:      repo,
		telephony: NewTelephonyHandler(repo, gov, eslClient, cfg.ScriptPath),
		wsLogs:    NewWSLogHandler(cfg.FSEslAddr, cfg.FSEslPassword),
		mux:       http.NewServeMux(),
	}
	s.registerRoutes()
	return s
}

func (s *Server) registerRoutes() {
	s.mux.HandleFunc("/api/v1/extensions", s.handleExtensions)
	s.mux.HandleFunc("/api/v1/health", s.handleHealth)
	s.mux.HandleFunc("/health", s.handleHealth)

	// 控制面 (fswitch-web) 专属接口
	s.mux.HandleFunc("/api/v1/telephony/status", s.telephony.HandleStatus)
	s.mux.HandleFunc("/api/v1/telephony/registrations", s.telephony.HandleRegistrations)
	s.mux.HandleFunc("/api/v1/telephony/registrations/flush", s.telephony.HandleRegFlush)
	s.mux.HandleFunc("/api/v1/telephony/extensions", s.telephony.HandleAllExtensions)
	s.mux.HandleFunc("/api/v1/telephony/extensions/password", s.telephony.HandleUpdatePassword)
	s.mux.HandleFunc("/api/v1/telephony/channels", s.telephony.HandleChannels)
	s.mux.HandleFunc("/api/v1/telephony/channels/kill", s.telephony.HandleChannelKill)
	s.mux.HandleFunc("/api/v1/telephony/channels/transfer", s.telephony.HandleChannelTransfer)
	s.mux.HandleFunc("/api/v1/telephony/sofia/profiles", s.telephony.HandleSofiaProfiles)
	s.mux.HandleFunc("/api/v1/telephony/gateways", s.telephony.HandleGateways)
	s.mux.HandleFunc("/api/v1/telephony/gateways/ping", s.telephony.HandlePingGateway)
	s.mux.HandleFunc("/api/v1/telephony/cdr", s.telephony.HandleCdr)
	s.mux.HandleFunc("/api/v1/telephony/cli/exec", s.telephony.HandleCliExec)
	s.mux.HandleFunc("/api/v1/telephony/ws/console-logs", s.wsLogs.HandleWS)
}

// handleExtensions 统一处理分机管理 (POST: 开户, DELETE: 销户, GET: 检查)
func (s *Server) handleExtensions(w http.ResponseWriter, r *http.Request) {
	setCORS(w)
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	switch r.Method {
	case http.MethodPost:
		var req ExtensionReq
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(BaseResponse{Code: 400, Error: "Invalid JSON request body: " + err.Error()})
			return
		}

		if strings.TrimSpace(req.Extension) == "" || strings.TrimSpace(req.Password) == "" {
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(BaseResponse{Code: 400, Error: "extension and password are required"})
			return
		}

		if req.Context == "" {
			req.Context = "default"
		}
		if req.Callgroup == "" {
			req.Callgroup = "default"
		}

		// 触发本地脚本写入 XML 并热重载
		log.Printf("📥 [HTTP] 收到分机开户请求: ext=%s, context=%s", req.Extension, req.Context)
		cmd := exec.Command("/bin/bash", s.cfg.ScriptPath, "create", req.Extension, req.Password, req.Context, req.Callgroup)
		out, err := cmd.CombinedOutput()
		if err != nil {
			log.Printf("❌ [HTTP] 分机开户执行脚本失败: %v, out: %s", err, string(out))
			w.WriteHeader(http.StatusInternalServerError)
			json.NewEncoder(w).Encode(BaseResponse{
				Code:  500,
				Error: fmt.Sprintf("Script execution failed: %v (%s)", err, strings.TrimSpace(string(out))),
			})
			return
		}

		log.Printf("✅ [HTTP] 分机开户成功: ext=%s", req.Extension)

		// 同步持久化至 PostgreSQL fs_extension 表 (确保 fswitch-web 可视化即时呈现)
		if s.repo != nil {
			epType := "SIP"
			if strings.HasPrefix(req.Extension, "901") {
				epType = "WebRTC"
			}
			_ = s.repo.CreateExtension(db.FsExtension{
				Extension:    req.Extension,
				Password:     req.Password,
				Context:      req.Context,
				Callgroup:    req.Callgroup,
				EndpointType: epType,
				IsEnabled:    true,
				Description:  "标准 SIP 分机 " + req.Extension,
			})
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(BaseResponse{
			Code:      200,
			Message:   "Extension created successfully",
			Extension: req.Extension,
		})

	case http.MethodDelete:
		ext := r.URL.Query().Get("extension")
		if ext == "" {
			// 支持 JSON Body 传入
			var req ExtensionReq
			_ = json.NewDecoder(r.Body).Decode(&req)
			ext = req.Extension
		}

		if strings.TrimSpace(ext) == "" {
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(BaseResponse{Code: 400, Error: "extension query param or body is required"})
			return
		}

		log.Printf("📥 [HTTP] 收到分机注销请求: ext=%s", ext)
		cmd := exec.Command("/bin/bash", s.cfg.ScriptPath, "delete", ext)
		out, err := cmd.CombinedOutput()
		if err != nil {
			log.Printf("❌ [HTTP] 分机删除执行脚本失败: %v, out: %s", err, string(out))
			w.WriteHeader(http.StatusInternalServerError)
			json.NewEncoder(w).Encode(BaseResponse{
				Code:  500,
				Error: fmt.Sprintf("Script execution failed: %v (%s)", err, strings.TrimSpace(string(out))),
			})
			return
		}

		// 同步从 PostgreSQL fs_extension 表删除
		if s.repo != nil {
			_ = s.repo.DeleteExtension(ext)
		}

		log.Printf("✅ [HTTP] 分机注销成功: ext=%s", ext)
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(BaseResponse{
			Code:      200,
			Message:   "Extension deleted successfully",
			Extension: ext,
		})

	case http.MethodGet:
		ext := r.URL.Query().Get("extension")
		if strings.TrimSpace(ext) == "" {
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(BaseResponse{Code: 400, Error: "extension query param is required"})
			return
		}

		cmd := exec.Command("/bin/bash", s.cfg.ScriptPath, "check", ext)
		out, err := cmd.CombinedOutput()
		if err != nil {
			w.WriteHeader(http.StatusNotFound)
			json.NewEncoder(w).Encode(BaseResponse{Code: 404, Extension: ext, Message: "Extension check returned error", Error: string(out)})
			return
		}

		var parsed map[string]interface{}
		_ = json.Unmarshal(out, &parsed)
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(BaseResponse{
			Code:      200,
			Message:   "success",
			Extension: ext,
			Data:      parsed,
		})

	default:
		w.WriteHeader(http.StatusMethodNotAllowed)
		json.NewEncoder(w).Encode(BaseResponse{Code: 405, Error: "Method not allowed"})
	}
}

// handleHealth 健康探活
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	fsAlive := s.eslClient.Ping()
	pgConnected := s.repo != nil && s.repo.Healthy()
	snap := s.gov.GetSnapshot()
	status := "UP"
	if !fsAlive || !pgConnected {
		status = "DEGRADED"
	}

	resp := map[string]interface{}{
		"status":          status,
		"node_id":         s.cfg.NodeID,
		"state":           string(snap.State),
		"fs_alive":        fsAlive,
		"pg_connected":    pgConnected,
		"active_channels": snap.ActiveChannels,
		"max_channels":    snap.MaxChannels,
	}

	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(resp)
}

// Start 启动 HTTP 监听
func (s *Server) Start() error {
	addr := s.cfg.HttpPort
	if !strings.HasPrefix(addr, ":") {
		addr = ":" + addr
	}
	log.Printf("🌐 [HTTP] Sidecar 同步管理接口监听在: http://0.0.0.0%s", addr)
	return http.ListenAndServe(addr, s.mux)
}
