package com.chandler.fcc.action;

import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.ActionType;

public interface IFccAction {
    ActionType getActionType();
    void execute(String callUuid, String flowUuid, FlowNode flowNode);
}
