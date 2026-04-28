/*
 * chandler25-jdk17-freeswitch
 * 2026/3/13 13:47
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package com.chandler.freeswitch.client.example.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandGateway;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandResult;
import com.chandler.freeswitch.client.example.domain.dataobject.CallLeg;
import com.chandler.freeswitch.client.example.domain.dataobject.CallSession;
import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import com.chandler.freeswitch.client.example.service.CallLegService;
import com.chandler.freeswitch.client.example.service.CallSessionService;
import com.chandler.freeswitch.client.example.service.CommandLogService;
import com.chandler.freeswitch.client.example.service.UserStatusService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2026/3/13 13:47
 * @version 1.0.0
 * @since 1.8
 */
@Slf4j
@RestController
@RequestMapping("/api/call")
public class CallController {
    @Autowired
    private FreeSwitchCommandGateway commandGateway;
    @Autowired
    private UserStatusService userStatusService;
    @Autowired
    private CallSessionService callSessionService;
    @Autowired
    private CallLegService callLegService;
    @Autowired
    private CommandLogService commandLogService;

    /**
     * 发起双人通话：1003 呼叫 1017
     * 访问地址示例：http://localhost:17680/api/call/bridge?a=1003&b=1017
     */
    @GetMapping("/bridge")
    public String bridgeCall(@RequestParam String a, @RequestParam String b) {
        String bizId = UUID.randomUUID().toString(); // 生成本次通话的唯一业务ID

        commandGateway.originateUserToPark(a, UUID.randomUUID().toString(), Map.of(
                "my_biz_id", bizId,
                "my_role", "caller"
        ));
        commandGateway.originateUserToPark(b, UUID.randomUUID().toString(), Map.of(
                "my_biz_id", bizId,
                "my_role", "callee"
        ));

        return "已下发双向呼叫，业务ID: " + bizId;
    }

    @Operation(summary = "发起DTMF测试呼叫", description = "拨打指定分机号，接通后播放并监听按键")
    @GetMapping("/dtmf/start")
    public String startIvrCall(
            @RequestParam String userId,
            @RequestParam(defaultValue = "local_stream://default") String audioFile) {

        String uuid = UUID.randomUUID().toString();
        String bizId = "dtmf-" + UUID.randomUUID();
        Date now = new Date();
        log.info("🚀 准备发起 DTMF 呼叫，目标分机: {}, 播放语音: {}, UUID: {}, bizId: {}", userId, audioFile, uuid, bizId);

        CallSession callSession = CallSession.builder()
                .bizId(bizId)
                .direction(2)
                .caller("SYSTEM")
                .callee(userId)
                .status(0)
                .startTime(now)
                .build();
        callSessionService.save(callSession);

        CallLeg callLeg = CallLeg.builder()
                .sessionId(callSession.getId())
                .legType("a-leg")
                .uuid(uuid)
                .extension(userId)
                .status("ORIGINATING")
                .build();
        callLegService.save(callLeg);

        commandLogService.saveBusinessLog(uuid, CommandLogService.DTMF_AUDIO_FILE_COMMAND, audioFile, "ACCEPTED", 1);

        FreeSwitchCommandResult result = commandGateway.originateUserPlayback(userId, uuid, "local_stream://default");
        commandLogService.saveCommandResult(result);

        return "DTMF 呼叫指令已下发，sessionId: " + callSession.getId()
                + "，UUID: " + uuid
                + "，目标用户: " + userId;
    }

    /**
     * 方式 B：通过分机号挂断（更符合业务逻辑）
     */
    @DeleteMapping("/hangup-user/{userId}")
    public String hangupUser(@PathVariable String userId) {
        // 1. 从数据库查出该分机当前对应的活跃 UUID
        UserStatus userStatus = userStatusService.getOne(
                new LambdaQueryWrapper<UserStatus>()
                        .eq(UserStatus::getUserId, userId)
        );

        if (userStatus == null || StringUtils.isEmpty(userStatus.getChannelUuid())) {
            return "该用户当前没有活跃的通话通道";
        }

        // 2. 执行挂断
        commandGateway.kill(userStatus.getChannelUuid());

        return "指令已下发：正在挂断分机 " + userId + " 的通道 " + userStatus.getChannelUuid();
    }
}
