package com.chandler.fcc.flow;

import com.chandler.fcc.common.entity.CallInfoBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 呼叫会话上下文管理器：内存维护所有进行中的通话上下文（类似 call-center-backend 中的 RouteRecordService）
 */
@Slf4j
@Component
public class CallSessionManager {

    // ctrlUuid -> CallInfoBO
    private final Map<String, CallInfoBO> ctrlSessions = new ConcurrentHashMap<>();
    // channelUuid -> ctrlUuid
    private final Map<String, String> channelToCtrl = new ConcurrentHashMap<>();
    // callUuid -> ctrlUuid
    private final Map<String, String> callToCtrl = new ConcurrentHashMap<>();

    public void registerSession(CallInfoBO callInfo) {
        if (callInfo == null) return;
        if (callInfo.getCtrlUuid() != null) {
            ctrlSessions.put(callInfo.getCtrlUuid(), callInfo);
        }
        if (callInfo.getCallUuid() != null && callInfo.getCtrlUuid() != null) {
            callToCtrl.put(callInfo.getCallUuid(), callInfo.getCtrlUuid());
        }
        if (callInfo.getAgentChannelUuid() != null && callInfo.getCtrlUuid() != null) {
            channelToCtrl.put(callInfo.getAgentChannelUuid(), callInfo.getCtrlUuid());
        }
        if (callInfo.getGuestChannelUuid() != null && callInfo.getCtrlUuid() != null) {
            channelToCtrl.put(callInfo.getGuestChannelUuid(), callInfo.getCtrlUuid());
        }
        log.info("📋 [会话注册] CtrlUUID: {}, CallUUID: {}, Model: {}",
                callInfo.getCtrlUuid(), callInfo.getCallUuid(), callInfo.getModelKey());
    }

    public void bindChannel(String channelUuid, String ctrlUuid) {
        if (channelUuid != null && ctrlUuid != null) {
            channelToCtrl.put(channelUuid, ctrlUuid);
        }
    }

    public Optional<CallInfoBO> getByCtrlUuid(String ctrlUuid) {
        if (ctrlUuid == null) return Optional.empty();
        return Optional.ofNullable(ctrlSessions.get(ctrlUuid));
    }

    public Optional<CallInfoBO> getByChannelUuid(String channelUuid) {
        if (channelUuid == null) return Optional.empty();
        String ctrlUuid = channelToCtrl.get(channelUuid);
        if (ctrlUuid != null) {
            return getByCtrlUuid(ctrlUuid);
        }
        ctrlUuid = callToCtrl.get(channelUuid);
        if (ctrlUuid != null) {
            return getByCtrlUuid(ctrlUuid);
        }
        return Optional.empty();
    }

    public void removeSession(String ctrlUuid) {
        if (ctrlUuid == null) return;
        CallInfoBO info = ctrlSessions.remove(ctrlUuid);
        if (info != null) {
            if (info.getCallUuid() != null) callToCtrl.remove(info.getCallUuid());
            if (info.getAgentChannelUuid() != null) channelToCtrl.remove(info.getAgentChannelUuid());
            if (info.getGuestChannelUuid() != null) channelToCtrl.remove(info.getGuestChannelUuid());
            log.info("🗑️ [会话释放] CtrlUUID: {}, CallUUID: {}", ctrlUuid, info.getCallUuid());
        }
    }
}
