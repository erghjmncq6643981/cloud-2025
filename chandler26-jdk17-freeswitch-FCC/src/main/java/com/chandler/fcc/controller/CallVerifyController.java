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
}
