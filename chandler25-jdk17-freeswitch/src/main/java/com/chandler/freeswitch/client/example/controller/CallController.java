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
import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import com.chandler.freeswitch.client.example.service.UserStatusService;
import link.thingscloud.freeswitch.esl.InboundClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2026/3/13 13:47
 * @version 1.0.0
 * @since 1.8
 */
@RestController
@RequestMapping("/api/call")
public class CallController {
    private final static String fsAddr = "127.0.0.1:8022";
    @Autowired
    private InboundClient inboundClient;
    @Autowired
    private UserStatusService userStatusService;

    /**
     * 发起双人通话：1003 呼叫 1017
     * 访问地址示例：http://localhost:17680/api/call/bridge?a=1003&b=1017
     */
    @GetMapping("/bridge")
    public String bridgeCall(@RequestParam String a, @RequestParam String b) {
        String bizId = UUID.randomUUID().toString(); // 生成本次通话的唯一业务ID

        // 给 A 下发呼叫：带上 bizId，接听后进入 park (挂起状态)
        String cmdA = String.format("originate {my_biz_id=%s,my_role=caller}user/%s &park", bizId, a);
        // 给 B 下发呼叫
        String cmdB = String.format("originate {my_biz_id=%s,my_role=callee}user/%s &park", bizId, b);

        inboundClient.sendAsyncApiCommand(fsAddr, cmdA, null);
        inboundClient.sendAsyncApiCommand(fsAddr, cmdB, null);

        return "已下发双向呼叫，业务ID: " + bizId;
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
        inboundClient.sendAsyncApiCommand(fsAddr, "uuid_kill " + userStatus.getChannelUuid(), null);

        return "指令已下发：正在挂断分机 " + userId + " 的通道 " + userStatus.getChannelUuid();
    }
}