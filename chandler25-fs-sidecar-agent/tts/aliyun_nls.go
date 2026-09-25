// Package tts provides controlled text-to-speech resolution for FNode media.
package tts

import (
	"crypto/hmac"
	"crypto/md5"
	"crypto/rand"
	"crypto/sha1"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"chandler25-fs-sidecar-agent/config"
	"github.com/gorilla/websocket"
)

// Resolver converts caller text into an audio file visible to FreeSWITCH.
type Resolver interface {
	Resolve(text, voice string) (string, error)
}

// Provider is the Sidecar Aliyun NLS TTS implementation.
type Provider struct {
	config       *config.Config
	httpClient   *http.Client
	localSynth   *LocalSynthesizer
	mu           sync.Mutex
	token        string
	tokenExpires time.Time
	inflight     map[string]chan result
}

type result struct {
	path string
	err  error
}

// NewProvider creates the configured provider.
func NewProvider(cfg *config.Config) Resolver {
	if cfg == nil || strings.EqualFold(cfg.TTSProvider, "disabled") || strings.EqualFold(cfg.TTSProvider, "none") {
		return nil
	}
	localSynth := NewLocalSynthesizer(cfg.TTSLocalVoice, cfg.AliyunNLSSampleRate)
	return &Provider{
		config:     cfg,
		httpClient: &http.Client{Timeout: 10 * time.Second},
		localSynth: localSynth,
		inflight:   make(map[string]chan result),
	}
}

func (p *Provider) isAliyunConfigured() bool {
	if p.config.AliyunNLSAppKey == "" {
		return false
	}
	if p.config.AliyunNLSToken != "" {
		return true
	}
	if p.config.AliyunNLSAccessKeyID != "" && p.config.AliyunNLSAccessKeySecret != "" {
		return true
	}
	return false
}

func textMD5(text string) string {
	h := md5.Sum([]byte(strings.TrimSpace(text)))
	return hex.EncodeToString(h[:])
}

// Resolve returns a deterministic cached path or synthesizes the text once.
func (p *Provider) Resolve(text, voice string) (string, error) {
	text = strings.TrimSpace(text)
	if text == "" {
		return "", errors.New("tts text is empty")
	}
	voice = strings.TrimSpace(voice)
	if voice == "" {
		voice = p.config.AliyunNLSVoice
	}

	// 1. 本地 MD5 缓存优先查找
	textKey := textMD5(text)
	fullKey := cacheKey(text, voice, p.config.AliyunNLSSampleRate, p.config.AliyunNLSSpeechRate)

	if path := p.cachedPath(textKey); path != "" {
		log.Printf("⚡ [TTS 缓存命中] text=%q -> file=%s", text, path)
		return path, nil
	}
	if path := p.cachedPath(fullKey); path != "" {
		log.Printf("⚡ [TTS 缓存命中] text=%q (voice=%s) -> file=%s", text, voice, path)
		return path, nil
	}

	p.mu.Lock()
	if wait, exists := p.inflight[fullKey]; exists {
		p.mu.Unlock()
		outcome := <-wait
		return outcome.path, outcome.err
	}
	wait := make(chan result, 1)
	p.inflight[fullKey] = wait
	p.mu.Unlock()

	outcome := result{}
	defer func() {
		p.mu.Lock()
		delete(p.inflight, fullKey)
		wait <- outcome
		close(wait)
		p.mu.Unlock()
	}()

	if path := p.cachedPath(textKey); path != "" {
		outcome.path = path
		return path, nil
	}
	if path := p.cachedPath(fullKey); path != "" {
		outcome.path = path
		return path, nil
	}

	targetPath := filepath.Join(p.workingDir(), textKey+".wav")

	// 2. 尝试阿里云在线 TTS 合成
	var aliyunErr error
	if p.isAliyunConfigured() {
		path, err := p.synthesize(text, voice, textKey)
		if err == nil && path != "" {
			log.Printf("☁️ [TTS 阿里云] 在线合成成功: text=%q -> file=%s", text, path)
			outcome.path = path
			return path, nil
		}
		aliyunErr = err
		log.Printf("⚠️ [TTS 阿里云] 在线合成失败 (%v)，启动本地 TTS 引擎降级合成...", err)
	} else {
		log.Printf("ℹ️ [TTS 阿里云] 未配置完整云端凭证，直接使用本地 TTS 引擎合成: text=%q", text)
	}

	// 3. 降级使用本地 TTS 引擎离线合成 (macOS say / Linux espeak)
	if p.localSynth != nil {
		if err := p.localSynth.Synthesize(text, targetPath); err == nil {
			log.Printf("💻 [TTS 本地降级] 离线合成成功: text=%q -> file=%s", text, targetPath)
			outcome.path = targetPath
			return targetPath, nil
		} else {
			log.Printf("⚠️ [TTS 本地降级] 本地合成失败: %v", err)
		}
	}

	// 4. 最终静态预置音兜底
	if fallback := findStaticSoundFallback(text); fallback != "" {
		log.Printf("📻 [TTS 静态兜底] 使用系统预置提示音: text=%q -> file=%s", text, fallback)
		outcome.path = fallback
		return fallback, nil
	}

	if aliyunErr != nil {
		outcome.err = fmt.Errorf("aliyun tts failed: %w", aliyunErr)
		return "", outcome.err
	}
	outcome.err = fmt.Errorf("all tts synthesizers failed for text: %s", text)
	return "", outcome.err
}

