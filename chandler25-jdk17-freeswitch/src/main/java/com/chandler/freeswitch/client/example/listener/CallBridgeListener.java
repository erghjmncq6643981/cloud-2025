package com.chandler.freeswitch.client.example.listener;

import com.chandler.freeswitch.client.example.command.FreeSwitchCommandGateway;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandResult;
import com.chandler.freeswitch.client.example.domain.dataobject.CallFileRecord;
import com.chandler.freeswitch.client.example.domain.dataobject.CallLeg;
import com.chandler.freeswitch.client.example.domain.dataobject.CallSession;
import com.chandler.freeswitch.client.example.service.*;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双呼桥接监听器：管理双向呼叫的全生命周期绑定 (CallSession + LegA + LegB + EventLog + Timeline + Recording + Survey)
 *
 * @author 钱丁君-chandler
 * @version 2.0.0
 */
@Slf4j
@Component
@EslEventName({
        "CHANNEL_PROGRESS",
        "CHANNEL_PROGRESS_MEDIA",
        "CHANNEL_ANSWER",
        "CHANNEL_BRIDGE",
        "CHANNEL_HANGUP",
        "DTMF",
        "RECORD_STOP"
})
public class CallBridgeListener implements EslEventHandler {

    @Autowired
    private FreeSwitchCommandGateway commandGateway;
    @Autowired
    private CallSessionService callSessionService;
    @Autowired
    private CallLegService callLegService;
    @Autowired
    private CommandLogService commandLogService;
    @Autowired
    private CallEventLogService callEventLogService;
    @Autowired
    private CallTimelineService callTimelineService;
    @Autowired
    private CallFileRecordService callFileRecordService;
    @Autowired
    private DtmfRecordService dtmfRecordService;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BridgeContext {
        private Long sessionId;
        private String bizId;
        private String uuidA;
        private String extensionA;
        private String uuidB;
        private String extensionB;
        private volatile boolean aAnswered;
        private volatile boolean bAnswered;
        private volatile boolean bridged;
        private volatile boolean ended;
        private volatile boolean aHungup;
        private volatile boolean bHungup;

        // 转接 C-leg 上下文
        private String uuidC;
        private String extensionC;
        private volatile boolean cAnswered;
        private volatile boolean cHungup;
        private volatile boolean transferring;
        private volatile boolean transferSuccess;

        // 三方通话 (Three-Way)
        private volatile boolean threeWay;
        private String conferenceName;

        // 录音 (Recording)
        private volatile boolean autoRecord;
        private volatile boolean recording;
        private String recordingFilePath;
        private Long recordFileRecordId;

        // 满意度评价 (Post-Call Survey IVR)
        private volatile boolean enableSurvey;
        private volatile boolean inSurvey;
        private String surveyScore;
    }

    // 内存中活跃的双呼上下文池: bizId -> BridgeContext
    private final Map<String, BridgeContext> bridgeTasks = new ConcurrentHashMap<>();
    // 辅助索引: uuid -> bizId
    private final Map<String, String> uuidToBizIdMap = new ConcurrentHashMap<>();

    /**
     * 注册双呼任务上下文
     */
    public void registerBridgeContext(BridgeContext context) {
        bridgeTasks.put(context.getBizId(), context);
        if (context.getUuidA() != null) {
            uuidToBizIdMap.put(context.getUuidA(), context.getBizId());
        }
        if (context.getUuidB() != null) {
            uuidToBizIdMap.put(context.getUuidB(), context.getBizId());
        }
        log.info("📝 [双呼] 注册双呼桥接任务: bizId={}, SessionId={}, A={}({}), B={}({})",
                context.getBizId(), context.getSessionId(),
                context.getExtensionA(), context.getUuidA(),
                context.getExtensionB(), context.getUuidB());
    }

    public BridgeContext getBridgeContextByBizId(String bizId) {
        return bridgeTasks.get(bizId);
    }

    public BridgeContext getBridgeContextBySessionId(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        for (BridgeContext ctx : bridgeTasks.values()) {
            if (sessionId.equals(ctx.getSessionId())) {
                return ctx;
            }
        }
        return null;
    }

