package governance

import (
	"fmt"
	"log"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"sync"
	"time"
)

// ESLCommandExecutor defines the minimum ESL API execution capability needed by the healer.
type ESLCommandExecutor interface {
	ExecuteAPI(command, args string) (string, error)
}

// NetworkHealer 负责监控并自动校准 FreeSWITCH 的网络监听 IP，
// 防止网络切换（如切换 Wi-Fi/热点）导致 Sofia SIP Profile 启动崩溃。
type NetworkHealer struct {
	eslClient  ESLCommandExecutor
	lastHealMu sync.Mutex
	lastHealAt time.Time
	lastHostIP string
}

// NewNetworkHealer 创建网络自愈器
func NewNetworkHealer(esl ESLCommandExecutor) *NetworkHealer {
	return &NetworkHealer{
		eslClient: esl,
	}
}

// DetectHostLANIP 探测本机在当前局域网内的真实物理 IPv4 地址。
// 优先排查 Clash TUN (198.18.x)、回环 (127.x)、链路本地 (169.254.x) 以及虚拟网卡。
func DetectHostLANIP() (string, error) {
	// 1. 如果显式配置了环境变量覆盖，直接使用
	if override := strings.TrimSpace(os.Getenv("FS_LOCAL_IP_OVERRIDE")); override != "" {
		if net.ParseIP(override) != nil {
			return override, nil
		}
	}

	// 2. 在 macOS (darwin) 下，优先使用系统命令直接获取物理网卡有效 IP
	if runtime.GOOS == "darwin" {
		if ip := detectMacOSLANIP(); ip != "" {
			return ip, nil
		}
	}

	// 3. 通用跨平台探测：遍历 net.Interfaces 筛选物理局域网网卡
	if ip := detectCrossPlatformLANIP(); ip != "" {
		return ip, nil
	}

	return "", fmt.Errorf("未能探测到有效的局域网 IPv4 地址")
}

// detectMacOSLANIP macOS 专用探测逻辑
func detectMacOSLANIP() string {
	// 尝试获取默认路由网卡
	out, err := exec.Command("route", "-n", "get", "default").Output()
	if err == nil {
		re := regexp.MustCompile(`interface:\s+(\w+)`)
		m := re.FindStringSubmatch(string(out))
		if len(m) >= 2 {
			iface := m[1]
			if !strings.HasPrefix(iface, "utun") && !strings.HasPrefix(iface, "tun") {
				if ip := getMacIfaceIP(iface); isValidLANIP(ip) {
					return ip
				}
			}
		}
	}

	// 依次探测物理 Wi-Fi / 以太网常用设备号
	for _, iface := range []string{"en0", "en1", "en2", "en3", "en4", "en5"} {
		if ip := getMacIfaceIP(iface); isValidLANIP(ip) {
			return ip
		}
	}
	return ""
}

func getMacIfaceIP(iface string) string {
	out, err := exec.Command("ipconfig", "getifaddr", iface).Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(out))
}

// detectCrossPlatformLANIP 跨平台网络接口遍历
func detectCrossPlatformLANIP() string {
	ifaces, err := net.Interfaces()
	if err != nil {
		return ""
	}

	var candidates []string
	for _, iface := range ifaces {
		// 忽略已关闭或回环接口
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		name := strings.ToLower(iface.Name)
		// 忽略虚拟网卡、VPN隧道、Docker、虚拟机等
		if strings.HasPrefix(name, "utun") || strings.HasPrefix(name, "tun") ||
			strings.HasPrefix(name, "tap") || strings.HasPrefix(name, "docker") ||
			strings.HasPrefix(name, "veth") || strings.HasPrefix(name, "br-") ||
			strings.HasPrefix(name, "bridge") || strings.HasPrefix(name, "vmnet") ||
			strings.HasPrefix(name, "vbox") || strings.HasPrefix(name, "llw") ||
			strings.HasPrefix(name, "awdl") {
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
			ip4 := ip.To4()
			if ip4 == nil {
				continue
			}
			ipStr := ip4.String()
			if isValidLANIP(ipStr) {
				candidates = append(candidates, ipStr)
			}
		}
	}

	// 优先级排序：192.168.x.x > 10.x.x.x > 172.16-31.x.x
	for _, ip := range candidates {
		if strings.HasPrefix(ip, "192.168.") {
			return ip
		}
	}
	for _, ip := range candidates {
		if strings.HasPrefix(ip, "10.") {
			return ip
		}
	}
	if len(candidates) > 0 {
		return candidates[0]
	}
	return ""
}

// isValidLANIP 检验是否为合法有效的局域网 IP
func isValidLANIP(ipStr string) bool {
	if ipStr == "" {
		return false
	}
	ip := net.ParseIP(ipStr)
	if ip == nil {
		return false
	}
	ip4 := ip.To4()
	if ip4 == nil {
		return false
	}
	// 排除 127.0.0.1、169.254.x.x (APIPA) 以及 198.18.x.x (Clash TUN 虚拟段)
	if ip4.IsLoopback() || ip4.IsLinkLocalUnicast() {
		return false
	}
	if ip4[0] == 198 && ip4[1] == 18 {
		return false
	}
	return true
}