func (p *Provider) cachedPath(key string) string {
	path := filepath.Join(p.workingDir(), key+".wav")
	info, err := os.Stat(path)
	if err == nil && info.Size() > 44 {
		return path
	}
	return ""
}

func (p *Provider) synthesize(text, voice, key string) (string, error) {
	workDir := p.workingDir()
	if err := os.MkdirAll(workDir, 0o755); err != nil {
		return "", fmt.Errorf("create tts work directory: %w", err)
	}
	token, err := p.accessToken()
	if err != nil {
		return "", err
	}
	endpoint := strings.TrimRight(p.config.AliyunNLSGatewayURL, "/")

	if strings.HasPrefix(endpoint, "http://") || strings.HasPrefix(endpoint, "https://") {
		return p.synthesizeHTTP(text, voice, key, endpoint, token)
	}

	if strings.Contains(endpoint, "{token}") {
		endpoint = strings.ReplaceAll(endpoint, "{token}", url.PathEscape(token))
	} else if strings.Contains(endpoint, "?") {
		endpoint += "&token=" + url.QueryEscape(token)
	} else {
		endpoint += "?token=" + url.QueryEscape(token)
	}
	dialer := websocket.Dialer{HandshakeTimeout: time.Duration(p.config.AliyunNLSTimeoutSec) * time.Second}
	conn, _, err := dialer.Dial(endpoint, nil)
	if err != nil {
		return "", fmt.Errorf("connect aliyun nls: %w", err)
	}
	defer conn.Close()

	deadline := time.Now().Add(time.Duration(p.config.AliyunNLSTimeoutSec) * time.Second)
	_ = conn.SetReadDeadline(deadline)
	taskID := randomID()
	if err := writeMessage(conn, "StartSynthesis", taskID, p.config.AliyunNLSAppKey, map[string]any{
		"text":        text,
		"format":      "wav",
		"sample_rate": p.config.AliyunNLSSampleRate,
		"voice":       voice,
		"speech_rate": p.config.AliyunNLSSpeechRate,
		"pitch_rate":  0,
	}); err != nil {
		return "", fmt.Errorf("start aliyun nls synthesis: %w", err)
	}

	data, err := readAudio(conn)
	if err != nil {
		return "", err
	}
	tmp, err := os.CreateTemp(workDir, ".fcc-tts-*.wav")
	if err != nil {
		return "", fmt.Errorf("create tts temp file: %w", err)
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)
	if _, err = tmp.Write(data); err != nil {
		tmp.Close()
		return "", fmt.Errorf("write tts audio: %w", err)
	}
	if err = tmp.Sync(); err != nil {
		tmp.Close()
		return "", fmt.Errorf("sync tts audio: %w", err)
	}
	if err = tmp.Close(); err != nil {
		return "", fmt.Errorf("close tts audio: %w", err)
	}
	target := filepath.Join(workDir, key+".wav")
	if err = os.Rename(tmpName, target); err != nil {
		return "", fmt.Errorf("publish tts audio: %w", err)
	}
	_ = os.Chmod(target, 0o644)
	return target, nil
}

