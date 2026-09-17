package com.chandler.fcc.flow.processor;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.flow.AbstractCallStageProcessor;
import com.chandler.fcc.flow.event.CallStartEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 阶段处理器：呼叫开始阶段 (START)
 */
@Slf4j
@Component
public class CallStartProcessor extends AbstractCallStageProcessor implements ApplicationListener<CallStartEvent> {

    @Override
    public CallStageState getCallStage() {
        return CallStageState.START;
    }

    @Override
    public void onApplicationEvent(CallStartEvent event) {
        CallInfoBO call = event.getSource();
        String modelKey = call.getModelKey();
        if (modelKey == null) {
            modelKey = call.getDirection() == DirectionType.inbound
                    ? FlowModelType.INBOUND_CUSTOMER_SERVICE.name()
                    : FlowModelType.OUTBOUND_TWO_WAY_CALL.name();
            call.setModelKey(modelKey);
        }

        log.info("🚀 [阶段流转 -> START] CallUUID: {}, Model: {}, Direction: {}",
                call.getCallUuid(), modelKey, call.getDirection());

        List<FlowNode> nodes = getFlowNodes(modelKey);
        for (FlowNode node : nodes) {
            executeAction(call, node);
        }
    }
}
