package com.chandler.freeswitch.client.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.freeswitch.client.example.domain.dataobject.DtmfRecord;
import com.chandler.freeswitch.client.example.domain.mapper.DtmfRecordMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * DTMF 按键明细记录服务
 */
@Service
public class DtmfRecordService extends ServiceImpl<DtmfRecordMapper, DtmfRecord> {

    public void recordDigit(Long sessionId, String channelUuid, String extension, String digit, Integer durationMs, String stepName) {
        save(DtmfRecord.builder()
                .sessionId(sessionId)
                .channelUuid(channelUuid)
                .extension(extension)
                .digit(digit)
                .durationMs(durationMs)
                .stepName(stepName != null ? stepName : "DEFAULT_IVR")
                .createdAt(new Date())
                .build());
    }

    public List<DtmfRecord> listBySessionId(Long sessionId) {
        if (sessionId == null) {
            return java.util.Collections.emptyList();
        }
        return list(
                new LambdaQueryWrapper<DtmfRecord>()
                        .eq(DtmfRecord::getSessionId, sessionId)
                        .orderByAsc(DtmfRecord::getId)
        );
    }
}
