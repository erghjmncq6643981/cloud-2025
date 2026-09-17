package com.chandler.freeswitch.client.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.freeswitch.client.example.domain.dataobject.CallTimeline;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通话时间线 Mapper
 */
@Mapper
public interface CallTimelineMapper extends BaseMapper<CallTimeline> {
}
