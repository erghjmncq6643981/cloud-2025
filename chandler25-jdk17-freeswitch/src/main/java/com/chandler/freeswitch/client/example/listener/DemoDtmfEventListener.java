package com.chandler.freeswitch.client.example.listener;

import com.chandler.freeswitch.client.example.command.FreeSwitchCommandGateway;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demo DTMF 监听器：负责监听通话的接通事件和用户按键事件
 */
@Slf4j
@Component
@EslEventName({"CHANNEL_ANSWER", "DTMF"})
public class DemoDtmfEventListener implements EslEventHandler {

    @Autowired
    private FreeSwitchCommandGateway commandGateway;

    // 记录活跃的 IVR 任务：UUID -> 音频文件路径
    private final Map<String, String> activeIvrTasks = new ConcurrentHashMap<>();

    /**
     * 注册一个新的 IVR 任务
     */
    public void addIvrTask(String uuid, String audioFile) {
        activeIvrTasks.put(uuid, audioFile);
    }

    @Override
    public void handle(String addr, EslEvent event) {
        String eventName = event.getEventName();
        Map<String, String> headers = event.getEventHeaders();

        String uuid = headers.get("Unique-ID");
        if (uuid == null || !activeIvrTasks.containsKey(uuid)) {
            return; // 忽略非我们的业务通话
        }

        // 无视条件打印，证明已经被 Spring Boot 集成并接收到事件
        log.info("📢 [IVR监听器] 捕获到 IVR 专属事件: {}, UUID: {}", eventName, uuid);

        CompletableFuture.runAsync(() -> {
            try {
                processEvent(eventName, headers, uuid);
            } catch (Exception e) {
                log.error("IVR 业务处理失败", e);
            }
        });
    }

    private void processEvent(String eventName, Map<String, String> headers, String uuid) {
        if ("CHANNEL_ANSWER".equals(eventName)) {
            String audioFile = activeIvrTasks.get(uuid);
            log.info("📞 [IVR Demo] 通道 {} 已接通，准备播放语音: {}", uuid, audioFile);

            // 异步执行播放语音指令
            commandGateway.playAndGetDigitsThenPark(uuid, audioFile);
        } else if ("DTMF".equals(eventName)) {
            String digit = headers.get("DTMF-Digit");
            log.info("🎹 [IVR Demo] 通道 {} 监听到用户按键: {}", uuid, digit);

            // 监听到按键后，立即挂断该呼叫
            log.info("👋 [IVR Demo] 已触发按键挂断，下发挂断指令...");
            commandGateway.kill(uuid);

            // 任务结束，清理缓存
            activeIvrTasks.remove(uuid);
        }
    }
}
