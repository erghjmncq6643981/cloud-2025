package com.chandler.freeswitch.client.example.command;

import link.thingscloud.freeswitch.esl.InboundClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Component
public class FreeSwitchCommandGateway {

    public static final String DEFAULT_HANGUP_CAUSE = "NORMAL_CLEARING";
    public static final String DEFAULT_DIALPLAN = "XML";
    public static final String DEFAULT_CONTEXT = "default";

    private final InboundClient inboundClient;
    private final String fsAddr;

    public FreeSwitchCommandGateway(
            InboundClient inboundClient,
            @Value("${freeswitch.command.fs-addr:127.0.0.1:8022}") String fsAddr) {
        this.inboundClient = inboundClient;
        this.fsAddr = fsAddr;
    }

    /**
     * 呼叫指定分机，接通后进入 park。
     * 用于验证 originate 发起呼叫、指定 origination_uuid、接通后可继续由 Java 控制。
     */
    public FreeSwitchCommandResult originateUserToPark(String userId, String uuid, Map<String, String> channelVariables) {
        return originateUser(userId, uuid, channelVariables, "park");
    }

    /**
     * 呼叫指定分机，接通后先播放一个媒体流或音频。
     * 用于验证 originate + playback 的基础放音能力。
     */
    public FreeSwitchCommandResult originateUserPlayback(String userId, String uuid, String playbackTarget) {
        return originateUser(userId, uuid, Map.of("absolute_codec_string", "PCMU"),
                "playback(" + safeArgument(playbackTarget, "playbackTarget") + ")");
    }

    /**
     * 通用分机呼叫入口。
     * userId 是被叫分机，uuid 是期望绑定的通道 UUID，channelVariables 会写入 FreeSWITCH 通道变量，application 是接通后执行的 app。
     */
    public FreeSwitchCommandResult originateUser(String userId, String uuid, Map<String, String> channelVariables, String application) {
        String safeUserId = safeToken(userId, "userId");
        String safeApplication = safeArgument(application, "application");

        Map<String, String> variables = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(uuid)) {
            variables.put("origination_uuid", safeToken(uuid, "uuid"));
        }
        if (channelVariables != null) {
            channelVariables.forEach((key, value) -> variables.put(safeToken(key, "channelVariableKey"), safeArgument(value, "channelVariableValue")));
        }

