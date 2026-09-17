/*
 * chandler25-jdk17-freeswitch
 * 2026/3/13 13:47
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package com.chandler.freeswitch.client.example.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandGateway;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandResult;
import com.chandler.freeswitch.client.example.domain.dataobject.*;
import com.chandler.freeswitch.client.example.listener.CallBridgeListener;
import com.chandler.freeswitch.client.example.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.*;

/**
 * 通话业务控制器：提供双呼桥接、DTMF放音收号、通道挂断与测试数据全景查询
 *
 * @author 钱丁君-chandler
 * @version 2.0.0
 */
@Tag(name = "通话业务控制与测试接口")
@Slf4j
@RestController
@RequestMapping("/api/call")
public class CallController {

    @Autowired
    private FreeSwitchCommandGateway commandGateway;
    @Autowired
    private UserStatusService userStatusService;
    @Autowired
    private CallSessionService callSessionService;
    @Autowired
    private CallLegService callLegService;
    @Autowired
    private CommandLogService commandLogService;
    @Autowired
    private CallBridgeListener callBridgeListener;
    @Autowired
    private CallEventLogService callEventLogService;
    @Autowired
    private CallTimelineService callTimelineService;
    @Autowired
    private DtmfRecordService dtmfRecordService;
    @Autowired
    private CallFileRecordService callFileRecordService;

    /**
     * 发起双呼桥接通话：如 1007 呼叫 1008
     * 访问地址示例：http://localhost:17681/api/call/bridge?a=1007&b=1008
     */
    @Operation(summary = "发起双呼桥接通话", description = "同时向两个分机发起呼叫进入Park，双方接听后自动执行Bridge强制桥接，全量持久化CallSession与CallLeg")
    @RequestMapping(value = "/bridge", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> bridgeCall(
            @RequestParam String a,
            @RequestParam String b,
            @RequestParam(defaultValue = "true") Boolean autoRecord,
            @RequestParam(defaultValue = "true") Boolean enableSurvey) {
        String bizId = "bridge-" + UUID.randomUUID().toString();
        String uuidA = UUID.randomUUID().toString();
        String uuidB = UUID.randomUUID().toString();
        Date now = new Date();

        log.info("🚀 [双呼发起] 分机A: {}, 分机B: {}, bizId: {}, uuidA: {}, uuidB: {}", a, b, bizId, uuidA, uuidB);

        // 1. 创建并持久化 CallSession (direction=3 表示双呼)
        CallSession callSession = CallSession.builder()
                .bizId(bizId)
                .direction(3)
                .caller(a)
                .callee(b)
                .status(0) // 0-呼叫中
                .startTime(now)
                .build();
        callSessionService.save(callSession);

        // 2. 写入时间线：会话创建
        callTimelineService.record(callSession.getId(), bizId, null, null,
                "SESSION", "SESSION_CREATED", "发起双呼请求: 分机 " + a + " (主叫) 呼叫 分机 " + b + " (被叫)");

        // 3. 创建并持久化 Leg-A (Caller)
        CallLeg legA = CallLeg.builder()
                .sessionId(callSession.getId())
                .legType("a-leg")
                .uuid(uuidA)
                .extension(a)
                .status("ORIGINATING")
                .build();
        callLegService.save(legA);
        callTimelineService.record(callSession.getId(), bizId, legA.getId(), uuidA,
                "LEG", "LEG_ORIGINATED", "已生成 Leg-A (主叫腿: " + a + ", UUID=" + uuidA + ")");

        // 4. 创建并持久化 Leg-B (Callee)
        CallLeg legB = CallLeg.builder()
                .sessionId(callSession.getId())
                .legType("b-leg")
                .uuid(uuidB)
                .extension(b)
                .status("ORIGINATING")
                .build();
        callLegService.save(legB);
        callTimelineService.record(callSession.getId(), bizId, legB.getId(), uuidB,
                "LEG", "LEG_ORIGINATED", "已生成 Leg-B (被叫腿: " + b + ", UUID=" + uuidB + ")");

        // 5. 将上下文注册到 CallBridgeListener 中以便事件驱动桥接和生命周期推进
        callBridgeListener.registerBridgeContext(CallBridgeListener.BridgeContext.builder()
                .sessionId(callSession.getId())
                .bizId(bizId)
                .uuidA(uuidA)
                .extensionA(a)
                .uuidB(uuidB)
                .extensionB(b)
                .autoRecord(Boolean.TRUE.equals(autoRecord))
                .enableSurvey(Boolean.TRUE.equals(enableSurvey))
                .build());

        // 6. 分别向分机 A 和分机 B 下发 originate 指令，接通后 park (锁定 PCMU/PCMA，开启 liberal_dtmf)
        Map<String, String> varsA = new LinkedHashMap<>();
        varsA.put("my_biz_id", bizId);
        varsA.put("my_role", "caller");
        varsA.put("liberal_dtmf", "true");
        varsA.put("absolute_codec_string", "PCMU,PCMA");
        if (Boolean.TRUE.equals(enableSurvey)) {
            // 开启满意度评价时，A 端在 B 挂机后不自动挂断，而是自动转入 park 准备播放评价语音
            varsA.put("hangup_after_bridge", "false");
            varsA.put("park_after_bridge", "true");
        } else {
            varsA.put("hangup_after_bridge", "true");
        }

        FreeSwitchCommandResult resultA = commandGateway.originateUserToPark(a, uuidA, varsA);
        commandLogService.saveCommandResult(resultA);

        Map<String, String> varsB = new LinkedHashMap<>();
        varsB.put("my_biz_id", bizId);
        varsB.put("my_role", "callee");
        varsB.put("liberal_dtmf", "true");
        varsB.put("absolute_codec_string", "PCMU,PCMA");
        varsB.put("hangup_after_bridge", "true");

        FreeSwitchCommandResult resultB = commandGateway.originateUserToPark(b, uuidB, varsB);
        commandLogService.saveCommandResult(resultB);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "双呼桥接呼叫已下发");
        resp.put("sessionId", callSession.getId());
        resp.put("bizId", bizId);
        resp.put("caller", Map.of("extension", a, "uuid", uuidA, "legId", legA.getId()));
        resp.put("callee", Map.of("extension", b, "uuid", uuidB, "legId", legB.getId()));
        return resp;
    }

