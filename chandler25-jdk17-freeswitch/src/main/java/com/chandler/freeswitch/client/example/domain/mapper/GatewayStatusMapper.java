package com.chandler.freeswitch.client.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.freeswitch.client.example.domain.dataobject.GatewayStatus;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网关状态Mapper
 *
 * @author chandler
 * @since 1.0
 */
@Mapper
public interface GatewayStatusMapper extends BaseMapper<GatewayStatus> {
}
