package com.chandler.fcc.flow.processor;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.flow.AbstractCallStageProcessor;
import com.chandler.fcc.flow.event.CallCallingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 阶段处理器：外呼中 (CALLING)
 */
@Slf4j
@Component
public class CallCallingProcessor extends AbstractCallStageProcessor implements ApplicationListener<CallCallingEvent> {

    @Override
    public CallStageState getCallStage() {
        return CallStageState.CALLING;
    }

    @Override
    public void onApplicationEvent(CallCallingEvent event) {
        CallInfoBO call = event.getSource();
        log.info("📞 [阶段流转 -> CALLING] CallUUID: {}, 被叫: {}",
                call.getCallUuid(), call.getDestinationNumber());
    }
}
