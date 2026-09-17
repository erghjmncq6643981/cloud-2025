package com.chandler.fcc.action;

import com.alibaba.fastjson2.JSON;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowCtrlRecord;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动作总控管理器：根据 FlowNode.ActionType 路由至具体处理器并记录执行轨迹
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultActionExecutorsManager {

    private final List<AbstractFccActionExecutor> handlers;
    // 内存存储通话动作执行流水 (生产环境可替换为 DAO 落表)
    private final Map<String, List<FlowCtrlRecord>> callFlowRecords = new ConcurrentHashMap<>();

    public void publish(CallInfoBO callInfo, FlowNode flowNode) {
        if (callInfo == null) {
            publish("", flowNode);
            return;
        }
        Map<String, String> mergedData = new HashMap<>();
        if (flowNode.getData() != null) {
            mergedData.putAll(flowNode.getData());
        }
        if (callInfo.getData() != null) {
            mergedData.putAll(callInfo.getData());
        }
        if (callInfo.getCtrlUuid() != null) {
            mergedData.putIfAbsent("ctrlUuid", callInfo.getCtrlUuid());
        }
        if (callInfo.getCallUuid() != null) {
            mergedData.putIfAbsent("callUuid", callInfo.getCallUuid());
        }
        if (callInfo.getCallerNumber() != null) {
            mergedData.putIfAbsent("callerNumber", callInfo.getCallerNumber());
        }
        if (callInfo.getDestinationNumber() != null) {
            mergedData.putIfAbsent("destNumber", callInfo.getDestinationNumber());
        }
        if (callInfo.getAgentChannelUuid() != null) {
            mergedData.putIfAbsent("agentChannelUuid", callInfo.getAgentChannelUuid());
            mergedData.putIfAbsent("uuidB", callInfo.getAgentChannelUuid());
        }
        if (callInfo.getGuestChannelUuid() != null) {
            mergedData.putIfAbsent("guestChannelUuid", callInfo.getGuestChannelUuid());
            mergedData.putIfAbsent("uuidA", callInfo.getGuestChannelUuid());
        }

        FlowNode execNode = FlowNode.builder()
                .modelKey(flowNode.getModelKey() != null ? flowNode.getModelKey() : callInfo.getModelKey())
                .modelType(flowNode.getModelType())
                .stageState(flowNode.getStageState() != null ? flowNode.getStageState() : callInfo.getStageState())
                .actionKey(flowNode.getActionKey())
                .actionType(flowNode.getActionType())
                .order(flowNode.getOrder())
                .data(mergedData)
                .build();

        publish(callInfo.getCallUuid(), execNode);
    }

    public void publish(String callUuid, FlowNode flowNode) {
        log.info("⚡ [动作调度] CallUUID: {}, ActionType: {}, Step: {}",
                callUuid, flowNode.getActionType(), flowNode.getActionKey());

        Optional<AbstractFccActionExecutor> handler = handlers.stream()
                .filter(h -> h.getActionType().equals(flowNode.getActionType()))
                .findFirst();

        if (handler.isEmpty()) {
            log.warn("⚠️ [动作未注册] 找不到对应的 FCC 动作处理器: {}", flowNode.getActionType());
            return;
        }

        String flowUuid = recordFlowStep(callUuid, flowNode);
        try {
            handler.get().execute(callUuid, flowUuid, flowNode);
        } catch (Exception e) {
            log.error("❌ [动作执行异常] CallUUID: {}, Action: {}, Error: {}",
                    callUuid, flowNode.getActionType(), e.getMessage(), e);
        }
    }

    private String recordFlowStep(String callUuid, FlowNode flowNode) {
        String flowUuid = IdUtil.getUuid();
        List<FlowCtrlRecord> records = callFlowRecords.computeIfAbsent(callUuid, k -> new ArrayList<>());

        FlowCtrlRecord record = FlowCtrlRecord.builder()
                .flowUuid(flowUuid)
                .callUuid(callUuid)
                .stepKey(flowNode.getActionKey())
                .stepType(flowNode.getActionType().name())
                .stepOrder(records.size() + 1)
                .modelKey(flowNode.getModelKey())
                .stageState(flowNode.getStageState() != null ? flowNode.getStageState().name() : "")
                .detail(JSON.toJSONString(flowNode.getData()))
                .createTime(LocalDateTime.now())
                .build();

        records.add(record);
        log.debug("📝 [动作轨迹落盘] 步骤 #{}: {}", record.getStepOrder(), record.getStepType());
        return flowUuid;
    }

    public void recordAudit(CallInfoBO callInfo, String stepKey, String actionType, Map<String, String> data) {
        String callUuid = callInfo != null ? callInfo.getCallUuid() : "";
        FlowNode node = FlowNode.builder()
                .actionKey(stepKey)
                .actionType(ActionType.valueOf(actionType))
                .modelKey(callInfo != null ? callInfo.getModelKey() : "")
                .stageState(callInfo != null ? callInfo.getStageState() : null)
                .data(data)
                .build();
        recordFlowStep(callUuid, node);
    }

    public List<FlowCtrlRecord> getRecords(String callUuid) {
        return callFlowRecords.getOrDefault(callUuid, Collections.emptyList());
    }
}
