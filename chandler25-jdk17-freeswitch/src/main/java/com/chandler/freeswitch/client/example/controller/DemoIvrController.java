package com.chandler.freeswitch.client.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import link.thingscloud.freeswitch.esl.InboundClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.chandler.freeswitch.client.example.listener.DemoIvrEventListener;

import java.util.UUID;

@Tag(name = "IVR Demo 接口")
@Slf4j
@RestController
@RequestMapping("/api/ivr")
public class DemoIvrController {

    private final static String fsAddr = "127.0.0.1:8022";

    @Autowired
    private InboundClient inboundClient;

    @Autowired
    private DemoIvrEventListener demoIvrEventListener;

    @Operation(summary = "发起IVR测试呼叫", description = "拨打指定分机号，接通后播放并监听按键")
    @GetMapping("/start")
    public String startIvrCall(
            @RequestParam String userId,
            @RequestParam(defaultValue = "/usr/local/freeswitch/sounds/en/us/callie/ivr/8000/ivr-welcome.wav") String audioFile) {
        
        String uuid = UUID.randomUUID().toString();
        log.info("🚀 准备发起 IVR 呼叫，目标分机: {}, 播放语音: {}, UUID: {}", userId, audioFile, uuid);

        // 注册到监听器中，以便正确过滤和处理事件
        demoIvrEventListener.addIvrTask(uuid, audioFile);

        // 语法：最小位数 最大位数 重试次数 超时(ms) 终止符 语音路径 错误路径 变量名 正则
//        String ivrArgs = "1 1 3 5000 # "+audioFile+" silence_stream://250 dtmf_input \\d+";

        String arg = String.format(
                "{origination_uuid=%s,absolute_codec_string=PCMU}user/%s &playback(local_stream://default)",
                uuid, userId
        );

        inboundClient.sendAsyncApiCommand(fsAddr, "originate", arg);

        log.info("指令:originate {}",arg);

        return "IVR 呼叫指令已下发，UUID: " + uuid + "，目标用户: " + userId;
    }
}
