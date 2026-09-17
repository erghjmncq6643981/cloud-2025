package com.chandler.fcc.flow.processor;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.flow.AbstractCallStageProcessor;
import com.chandler.fcc.flow.event.CallEndEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段处理器：通话结束阶段 (NORMAL_END)
 * 支持录音停止、服务评价 IVR 导流与对端联动挂机
 */
@Slf4j
@Component
public class CallEndProcessor extends AbstractCallStageProcessor implements ApplicationListener<CallEndEvent> {

    @Override
    public CallStageState getCallStage() {
        return CallStageState.NORMAL_END;
    }

    @Override
    public void onApplicationEvent(CallEndEvent event) {
        CallInfoBO call = event.getSource();
        String modelKey = call.getModelKey() != null ? call.getModelKey() : FlowModelType.INBOUND_CUSTOMER_SERVICE.name();

        log.info("🏁 [阶段流转 -> END] 通话结束. CallUUID: {}, 总时长: {}s, 计费: {}s, 原因: {}",
                call.getCallUuid(), call.getDuration(), call.getBillsec(), call.getHangupCause());

        // 1. 执行常规流程配置的动作 (如停止录音)
        List<FlowNode> nodes = getFlowNodes(modelKey);
        for (FlowNode node : nodes) {
            executeAction(call, node);
        }

        // 2. 检查存活对端话道：转服务评价 或 联动挂机
        String survivingUuid = call.getData() != null ? call.getData().get("survivingUuid") : null;
        if (survivingUuid != null && !survivingUuid.isEmpty()) {
            boolean enableSurvey = "true".equalsIgnoreCase(call.getData().getOrDefault("enableSurvey", "true"));
            if (enableSurvey) {
                log.info("⭐ [服务评价流程] 挂机后对端话道转入满意度评价: SurvivingUUID: {}", survivingUuid);
                FlowNode surveyNode = FlowNode.builder()
                        .actionType(ActionType.READ_DTMF)
                        .actionKey("survey-evaluation")
                        .order(10)
                        .data(new HashMap<>(Map.of(
                                "survivingUuid", survivingUuid,
                                "soundFile", "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_evaluation_prompt.wav",
                                "thankYouFile", "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_thankyou.wav",
                                "ctrlUuid", call.getCtrlUuid() != null ? call.getCtrlUuid() : ""
                        )))
                        .build();
                getHandlersManager().publish(call, surveyNode);
            } else {
                log.info("📴 [对端联动挂机] 自动挂断对端存活话道: SurvivingUUID: {}", survivingUuid);
                FlowNode hangupNode = FlowNode.builder()
                        .actionType(ActionType.HANGUP)
                        .actionKey("hangup-peer-surviving")
                        .order(10)
                        .data(new HashMap<>(Map.of(
                                "survivingUuid", survivingUuid,
                                "ctrlUuid", call.getCtrlUuid() != null ? call.getCtrlUuid() : "",
                                "cause", "NORMAL_CLEARING"
                        )))
                        .build();
                getHandlersManager().publish(call, hangupNode);
            }
        }
    }
}
