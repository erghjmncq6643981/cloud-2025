package config

import (
	"os"
	"strconv"
)

// Config 软交换一体化节点配置
type Config struct {
	NodeID                   string
	NatsURL                  string
	FSEslAddr                string
	FSEslPassword            string
	MaxChannels              int
	HeartbeatIntervalSec     int
	LogLevel                 string
	HttpPort                 string
	ScriptPath               string
	PostgresDSN              string
	DispatchIngressEnabled   bool
	TTSProvider              string
	TTSWorkDir               string
	AliyunNLSGatewayURL      string
	AliyunNLSAppKey          string
	AliyunNLSAccessKeyID     string
	AliyunNLSAccessKeySecret string
	AliyunNLSToken           string
	AliyunNLSVoice           string
	AliyunNLSSampleRate      int
	AliyunNLSSpeechRate      int
	AliyunNLSTimeoutSec      int
}

// LoadConfig 从环境变量加载配置，带生产级默认值
func LoadConfig() *Config {
	hostname, _ := os.Hostname()
	if hostname == "" {
		hostname = "telephony-pod-01"
	}

	return &Config{
		NodeID:                   getEnv("NODE_ID", hostname),
		NatsURL:                  getEnv("NATS_URL", "nats://127.0.0.1:4222"),
		FSEslAddr:                getEnv("FS_ESL_ADDR", "127.0.0.1:8021"),
		FSEslPassword:            getEnv("FS_ESL_PASSWORD", "ClueCon"),
		MaxChannels:              getEnvInt("MAX_CHANNELS", 1000),
		HeartbeatIntervalSec:     getEnvInt("HEARTBEAT_INTERVAL_SEC", 3),
		LogLevel:                 getEnv("LOG_LEVEL", "INFO"),
		HttpPort:                 getEnv("HTTP_PORT", "8088"),
		ScriptPath:               getEnv("EXTENSION_SCRIPT", "/opt/homebrew/etc/freeswitch/scripts/manage_extension.sh"),
		PostgresDSN:              getEnv("PG_DSN", "postgres://freeswitch:123456@127.0.0.1:5432/freeswitch?sslmode=disable"),
		DispatchIngressEnabled:   getEnvBool("DISPATCH_INGRESS_ENABLED", true),
		TTSProvider:              getEnv("TTS_PROVIDER", "aliyun_nls"),
		TTSWorkDir:               getEnv("TTS_WORK_DIR", "./work/tts"),
		AliyunNLSGatewayURL:      getEnv("ALIYUN_NLS_GATEWAY_URL", "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1"),
		AliyunNLSAppKey:          getEnv("ALIYUN_NLS_APP_KEY", ""),
		AliyunNLSAccessKeyID:     getEnv("ALIYUN_NLS_ACCESS_KEY_ID", ""),
		AliyunNLSAccessKeySecret: getEnv("ALIYUN_NLS_ACCESS_KEY_SECRET", ""),
		AliyunNLSToken:           getEnv("ALIYUN_NLS_TOKEN", ""),
		AliyunNLSVoice:           getEnv("ALIYUN_NLS_VOICE", "siyue"),
		AliyunNLSSampleRate:      getEnvInt("ALIYUN_NLS_SAMPLE_RATE", 16000),
		AliyunNLSSpeechRate:      getEnvInt("ALIYUN_NLS_SPEECH_RATE", -120),
		AliyunNLSTimeoutSec:      getEnvInt("ALIYUN_NLS_TIMEOUT_SEC", 15),
	}
}

func getEnvBool(key string, defaultVal bool) bool {
	if val := os.Getenv(key); val != "" {
		if boolVal, err := strconv.ParseBool(val); err == nil {
			return boolVal
		}
	}
	return defaultVal
}

func getEnv(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}

func getEnvInt(key string, defaultVal int) int {
	if val := os.Getenv(key); val != "" {
		if intVal, err := strconv.Atoi(val); err == nil {
			return intVal
		}
	}
	return defaultVal
}
