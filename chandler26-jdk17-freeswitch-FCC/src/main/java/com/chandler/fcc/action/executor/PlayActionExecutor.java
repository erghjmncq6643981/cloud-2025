package com.chandler.fcc.action.executor;

import com.chandler.fcc.action.AbstractFccActionExecutor;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.fcc.client.dto.FNodePlayDTO;
import com.chandler.fcc.fcc.client.dto.MediaInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 动作：语音播报
 */
@Slf4j
@Component
public class PlayActionExecutor extends AbstractFccActionExecutor {

    @Override
    public ActionType getActionType() {
        return ActionType.PLAY;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        Map<String, String> data = flowNode.getData();
        String text = data.getOrDefault("text", "您好，欢迎致电呼叫中心！");
        String type = data.getOrDefault("type", "TEXT");
        String ctrlUuid = data.getOrDefault("ctrlUuid", callUuid);

        log.info("🔊 [FCC 执行动作: 放音播报] CallUUID: {}, Type: {}, Content: {}",
                callUuid, type, text);

        MediaInfo media = MediaInfo.builder()
                .type(type)
                .data(text)
                .engine("ali")
                .voice("aiqi")
                .build();

        FNodePlayDTO playDTO = FNodePlayDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(callUuid)
                .media(media)
                .build();

        FNodeResult result = getFccClient().play(playDTO);
        log.info("📥 [FCC 放音应答] Result: {}", result);
    }
}
