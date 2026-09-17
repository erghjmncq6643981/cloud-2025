package com.chandler.fcc.action.executor;

import com.chandler.fcc.action.AbstractFccActionExecutor;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.fcc.client.dto.FNodeRecordDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 动作：双向通话录音控制
 */
@Slf4j
@Component
public class RecordActionExecutor extends AbstractFccActionExecutor {

    @Override
    public ActionType getActionType() {
        return ActionType.RECORD;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        Map<String, String> data = flowNode.getData();
        String action = data.getOrDefault("action", "START");
        String path = data.getOrDefault("path", "/tmp/recordings/" + callUuid + ".wav");
        String ctrlUuid = data.getOrDefault("ctrlUuid", callUuid);

        log.info("🎙️ [FCC 执行动作: 通话录音] CallUUID: {}, Action: {}, Path: {}",
                callUuid, action, path);

        FNodeRecordDTO recordDTO = FNodeRecordDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(callUuid)
                .action(action)
                .path(path)
                .build();

        FNodeResult result = getFccClient().record(recordDTO);
        log.info("📥 [FCC 录音应答] Result: {}", result);
    }
}
