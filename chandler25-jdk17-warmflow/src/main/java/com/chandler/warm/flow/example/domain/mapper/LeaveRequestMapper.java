package com.chandler.warm.flow.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.warm.flow.example.domain.dataobject.LeaveRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 请假单 mapper。
 */
@Mapper
public interface LeaveRequestMapper extends BaseMapper<LeaveRequest> {
}