    public BridgeContext getLatestActiveBridgeContext() {
        return bridgeTasks.values().stream()
                .filter(ctx -> !ctx.isEnded() && ctx.isBridged())
                .findFirst()
                .orElse(null);
    }

    public void updateBridgeContextForTransfer(String bizId, String uuidC, String extensionC) {
        BridgeContext ctx = bridgeTasks.get(bizId);
        if (ctx != null) {
            ctx.setUuidC(uuidC);
            ctx.setExtensionC(extensionC);
            ctx.setTransferring(true);
            ctx.setCAnswered(false);
            ctx.setCHungup(false);
            ctx.setTransferSuccess(false);
            uuidToBizIdMap.put(uuidC, bizId);
            log.info("📝 [转接上下文注册] bizId={}, C={}({})", bizId, extensionC, uuidC);
        }
    }

    public void updateBridgeContextForThreeWay(String bizId, String uuidC, String extensionC, String confName) {
        BridgeContext ctx = bridgeTasks.get(bizId);
        if (ctx != null) {
            ctx.setUuidC(uuidC);
            ctx.setExtensionC(extensionC);
            ctx.setThreeWay(true);
            ctx.setConferenceName(confName);
            ctx.setCAnswered(false);
            ctx.setCHungup(false);
            uuidToBizIdMap.put(uuidC, bizId);
            log.info("📝 [三方上下文注册] bizId={}, C={}({}), conf={}", bizId, extensionC, uuidC, confName);
        }
    }

