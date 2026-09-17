package com.chandler.fcc.flow;

import com.chandler.fcc.common.enums.CallStageState;

public interface ICallStageProcessor {
    CallStageState getCallStage();
}
