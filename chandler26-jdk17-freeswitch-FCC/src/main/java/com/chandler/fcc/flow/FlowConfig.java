package com.chandler.fcc.flow;

import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowModelType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程编排规则库：定义不同业务模型在各通话阶段需要执行的动作链
 */
@Component
public class FlowConfig {

    private final Map<String, List<FlowNode>> flowMap = new ConcurrentHashMap<>();

    public FlowConfig() {
        initDefaultFlows();
    }

    private void initDefaultFlows() {
        String inboundModel = FlowModelType.INBOUND_CUSTOMER_SERVICE.name();

        // 1. 来电呼入流程 (INBOUND):
        // START 阶段: 播报 IVR 导航语音并收号 (驻留等待按键)
        addNode(inboundModel, CallStageState.START, FlowNode.builder()
                .modelKey(inboundModel)
                .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                .stageState(CallStageState.START)
                .actionKey("ivr-navigation")
                .actionType(ActionType.READ_DTMF)
                .order(1)
                .data(Map.of(
                        "soundFile", "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_navigation.wav",
                        "regex", "[1-3]",
                        "actionAfter", "park",
                        "prompt", "您好，欢迎致电客服热线。人工服务请按1，业务咨询请按2，投诉建议请按3。"
                ))
                .build());

        // ROUTE 阶段 (按键路由): 呼叫坐席 1007
        addNode(inboundModel, CallStageState.ROUTE, FlowNode.builder()
                .modelKey(inboundModel)
                .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                .stageState(CallStageState.ROUTE)
                .actionKey("dial-agent-1007")
                .actionType(ActionType.DIAL_AGENT)
                .order(1)
                .data(Map.of("agentExt", "1007"))
                .build());

        // CONNECTED 阶段 (双方接通): 开启录音
        addNode(inboundModel, CallStageState.CONNECTED, FlowNode.builder()
                .modelKey(inboundModel)
                .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                .stageState(CallStageState.CONNECTED)
                .actionKey("start-record-inbound")
                .actionType(ActionType.RECORD)
                .order(1)
                .data(Map.of("action", "START"))
                .build());

        // NORMAL_END 阶段: 停止录音
        addNode(inboundModel, CallStageState.NORMAL_END, FlowNode.builder()
                .modelKey(inboundModel)
                .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                .stageState(CallStageState.NORMAL_END)
                .actionKey("stop-record")
                .actionType(ActionType.RECORD)
                .order(1)
                .data(Map.of("action", "STOP"))
                .build());

        // 2. 双向外呼流程 (OUTBOUND_TWO_WAY_CALL):
        String outboundModel = FlowModelType.OUTBOUND_TWO_WAY_CALL.name();

        // START 阶段: 先呼叫坐席 1008
        addNode(outboundModel, CallStageState.START, FlowNode.builder()
                .modelKey(outboundModel)
                .modelType(FlowModelType.OUTBOUND_TWO_WAY_CALL)
                .stageState(CallStageState.START)
                .actionKey("dial-agent-first")
                .actionType(ActionType.DIAL_AGENT)
                .order(1)
                .data(Map.of("agentExt", "1008"))
                .build());

        // ROUTE 阶段 (坐席接听进入 Park): 呼叫客户 1007 并桥接
        addNode(outboundModel, CallStageState.ROUTE, FlowNode.builder()
                .modelKey(outboundModel)
                .modelType(FlowModelType.OUTBOUND_TWO_WAY_CALL)
                .stageState(CallStageState.ROUTE)
                .actionKey("dial-guest-second")
                .actionType(ActionType.DIAL_GUEST)
                .order(1)
                .data(Map.of("destNumber", "1007"))
                .build());

        // CONNECTED 阶段: 开启录音
        addNode(outboundModel, CallStageState.CONNECTED, FlowNode.builder()
                .modelKey(outboundModel)
                .modelType(FlowModelType.OUTBOUND_TWO_WAY_CALL)
                .stageState(CallStageState.CONNECTED)
                .actionKey("start-record-outbound")
                .actionType(ActionType.RECORD)
                .order(1)
                .data(Map.of("action", "START"))
                .build());
    }

    private void addNode(String modelKey, CallStageState stage, FlowNode node) {
        String key = modelKey + ":" + stage.name();
        flowMap.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
    }

    public List<FlowNode> getFlowNodes(CallStageState stage, String modelKey) {
        if (modelKey == null) {
            modelKey = FlowModelType.INBOUND_CUSTOMER_SERVICE.name();
        }
        String key = modelKey + ":" + stage.name();
        return flowMap.getOrDefault(key, Collections.emptyList());
    }
}