    public synchronized void startRecording(BridgeContext context) {
        if (context == null || context.isRecording() || context.getUuidA() == null) {
            return;
        }
        String recordDir = "/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-freeswitch/sounds/records";
        File dir = new File(recordDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fileName = "rec_" + context.getBizId() + "_" + System.currentTimeMillis() + ".wav";
        String filePath = recordDir + "/" + fileName;

        log.info("🎙️ [通话录音] 启动双向通话录音: uuidA={}, path={}", context.getUuidA(), filePath);
        FreeSwitchCommandResult recResult = commandGateway.recordStart(context.getUuidA(), filePath);
        commandLogService.saveCommandResult(recResult);

        CallFileRecord fileRecord = callFileRecordService.createRecord(context.getUuidA(), fileName, filePath);
        context.setRecording(true);
        context.setRecordingFilePath(filePath);
        if (fileRecord != null) {
            context.setRecordFileRecordId(fileRecord.getId());
        }

        callTimelineService.record(context.getSessionId(), context.getBizId(), null, context.getUuidA(),
                "MEDIA", "RECORD_STARTED", "已开启双向通话录音: " + fileName);
    }

    public synchronized void stopRecording(BridgeContext context) {
        if (context == null || !context.isRecording() || context.getRecordingFilePath() == null) {
            return;
        }
        context.setRecording(false);
        String filePath = context.getRecordingFilePath();
        log.info("⏹️ [通话录音] 停止录音: uuidA={}, path={}", context.getUuidA(), filePath);
        FreeSwitchCommandResult stopResult = commandGateway.recordStop(context.getUuidA(), filePath);
        commandLogService.saveCommandResult(stopResult);

        if (context.getRecordFileRecordId() != null) {
            callFileRecordService.updateActualFileSize(context.getRecordFileRecordId(), filePath);
        }

        callTimelineService.record(context.getSessionId(), context.getBizId(), null, context.getUuidA(),
                "MEDIA", "RECORD_STOPPED", "已停止通话录音: " + filePath);
    }

    @Override
    public void handle(String addr, EslEvent event) {
        String eventName = event.getEventName();
        Map<String, String> headers = event.getEventHeaders();

        String uuid = headers.get("Unique-ID");
        if (uuid == null) {
            return;
        }

        // 尝试通过 variable_my_biz_id 或 UUID 索引定位任务
        String bizId = headers.get("variable_my_biz_id");
        if (bizId == null) {
            bizId = uuidToBizIdMap.get(uuid);
        }

        // 如果内存中没有，尝试查数据库判断是否属于我们关注的双呼会话
        BridgeContext context = bizId != null ? bridgeTasks.get(bizId) : null;
        if (context == null) {
            CallLeg leg = callLegService.getByUuid(uuid);
            if (leg != null && leg.getSessionId() != null) {
                CallSession session = callSessionService.getById(leg.getSessionId());
                // 仅处理双呼方向 (direction=3) 的会话
                if (session != null && Integer.valueOf(3).equals(session.getDirection())) {
                    bizId = session.getBizId();
                    if (session.getStatus() != null && session.getStatus() < 2) {
                        context = bridgeTasks.computeIfAbsent(bizId, k -> BridgeContext.builder()
                                .sessionId(session.getId())
                                .bizId(session.getBizId())
                                .build());
                        uuidToBizIdMap.put(uuid, bizId);
                    } else {
                        // 已经结束的会话，仅记录底层事件并同步通道状态，不触发业务状态机流转
                        String finalBizId = bizId;
                        CompletableFuture.runAsync(() -> {
                            try {
                                callEventLogService.recordEvent(
                                        session.getId(),
                                        finalBizId,
                                        uuid,
                                        eventName,
                                        headers.get("Event-Subclass"),
                                        headers.get("Hangup-Cause"),
                                        headers
                                );
                                if ("CHANNEL_HANGUP".equals(eventName)) {
                                    callLegService.updateStatusByUuid(uuid, "HANGUP");
                                }
                            } catch (Exception e) {
                                log.error("记录已结束会话事件异常: {}", uuid, e);
                            }
                        });
                        return;
                    }
                }
            }
        }

        if (context == null) {
            return; // 忽略非双呼业务事件
        }

        BridgeContext finalContext = context;
        String finalBizId = bizId;
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 优先推进业务处理与时间线（确保核心控制指令 0 延迟下发）
                processEvent(eventName, headers, uuid, finalBizId, finalContext);

                // 2. 异步记录底层原始事件到 call_event_log
                callEventLogService.recordEvent(
                        finalContext.getSessionId(),
                        finalBizId,
                        uuid,
                        eventName,
                        headers.get("Event-Subclass"),
                        headers.get("Hangup-Cause"),
                        headers
                );
            } catch (Exception e) {
                log.error("处理双呼事件异常, event={}, uuid={}, bizId={}", eventName, uuid, finalBizId, e);
            }
        });
    }

    private void processEvent(String eventName, Map<String, String> headers, String uuid, String bizId, BridgeContext context) {
        String role = headers.get("variable_my_role");
        String hangupCause = headers.get("Hangup-Cause");

        switch (eventName) {
            case "CHANNEL_PROGRESS":
            case "CHANNEL_PROGRESS_MEDIA":
                handleProgress(uuid, context);
                break;

            case "CHANNEL_ANSWER":
                handleAnswer(uuid, role, context);
                break;

            case "CHANNEL_BRIDGE":
                handleBridge(uuid, context);
                break;

            case "CHANNEL_HANGUP":
                handleHangup(uuid, role, hangupCause, context);
                break;

            case "DTMF":
                handleDtmf(uuid, headers, context);
                break;

            case "RECORD_STOP":
                handleRecordStop(uuid, headers, context);
                break;

            default:
                break;
        }
    }

    private void handleProgress(String uuid, BridgeContext context) {
        log.info("🔔 [通道振铃] 通道振铃中, UUID={}, bizId={}", uuid, context.getBizId());
        callLegService.updateStatusByUuid(uuid, "RINGING");
        if (uuid.equals(context.getUuidC())) {
            String roleName = context.isThreeWay() ? "三方专家通道振铃中" : "转接目标通道振铃中";
            callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuid,
                    "LEG", "LEG_RINGING", roleName + " (分机 " + context.getExtensionC() + "): " + uuid);
        } else {
            callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuid,
                    "LEG", "LEG_RINGING", "通道振铃中 (Early Media/Progress): " + uuid);
        }
    }

    private void handleAnswer(String uuid, String role, BridgeContext context) {
        log.info("🗣️ [通道接通] 通道已应答, UUID={}, 角色={}, bizId={}", uuid, role, context.getBizId());
        callLegService.updateStatusByUuid(uuid, "ANSWERED");

        // 如果是转接/三方目标分机 C 应答
        if (uuid.equals(context.getUuidC())) {
            if (context.isThreeWay()) {
                handleThreeWayAnswer(context);
            } else {
                handleTransferAnswer(context);
            }
            return;
        }

        callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuid,
                "LEG", "LEG_ANSWERED", "角色 [" + (role != null ? role : "未知") + "] 接听应答，进入Park状态");

        if (uuid.equals(context.getUuidA())) {
            context.setAAnswered(true);
        } else if (uuid.equals(context.getUuidB())) {
            context.setBAnswered(true);
        } else {
            // 如果 UUID 没对上，兜底根据 role 标记
            if ("caller".equalsIgnoreCase(role)) {
                context.setAAnswered(true);
                context.setUuidA(uuid);
            } else if ("callee".equalsIgnoreCase(role)) {
                context.setBAnswered(true);
                context.setUuidB(uuid);
            }
        }

        // 判断是否双方均已就绪
        synchronized (context) {
            if (context.isAAnswered() && context.isBAnswered() && !context.isBridged()) {
                context.setBridged(true);
                String uuidA = context.getUuidA();
                String uuidB = context.getUuidB();
                log.info("🔗 [双呼桥接] 双方均已应答，下发桥接指令: Leg-A({}) <-> Leg-B({})", uuidA, uuidB);

                callTimelineService.record(context.getSessionId(), context.getBizId(), null, null,
                        "COMMAND", "BRIDGE_TRIGGERED", "双方均已接听，触发强制桥接: " + uuidA + " <-> " + uuidB);

                FreeSwitchCommandResult result = commandGateway.bridge(uuidA, uuidB);
                commandLogService.saveCommandResult(result);

                // 更新主会话状态为 通话中 (1)
                callSessionService.updateStatus(context.getSessionId(), 1);
            } else {
                log.info("⏳ [双呼等待] 一方已接通 (A={}, B={})，等待对端接听...",
                        context.isAAnswered(), context.isBAnswered());
            }
        }
    }

    private void handleThreeWayAnswer(BridgeContext context) {
        synchronized (context) {
            context.setCAnswered(true);
            String conf = context.getConferenceName();
            String uuidC = context.getUuidC();
            log.info("🎉 [三方接听] 专家分机 C({}) 已应答！将 C 转入会议室: {}", context.getExtensionC(), conf);

            commandGateway.transferInline(uuidC, "conference:" + conf + "@default");
            callLegService.updateStatusByUuid(uuidC, "BRIDGED");

            callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuidC,
                    "COMMAND", "THREE_WAY_ESTABLISHED", "专家 C(" + context.getExtensionC() + ") 已接听并加入三方会议室 " + conf + "，三方全双工实时通话建立！");
        }
    }

    private void handleTransferAnswer(BridgeContext context) {
        synchronized (context) {
            context.setCAnswered(true);
            context.setTransferSuccess(true);
            context.setTransferring(false);
            String uuidA = context.getUuidA();
            String uuidB = context.getUuidB();
            String uuidC = context.getUuidC();
            log.info("🎉 [转接接听] 分机 C({}) 已应答！开始执行 A({}) 与 C({}) 桥接，释放 B({})",
                    context.getExtensionC(), uuidA, uuidC, uuidB);

            // 1. 停止 A 的回铃音
            commandGateway.stopPlayback(uuidA);

            // 2. 恢复 A 的 hangup_after_bridge 联动
            commandGateway.setVar(uuidA, "hangup_after_bridge", "true");

            // 3. 释放原坐席 B
            if (uuidB != null && !context.isBHungup()) {
                context.setBHungup(true);
                commandGateway.kill(uuidB, "NORMAL_CLEARING");
                callLegService.updateStatusByUuid(uuidB, "TRANSFERRED");
                callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuidB,
                        "LEG", "LEG_TRANSFERRED", "转接成功，原坐席 B (" + context.getExtensionB() + ") 释放退出");
            }

            // 4. 桥接 A 和 C
            callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuidC,
                    "COMMAND", "TRANSFER_BRIDGED", "转接目标 C 已接听，触发强制桥接: A(" + context.getExtensionA() + ") <-> C(" + context.getExtensionC() + ")");
            FreeSwitchCommandResult result = commandGateway.bridge(uuidA, uuidC);
            commandLogService.saveCommandResult(result);

            callLegService.updateStatusByUuid(uuidC, "BRIDGED");
        }
    }

    private void handleBridge(String uuid, BridgeContext context) {
        log.info("🎉 [双呼桥接完成] 通道已建立媒体连接, UUID={}, bizId={}", uuid, context.getBizId());
        callLegService.updateStatusByUuid(uuid, "BRIDGED");
        callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuid,
                "MEDIA", "CALL_BRIDGED", "通道桥接完成，双方媒体流打通: " + uuid);

        // 如果开启了自动录音，且尚未开启录音 (只在 A-leg 建立时触发一次)
        if (context.isAutoRecord() && !context.isRecording() && uuid.equals(context.getUuidA())) {
            startRecording(context);
        }
    }

    private void handleHangup(String uuid, String role, String cause, BridgeContext context) {
        log.info("👋 [通道挂断] 通道已挂断, UUID={}, 角色={}, 原因={}, bizId={}", uuid, role, cause, context.getBizId());
        callLegService.updateStatusByUuid(uuid, "HANGUP");
        callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuid,
                "LEG", "LEG_HANGUP", "通道挂机 (" + (role != null ? role : "未知") + "): 原因=" + cause);

        synchronized (context) {
            if (uuid.equals(context.getUuidA())) {
                context.setAHungup(true);
            } else if (uuid.equals(context.getUuidB())) {
                context.setBHungup(true);
            } else if (uuid.equals(context.getUuidC())) {
                context.setCHungup(true);
            }

            // --- 场景 0：三方通话模式挂机 ---
            if (context.isThreeWay()) {
                handleThreeWayHangup(uuid, role, cause, context);
                return;
            }

            // --- 场景 A：转接目标分机 C 挂机 ---
            if (uuid.equals(context.getUuidC())) {
                if (!context.isCAnswered()) {
                    // 分支 2：C 未应答就挂断 (拒接 / 超时 / 忙线) -> 触发回退重新桥接 A 与 B
                    handleTransferFallback(cause, context);
                    return;
                } else {
                    // C 已经接听并与 A 通话后挂机 -> 通话正常结束，释放 A
                    log.info("🏁 [转接通话结束] C 通道已挂机，挂断 A 通道: {}", context.getUuidA());
                    if (!context.isAHungup()) {
                        FreeSwitchCommandResult killA = commandGateway.kill(context.getUuidA(), cause);
                        commandLogService.saveCommandResult(killA);
                    }
                    stopRecording(context);
                    endSessionNormally("transferee 挂断: " + cause, context);
                    checkAndCleanContext(context);
                    return;
                }
            }

            // --- 场景 B：原坐席 B 挂机 ---
            if (uuid.equals(context.getUuidB())) {
                if (context.isTransferSuccess()) {
                    // 转接成功后 B 被系统释放的正常事件
                    checkAndCleanContext(context);
                    return;
                }
                if (context.isTransferring()) {
                    // 转接中坐席 B 自身主动挂机
                    log.warn("⚠️ [转接中坐席退出] 坐席 B 在转接过程中主动挂机: {}", uuid);
                    checkAndCleanContext(context);
                    return;
                }
                // === 满意度评价拦截 ===
                if (context.isEnableSurvey() && !context.isAHungup() && !context.isInSurvey()) {
                    handleSurveyInitiated(context);
                    return;
                }
            }

            // --- 场景 C：主叫 A 挂机 ---
            if (uuid.equals(context.getUuidA())) {
                if (context.isInSurvey()) {
                    log.info("ℹ️ [满意度未按键] 客户 A 在评价环节主动挂机");
                    callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuid,
                            "SURVEY", "SURVEY_SKIPPED", "客户未按键直接挂机退出评价");
                    context.setInSurvey(false);
                }
                stopRecording(context);
                log.info("👋 [主叫A挂机] 客户 A 已挂断，清理所有关联对端通道...");
                if (context.isTransferring() && context.getUuidC() != null && !context.isCHungup()) {
                    commandGateway.kill(context.getUuidC(), cause);
                }
                if (context.isTransferSuccess() && context.getUuidC() != null && !context.isCHungup()) {
                    commandGateway.kill(context.getUuidC(), cause);
                }
                if (!context.isBHungup() && context.getUuidB() != null) {
                    commandGateway.kill(context.getUuidB(), cause);
                }
                endSessionNormally("caller 挂断: " + cause, context);
                checkAndCleanContext(context);
                return;
            }

            // --- 场景 D：常规双呼 A-B 通话挂机 ---
            if (!context.isEnded()) {
                context.setEnded(true);
                stopRecording(context);

                String otherUuid = uuid.equals(context.getUuidA()) ? context.getUuidB() : context.getUuidA();
                if (otherUuid != null) {
                    log.info("👋 [对端补偿挂机] 本端({})挂断，主动挂断对端通道({})", uuid, otherUuid);
                    FreeSwitchCommandResult killResult = commandGateway.kill(otherUuid, cause);
                    commandLogService.saveCommandResult(killResult);
                    callTimelineService.record(context.getSessionId(), context.getBizId(), null, otherUuid,
                            "COMMAND", "COMPENSATION_KILL", "一方挂机，触发对端自动挂断: " + otherUuid);
                }

                if (!context.isBridged()) {
                    String reason = (role != null ? role : "peer") + " 未接通: " + cause;
                    callSessionService.updateStatus(context.getSessionId(), 3, reason);
                    callTimelineService.record(context.getSessionId(), context.getBizId(), null, null,
                            "SESSION", "SESSION_FAILED", "双呼会话未接通结束: " + reason);
                } else {
                    String reason = (role != null ? role : "party") + " 挂断: " + cause;
                    callSessionService.updateStatus(context.getSessionId(), 2, reason);
                    callTimelineService.record(context.getSessionId(), context.getBizId(), null, null,
                            "SESSION", "SESSION_ENDED", "双呼正常挂机结束: " + reason);
                }
            } else {
                log.info("ℹ️ [双呼全挂机] 对端通道({})也已完成挂机", uuid);
            }

            checkAndCleanContext(context);
        }
    }

    private void handleThreeWayHangup(String uuid, String role, String cause, BridgeContext context) {
        String memberName = "未知成员";
        if (uuid.equals(context.getUuidA())) {
            memberName = "客户 A (" + context.getExtensionA() + ")";
        } else if (uuid.equals(context.getUuidB())) {
            memberName = "坐席 B (" + context.getExtensionB() + ")";
        } else if (uuid.equals(context.getUuidC())) {
            memberName = "专家 C (" + context.getExtensionC() + ")";
            if (!context.isCAnswered()) {
                callLegService.updateStatusByUuid(uuid, "FAILED");
            }
        }

        // 计算当前存活的成员数量及剩余成员描述
        int aliveCount = 0;
        StringBuilder remainingDesc = new StringBuilder();
        if (!context.isAHungup()) {
            aliveCount++;
            remainingDesc.append("客户 A(").append(context.getExtensionA()).append(") ");
        }
        if (!context.isBHungup()) {
            aliveCount++;
            remainingDesc.append("坐席 B(").append(context.getExtensionB()).append(") ");
        }
        if (context.getUuidC() != null && !context.isCHungup()) {
            aliveCount++;
            remainingDesc.append("专家 C(").append(context.getExtensionC()).append(") ");
        }

        log.info("👋 [三方成员挂机] {} 挂机退出，当前存活成员数: {}, 剩余在线成员: [{}]",
                memberName, aliveCount, remainingDesc.toString().trim());

        // 情况 1：还有 2 人在线 -> 保持三方会议室通话，绝不挂断其余在线成员！
        if (aliveCount >= 2) {
            callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuid,
                    "LEG", "THREE_WAY_MEMBER_LEAVE",
                    memberName + " 挂机退出三方通话，其余成员继续在会议中保持通话: " + remainingDesc.toString().trim());
            return;
        }

        // 情况 2：只剩 1 人在线 (第 2 个人也挂机了) -> 单人无法继续通话，挂断最后一人并结束会话
        if (aliveCount == 1) {
            callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuid,
                    "LEG", "THREE_WAY_MEMBER_LEAVE",
                    memberName + " 挂机退出，会议中仅剩一人，会话将自动结束并释放最后一名成员: " + remainingDesc.toString().trim());
            stopRecording(context);

            // 释放最后存活的那个人
            if (!context.isAHungup() && context.getUuidA() != null) {
                log.info("🏁 [三方结束释放] 挂断最后存活成员 A: {}", context.getUuidA());
                commandGateway.kill(context.getUuidA(), cause);
            }
            if (!context.isBHungup() && context.getUuidB() != null) {
                log.info("🏁 [三方结束释放] 挂断最后存活成员 B: {}", context.getUuidB());
                commandGateway.kill(context.getUuidB(), cause);
            }
            if (!context.isCHungup() && context.getUuidC() != null) {
                log.info("🏁 [三方结束释放] 挂断最后存活成员 C: {}", context.getUuidC());
                commandGateway.kill(context.getUuidC(), cause);
            }

            endSessionNormally("三方通话成员陆续退出，仅剩单人结束", context);
            checkAndCleanContext(context);
            return;
        }

        // 情况 3：所有成员均已挂机退出
        stopRecording(context);
        endSessionNormally("三方通话所有成员已挂机退出", context);
        checkAndCleanContext(context);
    }

    private void handleSurveyInitiated(BridgeContext context) {
        String uuidA = context.getUuidA();
        context.setInSurvey(true);
        log.info("⭐ [满意度评价] 坐席 B 已挂机，拦截客户 A 挂断，转入满意度评价 IVR: uuidA={}", uuidA);

        // 1. 停止当前通话录音（录音只记录人工对话部分）
        stopRecording(context);

        // 2. 播放评价语音并等待用户按键 1-5 (自动开启 start_dtmf，结束后回到 park)
        String audioFile = "/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-freeswitch/sounds/ivr_evaluation.wav";
        FreeSwitchCommandResult playResult = commandGateway.playAndGetDigitsThenPark(
                uuidA, 1, 1, 1, 8000, "#", audioFile, "silence_stream://250", "survey_score", "[1-5]");
        commandLogService.saveCommandResult(playResult);

        callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuidA,
                "SURVEY", "SURVEY_INITIATED", "坐席服务结束挂机，引导客户进入满意度评价流程 (播放评价语音，等待按键1-5)");

        // 3. 设置安全超时（15秒内客户未按键且未挂断，系统自动挂机结束）
        CompletableFuture.delayedExecutor(15, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
            synchronized (context) {
                if (context.isInSurvey() && !context.isAHungup()) {
                    log.info("⏰ [满意度超时] 客户 A 未在规定时间内按键，自动挂机: {}", uuidA);
                    callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuidA,
                            "SURVEY", "SURVEY_TIMEOUT", "评价超时未按键，系统自动挂断");
                    context.setInSurvey(false);
                    endSessionNormally("满意度评价超时未按键结束", context);
                    commandGateway.kill(uuidA, "NORMAL_CLEARING");
                    checkAndCleanContext(context);
                }
            }
        });
    }

    private void handleDtmf(String uuid, Map<String, String> headers, BridgeContext context) {
        if (!context.isInSurvey() || !uuid.equals(context.getUuidA())) {
            return;
        }
        String digit = headers.get("DTMF-Digit");
        String durationStr = headers.get("DTMF-Duration");
        Integer durationMs = durationStr != null ? Integer.valueOf(durationStr) : null;
        log.info("⭐ [满意度按键] 收到客户 A 满意度评价按键: {}, 时长: {}ms, uuid={}", digit, durationMs, uuid);

        context.setSurveyScore(digit);
        context.setInSurvey(false);

        // 1. 记录按键明细到 dtmf_record (stepName: SATISFACTION)
        dtmfRecordService.recordDigit(context.getSessionId(), uuid, context.getExtensionA(), digit, durationMs, "SATISFACTION");

        // 2. 记录时间线
        callTimelineService.record(context.getSessionId(), context.getBizId(), null, uuid,
                "SURVEY", "SURVEY_COMPLETED", "客户已完成满意度评价，打分: " + digit + " 分 (1-非常满意, 2-满意, 3-一般, 4-不满意, 5-非常不满意)");

        // 3. 更新会话状态为正常结束并写入满意度打分
        endSessionNormally("满意度评价完成(打分: " + digit + "分)", context);

        // 4. 评价完成，礼貌挂机
        commandGateway.kill(uuid, "NORMAL_CLEARING");
        checkAndCleanContext(context);
    }

    private void handleRecordStop(String uuid, Map<String, String> headers, BridgeContext context) {
        log.info("⏹️ [ESL事件] 捕获 RECORD_STOP: UUID={}, bizId={}", uuid, context.getBizId());
        if (context.getRecordFileRecordId() != null && context.getRecordingFilePath() != null) {
            callFileRecordService.updateActualFileSize(context.getRecordFileRecordId(), context.getRecordingFilePath());
        }
    }

    private void handleTransferFallback(String cause, BridgeContext context) {
        String uuidA = context.getUuidA();
        String uuidB = context.getUuidB();
        log.warn("🔄 [转接失败回退] 分机 C({}) 未应答 (原因={})，立即停止 A 回铃，重新桥接 A({}) <-> B({})",
                context.getExtensionC(), cause, uuidA, uuidB);

        callLegService.updateStatusByUuid(context.getUuidC(), "FAILED");

        // 1. 停止 A 的回铃音
        commandGateway.stopPlayback(uuidA);

        // 2. 检查 B 是否仍在线
        if (context.isBHungup()) {
            log.warn("⚠️ [回退异常] 原坐席 B 已挂断，无法回退桥接，挂断 A 通道");
            commandGateway.kill(uuidA, cause);
            endSessionNormally("转接失败且坐席已退出", context);
            checkAndCleanContext(context);
            return;
        }

        // 3. 恢复 A 与 B 的 hangup_after_bridge 联动
        commandGateway.setVar(uuidA, "hangup_after_bridge", "true");
        commandGateway.setVar(uuidB, "hangup_after_bridge", "true");

        // 4. 重新桥接 A 与 B
        callTimelineService.record(context.getSessionId(), context.getBizId(), null, null,
                "COMMAND", "TRANSFER_FALLBACK_BRIDGED", "目标分机 C(" + context.getExtensionC() + ") 未接听 (" + cause + ")，自动回退重新桥接 A 与 B");
        FreeSwitchCommandResult fallbackResult = commandGateway.bridge(uuidA, uuidB);
        commandLogService.saveCommandResult(fallbackResult);

        callLegService.updateStatusByUuid(uuidB, "BRIDGED");
        context.setTransferring(false);
        log.info("✅ [转接回退成功] A 与 B 重新恢复桥接通话");
    }

    private void endSessionNormally(String reason, BridgeContext context) {
        if (!context.isEnded()) {
            context.setEnded(true);
            callSessionService.updateStatus(context.getSessionId(), 2, reason);
            callTimelineService.record(context.getSessionId(), context.getBizId(), null, null,
                    "SESSION", "SESSION_ENDED", "通话结束: " + reason);
        }
    }

    private void checkAndCleanContext(BridgeContext context) {
        boolean aDone = context.isAHungup();
        boolean bDone = context.isBHungup();
        boolean cDone = context.getUuidC() == null || context.isCHungup();

        if (aDone && bDone && cDone) {
            stopRecording(context);
            bridgeTasks.remove(context.getBizId());
            if (context.getUuidA() != null) uuidToBizIdMap.remove(context.getUuidA());
            if (context.getUuidB() != null) uuidToBizIdMap.remove(context.getUuidB());
            if (context.getUuidC() != null) uuidToBizIdMap.remove(context.getUuidC());
            log.info("🧹 [双呼清理] 所有关联通道已全部释放，清理上下文缓存: bizId={}", context.getBizId());
        }
    }
}
