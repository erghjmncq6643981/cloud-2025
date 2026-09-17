package com.chandler.fcc.flow.processor;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.flow.AbstractCallStageProcessor;
import com.chandler.fcc.flow.event.CallRouteEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 阶段处理器：路由与排队阶段 (ROUTE)
 * 当话道 Park 静默就绪后触发，驱动分配坐席或桥接
 */
@Slf4j
@Component
public class CallRouteProcessor extends AbstractCallStageProcessor implements ApplicationListener<CallRouteEvent> {

    @Override
    public CallStageState getCallStage() {
        return CallStageState.ROUTE;
    }

    @Override
    public void onApplicationEvent(CallRouteEvent event) {
        CallInfoBO call = event.getSource();
        String modelKey = call.getModelKey() != null ? call.getModelKey() : FlowModelType.INBOUND_CUSTOMER_SERVICE.name();

        log.info("🧭 [阶段流转 -> ROUTE] 话道就绪，执行路由排队. CallUUID: {}, Model: {}",
                call.getCallUuid(), modelKey);

        List<FlowNode> nodes = getFlowNodes(modelKey);
        for (FlowNode node : nodes) {
            executeAction(call, node);
        }
    }
}
