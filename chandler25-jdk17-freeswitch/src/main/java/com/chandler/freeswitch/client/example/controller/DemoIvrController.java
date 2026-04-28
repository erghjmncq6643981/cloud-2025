package com.chandler.freeswitch.client.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.chandler.freeswitch.client.example.listener.DemoDtmfEventListener;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandGateway;

import java.util.UUID;

@Tag(name = "IVR Demo 接口")
@Slf4j
@RestController
@RequestMapping("/api/ivr")
public class DemoIvrController {

    @Autowired
    private FreeSwitchCommandGateway commandGateway;

    @Autowired
    private DemoDtmfEventListener demoDtmfEventListener;

    @Operation(summary = "发起IVR测试呼叫", description = "拨打指定分机号，接通后播放并监听按键")
    @GetMapping("/start")
    public String startIvrCall(
            @RequestParam String userId,
            @RequestParam(defaultValue = "/usr/local/freeswitch/sounds/en/us/callie/ivr/8000/ivr-welcome.wav") String audioFile) {
        
        String uuid = UUID.randomUUID().toString();
        log.info("🚀 准备发起 IVR 呼叫，目标分机: {}, 播放语音: {}, UUID: {}", userId, audioFile, uuid);

        // 注册到监听器中，以便正确过滤和处理事件
        demoDtmfEventListener.addIvrTask(uuid, audioFile);

        commandGateway.originateUserPlayback(userId, uuid, "local_stream://default");

        return "IVR 呼叫指令已下发，UUID: " + uuid + "，目标用户: " + userId;
    }
}
