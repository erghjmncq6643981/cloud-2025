package com.chandler.freeswitch.client.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.freeswitch.client.example.domain.dataobject.CommandLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * ESL 控制指令日志 Mapper
 */
@Mapper
public interface CommandLogMapper extends BaseMapper<CommandLog> {
}