// workingDir returns the absolute shared directory used by Sidecar and FreeSWITCH.
func (p *Provider) workingDir() string {
	path, err := filepath.Abs(p.config.TTSWorkDir)
	if err != nil {
		return p.config.TTSWorkDir
	}
	return path
}

func (p *Provider) accessToken() (string, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.config.AliyunNLSToken != "" {
		return p.config.AliyunNLSToken, nil
	}
	if p.token != "" && time.Now().Before(p.tokenExpires.Add(-time.Minute)) {
		return p.token, nil
	}
	if p.config.AliyunNLSAccessKeyID == "" || p.config.AliyunNLSAccessKeySecret == "" {
		return "", errors.New("aliyun nls token or access key is not configured")
	}
	query := map[string]string{
		"AccessKeyId":      p.config.AliyunNLSAccessKeyID,
		"Action":           "CreateToken",
		"Format":           "JSON",
		"RegionId":         "cn-shanghai",
		"SignatureMethod":  "HMAC-SHA1",
		"SignatureNonce":   randomID(),
		"SignatureVersion": "1.0",
		"Timestamp":        time.Now().UTC().Format("2006-01-02T15:04:05Z"),
		"Version":          "2019-02-28",
	}
	canonical := canonicalQuery(query)
	stringToSign := "GET&%2F&" + percentEncode(canonical)
	h := hmac.New(sha1.New, []byte(p.config.AliyunNLSAccessKeySecret+"&"))
	_, _ = h.Write([]byte(stringToSign))
	query["Signature"] = base64.StdEncoding.EncodeToString(h.Sum(nil))
	requestURL := "https://nls-meta.cn-shanghai.aliyuncs.com/?" + canonicalQuery(query)
	request, err := http.NewRequest(http.MethodGet, requestURL, nil)
	if err != nil {
		return "", fmt.Errorf("build aliyun nls token request: %w", err)
	}
	response, err := p.httpClient.Do(request)
	if err != nil {
		return "", fmt.Errorf("request aliyun nls token: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode/100 != 2 {
		return "", fmt.Errorf("aliyun nls token status: %s", response.Status)
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, 1<<20))
	if err != nil {
		return "", fmt.Errorf("read aliyun nls token: %w", err)
	}
	var payload struct {
		Token struct {
			ID         string `json:"Id"`
			ExpireTime int64  `json:"ExpireTime"`
		} `json:"Token"`
	}
	if err := json.Unmarshal(body, &payload); err != nil || payload.Token.ID == "" {
		return "", errors.New("aliyun nls token response is invalid")
	}
	p.token = payload.Token.ID
	p.tokenExpires = time.Unix(payload.Token.ExpireTime, 0)
	if payload.Token.ExpireTime == 0 {
		p.tokenExpires = time.Now().Add(23 * time.Hour)
	}
	return p.token, nil
}

func writeMessage(conn *websocket.Conn, name, taskID, appKey string, payload any) error {
	return conn.WriteJSON(map[string]any{
		"header": map[string]any{
			"message_id": randomID(),
			"task_id":    taskID,
			"namespace":  "SpeechSynthesizer",
			"name":       name,
			"appkey":     appKey,
		},
		"payload": payload,
	})
}