        String argument = formatChannelVariables(variables) + "user/" + safeUserId + " &" + safeApplication;
        return sendAsync("originate", argument, uuid);
    }

    /**
     * 应答指定通道。
     * 用于验证 uuid_answer 对呼入或已创建通道的控制能力。
     */
    public FreeSwitchCommandResult answer(String uuid) {
        return sendAsync("uuid_answer", safeToken(uuid, "uuid"), uuid);
    }

    /**
     * 对指定通道进行早期应答。
     * 用于验证早媒体、回铃音或接听前放音等场景。
     */
    public FreeSwitchCommandResult preAnswer(String uuid) {
        return sendAsync("uuid_pre_answer", safeToken(uuid, "uuid"), uuid);
    }

    /**
     * 使用默认原因挂断指定通道。
     * 默认原因是 NORMAL_CLEARING，适合大多数正常挂断验证。
     */
    public FreeSwitchCommandResult kill(String uuid) {
        return kill(uuid, DEFAULT_HANGUP_CAUSE);
    }

    /**
     * 使用指定 hangup cause 挂断通道。
     * 用于验证 uuid_kill，以及不同挂断原因在 CHANNEL_HANGUP 事件中的表现。
     */
    public FreeSwitchCommandResult kill(String uuid, String cause) {
        String argument = safeToken(uuid, "uuid") + " " + safeToken(cause, "cause");
        return sendAsync("uuid_kill", argument, uuid);
    }

    /**
     * 桥接两个已存在通道。
     * 用于验证双呼、坐席和客户接通后进入真实通话的能力。
     */
    public FreeSwitchCommandResult bridge(String uuidA, String uuidB) {
        String argument = safeToken(uuidA, "uuidA") + " " + safeToken(uuidB, "uuidB");
        return sendAsync("uuid_bridge", argument, uuidA);
    }

    /**
     * 将通道转入 park。
     * 用于验证通道被应用层接管后保持可控，而不是立即结束。
     */
    public FreeSwitchCommandResult park(String uuid) {
        return transferInline(uuid, "park");
    }

    /**
     * 保持指定通道。
     * 用于验证 uuid_hold 的保持能力，以及后续 CHANNEL_HOLD/媒体效果。
     */
    public FreeSwitchCommandResult hold(String uuid) {
        return sendAsync("uuid_hold", safeToken(uuid, "uuid"), uuid);
    }

    /**
     * 取消指定通道的保持状态。
     * 用于验证通话从 hold 恢复到正常媒体通路。
     */
    public FreeSwitchCommandResult unhold(String uuid) {
        String argument = "off " + safeToken(uuid, "uuid");
        return sendAsync("uuid_hold", argument, uuid);
    }

    /**
     * 将通道转接到默认 dialplan/context 下的目标号码。
     * 用于验证最常见的 uuid_transfer。
     */
    public FreeSwitchCommandResult transfer(String uuid, String destination) {
        return transfer(uuid, destination, DEFAULT_DIALPLAN, DEFAULT_CONTEXT);
    }

    /**
     * 将通道转接到指定 dialplan/context 下的目标。
     * destination 可以是分机、拨号计划入口或其它 FreeSWITCH 可识别目标。
     */
    public FreeSwitchCommandResult transfer(String uuid, String destination, String dialplan, String context) {
        String argument = safeToken(uuid, "uuid")
                + " " + safeArgument(destination, "destination")
                + " " + safeToken(defaultIfBlank(dialplan, DEFAULT_DIALPLAN), "dialplan")
                + " " + safeToken(defaultIfBlank(context, DEFAULT_CONTEXT), "context");
        return sendAsync("uuid_transfer", argument, uuid);
    }

    /**
     * 将桥接双方一起转接到指定目标。
     * 用于验证 -both 转接行为，适合观察桥接关系整体迁移。
     */
    public FreeSwitchCommandResult transferBoth(String uuid, String destination, String dialplan, String context) {
        String argument = safeToken(uuid, "uuid")
                + " -both " + safeArgument(destination, "destination")
                + " " + safeToken(defaultIfBlank(dialplan, DEFAULT_DIALPLAN), "dialplan")
                + " " + safeToken(defaultIfBlank(context, DEFAULT_CONTEXT), "context");
        return sendAsync("uuid_transfer", argument, uuid);
    }

    /**
     * 只转接指定通道的 B-leg。
     * 用于验证桥接通话中只移动对端 leg 的控制效果。
     */
    public FreeSwitchCommandResult transferBleg(String uuid, String destination, String dialplan, String context) {
        String argument = safeToken(uuid, "uuid")
                + " -bleg " + safeArgument(destination, "destination")
                + " " + safeToken(defaultIfBlank(dialplan, DEFAULT_DIALPLAN), "dialplan")
                + " " + safeToken(defaultIfBlank(context, DEFAULT_CONTEXT), "context");
        return sendAsync("uuid_transfer", argument, uuid);
    }

    /**
     * 将通道转入 inline application。
     * 用于验证 park、play_and_get_digits、conference 等无需 dialplan 的即时控制动作。
     */
    public FreeSwitchCommandResult transferInline(String uuid, String inlineApplication) {
        String argument = safeToken(uuid, "uuid") + " '" + safeArgument(inlineApplication, "inlineApplication") + "' inline";
        return sendAsync("uuid_transfer", argument, uuid);
    }

    /**
     * 盲转到指定分机。
     * 用于验证通话中直接把一方转给第三方，不做咨询确认。
     */
    public FreeSwitchCommandResult blindTransferToExtension(String uuid, String extension) {
        return transfer(uuid, extension, DEFAULT_DIALPLAN, DEFAULT_CONTEXT);
    }

    /**
     * 向指定通道播放媒体。
     * leg 通常使用 aleg 或 bleg，用于验证 uuid_broadcast 放音和打断能力。
     */
    public FreeSwitchCommandResult play(String uuid, String mediaPath, String leg) {
        String argument = safeToken(uuid, "uuid")
                + " " + safeArgument(mediaPath, "mediaPath")
                + " " + safeToken(defaultIfBlank(leg, "aleg"), "leg");
        return sendAsync("uuid_broadcast", argument, uuid);
    }

    /**
     * 停止指定通道当前播放。
     * 用于验证放音可被业务侧主动打断。
     */
    public FreeSwitchCommandResult stopPlayback(String uuid) {
        String argument = safeToken(uuid, "uuid") + " all";
        return sendAsync("uuid_break", argument, uuid);
    }

    /**
     * 播放语音并收取一位按键，结束后回到 park。
     * 用于快速验证 IVR 放音、DTMF 事件、后续继续控通道。
     */
    public FreeSwitchCommandResult playAndGetDigitsThenPark(String uuid, String audioFile) {
        return playAndGetDigitsThenPark(uuid, 1, 1, 3, 5000, "#", audioFile,
                "silence_stream://250", "dtmf_var", "\\d+");
    }

    /**
     * 播放语音并按指定规则收号，结束后回到 park。
     * minDigits/maxDigits 控制位数，tries 控制重试次数，timeoutMs 控制收号超时，variableName 保存收号结果。
     */
    public FreeSwitchCommandResult playAndGetDigitsThenPark(
            String uuid,
            int minDigits,
            int maxDigits,
            int tries,
            int timeoutMs,
            String terminators,
            String audioFile,
            String invalidAudioFile,
            String variableName,
            String regex) {
        String application = String.format("play_and_get_digits:%d %d %d %d %s %s %s %s %s,park",
                minDigits,
                maxDigits,
                tries,
                timeoutMs,
                safeToken(terminators, "terminators"),
                safeArgument(audioFile, "audioFile"),
                safeArgument(invalidAudioFile, "invalidAudioFile"),
                safeToken(variableName, "variableName"),
                safeArgument(regex, "regex"));
        return transferInline(uuid, application);
    }

    /**
     * 开始录制指定通道。
     * 用于验证 uuid_record start、RECORD_START 事件和录音文件生成。
     */
    public FreeSwitchCommandResult recordStart(String uuid, String filePath) {
        String argument = safeToken(uuid, "uuid") + " start " + safeArgument(filePath, "filePath");
        return sendAsync("uuid_record", argument, uuid);
    }

    /**
     * 停止录制指定通道。
     * 用于验证 uuid_record stop、RECORD_STOP 事件和录音文件完整性。
     */
    public FreeSwitchCommandResult recordStop(String uuid, String filePath) {
        String argument = safeToken(uuid, "uuid") + " stop " + safeArgument(filePath, "filePath");
        return sendAsync("uuid_record", argument, uuid);
    }

    /**
     * 静音指定通道的指定媒体方向。
     * direction 只允许 read 或 write，用于验证单向静音控制。
     */
    public FreeSwitchCommandResult mute(String uuid, String direction) {
        String argument = safeToken(uuid, "uuid") + " start " + safeAudioDirection(direction) + " mute";
        return sendAsync("uuid_audio", argument, uuid);
    }

    /**
     * 恢复指定通道的静音方向。
     * 用于验证静音后的媒体恢复能力。
     */
    public FreeSwitchCommandResult unmute(String uuid, String direction) {
        String argument = safeToken(uuid, "uuid") + " stop " + safeAudioDirection(direction) + " mute";
        return sendAsync("uuid_audio", argument, uuid);
    }

    /**
     * 向指定通道发送 DTMF。
     * 用于验证业务侧主动发码，例如对接上游 IVR 或网关收号。
     */
    public FreeSwitchCommandResult sendDtmf(String uuid, String digits) {
        String argument = safeToken(uuid, "uuid") + " " + safeToken(digits, "digits");
        return sendAsync("uuid_send_dtmf", argument, uuid);
    }

    /**
     * 将通道加入会议。
     * 用于验证三方通话或多方会议的基础能力。
     */
    public FreeSwitchCommandResult joinConference(String uuid, String conferenceName) {
        return transferInline(uuid, "conference:" + safeArgument(conferenceName, "conferenceName"));
    }

    /**
     * 将 SIP 通道 deflect 到指定 SIP URI。
     * 用于验证网关侧支持的 SIP REFER/302 类转移能力。
     */
    public FreeSwitchCommandResult deflect(String uuid, String sipUri) {
        String argument = safeToken(uuid, "uuid") + " " + safeArgument(sipUri, "sipUri");
        return sendAsync("uuid_deflect", argument, uuid);
    }

    /**
     * 安排指定通道在若干秒后自动挂断。
     * 用于验证超时控制和补偿清理能力。
     */
    public FreeSwitchCommandResult schedHangup(String uuid, int seconds, String cause) {
        String argument = "+" + seconds + " " + safeToken(uuid, "uuid") + " " + safeToken(defaultIfBlank(cause, DEFAULT_HANGUP_CAUSE), "cause");
        return sendAsync("sched_hangup", argument, uuid);
    }

    /**
     * 设置指定通道变量。
     * 用于验证业务上下文写入 FreeSWITCH 通道并能在后续事件或查询中读取。
     */
    public FreeSwitchCommandResult setVar(String uuid, String name, String value) {
        String argument = safeToken(uuid, "uuid") + " " + safeToken(name, "name") + " " + safeArgument(value, "value");
        return sendAsync("uuid_setvar", argument, uuid);
    }

    /**
     * 查询指定通道变量。
     * 用于验证 Java 侧能按 UUID 读取通道上下文。
     */
    public FreeSwitchCommandResult getVar(String uuid, String name) {
        String argument = safeToken(uuid, "uuid") + " " + safeToken(name, "name");
        return sendSyncIfSupported("uuid_getvar", argument, uuid);
    }

    /**
     * 检查指定 UUID 的通道是否还存在。
     * 用于验证控制命令执行前后的通道生命周期。
     */
    public FreeSwitchCommandResult exists(String uuid) {
        return sendSyncIfSupported("uuid_exists", safeToken(uuid, "uuid"), uuid);
    }

    /**
     * 打印指定通道的详细变量和状态。
     * 用于排查通道变量、桥接关系、媒体状态和当前 application。
     */
    public FreeSwitchCommandResult dump(String uuid) {
        return sendSyncIfSupported("uuid_dump", safeToken(uuid, "uuid"), uuid);
    }

    /**
     * 查询当前 FreeSWITCH 上的活跃通道。
     * 用于验证测试前后是否存在残留通道。
     */
    public FreeSwitchCommandResult showChannels() {
        return sendSyncIfSupported("show", "channels", null);
    }

    /**
     * 查询当前 FreeSWITCH 上的活跃通话。
     * 用于验证桥接通话、会议通话等整体呼叫数量。
     */
    public FreeSwitchCommandResult showCalls() {
        return sendSyncIfSupported("show", "calls", null);
    }

    /**
     * 查询 FreeSWITCH 服务状态。
     * 用于验证 ESL 连接和底层服务是否正常。
     */
    public FreeSwitchCommandResult status() {
        return sendSyncIfSupported("status", null, null);
    }

    /**
     * 发送异步 API 命令。
     * 这是大部分通话控制命令的底层出口，返回值表示命令受理结果，不等同于最终通话状态。
     */
    public FreeSwitchCommandResult sendAsync(String command, String argument, String channelUuid) {
        String safeCommand = safeToken(command, "command");
        String safeArgument = nullableSafeArgument(argument, "argument");
        String response = inboundClient.sendAsyncApiCommand(fsAddr, safeCommand, safeArgument);
        log.info("FreeSWITCH async command sent, fsAddr={}, command={}, argument={}, response={}",
                fsAddr, safeCommand, safeArgument, response);
        return new FreeSwitchCommandResult(fsAddr, "async", safeCommand, safeArgument, response, channelUuid);
    }

    /**
     * 优先发送同步 API 命令，如果当前 InboundClient 不支持同步方法则退回异步。
     * 主要用于 dump、exists、show、status 这类查询命令。
     */
    public FreeSwitchCommandResult sendSyncIfSupported(String command, String argument, String channelUuid) {
        String safeCommand = safeToken(command, "command");
        String safeArgument = nullableSafeArgument(argument, "argument");
        try {
            Method method = inboundClient.getClass().getMethod("sendSyncApiCommand", String.class, String.class, String.class);
            Object response = method.invoke(inboundClient, fsAddr, safeCommand, safeArgument);
            log.info("FreeSWITCH sync command sent, fsAddr={}, command={}, argument={}, response={}",
                    fsAddr, safeCommand, safeArgument, response);
            return new FreeSwitchCommandResult(fsAddr, "sync", safeCommand, safeArgument,
                    response == null ? null : response.toString(), channelUuid);
        } catch (NoSuchMethodException e) {
            log.warn("sendSyncApiCommand is not available, fallback to async command={}", safeCommand);
            return sendAsync(safeCommand, safeArgument, channelUuid);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send sync FreeSWITCH command: " + safeCommand, e);
        }
    }

    private String formatChannelVariables(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        variables.forEach((key, value) -> joiner.add(key + "=" + value));
        return joiner.toString();
    }

    private String safeAudioDirection(String direction) {
        String safeDirection = safeToken(defaultIfBlank(direction, "read"), "direction");
        if (!"read".equals(safeDirection) && !"write".equals(safeDirection)) {
            throw new IllegalArgumentException("direction must be read or write");
        }
        return safeDirection;
    }

    private String safeToken(String value, String name) {
        String safeValue = safeArgument(value, name);
        if (StringUtils.containsWhitespace(safeValue)) {
            throw new IllegalArgumentException(name + " must not contain whitespace");
        }
        return safeValue;
    }

    private String safeArgument(String value, String name) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String safeValue = value.trim();
        if (safeValue.contains("\n") || safeValue.contains("\r")) {
            throw new IllegalArgumentException(name + " must not contain line breaks");
        }
        return safeValue;
    }

    private String nullableSafeArgument(String value, String name) {
        if (value == null) {
            return null;
        }
        return safeArgument(value, name);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.defaultIfBlank(value, defaultValue);
    }
}
