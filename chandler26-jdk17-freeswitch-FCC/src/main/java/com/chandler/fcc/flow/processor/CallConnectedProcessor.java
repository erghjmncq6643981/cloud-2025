package com.chandler.fcc.flow.processor;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.flow.AbstractCallStageProcessor;
import com.chandler.fcc.flow.event.CallConnectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 阶段处理器：双方接通阶段 (CONNECTED)
 */
@Slf4j
@Component
public class CallConnectedProcessor extends AbstractCallStageProcessor implements ApplicationListener<CallConnectedEvent> {

    @Override
    public CallStageState getCallStage() {
        return CallStageState.CONNECTED;
    }

    @Override
    public void onApplicationEvent(CallConnectedEvent event) {
        CallInfoBO call = event.getSource();
        String modelKey = call.getModelKey() != null ? call.getModelKey() : FlowModelType.INBOUND_CUSTOMER_SERVICE.name();

        log.info("🎉 [阶段流转 -> CONNECTED] 通话成功接通! CallUUID: {}, PeerUUID: {}",
                call.getCallUuid(), call.getAgentChannelUuid());

        List<FlowNode> nodes = getFlowNodes(modelKey);
        for (FlowNode node : nodes) {
            executeAction(call, node);
        }
    }
}
