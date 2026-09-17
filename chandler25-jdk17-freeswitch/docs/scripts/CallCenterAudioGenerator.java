package com.chandler.test.example;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 呼叫中心场景音频生成器 (阿里云 TTS)
 * 生成：1. 导航语音 2. 服务评价 3. 自动外呼通知 4. 坐席忙线提示
 */
public class CallCenterAudioGenerator {

    private static final Logger log = LoggerFactory.getLogger(CallCenterAudioGenerator.class);

    private final String appKey;
    private final NlsClient client;

    public CallCenterAudioGenerator(String appKey, String accessKeyId, String accessKeySecret, String url) throws IOException {
        this.appKey = appKey;
        AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
        accessToken.apply();
        if (accessToken.getToken() == null || accessToken.getToken().isBlank()) {
            throw new IllegalStateException("获取 NLS AccessToken 失败，请检查 AK/SK 配置！");
        }
        log.info("成功获取 NLS AccessToken: {}, 有效期至: {}", accessToken.getToken(), accessToken.getExpireTime());

        if (url == null || url.trim().isEmpty()) {
            this.client = new NlsClient(accessToken.getToken());
        } else {
            this.client = new NlsClient(url.trim(), accessToken.getToken());
        }
    }

    private SpeechSynthesizerListener createListener(File outputFile) {
        return new SpeechSynthesizerListener() {
            private FileOutputStream fout;

            @Override
            public void onMessage(ByteBuffer message) {
                try {
                    if (fout == null) {
                        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
                            outputFile.getParentFile().mkdirs();
                        }
                        fout = new FileOutputStream(outputFile);
                    }
                    byte[] bytes = new byte[message.remaining()];
                    message.get(bytes, 0, bytes.length);
                    fout.write(bytes);
                } catch (IOException e) {
                    log.error("写入音频文件异常: {}", e.getMessage(), e);
                }
            }

            @Override
            public void onComplete(SpeechSynthesizerResponse response) {
                try {
                    if (fout != null) {
                        fout.flush();
                        fout.close();
                    }
                } catch (IOException ignored) {
                }
                log.info("✅ 语音合成成功: taskId={}, 输出文件={}, 文件大小={} 字节",
                        response.getTaskId(), outputFile.getAbsolutePath(), outputFile.length());
            }

            @Override
            public void onFail(SpeechSynthesizerResponse response) {
                try {
                    if (fout != null) {
                        fout.close();
                    }
                } catch (IOException ignored) {
                }
                log.error("❌ 语音合成失败: taskId={}, status={}, statusText={}",
                        response.getTaskId(), response.getStatus(), response.getStatusText());
            }
        };
    }

    public void synthesizeWav(String text, String voice, File outputFile) {
        SpeechSynthesizer synthesizer = null;
        try {
            synthesizer = new SpeechSynthesizer(client, createListener(outputFile));
            synthesizer.setAppKey(appKey);
            // FreeSWITCH 最通用的电信级音频格式：WAV, 16000Hz (或 8000Hz)
            synthesizer.setFormat(OutputFormatEnum.WAV);
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            synthesizer.setVoice(voice != null ? voice : "siyue");
            synthesizer.setPitchRate(0);
            synthesizer.setSpeechRate(0);
            synthesizer.setText(text);

            long start = System.currentTimeMillis();
            synthesizer.start();
            synthesizer.waitForComplete();
            log.info("合成耗时: {} ms, 文本: [{}]", (System.currentTimeMillis() - start), text);
        } catch (Exception e) {
            log.error("合成发生异常: {}", e.getMessage(), e);
        } finally {
            if (synthesizer != null) {
                synthesizer.close();
            }
        }
    }

    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        // 从环境变量读取凭证，避免明文凭证泄露
        String appKey = System.getenv().getOrDefault("ALIYUN_NLS_APP_KEY", "YOUR_ALIYUN_APP_KEY");
        String accessKeyId = System.getenv().getOrDefault("ALIYUN_AK_ID", "YOUR_ALIYUN_AK_ID");
        String accessKeySecret = System.getenv().getOrDefault("ALIYUN_AK_SECRET", "YOUR_ALIYUN_AK_SECRET");
        String gatewayUrl = "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1";

        CallCenterAudioGenerator generator = new CallCenterAudioGenerator(appKey, accessKeyId, accessKeySecret, gatewayUrl);

        // 目标目录 1: FreeSWITCH demo 项目 resources/sounds
        File fsSoundsDir = new File("/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-freeswitch/src/main/resources/sounds");
        if (!fsSoundsDir.exists()) {
            fsSoundsDir.mkdirs();
        }

        // 目标目录 2: 当前工程 data/sounds
        File localSoundsDir = new File("/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-learning-assistant/data/sounds");
        if (!localSoundsDir.exists()) {
            localSoundsDir.mkdirs();
        }

        Map<String, String> materials = new LinkedHashMap<>();
        // 1. 导航语音 (Navigation IVR)
        materials.put("ivr_navigation.wav",
                "您好，欢迎致电客户服务中心。查话费请按一，业务办理请按二，投诉与建议请按三，人工服务请按零。");
        // 2. 服务评价 (Satisfaction Survey)
        materials.put("ivr_evaluation.wav",
                "感谢您的来电。请对本次通话服务进行评价：非常满意请按一，满意请按二，基本满意请按三，不满意请按四。感谢您的支持，再见！");
        // 3. 自动外呼 (Auto Dial Notification)
        materials.put("ivr_autodial.wav",
                "您好，这里是售后服务中心。通知您，您申请的业务已受理成功。确认办理请按一，咨询详情请按二，稍后请留意手机短信。祝您生活愉快，再见！");
        // 4. 坐席忙线提示音
        materials.put("ivr_busy.wav",
                "对不起，当前所有客服坐席均在通话中，请您稍后再拨，谢谢您的谅解。");

        for (Map.Entry<String, String> entry : materials.entrySet()) {
            String fileName = entry.getKey();
            String text = entry.getValue();
            File fsFile = new File(fsSoundsDir, fileName);
            File localFile = new File(localSoundsDir, fileName);

            log.info("🎙️ 正在生成音频材料: {} ...", fileName);
            generator.synthesizeWav(text, "siyue", fsFile);

            // 拷贝一份到 localSoundsDir
            if (fsFile.exists() && fsFile.length() > 0) {
                Files.copy(fsFile.toPath(), localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("📁 已同步拷贝到: {}", localFile.getAbsolutePath());
            }
        }

        generator.shutdown();
        log.info("🎉 全部音频材料生成完毕！");
    }
}
