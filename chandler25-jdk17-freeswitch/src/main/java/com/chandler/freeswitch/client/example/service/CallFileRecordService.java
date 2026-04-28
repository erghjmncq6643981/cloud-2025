package com.chandler.freeswitch.client.example.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.freeswitch.client.example.domain.dataobject.CallFileRecord;
import com.chandler.freeswitch.client.example.domain.mapper.CallFileRecordMapper;
import org.springframework.stereotype.Service;

/**
 * 录音文件记录服务
 */
@Service
public class CallFileRecordService extends ServiceImpl<CallFileRecordMapper, CallFileRecord> {
}
