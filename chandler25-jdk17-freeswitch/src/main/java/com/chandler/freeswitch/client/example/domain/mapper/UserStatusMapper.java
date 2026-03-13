package com.chandler.freeswitch.client.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.freeswitch.client.example.domain.dataobject.UserStatus;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户状态Mapper
 *
 * @author chandler
 * @since 1.0
 */
@Mapper
public interface UserStatusMapper extends BaseMapper<UserStatus> {
}
