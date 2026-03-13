package com.chandler.freeswitch.client.example.listener;

import com.chandler.freeswitch.client.example.domain.dataobject.GatewayStatus;
import com.chandler.freeswitch.client.example.service.GatewayStatusService;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 网关状态事件监听器
 * 监听所有FreeSWITCH网关相关事件 (涵盖原生事件与 CUSTOM sofia 事件)
 *
 * @author chandler
 * @since 1.0
 */
@Slf4j
@Component
// 【改造点1】：同时订阅 CUSTOM 事件和原生的 GATEWAY 事件
@EslEventName({"CUSTOM", "GATEWAY_STATE", "GATEWAY_ADD", "GATEWAY_DEL"})
public class GatewayStatusEventListener implements EslEventHandler {

    @Autowired
    private GatewayStatusService gatewayStatusService;

    @Override
    public void handle(String addr, EslEvent event) {
        String eventName = event.getEventName();

        // 【关键调试点】：打印所有进入该处理器的事件，看看有没有 GATEWAY_STATE
        log.debug("🔍 监听到事件: {}, 来自: {}", eventName, addr);

        try {
            if ("CUSTOM".equals(eventName)) {
                handleCustomEvent(event);
            } else if (eventName.startsWith("GATEWAY_")) {
                // 如果是原生事件，直接处理，不检查 Event-Subclass
                handleNativeGatewayEvent(event);
            }
        } catch (Exception e) {
            log.error("处理网关事件异常", e);
        }
    }

    /**
     * 解析并处理 CUSTOM 类型的事件
     */
    private void handleCustomEvent(EslEvent event) throws Exception {
        String subRaw = event.getEventHeaders().get("Event-Subclass");
        if (subRaw == null) return;

        String subClass = URLDecoder.decode(subRaw, StandardCharsets.UTF_8.name());
        log.info("📡 收到 CUSTOM 模块事件: {}", subClass);

        switch (subClass) {
            case "sofia::gateway_state":
                // 如果你更信任自定义事件的数据，可以在这里也调用处理逻辑
                handleGatewayState(event);
                break;
            case "sofia::gateway_add":
                handleGatewayAdd(event);
                break;
            case "sofia::gateway_del":
                // 【改造点3】：因为 FreeSWITCH 会同时抛出原生事件，为了防止数据库重复更新，这里只做 DEBUG 记录
                log.debug("收到 sofia 模块底层网关事件 (已交由原生事件处理): {}", subClass);
                break;
            case "sofia::gateway_update":
                handleGatewayUpdate(event);
                break;
            case "sofia::gateway_substate":
                handleGatewaySubstate(event);
                break;
            case "sofia::gateway_ping":
                handleGatewayPing(event);
                break;
            default:
                // 忽略非网关的 CUSTOM 事件
                break;
        }
    }

    /**
     * 解析并处理 原生 GATEWAY 类型的事件
     */
    private void handleNativeGatewayEvent(EslEvent event) {
        log.info("🌐 收到 FreeSWITCH 原生网关事件: {}", event.getEventName());
        handleGatewayState(event);
    }

    /**
     * 处理网关状态事件
     */
    private void handleGatewayState(EslEvent event) {
        try {
            String gatewayName = event.getEventHeaders().get("Gateway");
            String state = event.getEventHeaders().get("State");
            log.info("📍 网关状态明细 -> 名: {}, 状态: {}", gatewayName, state);
            String register = event.getEventHeaders().get("Register");
            String pingStatus = event.getEventHeaders().get("Ping-Status");
            String pingTime = event.getEventHeaders().get("Ping-Time");
            String networkIp = event.getEventHeaders().get("Network-IP");
            String networkPort = event.getEventHeaders().get("Network-Port");

            log.info("🌐 [GATEWAY STATE] 网关状态变更");
            log.info("  - 网关名称: {}", gatewayName);
            log.info("  - 状态: {}", state);
            log.info("  - 注册状态: {}", register);
            log.info("  - Ping状态: {}", pingStatus);
            log.info("  - Ping时间: {}", pingTime);
            log.info("  - 网络地址: {}:{}", networkIp, networkPort);

            if (gatewayName != null && state != null) {
                GatewayStatus.Status gatewayStatusEnum = mapGatewayState(state);

                GatewayStatus gatewayStatus = GatewayStatus.builder()
                        .gatewayName(gatewayName)
                        .status(gatewayStatusEnum.name())
                        .statusDescription(gatewayStatusEnum.getDescription() + " - " + state)
                        .registerStatus(register)
                        .pingStatus(pingStatus)
                        .pingTime(pingTime)
                        .networkIp(networkIp)
                        .networkPort(networkPort)
                        .updateTime(LocalDateTime.now())
                        .build();

                gatewayStatusService.updateGatewayStatus(gatewayStatus);
                log.info("✅ 网关状态已更新入库 - 网关: {}, 状态: {}", gatewayName, state);
            }

        } catch (Exception e) {
            log.error("Error handling GATEWAY_STATE event", e);
        }
    }

    /**
     * 处理网关添加事件
     */
    private void handleGatewayAdd(EslEvent event) {
        try {
            String gatewayName = event.getEventHeaders().get("Gateway");
            String profile = event.getEventHeaders().get("Profile");
            String domain = event.getEventHeaders().get("Domain");
            String fromDomain = event.getEventHeaders().get("From-Domain");
            String toDomain = event.getEventHeaders().get("To-Domain");

            log.info("➕ [GATEWAY ADD] 网关添加");
            log.info("  - 网关名称: {}", gatewayName);
            log.info("  - 配置文件: {}", profile);
            log.info("  - 域名: {}", domain);

            if (gatewayName != null) {
                GatewayStatus gatewayStatus = GatewayStatus.builder()
                        .gatewayName(gatewayName)
                        .status(GatewayStatus.Status.REGISTERING.name())
                        .statusDescription("网关已加载，等待注册...")
                        .profile(profile)
                        .domain(domain)
                        .fromDomain(fromDomain)
                        .toDomain(toDomain)
                        .updateTime(LocalDateTime.now())
                        .build();

                gatewayStatusService.updateGatewayStatus(gatewayStatus);
            }

        } catch (Exception e) {
            log.error("Error handling GATEWAY_ADD event", e);
        }
    }

