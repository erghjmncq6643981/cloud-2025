package com.chandler.fcc.action.executor;

import com.chandler.fcc.action.AbstractFccActionExecutor;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 动作：挂断通话（支持指定挂断目标、对端联动挂断或整通通话拆线）
 */
@Slf4j
@Component
public class HangupActionExecutor extends AbstractFccActionExecutor {

    @Override
    public ActionType getActionType() {
        return ActionType.HANGUP;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        Map<String, String> data = flowNode.getData();
        String cause = data.getOrDefault("cause", "NORMAL_CLEARING");
        String ctrlUuid = data.getOrDefault("ctrlUuid", callUuid);

        // 优先挂断指定的对端存活话道 (survivingUuid / peerUuid / targetUuid)
        String targetUuid = data.get("survivingUuid");
        if (targetUuid == null) {
            targetUuid = data.get("peerUuid");
        }
        if (targetUuid == null) {
            targetUuid = data.get("targetUuid");
        }
        if (targetUuid == null) {
            targetUuid = callUuid;
        }

        log.info("📴 [FCC 执行动作: 挂断通道] TargetUUID: {}, CtrlUUID: {}, Cause: {}", targetUuid, ctrlUuid, cause);
        FNodeResult result = getFccClient().hangup(ctrlUuid, targetUuid, cause);
        log.info("📥 [FCC 挂机应答] Result: {}", result);
    }
}
