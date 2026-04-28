package com.chandler.freeswitch.client.example.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.freeswitch.client.example.domain.dataobject.CallSession;
import com.chandler.freeswitch.client.example.domain.mapper.CallSessionMapper;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 通话业务会话服务
 */
@Service
public class CallSessionService extends ServiceImpl<CallSessionMapper, CallSession> {

    public void updateStatus(Long id, Integer status) {
        updateStatus(id, status, null);
    }

    public void updateStatus(Long id, Integer status, String hangupReason) {
        if (id == null) {
            return;
        }
        CallSession callSession = CallSession.builder()
                .id(id)
                .status(status)
                .hangupReason(hangupReason)
                .build();
        if (Integer.valueOf(2).equals(status) || Integer.valueOf(3).equals(status)) {
            callSession.setEndTime(new Date());
        }
        updateById(callSession);
    }
}
