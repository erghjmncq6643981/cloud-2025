/*
 * chandler25-jdk17-freeswitch
 * 2026/3/13 18:59
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package com.chandler.freeswitch.client.example.listener;

import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2026/3/13 18:59
 * @version 1.0.0
 * @since 1.8
 */
@Slf4j
@Component
@EslEventName({"RECORD_START", "RECORD_STOP"})
public class RecordEventListener implements EslEventHandler {

    @Override
    public void handle(String addr, EslEvent event) {
        String eventName = event.getEventName();
        Map<String, String> headers = event.getEventHeaders();

        // 核心字段提取
        String uuid = headers.get("Unique-ID");
        String filePath = headers.get("Record-File-Path");

        // 异步处理业务逻辑
        CompletableFuture.runAsync(() -> {
            if ("RECORD_START".equals(eventName)) {
                log.info("🎙️ [录音开始] 通道: {}, 文件路径: {}", uuid, filePath);
                // 业务逻辑：可以在数据库通话记录表中标记“录音中”并记录路径
            } else if ("RECORD_STOP".equals(eventName)) {
                log.info("⏹️ [录音结束] 通道: {}, 文件路径: {}", uuid, filePath);
                // 业务逻辑：更新数据库，记录录音结束时间，触发后续的文件上传或质检流程
            }
        });
    }
}