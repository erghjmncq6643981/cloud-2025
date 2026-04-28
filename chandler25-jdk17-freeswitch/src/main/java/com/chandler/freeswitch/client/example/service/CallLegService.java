package com.chandler.freeswitch.client.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.freeswitch.client.example.domain.dataobject.CallLeg;
import com.chandler.freeswitch.client.example.domain.mapper.CallLegMapper;
import org.springframework.stereotype.Service;

/**
 * 通话 Leg 服务
 */
@Service
public class CallLegService extends ServiceImpl<CallLegMapper, CallLeg> {

    public CallLeg getByUuid(String uuid) {
        return getOne(
                new LambdaQueryWrapper<CallLeg>()
                        .eq(CallLeg::getUuid, uuid)
                        .last("limit 1")
        );
    }

    public void updateStatus(Long id, String status) {
        if (id == null) {
            return;
        }
        CallLeg callLeg = CallLeg.builder()
                .id(id)
                .status(status)
                .build();
        updateById(callLeg);
    }
}