func readAudio(conn *websocket.Conn) ([]byte, error) {
	var audio []byte
	for {
		kind, data, err := conn.ReadMessage()
		if err != nil {
			return nil, fmt.Errorf("read aliyun nls response: %w", err)
		}
		if kind == websocket.BinaryMessage {
			audio = append(audio, data...)
			continue
		}
		var message struct {
			Header struct {
				Name       string `json:"name"`
				Status     any    `json:"status"`
				StatusText string `json:"status_text"`
			} `json:"header"`
		}
		if json.Unmarshal(data, &message) != nil {
			continue
		}
		if message.Header.Name == "TaskFailed" || message.Header.Name == "SynthesisFailed" {
			return nil, fmt.Errorf("aliyun nls synthesis failed: %s", message.Header.StatusText)
		}
		if message.Header.Name == "TaskFinished" || message.Header.Name == "SynthesisCompleted" {
			break
		}
	}
	if len(audio) <= 44 {
		return nil, errors.New("aliyun nls returned empty audio")
	}
	return audio, nil
}

func (p *Provider) synthesizeHTTP(text, voice, key, endpoint, token string) (string, error) {
	workDir := p.workingDir()
	reqBody := map[string]any{
		"appkey":      p.config.AliyunNLSAppKey,
		"text":        text,
		"token":       token,
		"format":      "wav",
		"sample_rate": p.config.AliyunNLSSampleRate,
		"voice":       voice,
		"speech_rate": p.config.AliyunNLSSpeechRate,
	}
	jsonBytes, _ := json.Marshal(reqBody)
	req, err := http.NewRequest(http.MethodPost, endpoint, strings.NewReader(string(jsonBytes)))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("X-NLS-Token", token)
	}
	resp, err := p.httpClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return "", fmt.Errorf("http tts error status %d: %s", resp.StatusCode, string(body))
	}
	data, err := io.ReadAll(resp.Body)
	if err != nil || len(data) <= 44 {
		return "", fmt.Errorf("http tts returned empty audio or read error: %w", err)
	}
	tmp, err := os.CreateTemp(workDir, ".fcc-tts-*.wav")
	if err != nil {
		return "", err
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)
	if _, err = tmp.Write(data); err != nil {
		tmp.Close()
		return "", err
	}
	_ = tmp.Sync()
	_ = tmp.Close()
	target := filepath.Join(workDir, key+".wav")
	if err = os.Rename(tmpName, target); err != nil {
		return "", err
	}
	return target, nil
}

func findStaticSoundFallback(text string) string {
	soundDirs := []string{
		"/opt/homebrew/Cellar/freeswitch/1.11.3/share/freeswitch/sounds",
		"/usr/share/freeswitch/sounds",
	}
	for _, soundDir := range soundDirs {
		if _, err := os.Stat(soundDir); err != nil {
			continue
		}
		if strings.Contains(text, "成功") {
			p := filepath.Join(soundDir, "ivr_bind_success.wav")
			if _, err := os.Stat(p); err == nil {
				return p
			}
		}
		if strings.Contains(text, "失败") {
			p := filepath.Join(soundDir, "ivr_bind_fail.wav")
			if _, err := os.Stat(p); err == nil {
				return p
			}
		}
		if strings.Contains(text, "工号") || strings.Contains(text, "输入") {
			p := filepath.Join(soundDir, "ivr_bind_input.wav")
			if _, err := os.Stat(p); err == nil {
				return p
			}
		}
	}
	return ""
}

func cacheKey(text, voice string, sampleRate, speechRate int) string {
	h := md5.Sum([]byte(fmt.Sprintf("%s|%s|%d|%d", strings.TrimSpace(text), voice, sampleRate, speechRate)))
	return hex.EncodeToString(h[:])
}

func randomID() string {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return fmt.Sprintf("%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(bytes)
}

func canonicalQuery(values map[string]string) string {
	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	parts := make([]string, 0, len(keys))
	for _, key := range keys {
		parts = append(parts, percentEncode(key)+"="+percentEncode(values[key]))
	}
	return strings.Join(parts, "&")
}

func percentEncode(value string) string {
	encoded := url.QueryEscape(value)
	encoded = strings.ReplaceAll(encoded, "+", "%20")
	encoded = strings.ReplaceAll(encoded, "%7E", "~")
	return encoded
}
