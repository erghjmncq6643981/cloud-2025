// Package tts provides controlled text-to-speech resolution for FNode media.
package tts

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
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
	mu           sync.Mutex
	token        string
	tokenExpires time.Time
	inflight     map[string]chan result
}

type result struct {
	path string
	err  error
}

// NewProvider creates the configured provider. The disabled provider is nil so
// callers receive an explicit unavailable error instead of a FreeSWITCH magic
// say expression.
func NewProvider(cfg *config.Config) Resolver {
	if cfg == nil || strings.EqualFold(cfg.TTSProvider, "disabled") || strings.EqualFold(cfg.TTSProvider, "none") {
		return nil
	}
	if !strings.EqualFold(cfg.TTSProvider, "aliyun_nls") && !strings.EqualFold(cfg.TTSProvider, "aliyun") {
		return nil
	}
	return &Provider{
		config:     cfg,
		httpClient: &http.Client{Timeout: 10 * time.Second},
		inflight:   make(map[string]chan result),
	}
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
	key := cacheKey(text, voice, p.config.AliyunNLSSampleRate, p.config.AliyunNLSSpeechRate)
	if path := p.cachedPath(key); path != "" {
		return path, nil
	}

	p.mu.Lock()
	if wait, exists := p.inflight[key]; exists {
		p.mu.Unlock()
		outcome := <-wait
		return outcome.path, outcome.err
	}
	wait := make(chan result, 1)
	p.inflight[key] = wait
	p.mu.Unlock()

	outcome := result{}
	defer func() {
		p.mu.Lock()
		delete(p.inflight, key)
		wait <- outcome
		close(wait)
		p.mu.Unlock()
	}()

	path := p.cachedPath(key)
	if path != "" {
		outcome.path = path
		return path, nil
	}
	path, err := p.synthesize(text, voice, key)
	outcome.path = path
	outcome.err = err
	return path, err
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
	if err := os.MkdirAll(workDir, 0o750); err != nil {
		return "", fmt.Errorf("create tts work directory: %w", err)
	}
	token, err := p.accessToken()
	if err != nil {
		return "", err
	}
	endpoint := strings.TrimRight(p.config.AliyunNLSGatewayURL, "/")
	if strings.Contains(endpoint, "{token}") {
		endpoint = strings.ReplaceAll(endpoint, "{token}", url.PathEscape(token))
	} else {
		endpoint += "/" + url.PathEscape(token)
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
		"format":      "wav",
		"sample_rate": p.config.AliyunNLSSampleRate,
		"voice":       voice,
		"speech_rate": p.config.AliyunNLSSpeechRate,
		"pitch_rate":  0,
	}); err != nil {
		return "", fmt.Errorf("start aliyun nls synthesis: %w", err)
	}
	if err := writeMessage(conn, "RunSynthesis", taskID, p.config.AliyunNLSAppKey, map[string]any{"text": text}); err != nil {
		return "", fmt.Errorf("send aliyun nls text: %w", err)
	}
	if err := writeMessage(conn, "StopSynthesis", taskID, p.config.AliyunNLSAppKey, nil); err != nil {
		return "", fmt.Errorf("stop aliyun nls synthesis: %w", err)
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
		"Version":          "2018-05-18",
	}
	canonical := canonicalQuery(query)
	stringToSign := "GET&%2F&" + percentEncode(canonical)
	h := hmac.New(sha1.New, []byte(p.config.AliyunNLSAccessKeySecret+"&"))
	_, _ = h.Write([]byte(stringToSign))
	query["Signature"] = base64.StdEncoding.EncodeToString(h.Sum(nil))
	requestURL := "https://nls-meta.cn-shanghai.aliyuncs.com/pop/2018-05-18/tokens?" + canonicalQuery(query)
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
				Status     string `json:"status"`
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

func cacheKey(text, voice string, sampleRate, speechRate int) string {
	digest := sha256.Sum256([]byte(fmt.Sprintf("%d|%d|%s|%s", sampleRate, speechRate, voice, text)))
	return hex.EncodeToString(digest[:])
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
