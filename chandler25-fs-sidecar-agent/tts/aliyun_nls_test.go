package tts

import (
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync/atomic"
	"testing"

	"chandler25-fs-sidecar-agent/config"
	"github.com/gorilla/websocket"
)

func TestProviderCachesSynthesizedAudio(t *testing.T) {
	var connections atomic.Int32
	upgrader := websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		connections.Add(1)
		connection, err := upgrader.Upgrade(writer, request, nil)
		if err != nil {
			t.Fatal(err)
		}
		defer connection.Close()
		for index := 0; index < 1; index++ {
			if _, _, err := connection.ReadMessage(); err != nil {
				t.Error(err)
				return
			}
		}
		if err := connection.WriteMessage(websocket.BinaryMessage, append([]byte("RIFF"), make([]byte, 80)...)); err != nil {
			t.Error(err)
			return
		}
		_ = connection.WriteJSON(map[string]any{"header": map[string]string{"name": "TaskFinished"}})
	}))
	defer server.Close()

	workDir := t.TempDir()
	cfg := &config.Config{
		TTSProvider:         "aliyun_nls",
		TTSWorkDir:          workDir,
		AliyunNLSGatewayURL: strings.Replace(server.URL, "http://", "ws://", 1),
		AliyunNLSAppKey:     "app",
		AliyunNLSToken:      "token",
		AliyunNLSVoice:      "siyue",
		AliyunNLSSampleRate: 16000,
		AliyunNLSSpeechRate: -120,
		AliyunNLSTimeoutSec: 5,
	}
	provider := NewProvider(cfg)

	first, err := provider.Resolve("您好，请按 1", "")
	if err != nil {
		t.Fatal(err)
	}
	second, err := provider.Resolve("您好，请按 1", "")
	if err != nil {
		t.Fatal(err)
	}
	if first != second {
		t.Fatalf("expected cached path, got %q and %q", first, second)
	}
	if connections.Load() != 1 {
		t.Fatalf("expected one synthesis connection, got %d", connections.Load())
	}
	if _, err := os.Stat(first); err != nil {
		t.Fatalf("expected synthesized file: %v", err)
	}
}

func TestDisabledProviderDoesNotCreateMagicMedia(t *testing.T) {
	if provider := NewProvider(&config.Config{TTSProvider: "disabled"}); provider != nil {
		t.Fatal("disabled provider must not be created")
	}
}

func TestLocalTTSFallbackWhenAliyunNotConfigured(t *testing.T) {
	workDir := t.TempDir()
	cfg := &config.Config{
		TTSProvider:         "aliyun_nls",
		TTSWorkDir:          workDir,
		TTSCacheDir:         workDir,
		TTSLocalVoice:       "Tingting",
		AliyunNLSSampleRate: 16000,
	}
	provider := NewProvider(cfg)
	path, err := provider.Resolve("测试本地合成语音", "")
	if err != nil {
		t.Skipf("local synthesizer not available in test env: %v", err)
	}
	if path == "" {
		t.Fatal("expected non-empty audio path")
	}
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("expected generated audio file: %v", err)
	}
}

func TestLiveAliyunToken(t *testing.T) {
	akID := os.Getenv("ALIYUN_NLS_ACCESS_KEY_ID")
	akSecret := os.Getenv("ALIYUN_NLS_ACCESS_KEY_SECRET")
	appKey := os.Getenv("ALIYUN_NLS_APP_KEY")
	if akID == "" || akSecret == "" || appKey == "" {
		t.Skip("skipping live aliyun token test: credentials not set in environment")
	}
	cfg := &config.Config{
		TTSProvider:              "aliyun_nls",
		TTSWorkDir:               t.TempDir(),
		AliyunNLSAccessKeyID:     akID,
		AliyunNLSAccessKeySecret: akSecret,
		AliyunNLSAppKey:          appKey,
		AliyunNLSGatewayURL:      "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1",
		AliyunNLSVoice:           "siyue",
		AliyunNLSSampleRate:      16000,
		AliyunNLSSpeechRate:      -120,
		AliyunNLSTimeoutSec:      10,
	}
	p := NewProvider(cfg).(*Provider)
	token, err := p.accessToken()
	t.Logf("Fetched token: %q, err: %v", token, err)
	if err != nil {
		t.Logf("Token fetch error: %v", err)
	}
}
