package com.chandler.freeswitch.client.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.freeswitch.client.example.domain.dataobject.CallEventLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * ESL事件日志 Mapper
 */
@Mapper
public interface CallEventLogMapper extends BaseMapper<CallEventLog> {
}
