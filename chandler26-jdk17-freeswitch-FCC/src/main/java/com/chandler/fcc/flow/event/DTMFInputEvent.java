package com.chandler.fcc.flow.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DTMFInputEvent extends ApplicationEvent {
    private final String ctrlUuid;
    private final String channelUuid;
    private final String digit;
    private final int durationMs;

    public DTMFInputEvent(Object source, String ctrlUuid, String channelUuid, String digit, int durationMs) {
        super(source);
        this.ctrlUuid = ctrlUuid;
        this.channelUuid = channelUuid;
        this.digit = digit;
        this.durationMs = durationMs;
    }
}
