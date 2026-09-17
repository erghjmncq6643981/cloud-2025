package com.chandler.fcc.flow;

import com.chandler.fcc.action.DefaultActionExecutorsManager;
import com.chandler.fcc.common.entity.FlowNode;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import com.chandler.fcc.common.entity.CallInfoBO;

public abstract class AbstractCallStageProcessor implements ICallStageProcessor {

    @Autowired
    @Getter
    private DefaultActionExecutorsManager handlersManager;

    @Autowired
    @Getter
    private FlowConfig flowConfig;

    public void executeAction(CallInfoBO callInfo, FlowNode node) {
        handlersManager.publish(callInfo, node);
    }

    public void executeAction(String callUuid, FlowNode node) {
        handlersManager.publish(callUuid, node);
    }

    public List<FlowNode> getFlowNodes(String modelKey) {
        return flowConfig.getFlowNodes(getCallStage(), modelKey);
    }
}
