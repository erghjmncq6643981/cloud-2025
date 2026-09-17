package com.chandler.freeswitch.client.example.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.freeswitch.client.example.domain.dataobject.CallEventLog;
import com.chandler.freeswitch.client.example.domain.mapper.CallEventLogMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * ESL 原始事件审计日志服务
 */
@Service
public class CallEventLogService extends ServiceImpl<CallEventLogMapper, CallEventLog> {

    /**
     * 异步或同步记录一条 ESL 原始事件
     */
    public void recordEvent(Long sessionId, String bizId, String channelUuid,
                            String eventName, String eventSubclass, String hangupCause,
                            Map<String, String> headers) {
        String jsonHeaders = null;
        if (headers != null && !headers.isEmpty()) {
            try {
                jsonHeaders = JSON.toJSONString(headers);
            } catch (Exception ignored) {
            }
        }

        save(CallEventLog.builder()
                .sessionId(sessionId)
                .bizId(bizId)
                .channelUuid(channelUuid)
                .eventName(eventName)
                .eventSubclass(eventSubclass)
                .hangupCause(hangupCause)
                .rawHeaders(jsonHeaders)
                .eventTime(new Date())
                .build());
    }

    public List<CallEventLog> listBySessionId(Long sessionId) {
        if (sessionId == null) {
            return java.util.Collections.emptyList();
        }
        return list(
                new LambdaQueryWrapper<CallEventLog>()
                        .eq(CallEventLog::getSessionId, sessionId)
                        .orderByAsc(CallEventLog::getId)
        );
    }

    public List<CallEventLog> listByUuid(String channelUuid) {
        if (channelUuid == null) {
            return java.util.Collections.emptyList();
        }
        return list(
                new LambdaQueryWrapper<CallEventLog>()
                        .eq(CallEventLog::getChannelUuid, channelUuid)
                        .orderByAsc(CallEventLog::getId)
        );
    }
}
