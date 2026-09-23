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
		for index := 0; index < 3; index++ {
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