    /**
     * 处理网关删除事件
     */
    private void handleGatewayDel(EslEvent event) {
        try {
            String gatewayName = event.getEventHeaders().get("Gateway");
            String profile = event.getEventHeaders().get("Profile");
            String reason = event.getEventHeaders().get("Reason");

            log.info("➖ [GATEWAY DEL] 网关删除 - 网关: {}", gatewayName);

            if (gatewayName != null) {
                GatewayStatus gatewayStatus = GatewayStatus.builder()
                        .gatewayName(gatewayName)
                        .status(GatewayStatus.Status.UNREGISTERING.name())
                        .statusDescription("网关已被移除 - " + (reason != null ? reason : "Unknown"))
                        .profile(profile)
                        .reason(reason)
                        .updateTime(LocalDateTime.now())
                        .build();

                gatewayStatusService.updateGatewayStatus(gatewayStatus);
            }

        } catch (Exception e) {
            log.error("Error handling GATEWAY_DEL event", e);
        }
    }

    /**
     * 处理网关更新事件
     */
    private void handleGatewayUpdate(EslEvent event) {
        try {
            String gatewayName = event.getEventHeaders().get("Gateway");
            String profile = event.getEventHeaders().get("Profile");
            String state = event.getEventHeaders().get("State");

            if (gatewayName != null && state != null) {
                GatewayStatus.Status gatewayStatusEnum = mapGatewayState(state);

                GatewayStatus gatewayStatus = GatewayStatus.builder()
                        .gatewayName(gatewayName)
                        .status(gatewayStatusEnum.name())
                        .statusDescription("网关已更新 - " + state)
                        .profile(profile)
                        .updateTime(LocalDateTime.now())
                        .build();

                gatewayStatusService.updateGatewayStatus(gatewayStatus);
            }

        } catch (Exception e) {
            log.error("Error handling GATEWAY_UPDATE event", e);
        }
    }

    /**
     * 处理网关子状态事件
     */
    private void handleGatewaySubstate(EslEvent event) {
        try {
            String gatewayName = event.getEventHeaders().get("Gateway");
            String substate = event.getEventHeaders().get("Substate");
            String state = event.getEventHeaders().get("State");

            if (gatewayName != null && substate != null) {
                GatewayStatus gatewayStatus = GatewayStatus.builder()
                        .gatewayName(gatewayName)
                        .status(mapGatewayState(state != null ? state : "UNKNOWN").name())
                        .statusDescription("网关子状态: " + substate)
                        .substate(substate)
                        .updateTime(LocalDateTime.now())
                        .build();

                gatewayStatusService.updateGatewayStatus(gatewayStatus);
            }

        } catch (Exception e) {
            log.error("Error handling GATEWAY_SUBSTATE event", e);
        }
    }

    /**
     * 处理网关Ping事件
     */
    private void handleGatewayPing(EslEvent event) {
        try {
            String gatewayName = event.getEventHeaders().get("Gateway");
            String pingStatus = event.getEventHeaders().get("Ping-Status");
            String pingTime = event.getEventHeaders().get("Ping-Time");
            String networkIp = event.getEventHeaders().get("Network-IP");

            if (gatewayName != null && pingStatus != null) {
                GatewayStatus.Status status = "SUCCESS".equalsIgnoreCase(pingStatus) ?
                        GatewayStatus.Status.REGISTED : GatewayStatus.Status.FAILED;

                GatewayStatus gatewayStatus = GatewayStatus.builder()
                        .gatewayName(gatewayName)
                        .status(status.name())
                        .statusDescription("Ping状态: " + pingStatus)
                        .pingStatus(pingStatus)
                        .pingTime(pingTime)
                        .networkIp(networkIp)
                        .updateTime(LocalDateTime.now())
                        .build();

                gatewayStatusService.updateGatewayStatus(gatewayStatus);
                log.info("🏓 [GATEWAY PING] 网关: {}, Ping: {}", gatewayName, pingStatus);
            }

        } catch (Exception e) {
            log.error("Error handling GATEWAY_PING event", e);
        }
    }

    /**
     * 映射网关状态
     */
    private GatewayStatus.Status mapGatewayState(String state) {
        if (state == null) {
            return GatewayStatus.Status.UNKNOWN;
        }

        switch (state.toUpperCase()) {
            case "REGISTED":
                return GatewayStatus.Status.REGISTED;
            case "UNREGISTED":
                return GatewayStatus.Status.UNREGISTED;
            case "REGISTERING":
                return GatewayStatus.Status.REGISTERING;
            case "UNREGISTERING":
                return GatewayStatus.Status.UNREGISTERING;
            case "FAILED":
                return GatewayStatus.Status.FAILED;
            case "NO_RESPONSE":
                return GatewayStatus.Status.NO_RESPONSE;
            case "TIMEOUT":
                return GatewayStatus.Status.TIMEOUT;
            case "TRYING":
                return GatewayStatus.Status.REGISTERING;
            case "NOREG":
                return GatewayStatus.Status.UNREGISTED;
            case "FAIL_WAIT":
                return GatewayStatus.Status.FAILED;
            default:
                return GatewayStatus.Status.UNKNOWN;
        }
    }
}