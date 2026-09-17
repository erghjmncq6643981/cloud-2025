package com.chandler.fcc.action.executor;

import com.chandler.fcc.action.AbstractFccActionExecutor;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.fcc.client.dto.FNodeDialDTO;
import com.chandler.fcc.flow.CallSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 动作：呼叫坐席话机
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DialAgentActionExecutor extends AbstractFccActionExecutor {

    private final CallSessionManager sessionManager;

    @Override
    public ActionType getActionType() {
        return ActionType.DIAL_AGENT;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        Map<String, String> data = flowNode.getData();
        String agentExt = data.getOrDefault("agentExt", "1008");
        String callerNumber = data.getOrDefault("callerNumber", "1007");
        String ctrlUuid = data.getOrDefault("ctrlUuid", callUuid);
        String agentCallUuid = data.get("agentChannelUuid");
        if (agentCallUuid == null || agentCallUuid.trim().isEmpty()) {
            agentCallUuid = data.get("agentCallUuid");
        }
        if (agentCallUuid == null || agentCallUuid.trim().isEmpty()) {
            agentCallUuid = com.chandler.fcc.common.util.IdUtil.getCallUuid();
        }

        // 注册话道与控制会话绑定
        sessionManager.bindChannel(agentCallUuid, ctrlUuid);
        final String finalAgentUuid = agentCallUuid;
        sessionManager.getByCtrlUuid(ctrlUuid).ifPresent(s -> {
            s.setAgentChannelUuid(finalAgentUuid);
            s.getData().put("agentChannelUuid", finalAgentUuid);
        });

        log.info("📞 [FCC 执行动作: 呼叫坐席] CallUUID: {}, AgentExt: {}, CtrlUUID: {}, AgentChannel: {}",
                callUuid, agentExt, ctrlUuid, agentCallUuid);

        Map<String, String> channelVars = new HashMap<>();
        channelVars.put("hangup_after_bridge", "false");
        channelVars.put("park_after_bridge", "true");
        channelVars.put("absolute_codec_string", "PCMU,PCMA");
        channelVars.put("liberal_dtmf", "true");

        String dialStr = agentExt.contains("/") ? agentExt : "user/" + agentExt;
        FNodeDialDTO.CallParam callParam = FNodeDialDTO.CallParam.builder()
                .dialString(dialStr)
                .cidName("AgentCall")
                .cidNumber(callerNumber)
                .uuid(agentCallUuid)
                .params(channelVars)
                .build();

        FNodeDialDTO dialDTO = FNodeDialDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(agentCallUuid)
                .destination(FNodeDialDTO.Destination.builder()
                        .callParams(Collections.singletonList(callParam))
                        .build())
                .timeout(30)
                .build();

        FNodeResult result = getFccClient().dial(dialDTO);
        log.info("📥 [FCC 呼叫坐席应答] Result: {}", result);
    }
}
