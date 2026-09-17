package com.chandler.fcc.action.executor;

import com.chandler.fcc.action.AbstractFccActionExecutor;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.flow.CallSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 动作：话道桥接（将客户 Leg 与坐席 Leg 连通）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelBridgeActionExecutor extends AbstractFccActionExecutor {

    private final CallSessionManager sessionManager;

    @Override
    public ActionType getActionType() {
        return ActionType.CHANNEL_BRIDGE;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        Map<String, String> data = flowNode.getData();
        String ctrlUuid = data.getOrDefault("ctrlUuid", callUuid);
        String uuidA = data.getOrDefault("uuidA", data.get("guestChannelUuid"));
        String uuidB = data.getOrDefault("uuidB", data.get("agentChannelUuid"));

        if (uuidA == null || uuidB == null) {
            Optional<CallInfoBO> sessionOpt = sessionManager.getByCtrlUuid(ctrlUuid);
            if (sessionOpt.isPresent()) {
                CallInfoBO session = sessionOpt.get();
                if (uuidA == null) uuidA = session.getGuestChannelUuid();
                if (uuidB == null) uuidB = session.getAgentChannelUuid();
            }
        }

        if (uuidA == null || uuidB == null) {
            log.warn("⚠️ [FCC 桥接失败] 缺少桥接话道: uuidA={}, uuidB={}, CtrlUUID: {}", uuidA, uuidB, ctrlUuid);
            return;
        }

        log.info("🔗 [FCC 执行动作: 话道桥接] UUID A: {}, UUID B: {}, CtrlUUID: {}",
                uuidA, uuidB, ctrlUuid);

        FNodeResult result = getFccClient().channelBridge(ctrlUuid, uuidA, uuidB);
        log.info("📥 [FCC 桥接应答] Result: {}", result);
    }
}
