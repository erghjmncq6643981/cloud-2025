package com.chandler.freeswitch.client.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.freeswitch.client.example.domain.dataobject.CallSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通话业务会话 Mapper
 */
@Mapper
public interface CallSessionMapper extends BaseMapper<CallSession> {
}
