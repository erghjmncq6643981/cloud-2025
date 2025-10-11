/*
 * chandler25-freeswitch
 * 2025/8/23 21:42
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package org.chandler25.freeswitch.esl;

import link.thingscloud.freeswitch.esl.constant.EventNames;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/8/23 21:42
 * @version 1.0.0
 * @since 1.8
 */
@Slf4j
@Component
@EslEventName(EventNames.HEARTBEAT)
public class HeartbeatEslEventHandler implements EslEventHandler {
    @Override
    public void handle(String addr, EslEvent event) {
        log.info("HeartbeatEslEventHandler handle addr[{}] EslEvent[{}].", addr, event);
    }
}