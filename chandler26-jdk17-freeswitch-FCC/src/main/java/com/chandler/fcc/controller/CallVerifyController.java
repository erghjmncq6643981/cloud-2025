package com.chandler.fcc.controller;

import com.chandler.fcc.action.DefaultActionExecutorsManager;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowCtrlRecord;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.fcc.client.FccClient;
import com.chandler.fcc.fcc.client.dto.FNodeDialDTO;
import com.chandler.fcc.fcc.client.dto.FNodePlayDTO;
import com.chandler.fcc.fcc.client.dto.FNodeReadDTMFDTO;
import com.chandler.fcc.fcc.client.dto.FNodeRecordDTO;
import com.chandler.fcc.fcc.client.dto.MediaInfo;
import com.chandler.fcc.fcc.config.FccProperties;
import com.chandler.fcc.flow.CallSessionManager;
import com.chandler.fcc.flow.event.CallStartEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FCC 技术与功能验证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/fcc")
@RequiredArgsConstructor
public class CallVerifyController {

    private final FccClient fccClient;
    private final FccProperties fccProperties;
    private final ApplicationEventPublisher publisher;
    private final DefaultActionExecutorsManager handlersManager;
    private final CallSessionManager sessionManager;

    /**
     * 1. 节点健康与通信探活
     */
    @GetMapping("/status")
    public FNodeResult getStatus() {
        return fccClient.status();
    }