// ReconcileAndHeal 检查并自动执行网络自愈。
// 返回: healed (是否执行了自愈动作), err (是否有严重错误)
func (h *NetworkHealer) ReconcileAndHeal() (bool, error) {
	h.lastHealMu.Lock()
	// 防抖，最少间隔 5 秒执行一次全量自愈
	if time.Since(h.lastHealAt) < 5*time.Second {
		h.lastHealMu.Unlock()
		return false, nil
	}
	h.lastHealMu.Unlock()

	hostIP, err := DetectHostLANIP()
	if err != nil {
		return false, fmt.Errorf("自愈失败: %w", err)
	}

	// 检查 FreeSWITCH 运行时配置
	confDir, err := h.eslClient.ExecuteAPI("global_getvar", "conf_dir")
	if err != nil || strings.HasPrefix(strings.TrimSpace(confDir), "-ERR") {
		return false, nil // FS 尚未准备就绪，跳过
	}
	confDir = strings.TrimSpace(confDir)

	currentFSIP, _ := h.eslClient.ExecuteAPI("global_getvar", "local_ip_v4")
	currentFSIP = strings.TrimSpace(currentFSIP)

	// 检查 Sofia profile internal 状态
	sofiaRaw, _ := h.eslClient.ExecuteAPI("sofia", "status profile internal")
	profileBroken := strings.Contains(strings.ToLower(sofiaRaw), "invalid profile") ||
		strings.HasPrefix(strings.TrimSpace(sofiaRaw), "-ERR")

	needsHealing := (currentFSIP != "" && currentFSIP != hostIP) || profileBroken
	if !needsHealing {
		return false, nil
	}

	h.lastHealMu.Lock()
	h.lastHealAt = time.Now()
	h.lastHostIP = hostIP
	h.lastHealMu.Unlock()

	log.Printf("🩺 [网络自愈] 触发自愈检查: FS当前IP=%s, 本机实际IP=%s, internal异常=%v", currentFSIP, hostIP, profileBroken)

	// 定位 vars.xml
	varsPath := filepath.Join(confDir, "vars.xml")
	if _, err := os.Stat(varsPath); os.IsNotExist(err) {
		// 备用路径查询
		for _, alt := range []string{
			"/opt/homebrew/Cellar/freeswitch/1.11.3/etc/freeswitch/vars.xml",
			"/opt/homebrew/etc/freeswitch/vars.xml",
			"/etc/freeswitch/vars.xml",
		} {
			if _, statErr := os.Stat(alt); statErr == nil {
				varsPath = alt
				break
			}
		}
	}

	// 1. 更新 vars.xml
	contentBytes, err := os.ReadFile(varsPath)
	if err != nil {
		return false, fmt.Errorf("读取 %s 失败: %w", varsPath, err)
	}
	content := string(contentBytes)

	reLocalIP := regexp.MustCompile(`(?m)(<X-PRE-PROCESS\s+cmd="set"\s+data="local_ip_v4=)[^"]*("/>)`)
	var newContent string
	if reLocalIP.MatchString(content) {
		newContent = reLocalIP.ReplaceAllString(content, `${1}`+hostIP+`${2}`)
	} else {
		// 如果未找到 local_ip_v4 覆盖行，在头部插入
		newContent = fmt.Sprintf("  <X-PRE-PROCESS cmd=\"set\" data=\"local_ip_v4=%s\"/>\n", hostIP) + content
	}

	if newContent != content {
		if err := os.WriteFile(varsPath, []byte(newContent), 0644); err != nil {
			return false, fmt.Errorf("写入 %s 失败: %w", varsPath, err)
		}
		log.Printf("📝 [网络自愈] 已自动更新 %s: local_ip_v4=%s", varsPath, hostIP)
	}

	// 2. 通过 ESL 通知 FreeSWITCH 热重载与重启 Profile
	_, _ = h.eslClient.ExecuteAPI("reloadxml", "")
	time.Sleep(1 * time.Second)

	_, _ = h.eslClient.ExecuteAPI("sofia", "profile internal start")
	_, _ = h.eslClient.ExecuteAPI("sofia", "profile external start")

	// 短暂等待并重试确保绑定成功（处理端口释放延迟）
	time.Sleep(2 * time.Second)
	checkRaw, _ := h.eslClient.ExecuteAPI("sofia", "status profile internal")
	if strings.Contains(strings.ToLower(checkRaw), "invalid profile") || strings.HasPrefix(strings.TrimSpace(checkRaw), "-ERR") {
		log.Printf("⏳ [网络自愈] Profile 启动稍有延迟，进行二次拉起...")
		time.Sleep(2 * time.Second)
		_, _ = h.eslClient.ExecuteAPI("sofia", "profile internal start")
		_, _ = h.eslClient.ExecuteAPI("sofia", "profile external start")
	}

	log.Printf("✅ [网络自愈] 自愈完成，FreeSWITCH 已绑定到本机新 IP: %s", hostIP)
	return true, nil
}
