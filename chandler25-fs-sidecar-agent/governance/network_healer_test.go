package governance

import (
	"os"
	"strings"
	"testing"
)

type mockESLClient struct {
	vars     map[string]string
	profiles map[string]string
	calls    []string
}

func (m *mockESLClient) ExecuteAPI(cmd, args string) (string, error) {
	call := cmd + " " + args
	m.calls = append(m.calls, call)
	if cmd == "global_getvar" {
		return m.vars[strings.TrimSpace(args)], nil
	}
	if cmd == "sofia" && strings.HasPrefix(args, "status profile") {
		pName := strings.TrimPrefix(args, "status profile ")
		pName = strings.TrimSpace(pName)
		return m.profiles[pName], nil
	}
	return "+OK", nil
}

func TestDetectHostLANIP(t *testing.T) {
	ip, err := DetectHostLANIP()
	if err != nil {
		t.Fatalf("DetectHostLANIP failed: %v", err)
	}
	if ip == "" {
		t.Fatalf("expected non-empty IP")
	}
	if !isValidLANIP(ip) {
		t.Fatalf("expected valid LAN IP, got: %s", ip)
	}
	t.Logf("Detected LAN IP: %s", ip)
}

func TestIsValidLANIP(t *testing.T) {
	tests := []struct {
		ip    string
		valid bool
	}{
		{"127.0.0.1", false},
		{"169.254.1.1", false},
		{"198.18.0.1", false},
		{"192.168.3.132", true},
		{"10.0.0.15", true},
		{"172.20.10.2", true},
		{"", false},
		{"invalid", false},
	}

	for _, tt := range tests {
		got := isValidLANIP(tt.ip)
		if got != tt.valid {
			t.Errorf("isValidLANIP(%q) = %v, expected %v", tt.ip, got, tt.valid)
		}
	}
}

func TestNetworkHealerNoOpWhenHealthy(t *testing.T) {
	currentIP, err := DetectHostLANIP()
	if err != nil {
		t.Skip("No LAN IP detected, skipping test")
	}

	mock := &mockESLClient{
		vars: map[string]string{
			"conf_dir":    "/tmp",
			"local_ip_v4": currentIP,
		},
		profiles: map[string]string{
			"internal": "State: RUNNING (0)",
		},
	}

	healer := NewNetworkHealer(mock)
	healed, err := healer.ReconcileAndHeal()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if healed {
		t.Fatalf("expected no-op when already healthy")
	}
}

func TestNetworkHealerTriggerHealing(t *testing.T) {
	currentIP, err := DetectHostLANIP()
	if err != nil {
		t.Skip("No LAN IP detected, skipping test")
	}

	// 创建临时 vars.xml
	tmpDir := t.TempDir()
	varsFile := tmpDir + "/vars.xml"
	initialContent := `<X-PRE-PROCESS cmd="set" data="local_ip_v4=192.168.99.99"/>`
	if err := os.WriteFile(varsFile, []byte(initialContent), 0644); err != nil {
		t.Fatalf("failed to write temp vars.xml: %v", err)
	}

	mock := &mockESLClient{
		vars: map[string]string{
			"conf_dir":    tmpDir,
			"local_ip_v4": "192.168.99.99",
		},
		profiles: map[string]string{
			"internal": "-ERR Invalid Profile [internal]",
		},
	}

	healer := NewNetworkHealer(mock)
	healed, err := healer.ReconcileAndHeal()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !healed {
		t.Fatalf("expected healing to be performed")
	}

	// 检查 vars.xml 是否已更新为当前 IP
	updatedContent, _ := os.ReadFile(varsFile)
	if !strings.Contains(string(updatedContent), currentIP) {
		t.Fatalf("expected vars.xml to contain %s, got: %s", currentIP, string(updatedContent))
	}

	// 检查 mock 是否收到了 reloadxml 和 profile start 命令
	foundReload := false
	foundStart := false
	for _, call := range mock.calls {
		if strings.Contains(call, "reloadxml") {
			foundReload = true
		}
		if strings.Contains(call, "sofia profile internal start") {
			foundStart = true
		}
	}
	if !foundReload || !foundStart {
		t.Fatalf("expected reloadxml and profile start calls, got: %v", mock.calls)
	}
}