    /**
     * 2. 发起外呼流程验证 (指令/动作 + 流程 + FCC)
     */
    @PostMapping("/call/outbound")
    public Map<String, Object> triggerOutboundCall(
            @RequestParam(defaultValue = "1008") String agentExt,
            @RequestParam(defaultValue = "1007") String destNumber,
            @RequestParam(defaultValue = "true") boolean enableSurvey) {

        String ctrlUuid = IdUtil.getCtrlUuid(fccProperties.getCtrlUuidPrefix());
        String callUuid = IdUtil.getCallUuid();
        String agentCallUuid = IdUtil.getCallUuid();

        log.info("🎯 [测试触发外呼] CtrlUUID: {}, CallUUID(Guest): {}, AgentChannel: {}, AgentExt: {}, Dest: {}, EnableSurvey: {}",
                ctrlUuid, callUuid, agentCallUuid, agentExt, destNumber, enableSurvey);

        Map<String, String> data = new HashMap<>();
        data.put("agentExt", agentExt);
        data.put("destNumber", destNumber);
        data.put("ctrlUuid", ctrlUuid);
        data.put("callUuid", callUuid);
        data.put("guestChannelUuid", callUuid);
        data.put("agentChannelUuid", agentCallUuid);
        data.put("enableSurvey", String.valueOf(enableSurvey));

        CallInfoBO callInfo = CallInfoBO.builder()
                .callUuid(callUuid)
                .ctrlUuid(ctrlUuid)
                .agentChannelUuid(agentCallUuid)
                .guestChannelUuid(callUuid)
                .modelKey(FlowModelType.OUTBOUND_TWO_WAY_CALL.name())
                .direction(DirectionType.outbound)
                .stageState(CallStageState.START)
                .callerNumber(agentExt)
                .destinationNumber(destNumber)
                .data(data)
                .build();

        // 注册到会话管理器并绑定通道
        sessionManager.registerSession(callInfo);
        sessionManager.bindChannel(agentCallUuid, ctrlUuid);
        sessionManager.bindChannel(callUuid, ctrlUuid);

        // 发布启动事件，交由流程引擎调度
        publisher.publishEvent(new CallStartEvent(callInfo));

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "外呼流程已成功触发");
        resp.put("callUuid", callUuid);
        resp.put("ctrlUuid", ctrlUuid);
        resp.put("agentChannelUuid", agentCallUuid);
        return resp;
    }

    /**
     * 发起自动外呼通知流程验证 (AUTO_DIAL_NOTIFICATION)
     */
    @PostMapping("/call/notify")
    public Map<String, Object> triggerNotifyCall(
            @RequestParam(defaultValue = "1008") String destNumber,
            @RequestParam(defaultValue = "9000") String callerNumber) {

        String ctrlUuid = IdUtil.getCtrlUuid("fcc-notify");
        String callUuid = IdUtil.getCallUuid();

        log.info("📢 [测试触发自动通知] CtrlUUID: {}, CallUUID(Guest): {}, DestNumber: {}, CallerNumber: {}",
                ctrlUuid, callUuid, destNumber, callerNumber);

        Map<String, String> data = new HashMap<>();
        data.put("destNumber", destNumber);
        data.put("callerNumber", callerNumber);
        data.put("ctrlUuid", ctrlUuid);
        data.put("callUuid", callUuid);
        data.put("guestChannelUuid", callUuid);

        CallInfoBO callInfo = CallInfoBO.builder()
                .callUuid(callUuid)
                .ctrlUuid(ctrlUuid)
                .guestChannelUuid(callUuid)
                .modelKey(FlowModelType.AUTO_DIAL_NOTIFICATION.name())
                .direction(DirectionType.outbound)
                .stageState(CallStageState.START)
                .callerNumber(callerNumber)
                .destinationNumber(destNumber)
                .data(data)
                .build();

        sessionManager.registerSession(callInfo);
        sessionManager.bindChannel(callUuid, ctrlUuid);

        publisher.publishEvent(new CallStartEvent(callInfo));

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "自动外呼通知流程已成功触发");
        resp.put("callUuid", callUuid);
        resp.put("ctrlUuid", ctrlUuid);
        resp.put("destNumber", destNumber);
        return resp;
    }

    /**
     * 3. 手动挂机指令测试
     */
    @PostMapping("/call/hangup")
    public FNodeResult triggerHangup(
            @RequestParam String uuid,
            @RequestParam(required = false) String ctrlUuid,
            @RequestParam(defaultValue = "NORMAL_CLEARING") String cause) {
        return fccClient.hangup(ctrlUuid, uuid, cause);
    }

    /**
     * 4. 话道桥接测试
     */
    @PostMapping("/call/bridge")
    public FNodeResult triggerBridge(
            @RequestParam String uuidA,
            @RequestParam String uuidB,
            @RequestParam(required = false) String ctrlUuid) {
        return fccClient.channelBridge(ctrlUuid, uuidA, uuidB);
    }

    /**
     * 5. 放音测试 (支持音频文件或文本TTS)
     */
    @PostMapping("/call/play")
    public FNodeResult triggerPlay(
            @RequestParam String uuid,
            @RequestParam(defaultValue = "TEXT") String type,
            @RequestParam(defaultValue = "您好，欢迎致电客服热线") String content,
            @RequestParam(required = false) String ctrlUuid) {
        MediaInfo media = MediaInfo.builder()
                .type(type)
                .data(content)
                .build();
        FNodePlayDTO dto = FNodePlayDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(uuid)
                .media(media)
                .build();
        return fccClient.play(dto);
    }

    /**
     * 6. IVR 放音收号测试
     */
    @PostMapping("/call/read-dtmf")
    public FNodeResult triggerReadDTMF(
            @RequestParam String uuid,
            @RequestParam(defaultValue = "请给本次服务评价，满意请按1，不满意请按2") String prompt,
            @RequestParam(required = false) String ctrlUuid) {
        MediaInfo media = MediaInfo.builder()
                .type("TEXT")
                .data(prompt)
                .build();
        FNodeReadDTMFDTO dto = FNodeReadDTMFDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(uuid)
                .media(media)
                .minDigits(1)
                .maxDigits(1)
                .timeout(8000)
                .digitTimeout(2000)
                .terminators("#")
                .build();
        return fccClient.readDTMF(dto);
    }

    /**
     * 7. 录音启停测试
     */
    @PostMapping("/call/record")
    public FNodeResult triggerRecord(
            @RequestParam String uuid,
            @RequestParam(defaultValue = "START") String action,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String ctrlUuid) {
        if (path == null) {
            path = "/tmp/recordings/" + uuid + ".wav";
        }
        FNodeRecordDTO dto = FNodeRecordDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(uuid)
                .action(action)
                .path(path)
                .build();
        return fccClient.record(dto);
    }

    /**
     * 8. 查看流程执行轨迹 (Audit Trail)
     */
    @GetMapping("/flow/records/{callUuid}")
    public List<FlowCtrlRecord> getFlowRecords(@PathVariable String callUuid) {
        return handlersManager.getRecords(callUuid);
    }

    /**
     * 9. 模拟客户呼入客服中心流程 (迎宾语 -> 话道就绪 -> 坐席分配)
     */
    @PostMapping("/call/inbound-simulate")
    public Map<String, Object> simulateInboundCall(
            @RequestParam(defaultValue = "1007") String customerNumber,
            @RequestParam(defaultValue = "1008") String targetAgent) {

        String ctrlUuid = IdUtil.getCtrlUuid(fccProperties.getCtrlUuidPrefix());
        String callUuid = IdUtil.getCallUuid();

        log.info("🎯 [模拟客户呼入] CtrlUUID: {}, CallUUID: {}, Customer: {}, TargetAgent: {}",
                ctrlUuid, callUuid, customerNumber, targetAgent);

        Map<String, String> data = new HashMap<>();
        data.put("customerNumber", customerNumber);
        data.put("agentExt", targetAgent);
        data.put("ctrlUuid", ctrlUuid);
        data.put("callUuid", callUuid);
        data.put("guestChannelUuid", callUuid);

        CallInfoBO callInfo = CallInfoBO.builder()
                .callUuid(callUuid)
                .ctrlUuid(ctrlUuid)
                .guestChannelUuid(callUuid)
                .modelKey(FlowModelType.INBOUND_CUSTOMER_SERVICE.name())
                .direction(DirectionType.inbound)
                .stageState(CallStageState.START)
                .callerNumber(customerNumber)
                .destinationNumber("95598")
                .data(data)
                .build();

        sessionManager.registerSession(callInfo);
        sessionManager.bindChannel(callUuid, ctrlUuid);

        publisher.publishEvent(new CallStartEvent(callInfo));

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "客户呼入流程已成功触发");
        resp.put("callUuid", callUuid);
        resp.put("ctrlUuid", ctrlUuid);
        return resp;
    }

    /**
     * 10. 呼叫中心坐席工作台 - 呼叫转接接口 (Call Transfer)
     * 将正在通话中的客户话道转接给目标坐席/分机 (如 1017)
     */
    @PostMapping("/call/transfer")
    public Map<String, Object> transferCall(
            @RequestParam(required = false) String ctrlUuid,
            @RequestParam(defaultValue = "1017") String targetExt) {

        CallInfoBO callInfo = null;
        if (ctrlUuid != null && !ctrlUuid.trim().isEmpty()) {
            callInfo = sessionManager.getByCtrlUuid(ctrlUuid).orElse(null);
        } else {
            callInfo = sessionManager.getLatestActiveSession().orElse(null);
        }

        if (callInfo == null) {
            return Map.of("code", 404, "message", "未找到活跃的通话会话");
        }

        String actualCtrlUuid = callInfo.getCtrlUuid();
        String originalAgentUuid = callInfo.getAgentChannelUuid();
        String guestUuid = callInfo.getGuestChannelUuid();
        String targetAgentUuid = IdUtil.getCallUuid();

        log.info("🔀 [呼叫转接发起] CtrlUUID: {}, 原坐席: {}, 客户: {}, 目标分机: {}, 新坐席UUID: {}",
                actualCtrlUuid, originalAgentUuid, guestUuid, targetExt, targetAgentUuid);

        // 记录状态与上下文
        callInfo.getData().put("isTransferring", "true");
        callInfo.getData().put("originalAgentUuid", originalAgentUuid);
        callInfo.getData().put("transferTargetExt", targetExt);
        callInfo.getData().put("transferTargetAgentUuid", targetAgentUuid);

        // 1. 挂断原坐席话道 (让客户留在 park 静默/回铃等待)
        if (originalAgentUuid != null && !originalAgentUuid.trim().isEmpty()) {
            fccClient.hangup(actualCtrlUuid, originalAgentUuid, "NORMAL_CLEARING");
        }

        // 2. 绑定新坐席话道到当前 ctrlUuid
        sessionManager.bindChannel(targetAgentUuid, actualCtrlUuid);

        // 3. 构建呼叫目标分机参数
        Map<String, String> channelVars = new HashMap<>();
        channelVars.put("hangup_after_bridge", "false");
        channelVars.put("park_after_bridge", "true");
        channelVars.put("absolute_codec_string", "PCMU,PCMA");
        channelVars.put("liberal_dtmf", "true");

        String dialStr = targetExt.contains("/") ? targetExt : "user/" + targetExt;
        FNodeDialDTO.CallParam callParam = FNodeDialDTO.CallParam.builder()
                .dialString(dialStr)
                .cidName("TransferCall")
                .cidNumber(callInfo.getCallerNumber() != null ? callInfo.getCallerNumber() : "Transfer")
                .uuid(targetAgentUuid)
                .params(channelVars)
                .build();

        FNodeDialDTO dialDTO = FNodeDialDTO.builder()
                .ctrlUuid(actualCtrlUuid)
                .uuid(targetAgentUuid)
                .destination(FNodeDialDTO.Destination.builder()
                        .callParams(Collections.singletonList(callParam))
                        .build())
                .timeout(30)
                .build();

        fccClient.dial(dialDTO);

        // 4. 记录审计日志
        handlersManager.recordAudit(callInfo, "transfer-initiated", "TRANSFER", Map.of(
                "result", "INITIATED",
                "originalAgent", originalAgentUuid != null ? originalAgentUuid : "",
                "targetExt", targetExt,
                "targetAgentUuid", targetAgentUuid,
                "detail", "坐席工作台触发呼叫转接至目标分机: " + targetExt
        ));

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "呼叫转接已成功发起");
        resp.put("ctrlUuid", actualCtrlUuid);
        resp.put("originalAgentUuid", originalAgentUuid);
        resp.put("guestChannelUuid", guestUuid);
        resp.put("targetExt", targetExt);
        resp.put("targetAgentUuid", targetAgentUuid);
        return resp;
    }
}
