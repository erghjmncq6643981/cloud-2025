package com.chandler.fcc.fcc.listener;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chandler.fcc.action.DefaultActionExecutorsManager;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.fcc.client.FccClient;
import com.chandler.fcc.flow.CallSessionManager;
import com.chandler.fcc.flow.event.*;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * FCC 事件监听网关：订阅 NATS 事件并将 Event.Channel 状态机映射为 Spring 领域事件与流程协同
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FccEventListener {

    private final Connection natsConnection;
    private final ApplicationEventPublisher publisher;
    private final CallSessionManager sessionManager;
    private final DefaultActionExecutorsManager handlersManager;
    private final FccClient fccClient;

    @PostConstruct
    public void startListening() {
        Dispatcher dispatcher = natsConnection.createDispatcher(this::onMessage);

        // 订阅所有 FNode 节点的呼叫状态机流与按键流 (使用 > 通配符兼容包含点号的主机名与FQDN)
        String eventSubject = "fs.event.>";
        dispatcher.subscribe(eventSubject);

        log.info("👂 [FCC] 已成功注册 NATS 事件监听: {}", eventSubject);
    }

    private void onMessage(Message msg) {
        try {
            String jsonStr = new String(msg.getData(), StandardCharsets.UTF_8);
            log.debug("🔔 [FCC 收到原始事件] Subject: {}\n{}", msg.getSubject(), jsonStr);

            JSONObject root = JSON.parseObject(jsonStr);
            String method = root.getString("method");
            JSONObject params = root.getJSONObject("params");
            if (params == null) {
                return;
            }

            if ("Event.Channel".equalsIgnoreCase(method)) {
                handleChannelEvent(params);
            } else if ("Event.DTMF".equalsIgnoreCase(method)) {
                handleDTMFEvent(params);
            }
        } catch (Exception e) {
            log.error("❌ [FCC] 处理 NATS 事件异常: {}", e.getMessage(), e);
        }
    }

    private void handleChannelEvent(JSONObject params) {
        String state = params.getString("state");
        String uuid = params.getString("uuid");
        String peerUuid = params.getString("peer_uuid");
        String ctrlUuid = params.getString("ctrl_uuid");
        String direction = params.getString("direction");
        String cidNumber = params.getString("cid_number");
        String destNumber = params.getString("dest_number");
        Integer duration = params.getInteger("duration");
        Integer billsec = params.getInteger("billsec");
        String cause = params.getString("cause");

        // 尝试从会话管理器获取已有通话上下文
        CallInfoBO callInfo = sessionManager.getByCtrlUuid(ctrlUuid)
                .or(() -> sessionManager.getByChannelUuid(uuid))
                .orElse(null);

        if (callInfo == null) {
            boolean isInbound = "inbound".equalsIgnoreCase(direction)
                    || "9000".equals(destNumber) || "8000".equals(destNumber) || "9999".equals(destNumber);
            String effectiveCtrlUuid = (ctrlUuid != null && !ctrlUuid.isEmpty())
                    ? ctrlUuid
                    : IdUtil.getCtrlUuid("fcc-inbound");
            String modelKey = isInbound ? FlowModelType.INBOUND_CUSTOMER_SERVICE.name() : null;

            callInfo = CallInfoBO.builder()
                    .callUuid(uuid)
                    .ctrlUuid(effectiveCtrlUuid)
                    .guestChannelUuid(uuid)
                    .agentChannelUuid(peerUuid)
                    .modelKey(modelKey)
                    .direction(isInbound ? DirectionType.inbound : DirectionType.outbound)
                    .callerNumber(cidNumber)
                    .destinationNumber(destNumber)
                    .duration(duration)
                    .billsec(billsec)
                    .hangupCause(cause)
                    .data(new HashMap<>())
                    .build();
            callInfo.getData().put("ctrlUuid", effectiveCtrlUuid);
            callInfo.getData().put("callUuid", uuid);
            callInfo.getData().put("guestChannelUuid", uuid);
            callInfo.getData().put("enableSurvey", "true");
            callInfo.getData().put("agentExt", "1007");

            sessionManager.registerSession(callInfo);
            sessionManager.bindChannel(uuid, effectiveCtrlUuid);
        } else {
            if (duration != null) callInfo.setDuration(duration);
            if (billsec != null) callInfo.setBillsec(billsec);
            if (cause != null) callInfo.setHangupCause(cause);
            if (peerUuid != null && callInfo.getAgentChannelUuid() == null) {
                callInfo.setAgentChannelUuid(peerUuid);
            }
            if (callInfo.getData() == null) {
                callInfo.setData(new HashMap<>());
            }
        }

        log.info("📞 [FCC 状态机流转] State: {}, UUID: {}, CtrlUUID: {}, Model: {}, Caller: {}, Dest: {}",
                state, uuid, ctrlUuid, callInfo.getModelKey(), cidNumber, destNumber);

        switch (state) {
            case "START":
                // 仅呼入且未启动过的通话由 FreeSWITCH START 事件触发业务引擎
                if (callInfo.getDirection() == DirectionType.inbound && callInfo.getData().putIfAbsent("started", "true") == null) {
                    callInfo.setStageState(CallStageState.START);
                    publisher.publishEvent(new CallStartEvent(callInfo));
                }
                break;

            case "CALLING":
            case "RINGING":
                callInfo.setStageState(CallStageState.CALLING);
                publisher.publishEvent(new CallCallingEvent(callInfo));
                break;

            case "READY": // 话道已 Park 静默驻留
                handleChannelReady(callInfo, uuid);
                break;

            case "BRIDGE": // 双方成功桥接连通
                if (callInfo.getData().putIfAbsent("connected", "true") == null) {
                    callInfo.setStageState(CallStageState.CONNECTED);
                    publisher.publishEvent(new CallConnectedEvent(callInfo));
                }
                break;

            case "DESTROY": // 话道销毁
                handleChannelDestroy(callInfo, uuid, ctrlUuid, params);
                break;

            default:
                log.debug("ℹ️ [FCC] 状态暂无需特殊流转: {}", state);
                break;
        }
    }

    private void handleChannelReady(CallInfoBO callInfo, String uuid) {
        // 1. 优先检查是否为呼叫转接的目标坐席 Leg 应答 (Transfer Target Agent READY)
        String transferTargetUuid = callInfo.getData() != null ? callInfo.getData().get("transferTargetAgentUuid") : null;
        if (transferTargetUuid != null && transferTargetUuid.equals(uuid)) {
            callInfo.getData().remove("transferTargetAgentUuid");
            callInfo.getData().remove("isTransferring");
            callInfo.setAgentChannelUuid(uuid);
            callInfo.getData().put("agentChannelUuid", uuid);

            log.info("🔗 [呼叫转接协同] 目标坐席 {} 已应答就绪，重新桥接客户话道 {} 与目标坐席！",
                    uuid, callInfo.getGuestChannelUuid());

            FlowNode bridgeNode = FlowNode.builder()
                    .actionType(ActionType.CHANNEL_BRIDGE)
                    .actionKey("bridge-transfer-target")
                    .order(1)
                    .data(new HashMap<>(Map.of(
                            "uuidA", callInfo.getGuestChannelUuid(),
                            "uuidB", uuid,
                            "ctrlUuid", callInfo.getCtrlUuid() != null ? callInfo.getCtrlUuid() : ""
                    )))
                    .build();
            handlersManager.publish(callInfo, bridgeNode);

            String targetExt = callInfo.getData().getOrDefault("transferTargetExt", "unknown");
            String origAgent = callInfo.getData().getOrDefault("originalAgentUuid", "unknown");
            handlersManager.recordAudit(callInfo, "call-transferred", "TRANSFER", Map.of(
                    "result", "SUCCESS",
                    "fromAgent", origAgent,
                    "toAgent", uuid,
                    "targetExt", targetExt,
                    "detail", "呼叫成功转接至目标坐席: " + targetExt + " (话道重新桥接对讲)"
            ));
            return;
        }

        String modelKey = callInfo.getModelKey() != null ? callInfo.getModelKey() : FlowModelType.INBOUND_CUSTOMER_SERVICE.name();

        if (FlowModelType.OUTBOUND_TWO_WAY_CALL.name().equals(modelKey)) {
            // 双向外呼场景：
            // 若坐席 Leg 就绪，且客户未呼叫 -> 外呼客户 Leg
            if (uuid.equals(callInfo.getAgentChannelUuid())) {
                if (callInfo.getData().putIfAbsent("guestDialed", "true") == null) {
                    callInfo.setStageState(CallStageState.ROUTE);
                    log.info("🎯 [双向外呼协同] 坐席已应答驻留，开始路由外呼客户: {}", callInfo.getDestinationNumber());
                    publisher.publishEvent(new CallRouteEvent(callInfo));
                }
            }
            // 若客户 Leg 也应答驻留 -> 双方均 READY，触发桥接
            else if (uuid.equals(callInfo.getGuestChannelUuid()) || "true".equals(callInfo.getData().get("guestDialed"))) {
                if (callInfo.getData().putIfAbsent("bridgeDispatched", "true") == null) {
                    log.info("🔗 [双向外呼协同] 双方均已应答 (READY)，自动触发话道桥接! Agent: {}, Guest: {}",
                            callInfo.getAgentChannelUuid(), callInfo.getGuestChannelUuid());
                    FlowNode bridgeNode = FlowNode.builder()
                            .actionType(ActionType.CHANNEL_BRIDGE)
                            .actionKey("bridge-agent-guest")
                            .order(1)
                            .data(new HashMap<>(Map.of(
                                    "uuidA", callInfo.getGuestChannelUuid(),
                                    "uuidB", callInfo.getAgentChannelUuid(),
                                    "ctrlUuid", callInfo.getCtrlUuid() != null ? callInfo.getCtrlUuid() : ""
                            )))
                            .build();
                    handlersManager.publish(callInfo, bridgeNode);
                }
            }
        } else if (FlowModelType.AUTO_DIAL_NOTIFICATION.name().equals(modelKey)) {
            // 自动外呼通知场景 (AUTO_DIAL_NOTIFICATION):
            // 客户接听后话道就绪 (Guest READY) -> 触发 ROUTE 阶段 (播放通知语音并收号确认)
            if (uuid.equals(callInfo.getGuestChannelUuid())) {
                if (callInfo.getData().putIfAbsent("notifyStarted", "true") == null) {
                    callInfo.setStageState(CallStageState.ROUTE);
                    log.info("📢 [自动通知协同] 客户已接听话道就绪，触发通知放音收号: Guest={}", uuid);
                    publisher.publishEvent(new CallRouteEvent(callInfo));
                }
            }
        } else {
            // 呼入客服场景 (INBOUND_CUSTOMER_SERVICE)
            // 1. 客户通道就绪 (Guest 1008 READY)
            if (uuid.equals(callInfo.getGuestChannelUuid())) {
                if (callInfo.getData().putIfAbsent("started", "true") == null) {
                    callInfo.setStageState(CallStageState.START);
                    log.info("🎯 [呼入流程协同] 客户通道已就绪，启动 IVR 导航放音收号: Guest={}", uuid);
                    publisher.publishEvent(new CallStartEvent(callInfo));
                }
            }
            // 2. 坐席通道就绪 (Agent 1007 READY)
            else if (uuid.equals(callInfo.getAgentChannelUuid()) || "true".equals(callInfo.getData().get("agentDialed"))) {
                if (callInfo.getData().putIfAbsent("bridgeDispatched", "true") == null) {
                    log.info("🔗 [呼入流程协同] 客户与坐席均已就绪，触发话道桥接! Guest: {}, Agent: {}",
                            callInfo.getGuestChannelUuid(), callInfo.getAgentChannelUuid());
                    FlowNode bridgeNode = FlowNode.builder()
                            .actionType(ActionType.CHANNEL_BRIDGE)
                            .actionKey("bridge-inbound-agent")
                            .order(1)
                            .data(new HashMap<>(Map.of(
                                    "uuidA", callInfo.getGuestChannelUuid(),
                                    "uuidB", callInfo.getAgentChannelUuid(),
                                    "ctrlUuid", callInfo.getCtrlUuid() != null ? callInfo.getCtrlUuid() : ""
                            )))
                            .build();
                    handlersManager.publish(callInfo, bridgeNode);
                }
            }
        }
    }

    private void handleChannelDestroy(CallInfoBO callInfo, String uuid, String ctrlUuid, JSONObject eventParams) {
        String hungupUuid = uuid;

        // 呼叫转接保护：若正在转接且挂机的是原坐席话道，静默忽略，避免触发 CallEndEvent 导致客户过早进入满意度评价或挂机
        if ("true".equals(callInfo.getData().get("isTransferring")) && hungupUuid.equals(callInfo.getData().get("originalAgentUuid"))) {
            log.info("🔀 [呼叫转接协同] 原坐席话道 {} 已挂断退出，客户话道驻留等待目标坐席应答", hungupUuid);
            return;
        }

        if (uuid.equals(callInfo.getAgentChannelUuid())) callInfo.getData().put("agentEnded", "true");
        if (uuid.equals(callInfo.getGuestChannelUuid())) callInfo.getData().put("guestEnded", "true");

        // 检查是否有通过通道变量带回的收号评分 (dtmf_val)
        if (eventParams != null) {
            JSONObject extraParams = eventParams.getJSONObject("params");
            String dtmfVal = extraParams != null ? extraParams.getString("dtmf_val") : null;
            if (dtmfVal != null && !dtmfVal.isEmpty() && !"_none_".equalsIgnoreCase(dtmfVal) && callInfo.getData().get("surveyScore") == null) {
                callInfo.getData().put("surveyScore", dtmfVal);
                handlersManager.recordAudit(callInfo, "survey-score-recorded", "READ_DTMF", Map.of(
                        "score", dtmfVal,
                        "digit", dtmfVal,
                        "result", "SUCCESS",
                        "detail", "用户按键评价成功: " + dtmfVal + "分",
                        "targetUuid", uuid
                ));
                log.info("⭐ [满意度评价完成] 话道结算捕获用户按键评分: {} 分, CallUUID: {}", dtmfVal, callInfo.getCallUuid());
            }
        }

        String survivingUuid = null;
        if ("true".equals(callInfo.getData().get("agentEnded")) && !"true".equals(callInfo.getData().get("guestEnded"))) {
            survivingUuid = callInfo.getGuestChannelUuid();
        } else if ("true".equals(callInfo.getData().get("guestEnded")) && !"true".equals(callInfo.getData().get("agentEnded"))) {
            survivingUuid = callInfo.getAgentChannelUuid();
        } else if (hungupUuid.equals(callInfo.getGuestChannelUuid())) {
            survivingUuid = callInfo.getAgentChannelUuid();
        } else if (hungupUuid.equals(callInfo.getAgentChannelUuid())) {
            survivingUuid = callInfo.getGuestChannelUuid();
        }

        callInfo.getData().put("hungupUuid", hungupUuid);
        if (survivingUuid != null) {
            callInfo.getData().put("survivingUuid", survivingUuid);
            callInfo.getData().put("peerUuid", survivingUuid);
        }

        // 第一次收到话道销毁，触发业务层的 CallEndEvent (停止录音、转满意度或挂断对端)
        if (callInfo.getData().putIfAbsent("callEndFired", "true") == null) {
            callInfo.setStageState(CallStageState.NORMAL_END);
            log.info("🏁 [FCC] 话道挂机触发 CallEndEvent: Hungup={}, Surviving={}", hungupUuid, survivingUuid);
            publisher.publishEvent(new CallEndEvent(callInfo));
        }

        // 当双方通道均已挂机，检查是否需要记录超时未评价并释放内存会话
        if ("true".equals(callInfo.getData().get("agentEnded")) && "true".equals(callInfo.getData().get("guestEnded"))) {
            if ("true".equalsIgnoreCase(callInfo.getData().get("enableSurvey")) && callInfo.getData().get("surveyScore") == null) {
                handlersManager.recordAudit(callInfo, "survey-timeout", "READ_DTMF", Map.of(
                        "result", "TIMEOUT",
                        "detail", "用户未按键，引导语重复播放2次后超时自动挂机",
                        "callUuid", callInfo.getCallUuid()
                ));
                log.info("⌛ [满意度评价结果] 用户未按键，引导语重复播放2次超时挂机: CallUUID={}", callInfo.getCallUuid());
            }
            if (ctrlUuid != null) {
                sessionManager.removeSession(ctrlUuid);
            }
        }
    }

    private void handleDTMFEvent(JSONObject params) {
        String ctrlUuid = params.getString("ctrl_uuid");
        String uuid = params.getString("uuid");
        String digit = params.getString("digit");
        int durationMs = params.getIntValue("duration_ms", 0);

        if (digit == null || digit.isEmpty() || "_none_".equalsIgnoreCase(digit)) {
            return;
        }

        log.info("🔢 [FCC 收到按键] Digit: {}, UUID: {}, CtrlUUID: {}", digit, uuid, ctrlUuid);
        publisher.publishEvent(new DTMFInputEvent(this, ctrlUuid, uuid, digit, durationMs));

        // 针对服务评价收号与 IVR 导航收号
        sessionManager.getByCtrlUuid(ctrlUuid)
                .or(() -> sessionManager.getByChannelUuid(uuid))
                .ifPresent(session -> {
                    String modelKey = session.getModelKey();

                    // 场景 A: 呼入客服流程中的 IVR 导航按键选择
                    if (FlowModelType.INBOUND_CUSTOMER_SERVICE.name().equals(modelKey) && session.getData().get("agentDialed") == null) {
                        session.getData().put("agentDialed", "true");
                        session.getData().put("ivrSelectedDigit", digit);
                        session.getData().put("agentExt", "1007"); // 路由至客服 1007
                        log.info("🎯 [呼入导航] 客户按键选择: {} 业务，准备路由客服坐席 1007", digit);
                        handlersManager.recordAudit(session, "ivr-navigation-selected", "READ_DTMF", Map.of(
                                "digit", digit,
                                "result", "SUCCESS",
                                "detail", "客户按键选择业务: " + digit + " (路由人工客服1007)",
                                "targetUuid", uuid
                        ));

                        // 触发外呼坐席 1007
                        session.setStageState(CallStageState.ROUTE);
                        publisher.publishEvent(new CallRouteEvent(session));
                        return;
                    }

                    // 场景 B: 自动外呼通知流程中的按键意图确认
                    if (FlowModelType.AUTO_DIAL_NOTIFICATION.name().equals(modelKey)) {
                        if (session.getData().putIfAbsent("notifyDigit", digit) == null) {
                            String intentDesc = "1".equals(digit) ? "确认办理" : ("2".equals(digit) ? "咨询详情" : "其他业务");
                            handlersManager.recordAudit(session, "notification-confirmed", "READ_DTMF", Map.of(
                                    "digit", digit,
                                    "result", "SUCCESS",
                                    "detail", "客户按键确认意向: " + digit + " (" + intentDesc + ")",
                                    "durationMs", String.valueOf(durationMs),
                                    "targetUuid", uuid
                            ));
                            log.info("📢 [自动通知按键确认] 客户按键确认意向: {} ({}), Channel={}", digit, intentDesc, uuid);
                        }
                        return;
                    }

                    // 场景 C: 满意度评价按键 (仅在坐席已挂机或已进入服务评价阶段时触发)
                    boolean canSurvey = "true".equals(session.getData().get("callEndFired"))
                            || "true".equals(session.getData().get("agentEnded"))
                            || session.getStageState() == CallStageState.NORMAL_END;
                    if (canSurvey && session.getData().putIfAbsent("surveyScore", digit) == null) {
                        handlersManager.recordAudit(session, "survey-score-recorded", "READ_DTMF", Map.of(
                                "score", digit,
                                "digit", digit,
                                "result", "SUCCESS",
                                "detail", "用户实时按键评价: " + digit + "分",
                                "durationMs", String.valueOf(durationMs),
                                "targetUuid", uuid
                        ));

                        log.info("⭐ [满意度评价完成] 实时捕获用户按键评分: {} 分, Channel={}", digit, uuid);
                    }
                });
    }
}
