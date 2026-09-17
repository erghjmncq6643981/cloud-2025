package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 呼叫中心动作类型枚举
 */
@Getter
public enum ActionType {
    START("FNode.Start", "start"),
    EMPTY("FNode.Empty", ""),
    END("FNode.End", "end"),
    ROUTE("FNode.Route", "route"),
    DIRECT_ROUTE("FNode.Route", "direct_route"),

    // 通信控制类动作 (对应底层 FNode.* 指令)
    DIAL_AGENT("FNode.Dial", "dial-agent"),
    DIAL_GUEST("FNode.Dial", "dial-guest"),
    CHANNEL_BRIDGE("FNode.ChannelBridge", "channel-bridge"),
    RECORD("FNode.Record", "record"),
    RECORD_STOP("FNode.Record", "record-stop"),
    PLAY("FNode.Play", "play"),
    READ_DTMF("FNode.ReadDTMF", "read-dtmf"),
    DTMF_NAVIGATION("FNode.ReadDTMF", "dtmf-navigation"),
    DTMF_EVALUATION("FNode.ReadDTMF", "dtmf-evaluation"),
    TRANSFER("FNode.Transfer", "transfer"),
    HANGUP("FNode.Hangup", "hangup-all"),
    HANGUP_AGENT("FNode.Hangup", "hangup-agent"),
    HANGUP_GUEST("FNode.Hangup", "hangup-guest");

    private final String action;
    private final String id;

    ActionType(String action, String id) {
        this.action = action;
        this.id = id;
    }

    public static ActionType valueOfAction(String action) {
        for (ActionType type : ActionType.values()) {
            if (type.action.equalsIgnoreCase(action) || type.name().equalsIgnoreCase(action)) {
                return type;
            }
        }
        return EMPTY;
    }
}
