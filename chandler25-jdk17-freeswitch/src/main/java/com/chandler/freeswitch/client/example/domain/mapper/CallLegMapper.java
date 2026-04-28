package com.chandler.freeswitch.client.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.freeswitch.client.example.domain.dataobject.CallLeg;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通话 Leg Mapper
 */
@Mapper
public interface CallLegMapper extends BaseMapper<CallLeg> {
}
