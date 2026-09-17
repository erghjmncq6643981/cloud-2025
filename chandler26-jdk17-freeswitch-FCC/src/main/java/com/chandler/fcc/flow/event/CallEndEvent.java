package com.chandler.fcc.flow.event;

import com.chandler.fcc.common.entity.CallInfoBO;
import org.springframework.context.ApplicationEvent;

public class CallEndEvent extends ApplicationEvent {
    public CallEndEvent(CallInfoBO source) {
        super(source);
    }

    @Override
    public CallInfoBO getSource() {
        return (CallInfoBO) super.getSource();
    }
}
