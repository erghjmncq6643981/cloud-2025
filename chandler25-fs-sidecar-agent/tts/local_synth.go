package tts

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
)

// LocalSynthesizer 使用本地系统工具（如 macOS say、Linux espeak-ng/espeak）进行离线语音合成
type LocalSynthesizer struct {
	voice      string
	sampleRate int
}

func NewLocalSynthesizer(voice string, sampleRate int) *LocalSynthesizer {
	if voice == "" {
		voice = "Tingting"
	}
	if sampleRate <= 0 {
		sampleRate = 16000
	}
	return &LocalSynthesizer{
		voice:      voice,
		sampleRate: sampleRate,
	}
}

// Synthesize 离线合成文本并输出为标准 PCM WAV 文件
func (s *LocalSynthesizer) Synthesize(text, targetPath string) error {
	if err := os.MkdirAll(filepath.Dir(targetPath), 0755); err != nil {
		return err
	}

	tmpFile := targetPath + ".tmp.wav"
	defer os.Remove(tmpFile)

	// 1. 首选 macOS 原生高音质 say 引擎 (WAVE, 16bit LE, 16kHz)
	if sayPath, err := exec.LookPath("say"); err == nil && sayPath != "" {
		dataFormat := fmt.Sprintf("LEI16@%d", s.sampleRate)
		cmd := exec.Command(sayPath, "--file-format=WAVE", "--data-format="+dataFormat, "-v", s.voice, "-o", tmpFile, text)
		if output, err := cmd.CombinedOutput(); err == nil {
			info, err := os.Stat(tmpFile)
			if err == nil && info.Size() > 44 {
				return os.Rename(tmpFile, targetPath)
			}
		} else {
			// 如果指定声音名称失败，尝试直接让系统选用默认中文声音
			cmdDefault := exec.Command(sayPath, "--file-format=WAVE", "--data-format="+dataFormat, "-o", tmpFile, text)
			if _, errDefault := cmdDefault.CombinedOutput(); errDefault == nil {
				info, err := os.Stat(tmpFile)
				if err == nil && info.Size() > 44 {
					return os.Rename(tmpFile, targetPath)
				}
			}
			log.Printf("⚠️ [本地TTS] say 命令执行异常: %v (output: %s)", err, string(output))
		}
	}

	// 2. Linux 容器环境尝试 espeak-ng / espeak
	if espeakPath, err := exec.LookPath("espeak-ng"); err == nil && espeakPath != "" {
		cmd := exec.Command(espeakPath, "-v", "zh", "-w", tmpFile, text)
		if _, err := cmd.CombinedOutput(); err == nil {
			info, err := os.Stat(tmpFile)
			if err == nil && info.Size() > 44 {
				return os.Rename(tmpFile, targetPath)
			}
		}
	} else if espeakPath, err := exec.LookPath("espeak"); err == nil && espeakPath != "" {
		cmd := exec.Command(espeakPath, "-v", "zh", "-w", tmpFile, text)
		if _, err := cmd.CombinedOutput(); err == nil {
			info, err := os.Stat(tmpFile)
			if err == nil && info.Size() > 44 {
				return os.Rename(tmpFile, targetPath)
			}
		}
	}

	return fmt.Errorf("no available local TTS synthesizer (say/espeak) succeeded")
}
