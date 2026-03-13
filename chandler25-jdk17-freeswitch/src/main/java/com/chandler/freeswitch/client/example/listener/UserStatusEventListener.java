package com.chandler.freeswitch.client.example.listener;

import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import com.chandler.freeswitch.client.example.service.UserStatusService;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
@EslEventName("CUSTOM")
public class UserStatusEventListener implements EslEventHandler {

    @Autowired
    private UserStatusService userStatusService;

    @Override
    public void handle(String addr, EslEvent event) {
        try {
            String eventSubclassRaw = event.getEventHeaders().get("Event-Subclass");

            if (eventSubclassRaw != null) {
                // 防范 URL 编码陷阱！
                String eventSubclass = URLDecoder.decode(eventSubclassRaw, StandardCharsets.UTF_8.name());

                switch (eventSubclass) {
                    case "sofia::register":
                        handleSipRegister(event);
                        break;
                    case "sofia::unregister":
                        handleSipUnregister(event);
                        break;
                    case "sofia::expire":
                        handleSipExpire(event);
                        break;
                    default:
                        log.debug("Unhandled CUSTOM event subclass: {}", eventSubclass);
                }
            }
        } catch (Exception e) {
            log.error("Error handling CUSTOM event", e);
        }
    }

    private void handleSipRegister(EslEvent event) {
        try {
            String fromUser = event.getEventHeaders().get("from-user");
            String fromHost = event.getEventHeaders().get("from-host");
            String status = event.getEventHeaders().get("status");
            String networkIp = event.getEventHeaders().get("network-ip");

            log.info("🎉 [SIP REGISTER] 账号成功注册! 账号: {}@{}, 来源 IP: {}, 状态: {}",
                    fromUser, fromHost, networkIp, status);

            if (fromUser != null && !fromUser.isEmpty()) {
                UserStatus userStatus = UserStatus.builder()
                        .userId(fromUser)
                        .status(UserStatus.Status.REGISTERED.name())
                        .statusDescription("SIP已注册 - " + status)
                        .callerNumber(fromUser)
                        .updateTime(LocalDateTime.now())
                        .build();
                userStatusService.updateUserStatus(userStatus);
            }
        } catch (Exception e) {
            log.error("Error handling SIP register event", e);
        }
    }

    /**
     * 处理SIP注销事件
     */
    private void handleSipUnregister(EslEvent event) {
        try {
            String fromUser = event.getEventHeaders().get("from-user");
            String fromHost = event.getEventHeaders().get("from-host");
            String reason = event.getEventHeaders().get("reason");

            log.info("👋 [SIP UNREGISTER] 账号成功注销 - User: {}@{}, Reason: {}", fromUser, fromHost, reason);

            // 更新用户状态为注销
            if (fromUser != null && !fromUser.isEmpty()) {
                UserStatus userStatus = UserStatus.builder()
                        .userId(fromUser)
                        .status(UserStatus.Status.UNREGISTERED.name())
                        .statusDescription("SIP已注销 - " + (reason != null ? reason : "Unknown"))
                        .channelUuid(null)
                        .destinationNumber(null)
                        .callerNumber(fromUser)
                        .updateTime(LocalDateTime.now())
                        .build();

                userStatusService.updateUserStatus(userStatus);
            }

        } catch (Exception e) {
            log.error("Error handling SIP unregister event", e);
        }
    }

    /**
     * 处理SIP过期事件
     */
    private void handleSipExpire(EslEvent event) {
        try {
            String fromUser = event.getEventHeaders().get("from-user");
            String fromHost = event.getEventHeaders().get("from-host");

            log.info("⏳ [SIP EXPIRE] 账号注册过期 - User: {}@{}", fromUser, fromHost);

            if (fromUser != null && !fromUser.isEmpty()) {
                UserStatus userStatus = UserStatus.builder()
                        .userId(fromUser)
                        .status(UserStatus.Status.EXPIRED.name())
                        .statusDescription("SIP注册已过期")
                        .channelUuid(null)
                        .destinationNumber(null)
                        .callerNumber(fromUser)
                        .updateTime(LocalDateTime.now())
                        .build();

                userStatusService.updateUserStatus(userStatus);
            }
        } catch (Exception e) {
            log.error("Error handling SIP expire event", e);
        }
    }
}