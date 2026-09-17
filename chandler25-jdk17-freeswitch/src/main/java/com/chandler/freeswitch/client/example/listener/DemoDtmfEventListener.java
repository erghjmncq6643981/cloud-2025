package com.chandler.freeswitch.client.example.listener;

import com.chandler.freeswitch.client.example.command.FreeSwitchCommandGateway;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandResult;
import com.chandler.freeswitch.client.example.domain.dataobject.CallLeg;
import com.chandler.freeswitch.client.example.domain.dataobject.CallSession;
import com.chandler.freeswitch.client.example.domain.dataobject.CommandLog;
import com.chandler.freeswitch.client.example.service.*;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Demo DTMF 监听器：负责监听通话的接通事件和用户按键事件 (全量记录 EventLog, DtmfRecord, Timeline)
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
    @Autowired
    private CallEventLogService callEventLogService;
    @Autowired
    private CallTimelineService callTimelineService;
    @Autowired
    private DtmfRecordService dtmfRecordService;

    @Override
    public void handle(String addr, EslEvent event) {
        String eventName = event.getEventName();
        Map<String, String> headers = event.getEventHeaders();

        String uuid = headers.get("Unique-ID");
        CallLeg callLeg = uuid == null ? null : callLegService.getByUuid(uuid);
        if (callLeg == null || callLeg.getSessionId() == null) {
            return; // 忽略非我们的业务通话
        }

        // 仅处理 IVR 单通外呼/收号 (direction=2)，严格禁止介入双呼桥接 (direction=3) 等其他业务
        CallSession session = callSessionService.getById(callLeg.getSessionId());
        if (session == null || !Integer.valueOf(2).equals(session.getDirection())) {
            return;
        }

        // 无视条件打印，证明已经被 Spring Boot 集成并接收到事件
        log.info("📢 [IVR监听器] 捕获到 IVR 专属事件: {}, UUID: {}", eventName, uuid);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. 记录底层事件
                callEventLogService.recordEvent(
                        callLeg.getSessionId(),
                        null,
                        uuid,
                        eventName,
                        headers.get("Event-Subclass"),
                        headers.get("Hangup-Cause"),
                        headers
                );

                // 2. 业务处理
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

            callTimelineService.record(callLeg.getSessionId(), null, callLeg.getId(), uuid,
                    "LEG", "LEG_ANSWERED", "分机已接听，启动IVR放音收号: " + audioFile);

            // 开启带内音频按键检测器 (DSP)，确保即使客户端发送 In-band 纯音频按键也能被 FreeSWITCH 识别
            FreeSwitchCommandResult startDtmfResult = commandGateway.startDtmf(uuid);
            commandLogService.saveCommandResult(startDtmfResult);

            // 异步执行播放语音指令 (已内嵌 start_dtmf 双重保险)
            FreeSwitchCommandResult result = commandGateway.playAndGetDigitsThenPark(uuid, audioFile);
            commandLogService.saveCommandResult(result);
        } else if ("DTMF".equals(eventName)) {
            String digit = headers.get("DTMF-Digit");
            String durationStr = headers.get("DTMF-Duration");
            Integer durationMs = durationStr != null ? Integer.valueOf(durationStr) : null;
            log.info("🎹 [IVR Demo] 通道 {} 监听到用户按键: {}, 时长: {}ms", uuid, digit, durationMs);

            // 1. 记录到指令日志
            commandLogService.saveBusinessLog(uuid, CommandLogService.DTMF_DIGIT_COMMAND, digit, "RECEIVED", 1);

            // 2. 记录到专用按键明细表 dtmf_record
            dtmfRecordService.recordDigit(callLeg.getSessionId(), uuid, callLeg.getExtension(), digit, durationMs, "DEMO_IVR");

            // 3. 记录业务时间线
            callTimelineService.record(callLeg.getSessionId(), null, callLeg.getId(), uuid,
                    "MEDIA", "DTMF_RECEIVED", "监听到用户按键 [" + digit + "], 持续 " + durationMs + "ms");

            // 4. 监听到按键后，立即挂断该呼叫
            log.info("👋 [IVR Demo] 已触发按键挂断，下发挂断指令...");
            FreeSwitchCommandResult result = commandGateway.kill(uuid);
            commandLogService.saveCommandResult(result);

            callLegService.updateStatus(callLeg.getId(), "HANGUP");
            callSessionService.updateStatus(callLeg.getSessionId(), 2, "DTMF:" + digit);

            callTimelineService.record(callLeg.getSessionId(), null, callLeg.getId(), uuid,
                    "SESSION", "SESSION_ENDED", "按键采集完成，触发挂机结束通话 (DTMF: " + digit + ")");
        }
    }
}
