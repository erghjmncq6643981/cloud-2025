package com.chandler.freeswitch.client.example.listener;

import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import com.chandler.freeswitch.client.example.service.UserStatusService;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 通道状态监听器：负责监控通话生命周期 (A-leg & B-leg)
 */
@Slf4j
@Component
@EslEventName({"CHANNEL_CREATE", "CHANNEL_ANSWER", "CHANNEL_BRIDGE", "CHANNEL_UNBRIDGE", "CHANNEL_HANGUP", "CHANNEL_DESTROY"})
public class CallStatusEventListener implements EslEventHandler {

    @Autowired
    private UserStatusService userStatusService;

    @Override
    public void handle(String addr, EslEvent event) {
        // 1. 极速提取核心字段（必须在当前线程完成）
        String eventName = event.getEventName();
        Map<String, String> headers = event.getEventHeaders();
        // 第一行：无视任何条件，直接打印事件名！
        log.info("📢！！！【底层捕获】接收到 ESL 事件: {}", event.getEventName());

        // 2. 扔进异步任务（推荐使用 Spring 的 @Async 或 自定义 ExecutorService）
        CompletableFuture.runAsync(() -> {
            try {
                processBusiness(eventName, headers);
            } catch (Exception e) {
                log.error("业务处理失败", e);
            }
        });

    }

    private void processBusiness(String eventName, Map<String, String> headers) {

        String uuid = headers.get("Unique-ID");
        String direction = headers.get("Call-Direction");

        // 核心：区分 A-leg 和 B-leg
        // 通常 A-leg 是 inbound, B-leg 是 outbound
        String otherUuid = headers.get("Other-Leg-Unique-ID");
        String destination = headers.get("Caller-Destination-Number");
        String caller = headers.get("Caller-Caller-ID-Number");

        switch (eventName) {
            case "CHANNEL_CREATE":
                log.info("🐣 [创建] UUID: {}, 方向: {}, 目标: {}", uuid, direction, destination);
                updateUserStatus(caller, "TRYING", "正在发起呼叫", uuid, "RINGING");
                break;

            case "CHANNEL_PROGRESS":
            case "CHANNEL_PROGRESS_MEDIA":
                log.info("🔔 [振铃] UUID: {} 正在振铃 (Early Media)", uuid);
                updateUserStatus(caller, "RINGING", "分机振铃中", uuid, "RINGING");
                break;

            case "CHANNEL_ANSWER":
                log.info("🗣️ [接通] UUID: {} 已应答", uuid);
                updateUserStatus(caller, "ON_CALL", "通话中", uuid, "BUSY");
                break;

            case "CHANNEL_BRIDGE":
                log.info("🔗 [桥接] A: {} <-> B: {}", uuid, otherUuid);
                break;

            case "CHANNEL_UNBRIDGE":
                log.info("💔 [解桥] A: {} 与 B: {} 断开连接 (可能正在转接)", uuid, otherUuid);
                break;

            case "CHANNEL_HANGUP":
                String cause = headers.get("Hangup-Cause");
                log.info("👋 [挂断] UUID: {}, 原因: {}", uuid, cause);
                updateUserStatus(caller, "REGISTERED", "通话结束: " + cause, null, "IDLE");
                break;

            case "CHANNEL_DESTROY":
                log.debug("♻️ [销毁] UUID: {} 资源已回收", uuid);
                break;
        }
    }

    private void updateUserStatus(String userId, String status, String desc, String uuid, String callStatus) {
        if (userId == null || "0000000000".equals(userId)) return;

        UserStatus userStatus = UserStatus.builder()
                .userId(userId)
                .status(status)
                .statusDescription(desc)
                .channelUuid(uuid)
                .callStatus(callStatus)
                .build();

        userStatusService.updateUserStatus(userStatus);
        log.info("✅ 用户 {} 状态已更新为: {}, 通话状态: {}", userId, status, callStatus);
    }
}