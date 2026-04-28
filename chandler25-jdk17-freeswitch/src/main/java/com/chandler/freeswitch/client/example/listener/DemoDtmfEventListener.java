package com.chandler.freeswitch.client.example.listener;

import com.chandler.freeswitch.client.example.command.FreeSwitchCommandGateway;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandResult;
import com.chandler.freeswitch.client.example.domain.dataobject.CallLeg;
import com.chandler.freeswitch.client.example.domain.dataobject.CommandLog;
import com.chandler.freeswitch.client.example.service.CallLegService;
import com.chandler.freeswitch.client.example.service.CallSessionService;
import com.chandler.freeswitch.client.example.service.CommandLogService;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Demo DTMF 监听器：负责监听通话的接通事件和用户按键事件
 */
@Slf4j
@Component
@EslEventName({"CHANNEL_ANSWER", "DTMF"})
public class DemoDtmfEventListener implements EslEventHandler {

    @Autowired
    private FreeSwitchCommandGateway commandGateway;
    @Autowired
    private CallLegService callLegService;
    @Autowired
    private CallSessionService callSessionService;
    @Autowired
    private CommandLogService commandLogService;

    @Override
    public void handle(String addr, EslEvent event) {
        String eventName = event.getEventName();
        Map<String, String> headers = event.getEventHeaders();

        String uuid = headers.get("Unique-ID");
        CallLeg callLeg = uuid == null ? null : callLegService.getByUuid(uuid);
        if (callLeg == null) {
            return; // 忽略非我们的业务通话
        }

        // 无视条件打印，证明已经被 Spring Boot 集成并接收到事件
        log.info("📢 [IVR监听器] 捕获到 IVR 专属事件: {}, UUID: {}", eventName, uuid);

        CompletableFuture.runAsync(() -> {
            try {
                processEvent(eventName, headers, callLeg);
            } catch (Exception e) {
                log.error("IVR 业务处理失败", e);
            }
        });
    }

    private void processEvent(String eventName, Map<String, String> headers, CallLeg callLeg) {
        String uuid = callLeg.getUuid();
        if ("CHANNEL_ANSWER".equals(eventName)) {
            CommandLog audioFileLog = commandLogService.getLatestByUuidAndCommandName(
                    uuid, CommandLogService.DTMF_AUDIO_FILE_COMMAND);
            String audioFile = audioFileLog == null ? "local_stream://default" : audioFileLog.getCommandArgs();
            log.info("📞 [IVR Demo] 通道 {} 已接通，准备播放语音: {}", uuid, audioFile);

            callLegService.updateStatus(callLeg.getId(), "ANSWERED");
            callSessionService.updateStatus(callLeg.getSessionId(), 1);

            // 异步执行播放语音指令
            FreeSwitchCommandResult result = commandGateway.playAndGetDigitsThenPark(uuid, audioFile);
            commandLogService.saveCommandResult(result);
        } else if ("DTMF".equals(eventName)) {
            String digit = headers.get("DTMF-Digit");
            log.info("🎹 [IVR Demo] 通道 {} 监听到用户按键: {}", uuid, digit);

            commandLogService.saveBusinessLog(uuid, CommandLogService.DTMF_DIGIT_COMMAND, digit, "RECEIVED", 1);

            // 监听到按键后，立即挂断该呼叫
            log.info("👋 [IVR Demo] 已触发按键挂断，下发挂断指令...");
            FreeSwitchCommandResult result = commandGateway.kill(uuid);
            commandLogService.saveCommandResult(result);

            callLegService.updateStatus(callLeg.getId(), "HANGUP");
            callSessionService.updateStatus(callLeg.getSessionId(), 2, "DTMF:" + digit);
        }
    }
}
