package config

import (
	"os"
	"strconv"
)

// Config 软交换一体化节点配置
type Config struct {
	NodeID               string
	NatsURL              string
	FSEslAddr            string
	FSEslPassword        string
	MaxChannels          int
	HeartbeatIntervalSec int
	LogLevel             string
}

// LoadConfig 从环境变量加载配置，带生产级默认值
func LoadConfig() *Config {
	hostname, _ := os.Hostname()
	if hostname == "" {
		hostname = "telephony-pod-01"
	}

	return &Config{
		NodeID:               getEnv("NODE_ID", hostname),
		NatsURL:              getEnv("NATS_URL", "nats://127.0.0.1:4222"),
		FSEslAddr:            getEnv("FS_ESL_ADDR", "127.0.0.1:8021"),
		FSEslPassword:        getEnv("FS_ESL_PASSWORD", "ClueCon"),
		MaxChannels:          getEnvInt("MAX_CHANNELS", 1000),
		HeartbeatIntervalSec: getEnvInt("HEARTBEAT_INTERVAL_SEC", 3),
		LogLevel:             getEnv("LOG_LEVEL", "INFO"),
	}
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
