package com.chandler.fcc.flow.event;

import com.chandler.fcc.common.entity.CallInfoBO;
import org.springframework.context.ApplicationEvent;

public class CallStartEvent extends ApplicationEvent {
    public CallStartEvent(CallInfoBO source) {
        super(source);
    }

    @Override
    public CallInfoBO getSource() {
        return (CallInfoBO) super.getSource();
    }
}
