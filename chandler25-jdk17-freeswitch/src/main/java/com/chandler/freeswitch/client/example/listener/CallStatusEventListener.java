package com.chandler.freeswitch.client.example.listener;

import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import com.chandler.freeswitch.client.example.service.UserStatusService;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通道状态监听器：负责监控通话生命周期 (A-leg & B-leg)
 */
@Slf4j
@Component
@EslEventName({"CUSTOM", "CHANNEL_CREATE", "CHANNEL_ANSWER", "CHANNEL_BRIDGE", "CHANNEL_HANGUP"})
public class CallStatusEventListener implements EslEventHandler {

    @Autowired
    private UserStatusService userStatusService;

    @Override
    public void handle(String addr, EslEvent event) {

        String eventName = event.getEventName();
        String uuid = event.getEventHeaders().get("Unique-ID");
        String direction = event.getEventHeaders().get("Call-Direction");

        // 核心：区分 A-leg 和 B-leg
        // 通常 A-leg 是 inbound, B-leg 是 outbound
        String otherUuid = event.getEventHeaders().get("Other-Leg-Unique-ID");
        String destination = event.getEventHeaders().get("Caller-Destination-Number");
        String caller = event.getEventHeaders().get("Caller-Caller-ID-Number");

        switch (eventName) {
            case "CHANNEL_CREATE":
                log.info("🐣 [创建] UUID: {}, 方向: {}, 目标: {}", uuid, direction, destination);
                updateUserStatus(caller, "TRYING", "正在发起呼叫", uuid);
                break;

            case "CHANNEL_PROGRESS":
            case "CHANNEL_PROGRESS_MEDIA":
                log.info("🔔 [振铃] UUID: {} 正在振铃 (Early Media)", uuid);
                updateUserStatus(caller, "RINGING", "分机振铃中", uuid);
                break;

            case "CHANNEL_ANSWER":
                log.info("🗣️ [接通] UUID: {} 已应答", uuid);
                updateUserStatus(caller, "ON_CALL", "通话中", uuid);
                break;

            case "CHANNEL_BRIDGE":
                log.info("🔗 [桥接] A: {} <-> B: {}", uuid, otherUuid);
                break;

            case "CHANNEL_UNBRIDGE":
                log.info("💔 [解桥] A: {} 与 B: {} 断开连接 (可能正在转接)", uuid, otherUuid);
                break;

            case "CHANNEL_HANGUP":
                String cause = event.getEventHeaders().get("Hangup-Cause");
                log.info("👋 [挂断] UUID: {}, 原因: {}", uuid, cause);
                updateUserStatus(caller, "REGISTERED", "通话结束: " + cause, null);
                break;

            case "CHANNEL_DESTROY":
                log.debug("♻️ [销毁] UUID: {} 资源已回收", uuid);
                break;
        }
    }

    private void updateUserStatus(String userId, String status, String desc, String uuid) {
        if (userId == null || "0000000000".equals(userId)) return;

        UserStatus userStatus = UserStatus.builder()
                .userId(userId)
                .status(status)
                .statusDescription(desc)
                .channelUuid(uuid)
                .updateTime(LocalDateTime.now())
                .build();

        userStatusService.updateUserStatus(userStatus);
        log.info("✅ 用户 {} 状态已更新为: {}", userId, status);
    }
}