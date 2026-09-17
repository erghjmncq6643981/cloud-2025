package com.chandler.fcc.action.executor;

import com.chandler.fcc.action.AbstractFccActionExecutor;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.fcc.client.dto.FNodeReadDTMFDTO;
import com.chandler.fcc.fcc.client.dto.MediaInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

/**
 * 动作：IVR放音与按键收号（支持满意度收集、导航菜单）
 */
@Slf4j
@Component
public class ReadDTMFActionExecutor extends AbstractFccActionExecutor {

    @Override
    public ActionType getActionType() {
        return ActionType.READ_DTMF;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        Map<String, String> data = flowNode.getData();
        String ctrlUuid = data.getOrDefault("ctrlUuid", callUuid);

        // 目标通道：优先对存活话道播放，其次是 callUuid
        String targetUuid = data.get("survivingUuid");
        if (targetUuid == null) {
            targetUuid = data.get("targetUuid");
        }
        if (targetUuid == null) {
            targetUuid = callUuid;
        }

        String actionAfter = data.getOrDefault("actionAfter", "hangup");
        String regex = data.getOrDefault("regex", "[1-4]");

        // 音频资源定位：优先检查本地音频文件
        String soundFile = data.get("soundFile");
        if (soundFile == null) {
            soundFile = "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_evaluation_prompt.wav";
        }
        String thankYouFile = data.get("thankYouFile");
        if (thankYouFile == null && !"park".equalsIgnoreCase(actionAfter)) {
            thankYouFile = "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_thankyou.wav";
        }

        String prompt = data.getOrDefault("prompt", "请按键输入");
        String mediaType = new File(soundFile).exists() ? "FILE" : "TEXT";
        String mediaData = "FILE".equals(mediaType) ? soundFile : prompt;

        log.info("🔢 [FCC 执行动作: 放音收号] TargetUUID: {}, ActionAfter: {}, Regex: {}, MediaData: {}, ThankYou: {}",
                targetUuid, actionAfter, regex, mediaData, thankYouFile);

        MediaInfo media = MediaInfo.builder()
                .type(mediaType)
                .data(mediaData)
                .engine("ali")
                .voice("aiqi")
                .build();

        int timeout = Integer.parseInt(data.getOrDefault("timeout", "2000"));
        int tries = Integer.parseInt(data.getOrDefault("tries", "2"));
        int digitTimeout = Integer.parseInt(data.getOrDefault("digitTimeout", "2000"));

        FNodeReadDTMFDTO dtmfDTO = FNodeReadDTMFDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(targetUuid)
                .media(media)
                .minDigits(1)
                .maxDigits(1)
                .tries(tries)
                .timeout(timeout)
                .digitTimeout(digitTimeout)
                .terminators("#")
                .thankYouFile(thankYouFile)
                .regex(regex)
                .actionAfter(actionAfter)
                .build();

        FNodeResult result = getFccClient().readDTMF(dtmfDTO);
        log.info("📥 [FCC 放音收号应答] Result: {}", result);
    }
}
