package com.chandler.freeswitch.client.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.freeswitch.client.example.domain.dataobject.CallTimeline;
import com.chandler.freeswitch.client.example.domain.mapper.CallTimelineMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 通话业务时间线服务
 */
@Service
public class CallTimelineService extends ServiceImpl<CallTimelineMapper, CallTimeline> {

    /**
     * 记录业务时间线节点
     */
    public void record(Long sessionId, String bizId, Long legId, String channelUuid,
                       String nodeType, String eventType, String eventDesc) {
        if (sessionId == null) {
            return;
        }
        Date now = new Date();

        save(CallTimeline.builder()
                .sessionId(sessionId)
                .bizId(bizId)
                .legId(legId)
                .channelUuid(channelUuid)
                .nodeType(nodeType)
                .eventType(eventType)
                .eventDesc(eventDesc)
                .eventTime(now)
                .build());
    }

    public List<CallTimeline> listBySessionId(Long sessionId) {
        if (sessionId == null) {
            return java.util.Collections.emptyList();
        }
        return list(
                new LambdaQueryWrapper<CallTimeline>()
                        .eq(CallTimeline::getSessionId, sessionId)
                        .orderByAsc(CallTimeline::getEventTime)
                        .orderByAsc(CallTimeline::getId)
        );
    }
}