    @Operation(summary = "双呼转接目标分机(支持A听回铃及C未接听回退重新桥接A与B)")
    @PostMapping("/transfer")
    public Map<String, Object> transferCall(
            @RequestParam(required = false) Long sessionId,
            @RequestParam String c,
            @RequestParam(defaultValue = "20") Integer timeoutSeconds) {

        CallBridgeListener.BridgeContext context = null;
        if (sessionId != null) {
            context = callBridgeListener.getBridgeContextBySessionId(sessionId);
        } else {
            context = callBridgeListener.getLatestActiveBridgeContext();
        }

        if (context == null) {
            return Map.of("code", 400, "message", "未找到处于通话中的双呼会话，请先发起双呼接通后再转接");
        }

        if (context.isEnded() || !context.isBridged()) {
            return Map.of("code", 400, "message", "当前双呼会话未处于桥接通话中，无法转接");
        }

        if (context.isTransferring()) {
            return Map.of("code", 400, "message", "当前通话已在转接中，请勿重复发起");
        }

        String uuidA = context.getUuidA();
        String uuidB = context.getUuidB();
        String bizId = context.getBizId();
        Long currentSessionId = context.getSessionId();
        String uuidC = UUID.randomUUID().toString();

        log.info("🔀 [发起转接] sessionId={}, bizId={}, A={}, B={}, 目标C={}, uuidC={}",
                currentSessionId, bizId, context.getExtensionA(), context.getExtensionB(), c, uuidC);

        // 1. 创建并持久化 Leg-C
        CallLeg legC = CallLeg.builder()
                .sessionId(currentSessionId)
                .legType("c-leg")
                .uuid(uuidC)
                .extension(c)
                .status("ORIGINATING")
                .build();
        callLegService.save(legC);

        // 2. 注册转接上下文到 CallBridgeListener
        callBridgeListener.updateBridgeContextForTransfer(bizId, uuidC, c);

        // 3. 临时关闭 A 和 B 的 hangup_after_bridge，防止 unbridge 时被自动挂断
        commandGateway.setVar(uuidA, "hangup_after_bridge", "false");
        commandGateway.setVar(uuidB, "hangup_after_bridge", "false");

        // 4. 将 A 和 B 解除桥接并转入 park 后台等待 (纯 park，避免逗号被 inline 解析器错误拆分)
        commandGateway.transferInline(uuidA, "park");
        commandGateway.transferInline(uuidB, "park");

        // 5. 对处于 park 状态的 A 广播放音，播放循环标准回铃音 (uuid_broadcast 独立执行，不受逗号解析影响)
        commandGateway.play(uuidA, "tone_stream://%(2000,4000,440,480);loops=-1", "aleg");

        // 6. 记录时间线
        callTimelineService.record(currentSessionId, bizId, legC.getId(), uuidC,
                "TRANSFER", "TRANSFER_INITIATED", "发起咨询转接: A与B解除桥接，A听标准回铃音，开始呼叫目标分机 " + c);

        // 7. 向分机 C 发起呼叫 (带上超时时间，锁定PCMU/PCMA)
        FreeSwitchCommandResult resultC = commandGateway.originateUserToPark(c, uuidC, Map.of(
                "my_biz_id", bizId,
                "my_role", "transferee",
                "liberal_dtmf", "true",
                "absolute_codec_string", "PCMU,PCMA",
                "hangup_after_bridge", "true",
                "originate_timeout", String.valueOf(timeoutSeconds)
        ));
        commandLogService.saveCommandResult(resultC);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "咨询转接已发起，分机 A 正在听回铃，正在呼叫分机 " + c);
        resp.put("sessionId", currentSessionId);
        resp.put("bizId", bizId);
        resp.put("caller", Map.of("extension", context.getExtensionA(), "uuid", uuidA));
        resp.put("originalAgent", Map.of("extension", context.getExtensionB(), "uuid", uuidB));
        resp.put("targetAgent", Map.of("extension", c, "uuid", uuidC, "legId", legC.getId()));
        return resp;
    }

    @Operation(summary = "双呼三方通话(邀请第三方专家C加入A与B通话)")
    @PostMapping("/three-way")
    public Map<String, Object> threeWayCall(
            @RequestParam(required = false) Long sessionId,
            @RequestParam String c,
            @RequestParam(defaultValue = "30") Integer timeoutSeconds) {

        CallBridgeListener.BridgeContext context = null;
        if (sessionId != null) {
            context = callBridgeListener.getBridgeContextBySessionId(sessionId);
        } else {
            context = callBridgeListener.getLatestActiveBridgeContext();
        }

        if (context == null) {
            return Map.of("code", 400, "message", "未找到处于通话中的双呼会话，请先发起双呼接通后再发起三方通话");
        }

        if (context.isEnded() || !context.isBridged()) {
            return Map.of("code", 400, "message", "当前双呼会话未处于桥接通话中，无法发起三方通话");
        }

        if (context.isThreeWay()) {
            return Map.of("code", 400, "message", "当前通话已处于三方通话模式中");
        }

        String uuidA = context.getUuidA();
        String uuidB = context.getUuidB();
        String bizId = context.getBizId();
        Long currentSessionId = context.getSessionId();
        String uuidC = UUID.randomUUID().toString();
        String confName = "conf_" + bizId.replace("-", "_");

        log.info("👥 [发起三方通话] sessionId={}, bizId={}, A={}, B={}, 目标专家C={}, uuidC={}, confName={}",
                currentSessionId, bizId, context.getExtensionA(), context.getExtensionB(), c, uuidC, confName);

        // 1. 创建并持久化 Leg-C
        CallLeg legC = CallLeg.builder()
                .sessionId(currentSessionId)
                .legType("c-leg")
                .uuid(uuidC)
                .extension(c)
                .status("ORIGINATING")
                .build();
        callLegService.save(legC);

        // 2. 注册三方通话上下文到 CallBridgeListener
        callBridgeListener.updateBridgeContextForThreeWay(bizId, uuidC, c, confName);

        // 3. 关闭 A 和 B 的 hangup_after_bridge 联动并开启 park_after_bridge
        commandGateway.setVar(uuidA, "hangup_after_bridge", "false");
        commandGateway.setVar(uuidB, "hangup_after_bridge", "false");
        commandGateway.setVar(uuidA, "park_after_bridge", "true");
        commandGateway.setVar(uuidB, "park_after_bridge", "true");

        // 4. 将 A 和 B 转入三方会议室 (A 与 B 保持实时通话不掉线)
        commandGateway.transferInline(uuidA, "conference:" + confName + "@default");
        commandGateway.transferInline(uuidB, "conference:" + confName + "@default");

        // 5. 记录时间线
        callTimelineService.record(currentSessionId, bizId, legC.getId(), uuidC,
                "THREE_WAY", "THREE_WAY_INITIATED", "发起三方通话: A与B进入会议室保持通话，开始呼叫专家分机 " + c);

        // 6. 向专家 C 发起呼叫，接通后 park (由 CallBridgeListener 自动将其加入三方会议室)
        FreeSwitchCommandResult resultC = commandGateway.originateUserToPark(c, uuidC, Map.of(
                "my_biz_id", bizId,
                "my_role", "expert",
                "liberal_dtmf", "true",
                "absolute_codec_string", "PCMU,PCMA",
                "hangup_after_bridge", "false",
                "park_after_bridge", "true",
                "originate_timeout", String.valueOf(timeoutSeconds)
        ));
        commandLogService.saveCommandResult(resultC);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "三方通话已发起，分机 A 与 B 保持通话中，正在呼叫专家分机 " + c);
        resp.put("sessionId", currentSessionId);
        resp.put("bizId", bizId);
        resp.put("conferenceName", confName);
        resp.put("caller", Map.of("extension", context.getExtensionA(), "uuid", uuidA));
        resp.put("agent", Map.of("extension", context.getExtensionB(), "uuid", uuidB));
        resp.put("expert", Map.of("extension", c, "uuid", uuidC, "legId", legC.getId()));
        return resp;
    }

    @Operation(summary = "手动开启通话录音")
    @PostMapping("/{sessionId}/record/start")
    public Map<String, Object> startRecord(@PathVariable Long sessionId) {
        CallBridgeListener.BridgeContext context = callBridgeListener.getBridgeContextBySessionId(sessionId);
        if (context == null || context.isEnded()) {
            return Map.of("code", 400, "message", "通话会话不存在或已结束");
        }
        if (context.isRecording()) {
            return Map.of("code", 0, "message", "当前通话已在录音中", "filePath", context.getRecordingFilePath());
        }
        callBridgeListener.startRecording(context);
        return Map.of("code", 0, "message", "录音已开启", "filePath", context.getRecordingFilePath());
    }

    @Operation(summary = "手动停止通话录音")
    @PostMapping("/{sessionId}/record/stop")
    public Map<String, Object> stopRecord(@PathVariable Long sessionId) {
        CallBridgeListener.BridgeContext context = callBridgeListener.getBridgeContextBySessionId(sessionId);
        if (context == null) {
            return Map.of("code", 400, "message", "通话会话不存在");
        }
        if (!context.isRecording()) {
            return Map.of("code", 0, "message", "当前通话未处于录音状态");
        }
        String path = context.getRecordingFilePath();
        callBridgeListener.stopRecording(context);
        return Map.of("code", 0, "message", "录音已停止", "filePath", path);
    }

    @Operation(summary = "查询通话录音文件列表")
    @GetMapping("/{sessionId}/record")
    public Map<String, Object> getRecordList(@PathVariable Long sessionId) {
        CallSession session = callSessionService.getById(sessionId);
        if (session == null) {
            return Map.of("code", 404, "message", "通话会话不存在");
        }
        List<CallLeg> legs = callLegService.listBySessionId(sessionId);
        List<CallFileRecord> allRecords = new ArrayList<>();
        for (CallLeg leg : legs) {
            allRecords.addAll(callFileRecordService.listByUuid(leg.getUuid()));
        }
        return Map.of("code", 0, "sessionId", sessionId, "records", allRecords);
    }

    @Operation(summary = "下载或试听通话录音文件")
    @GetMapping("/{sessionId}/record/download")
    public ResponseEntity<Resource> downloadRecord(@PathVariable Long sessionId) {
        List<CallLeg> legs = callLegService.listBySessionId(sessionId);
        CallFileRecord targetRecord = null;
        for (CallLeg leg : legs) {
            List<CallFileRecord> list = callFileRecordService.listByUuid(leg.getUuid());
            if (!list.isEmpty()) {
                targetRecord = list.get(0);
                break;
            }
        }
        if (targetRecord == null || targetRecord.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        File file = new File(targetRecord.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + targetRecord.getFileName() + "\"")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(resource);
    }

    @Operation(summary = "发起DTMF测试呼叫", description = "拨打指定分机号，接通后播放并监听按键")
    @GetMapping("/dtmf/start")
    public String startIvrCall(
            @RequestParam String userId,
            @RequestParam(defaultValue = "/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-freeswitch/sounds/ivr_navigation.wav") String audioFile) {

        String uuid = UUID.randomUUID().toString();
        String bizId = "dtmf-" + UUID.randomUUID();
        Date now = new Date();
        log.info("🚀 准备发起 DTMF 呼叫，目标分机: {}, 播放语音: {}, UUID: {}, bizId: {}", userId, audioFile, uuid, bizId);

        CallSession callSession = CallSession.builder()
                .bizId(bizId)
                .direction(2)
                .caller("SYSTEM")
                .callee(userId)
                .status(0)
                .startTime(now)
                .build();
        callSessionService.save(callSession);

        callTimelineService.record(callSession.getId(), bizId, null, null,
                "SESSION", "SESSION_CREATED", "发起IVR测试呼叫: 目标分机 " + userId);

        CallLeg callLeg = CallLeg.builder()
                .sessionId(callSession.getId())
                .legType("a-leg")
                .uuid(uuid)
                .extension(userId)
                .status("ORIGINATING")
                .build();
        callLegService.save(callLeg);

        callTimelineService.record(callSession.getId(), bizId, callLeg.getId(), uuid,
                "LEG", "LEG_ORIGINATED", "已生成 IVR 通道腿: 分机=" + userId + ", UUID=" + uuid);

        commandLogService.saveBusinessLog(uuid, CommandLogService.DTMF_AUDIO_FILE_COMMAND, audioFile, "ACCEPTED", 1);

        // 接通后进入 park，由 DemoDtmfEventListener 执行 playAndGetDigitsThenPark 放音收号
        // 锁定 PCMU/PCMA 消除宽带编码采样率与PT不匹配问题，并携带 liberal_dtmf=true 自动兼容 SIP INFO
        FreeSwitchCommandResult result = commandGateway.originateUserToPark(userId, uuid, Map.of(
                "my_biz_id", bizId,
                "liberal_dtmf", "true",
                "absolute_codec_string", "PCMU,PCMA"
        ));
        commandLogService.saveCommandResult(result);

        return "DTMF 呼叫指令已下发，sessionId: " + callSession.getId()
                + "，UUID: " + uuid
                + "，目标用户: " + userId;
    }

    @Operation(summary = "查询通话会话全景详情(Session + Legs + Timeline + Events + DTMF)")
    @GetMapping("/session/{sessionId}")
    public Map<String, Object> getSessionDetail(@PathVariable Long sessionId) {
        CallSession session = callSessionService.getById(sessionId);
        if (session == null) {
            return Map.of("code", 404, "message", "通话会话不存在");
        }
        List<CallLeg> legs = callLegService.listBySessionId(sessionId);
        List<CallTimeline> timeline = callTimelineService.listBySessionId(sessionId);
        List<CallEventLog> events = callEventLogService.listBySessionId(sessionId);
        List<DtmfRecord> dtmfList = dtmfRecordService.listBySessionId(sessionId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("session", session);
        resp.put("legs", legs);
        resp.put("timeline", timeline);
        resp.put("events", events);
        resp.put("dtmfList", dtmfList);
        return resp;
    }

    @Operation(summary = "查询通话时间线轨迹")
    @GetMapping("/session/{sessionId}/timeline")
    public Map<String, Object> getSessionTimeline(@PathVariable Long sessionId) {
        return Map.of(
                "code", 0,
                "sessionId", sessionId,
                "timeline", callTimelineService.listBySessionId(sessionId)
        );
    }

    @Operation(summary = "查询通话收到的底层ESL事件明细")
    @GetMapping("/session/{sessionId}/events")
    public Map<String, Object> getSessionEvents(@PathVariable Long sessionId) {
        return Map.of(
                "code", 0,
                "sessionId", sessionId,
                "events", callEventLogService.listBySessionId(sessionId)
        );
    }

    @Operation(summary = "查询通话按键DTMF明细")
    @GetMapping("/session/{sessionId}/dtmf")
    public Map<String, Object> getSessionDtmf(@PathVariable Long sessionId) {
        return Map.of(
                "code", 0,
                "sessionId", sessionId,
                "dtmfList", dtmfRecordService.listBySessionId(sessionId)
        );
    }

    @Operation(summary = "通过业务ID查询通话会话详情")
    @GetMapping("/session/by-biz-id/{bizId}")
    public Map<String, Object> getSessionByBizId(@PathVariable String bizId) {
        CallSession session = callSessionService.getByBizId(bizId);
        if (session == null) {
            return Map.of("code", 404, "message", "通话会话不存在");
        }
        return getSessionDetail(session.getId());
    }

    @Operation(summary = "查询所有正在进行的通话会话")
    @GetMapping("/session/active")
    public Map<String, Object> getActiveSessions() {
        List<CallSession> list = callSessionService.list(
                new LambdaQueryWrapper<CallSession>()
                        .in(CallSession::getStatus, Arrays.asList(0, 1))
                        .orderByDesc(CallSession::getId)
        );
        return Map.of("code", 0, "total", list.size(), "sessions", list);
    }

    /**
     * 方式 B：通过分机号挂断（更符合业务逻辑）
     */
    @Operation(summary = "通过分机号挂断当前活跃通话")
    @DeleteMapping("/hangup-user/{userId}")
    public String hangupUser(@PathVariable String userId) {
        // 1. 从数据库查出该分机当前对应的活跃 UUID
        UserStatus userStatus = userStatusService.getOne(
                new LambdaQueryWrapper<UserStatus>()
                        .eq(UserStatus::getUserId, userId)
        );

        if (userStatus == null || StringUtils.isEmpty(userStatus.getChannelUuid())) {
            return "该用户当前没有活跃的通话通道";
        }

        // 2. 执行挂断
        commandGateway.kill(userStatus.getChannelUuid());

        return "指令已下发：正在挂断分机 " + userId + " 的通道 " + userStatus.getChannelUuid();
    }
}
