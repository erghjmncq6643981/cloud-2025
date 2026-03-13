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
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@EslEventName("CUSTOM")
public class UserRegistrationListener implements EslEventHandler {

    @Autowired
    private UserStatusService userStatusService;

    @Override
    public void handle(String addr, EslEvent event) {
        // 1. 极速提取核心字段（必须在当前线程完成）
        String eventName = event.getEventName();
        String eventSubclassRaw = event.getEventHeaders().get("Event-Subclass");
        CompletableFuture.runAsync(()->{
            try {
                // 防范 URL 编码陷阱！
                String eventSubclass = URLDecoder.decode(eventSubclassRaw, StandardCharsets.UTF_8.name());

                if (eventSubclassRaw != null) {

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
        });
    }

    private void handleSipRegister(EslEvent event) {
        try {
            String fromUser = event.getEventHeaders().get("from-user");
            String fromHost = event.getEventHeaders().get("from-host");
            String status = event.getEventHeaders().get("status");
            String networkIp = event.getEventHeaders().get("network-ip");
            String userAgent = event.getEventHeaders().get("user-agent");

            log.info("🎉 [SIP REGISTER] 账号成功注册! 账号: {}@{}, 来源 IP: {}, 状态: {}",
                    fromUser, fromHost, networkIp, status);

            if (fromUser != null && !fromUser.isEmpty()) {
                UserStatus userStatus = UserStatus.builder()
                        .userId(fromUser)
                        .status("REGISTERED")
                        .statusDescription("SIP已注册 - " + status)
                        .networkIp(networkIp)
                        .userAgent(userAgent)
                        .callStatus("IDLE")
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

            if (fromUser != null && !fromUser.isEmpty()) {
                UserStatus userStatus = UserStatus.builder()
                        .userId(fromUser)
                        .status("UNREGISTERED")
                        .statusDescription("SIP已注销 - " + (reason != null ? reason : "Unknown"))
                        .channelUuid(null)
                        .callStatus("IDLE")
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
                        .status("EXPIRED")
                        .statusDescription("SIP注册已过期")
                        .channelUuid(null)
                        .callStatus("IDLE")
                        .build();

                userStatusService.updateUserStatus(userStatus);
            }
        } catch (Exception e) {
            log.error("Error handling SIP expire event", e);
        }
    }
}