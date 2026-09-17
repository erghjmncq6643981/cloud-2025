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
 * 动作：外呼客户
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DialGuestActionExecutor extends AbstractFccActionExecutor {

    private final CallSessionManager sessionManager;

    @Override
    public ActionType getActionType() {
        return ActionType.DIAL_GUEST;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        Map<String, String> data = flowNode.getData();
        String destNumber = data.getOrDefault("destNumber", "1007");
        String callerNumber = data.getOrDefault("callerNumber", "1008");
        String ctrlUuid = data.getOrDefault("ctrlUuid", callUuid);
        String guestCallUuid = data.getOrDefault("guestChannelUuid", data.getOrDefault("callUuid", callUuid));

        // 注册话道与控制会话绑定
        sessionManager.bindChannel(guestCallUuid, ctrlUuid);
        sessionManager.getByCtrlUuid(ctrlUuid).ifPresent(s -> s.setGuestChannelUuid(guestCallUuid));

        log.info("📞 [FCC 执行动作: 外呼客户] CallUUID: {}, DestNumber: {}, CtrlUUID: {}, GuestChannel: {}",
                callUuid, destNumber, ctrlUuid, guestCallUuid);

        Map<String, String> channelVars = new HashMap<>();
        channelVars.put("hangup_after_bridge", "false");
        channelVars.put("park_after_bridge", "true");
        channelVars.put("absolute_codec_string", "PCMU,PCMA");
        channelVars.put("liberal_dtmf", "true");

        String dialStr = destNumber.contains("/") ? destNumber : "user/" + destNumber;
        FNodeDialDTO.CallParam callParam = FNodeDialDTO.CallParam.builder()
                .dialString(dialStr)
                .cidName("CallCenter")
                .cidNumber(callerNumber)
                .uuid(guestCallUuid)
                .params(channelVars)
                .build();

        FNodeDialDTO dialDTO = FNodeDialDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(guestCallUuid)
                .destination(FNodeDialDTO.Destination.builder()
                        .callParams(Collections.singletonList(callParam))
                        .build())
                .timeout(30)
                .build();

        FNodeResult result = getFccClient().dial(dialDTO);
        log.info("📥 [FCC 外呼客户应答] Result: {}", result);
    }
}
